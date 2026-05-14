package com.circleguard.promotion.service;

import com.circleguard.promotion.integration.AbstractPromotionIntegrationTest;
import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
public class AdministrativeCorrectionTest extends AbstractPromotionIntegrationTest {

    /** Neo4j → Postgres → Redis when Kafka is mocked only (field declaration order). */
    @Container
    @SuppressWarnings("resource")
    static Neo4jContainer<?> neo4j =
            new Neo4jContainer<>("neo4j:5.26").withAdminPassword("password").withReuse(true);

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("testuser")
                    .withPassword("testpassword")
                    .withReuse(true);

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379).withReuse(true);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "password");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private HealthStatusService statusService;

    @Autowired
    private CircleService circleService;

    @Autowired
    private UserNodeRepository userRepository;

    @Autowired
    private CircleNodeRepository circleRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setup() {
        circleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void invalidateCircle_PreventsPropagation() {
        UserNode a = UserNode.builder().anonymousId("A").status("ACTIVE").build();
        UserNode b = UserNode.builder().anonymousId("B").status("ACTIVE").build();
        userRepository.save(a);
        userRepository.save(b);

        CircleNode circle = circleService.createCircle("RiskGroup", "loc1");
        userRepository.recordEncounter("A", "B", System.currentTimeMillis(), "loc1");
        circleRepository.joinCircle("A", circle.getInviteCode());
        circleRepository.joinCircle("B", circle.getInviteCode());

        circleService.toggleCircleValidity(circle.getId());

        userRepository.purgeStaleEncounters(System.currentTimeMillis() + 10000);
        statusService.updateStatus("A", "CONFIRMED");

        statusService.getCachedStatus("B");
        assertThat(userRepository.findById("B").get().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void forceFence_PromotesAllMembers() {
        UserNode a = UserNode.builder().anonymousId("A").status("ACTIVE").build();
        UserNode b = UserNode.builder().anonymousId("B").status("ACTIVE").build();
        userRepository.save(a);
        userRepository.save(b);

        CircleNode circle = circleService.createCircle("Forced containment", "loc2");
        circleRepository.joinCircle("A", circle.getInviteCode());
        circleRepository.joinCircle("B", circle.getInviteCode());

        circleService.forceFenceCircle(circle.getId());

        assertThat(userRepository.findById("A").get().getStatus()).isEqualTo("PROBABLE");
        assertThat(userRepository.findById("B").get().getStatus()).isEqualTo("PROBABLE");
    }
}
