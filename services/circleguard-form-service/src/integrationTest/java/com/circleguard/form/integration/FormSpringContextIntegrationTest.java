package com.circleguard.form.integration;

import com.circleguard.form.FormApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Kafka → Postgres {@code @Container} order (AC4). */
@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
class FormSpringContextIntegrationTest {

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

    @DynamicPropertySource
    static void infrastructureProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void loadsApplicationContext(@Autowired ApplicationContext applicationContext) {
        assertNotNull(applicationContext.getBean(FormApplication.class));
    }
}
