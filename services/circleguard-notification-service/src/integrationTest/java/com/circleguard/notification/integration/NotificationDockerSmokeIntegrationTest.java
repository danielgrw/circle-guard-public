package com.circleguard.notification.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class NotificationDockerSmokeIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0")).withReuse(true);

    @Test
    void kafkaStarts() {
        assertTrue(kafka.isRunning());
    }
}
