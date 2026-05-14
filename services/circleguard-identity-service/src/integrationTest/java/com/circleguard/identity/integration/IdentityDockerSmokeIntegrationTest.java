package com.circleguard.identity.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class IdentityDockerSmokeIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("circleguard_identity_it")
                    .withUsername("admin")
                    .withPassword("password")
                    .withReuse(true);

    @Test
    void postgresStarts() {
        assertTrue(postgres.isRunning());
    }
}
