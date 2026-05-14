package com.circleguard.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import com.circleguard.auth.AuthServiceApplication;
import com.circleguard.test.TestDataBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sync chain — auth-service login obtains {@code anonymousId} from configurable identity vault URL
 * (stubbed locally). LDAP provider is mocked so authentication falls through to the local JDBC DAO path.
 */
@SpringBootTest(
        classes = AuthServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class LoginIdentityChainIntegrationTest {

    private static final HttpServer IDENTITY_STUB;
    /** Current anonymousId returned by stub (set fresh per test method). */
    private static volatile UUID stubAnonymousUuid;

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("circleguard_auth_login_it")
                    .withUsername("admin")
                    .withPassword("password")
                    .withReuse(true);

    static {
        try {
            IDENTITY_STUB = HttpServer.create(new InetSocketAddress(0), 0);
            IDENTITY_STUB.createContext(
                    "/api/v1/identities/map",
                    LoginIdentityChainIntegrationTest::serveIdentityMap);
            IDENTITY_STUB.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        int port = IDENTITY_STUB.getAddress().getPort();
        registry.add("circleguard.identity.map-url",
                () -> "http://127.0.0.1:" + port + "/api/v1/identities/map");
    }

    private static void serveIdentityMap(HttpExchange exchange) throws java.io.IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
        UUID anon = stubAnonymousUuid;
        if (anon == null) {
            byte[] msg = "{\"message\":\"stub not initialized\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, msg.length);
            exchange.getResponseBody().write(msg);
            exchange.close();
            return;
        }
        byte[] resp = ("{\"anonymousId\":\"" + anon + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, resp.length);
        exchange.getResponseBody().write(resp);
        exchange.close();
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Spring injects mocked LDAP provider → {@link DualChainAuthenticationProvider} falls through to JDBC.
     */
    @MockBean
    private LdapAuthenticationProvider ldapAuthenticationProvider;

    private static final String IT_USER = "synthetic-it-login-244";

    @BeforeEach
    void stubAndSeedUser() {
        stubAnonymousUuid = TestDataBuilder.randomAnonymousId();
        when(ldapAuthenticationProvider.authenticate(any()))
                .thenThrow(new BadCredentialsException("LDAP bypassed for integration test"));
        when(ldapAuthenticationProvider.supports(any())).thenReturn(true);
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM local_users WHERE username = ?)", IT_USER);
        jdbcTemplate.update("DELETE FROM local_users WHERE username = ?", IT_USER);
        String hash = passwordEncoder.encode("IntegrationTestPass!244");
        UUID uid = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO local_users (id, username, password_hash, email, is_active) VALUES (?,?,?,?,true)",
                uid,
                IT_USER,
                hash,
                "synthetic-it-nonpii@test.local");
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) SELECT ?, r.id FROM roles r WHERE r.name = 'STUDENT'", uid);
    }

    @Test
    @DisplayName("Sync chain: login JWT subject matches identity vault anonymousId from HTTP stub")
    void loginJwtSubject_matchesIdentityAnonymousId() {
        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        "/api/v1/auth/login",
                        Map.of("username", IT_USER, "password", "IntegrationTestPass!244"),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("token", "anonymousId");
        assertThat(response.getBody().get("anonymousId")).isEqualTo(stubAnonymousUuid.toString());
        String token = (String) response.getBody().get("token");
        Claims claims =
                Jwts.parserBuilder()
                        .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
        assertThat(claims.getSubject()).isEqualTo(stubAnonymousUuid.toString());
    }
}
