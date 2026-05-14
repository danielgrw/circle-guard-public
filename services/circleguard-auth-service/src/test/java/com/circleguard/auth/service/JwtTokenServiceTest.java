package com.circleguard.auth.service;

import com.circleguard.test.TestDataBuilder;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class JwtTokenServiceTest {

    private static final String SECRET = "my-super-secret-test-key-32-chars-long";
    private static final long EXPIRATION_MS = 3_600_000L;

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(SECRET, EXPIRATION_MS);
    }

    @Test
    void generateToken_setsSubjectToAnonymousId() {
        UUID anonymousId = TestDataBuilder.randomAnonymousId();
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getAuthorities()).thenAnswer(inv -> List.<GrantedAuthority>of());

        String token = jwtTokenService.generateToken(anonymousId, auth);

        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        assertEquals(anonymousId.toString(), claims.getSubject());
    }

    @Test
    void generateToken_includesPermissionClaimsFromAuthentication() {
        UUID anonymousId = TestDataBuilder.randomAnonymousId();
        Authentication auth = Mockito.mock(Authentication.class);
        GrantedAuthority authority = () -> "READ_GATE";
        when(auth.getAuthorities()).thenAnswer(inv -> List.of(authority));

        String token = jwtTokenService.generateToken(anonymousId, auth);

        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions");
        assertNotNull(permissions);
        assertTrue(permissions.contains("READ_GATE"));
    }
}
