package com.circleguard.promotion.performance;

import com.circleguard.promotion.integration.AbstractPromotionIntegrationTest;
import com.circleguard.promotion.service.HealthStatusService;
import com.circleguard.test.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
public class PromotionPerformanceTest extends AbstractPromotionIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static Neo4jContainer<?> neo4jContainer =
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
    static void neo4jProperties(DynamicPropertyRegistry registry) {
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
    }

    @Autowired
    private HealthStatusService healthStatusService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private Neo4jClient neo4jClient;

    private String rootUser;

    @BeforeEach
    void setupBenchmarkData() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        rootUser = TestDataBuilder.randomAnonymousId().toString();

        neo4jClient.query("CREATE (:User {anonymousId: $id, status: 'ACTIVE'})")
                .bind(rootUser).to("id")
                .run();

        neo4jClient.query(
                        "UNWIND range(1, 10000) as i "
                                + "CREATE (u:User {anonymousId: 'user-' + toString(i), status: 'ACTIVE'})")
                .run();

        neo4jClient.query(
                        "MATCH (root:User {anonymousId: $id}), (others:User) "
                                + "WHERE others.anonymousId <> $id "
                                + "WITH root, others LIMIT 50 "
                                + "CREATE (root)-[:ENCOUNTERED {startTime: timestamp()}]->(others)")
                .bind(rootUser).to("id")
                .run();

        neo4jClient.query(
                        "MATCH (u1:User), (u2:User) "
                                + "WHERE u1.anonymousId < u2.anonymousId "
                                + "WITH u1, u2 LIMIT 300 "
                                + "CREATE (u1)-[:ENCOUNTERED {startTime: timestamp()}]->(u2)")
                .run();
    }

    @Test
    void benchmarkPromotionPerformance() {
        String warmupUser = "user-1";
        healthStatusService.updateStatus(warmupUser, "CONFIRMED");

        long startTime = System.currentTimeMillis();

        healthStatusService.updateStatus(rootUser, "CONFIRMED");

        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < 30000, "Promotion cascade exceeded 30 second CI threshold. Actual: " + duration + "ms");

        Long suspectCount =
                neo4jClient.query(
                                "MATCH (root:User {anonymousId: $id})-[:ENCOUNTERED]-(c1:User) "
                                        + "WHERE c1.status = 'SUSPECT' RETURN count(c1) as count")
                        .bind(rootUser).to("id")
                        .fetchAs(Long.class)
                        .one()
                        .orElseThrow(() -> new AssertionError("expected L1 SUSPECT count"));
        assertTrue(suspectCount > 0, "No L1 contacts were promoted to SUSPECT");

        Long probableCount =
                neo4jClient.query(
                                "MATCH (root:User {anonymousId: $id})-[:ENCOUNTERED]-(c1)-[:ENCOUNTERED]-(c2:User) "
                                        + "WHERE c2.status = 'PROBABLE' AND c2.anonymousId <> root.anonymousId RETURN count(c2) as count")
                        .bind(rootUser).to("id")
                        .fetchAs(Long.class)
                        .one()
                        .orElseThrow(() -> new AssertionError("expected L2 PROBABLE count"));
        assertTrue(probableCount > 0, "No L2 contacts were promoted to PROBABLE");
    }
}
