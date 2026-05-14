package com.circleguard.identity.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Pattern;

import com.circleguard.identity.IdentityServiceApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sync chain — identity vault HTTP (maps synthetic {@code realIdentity} → UUID {@code anonymousId}).
 */
@SpringBootTest(classes = IdentityServiceApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("integration-test")
@Testcontainers
class IdentityMapEndpointIntegrationTest {

    private static final Pattern UUID_RX =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("circleguard_identity_map_it")
                    .withUsername("admin")
                    .withPassword("password")
                    .withReuse(true);

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Sync chain: POST /map returns UUID anonymousId for deterministic synthetic identity key")
    void map_returnsAnonymousId() throws Exception {
        String bodyJson = "{\"realIdentity\":\"SYNTHETIC_EPIC_MAP_KEY_004\"}";
        MvcResult result =
                mockMvc.perform(post("/api/v1/identities/map")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyJson))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.anonymousId").exists())
                        .andReturn();
        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(root.path("anonymousId").asText());
        assertThat(id.toString()).matches(UUID_RX);
    }

    @Test
    @DisplayName("Sync chain: POST /map is idempotent for the same synthetic identity")
    void map_sameIdentity_returnsSameAnonymousId() throws Exception {
        String bodyJson = "{\"realIdentity\":\"SYNTHETIC_EPIC_MAP_IDEMPOTENT_004\"}";
        String firstBody =
                mockMvc.perform(post("/api/v1/identities/map")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyJson))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String secondBody =
                mockMvc.perform(post("/api/v1/identities/map")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyJson))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID a = UUID.fromString(MAPPER.readTree(firstBody).path("anonymousId").asText());
        UUID b = UUID.fromString(MAPPER.readTree(secondBody).path("anonymousId").asText());
        assertThat(b).isEqualTo(a);
    }
}
