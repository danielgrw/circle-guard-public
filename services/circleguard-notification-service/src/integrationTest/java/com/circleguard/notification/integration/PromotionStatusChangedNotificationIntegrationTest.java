package com.circleguard.notification.integration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.circleguard.notification.NotificationApplication;
import com.circleguard.notification.service.LmsService;
import com.circleguard.notification.service.NotificationDispatcher;
import com.circleguard.test.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.awaitility.Awaitility;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Async chain — {@code promotion.status.changed} fan-out terminates in {@link
 * com.circleguard.notification.service.ExposureNotificationListener}.
 */
@SpringBootTest(classes = NotificationApplication.class)
@ActiveProfiles("integration-test")
@Testcontainers
class PromotionStatusChangedNotificationIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0")).withReuse(true);

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add(
                "spring.kafka.consumer.group-id",
                () -> "notification-it-" + java.util.UUID.randomUUID());
        registry.add(
                "spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add(
                "spring.kafka.producer.key-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add(
                "spring.kafka.producer.value-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
    }

    @Autowired @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;

    @MockBean private NotificationDispatcher notificationDispatcher;

    @MockBean private LmsService lmsService;

    @BeforeEach
    void resetMocks() {
        reset(notificationDispatcher, lmsService);
    }

    @Test
    @DisplayName("Async chain: promotion.status.changed delegates to dispatcher for non-ACTIVE status")
    void promotionStatusSuspect_dispatchesViaListener() throws Exception {
        java.util.UUID id = TestDataBuilder.randomAnonymousId();
        String aid = id.toString();
        String payload =
                "{\"anonymousId\":\"" + aid + "\",\"status\":\"SUSPECT\",\"timestamp\":"
                        + System.currentTimeMillis()
                        + "}";

        kafkaTemplate.send("promotion.status.changed", aid, payload).get(30, SECONDS);

        Awaitility.await()
                .atMost(10, SECONDS)
                .pollInterval(500, MILLISECONDS)
                .untilAsserted(() -> verify(notificationDispatcher).dispatch(eq(aid), eq("SUSPECT")));
    }
}
