package com.circleguard.form.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Declares Kafka → Postgres (@Container field order per Epic 2.2). */
@Testcontainers
class FormDockerSmokeIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0")).withReuse(true);

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("circleguard_form_it")
                    .withUsername("admin")
                    .withPassword("password")
                    .withReuse(true);

    @Test
    void kafkaAndPostgresStart() {
        assertTrue(kafka.isRunning());
        assertTrue(postgres.isRunning());
    }
}
