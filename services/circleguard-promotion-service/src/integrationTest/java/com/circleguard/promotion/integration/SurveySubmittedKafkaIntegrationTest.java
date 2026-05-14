package com.circleguard.promotion.integration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.circleguard.promotion.PromotionApplication;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.service.HealthStatusService;
import com.circleguard.test.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.awaitility.Awaitility;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Async chain — {@code survey.submitted} consumed by promotion-service ({@link
 * com.circleguard.promotion.listener.SurveyListener}) with observable Redis status within the epic
 * window.
 */
@SpringBootTest(classes = PromotionApplication.class)
@ActiveProfiles("integration-test")
@Testcontainers
class SurveySubmittedKafkaIntegrationTest {

    private static final String TOPIC = "survey.submitted";

    @Container
    @SuppressWarnings("resource")
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0")).withReuse(true);

    @Container
    @SuppressWarnings("resource")
    static Neo4jContainer<?> neo4jContainer =
            new Neo4jContainer<>("neo4j:5.26").withAdminPassword("password").withReuse(true);

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("promotion_kafka_it")
                    .withUsername("testuser")
                    .withPassword("testpassword")
                    .withReuse(true);

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379).withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4jContainer::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "password");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add(
                "spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add(
                "spring.kafka.producer.key-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add(
                "spring.kafka.producer.value-serializer",
                () -> "org.springframework.kafka.support.serializer.JsonSerializer");
        registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages", () -> "*");
    }

    @Autowired private HealthStatusService healthStatusService;

    @Autowired private UserNodeRepository userNodeRepository;

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

    private String graphUserStatus(String anonymousId) {
        return userNodeRepository.findById(anonymousId).map(UserNode::getStatus).orElse(null);
    }

    @BeforeEach
    void purgeGraph() {
        userNodeRepository.deleteAll();
    }

    @Test
    @DisplayName("Async chain: survey.submitted (symptoms) promotes Redis status to SUSPECT within 10s")
    void surveySubmittedWithSymptoms_updatesRedisWithinTenSeconds() throws Exception {
        UUID aid = TestDataBuilder.randomAnonymousId();
        userNodeRepository.save(
                UserNode.builder().anonymousId(aid.toString()).status("ACTIVE").build());

        Map<String, Object> event = new HashMap<>();
        event.put("anonymousId", aid.toString());
        event.put("hasSymptoms", true);
        event.put("timestamp", System.currentTimeMillis());

        kafkaTemplate.send(TOPIC, aid.toString(), event).get(30, SECONDS);

        Awaitility.await()
                .atMost(10, SECONDS)
                .pollInterval(500, MILLISECONDS)
                .untilAsserted(
                        () -> {
                            assertThat(graphUserStatus(aid.toString())).isEqualTo("SUSPECT");
                            assertThat(healthStatusService.getCachedStatus(aid.toString()))
                                    .isEqualTo("SUSPECT");
                        });
    }
}
