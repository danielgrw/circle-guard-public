package com.circleguard.form.integration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.circleguard.form.FormApplication;
import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.repository.QuestionnaireRepository;
import com.circleguard.form.service.HealthSurveyService;
import com.circleguard.test.TestDataBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.awaitility.Awaitility;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Async chain — form-service publishes {@code survey.submitted} aligned with producer payload shape used
 * by promotion {@code SurveyListener}.
 */
@SpringBootTest(classes = FormApplication.class)
@ActiveProfiles("integration-test")
@Testcontainers
class HealthSurveyKafkaSubmitIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0")).withReuse(true);

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("circleguard_form_kafka_it")
                    .withUsername("admin")
                    .withPassword("password")
                    .withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired private HealthSurveyService healthSurveyService;

    @Autowired private QuestionnaireRepository questionnaireRepository;

    @BeforeEach
    void wipe() {
        questionnaireRepository.deleteAll();
    }

    @Test
    @DisplayName("Async chain: submitSurvey emits survey.submitted record containing anonymous UUID")
    void submitSurvey_publishesSurveySubmittedKafkaRecord() throws Exception {
        Questionnaire questionnaire =
                Questionnaire.builder().title("IT active").description("syn").version(1).isActive(true).build();
        Question fever =
                Question.builder()
                        .questionnaire(questionnaire)
                        .text("Do you have a fever?")
                        .type(QuestionType.YES_NO)
                        .options("[]")
                        .orderIndex(1)
                        .build();
        questionnaire.setQuestions(List.of(fever));
        questionnaireRepository.save(questionnaire);

        Questionnaire active =
                questionnaireRepository
                        .findFirstByIsActiveTrueOrderByVersionDesc()
                        .orElseThrow(() -> new IllegalStateException("active questionnaire"));

        Question q =
                active.getQuestions().stream()
                        .filter(x -> x.getText().contains("fever"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("fever question missing"));

        UUID aid = TestDataBuilder.randomAnonymousId();
        Map<String, Object> responses = new HashMap<>();
        responses.put(q.getId().toString(), "YES");

        HealthSurvey survey =
                HealthSurvey.builder()
                        .anonymousId(aid)
                        .hasFever(true)
                        .hasCough(true)
                        .responses(responses)
                        .build();

        Properties cp = new Properties();
        cp.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        cp.put(ConsumerConfig.GROUP_ID_CONFIG, "form-it-" + UUID.randomUUID());
        cp.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        cp.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        cp.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(cp)) {
            consumer.subscribe(List.of("survey.submitted"));
            consumer.poll(Duration.ofMillis(200));

            healthSurveyService.submitSurvey(survey);

            Awaitility.await()
                    .atMost(20, SECONDS)
                    .pollInterval(400, MILLISECONDS)
                    .untilAsserted(
                            () -> {
                                ConsumerRecords<String, String> records =
                                        consumer.poll(Duration.ofMillis(900));
                                assertThat(records.isEmpty()).isFalse();
                                String value = records.iterator().next().value();
                                assertThat(value).contains(aid.toString());
                                assertThat(value).contains("hasSymptoms");
                            });
        }
    }
}
