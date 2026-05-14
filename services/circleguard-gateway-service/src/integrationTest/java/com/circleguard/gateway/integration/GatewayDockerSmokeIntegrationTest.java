package com.circleguard.gateway.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minimal Docker smoke — proves {@code integrationTest} classpath + ordering constraints:
 * only Redis applies here (Kafka / Neo4j / Postgres N/A for this service).
 */
@Testcontainers
class GatewayDockerSmokeIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379).withReuse(true);

    @Test
    void redisStartsAndMapsPort() {
        assertTrue(redis.isRunning());
        assertTrue(redis.getMappedPort(6379) > 0);
    }
}
