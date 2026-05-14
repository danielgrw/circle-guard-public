package com.circleguard.promotion.service;

import com.circleguard.promotion.integration.AbstractPromotionIntegrationTest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
public class HealthStatusReevaluationTest extends AbstractPromotionIntegrationTest {

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

    @Autowired
    private Neo4jClient neo4jClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setup() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    @Test
    void testSingleRelease() {
        createNode("A", "CONFIRMED");
        createNode("B", "SUSPECT");
        createRelationship("A", "B");

        healthStatusService.resolveStatus("A");

        assertEquals("ACTIVE", getStatus("B"));
    }

    @Test
    void testBlockedRelease() {
        createNode("A", "CONFIRMED");
        createNode("B", "SUSPECT");
        createNode("C", "CONFIRMED");
        createRelationship("A", "B");
        createRelationship("C", "B");

        healthStatusService.resolveStatus("A");

        assertEquals("SUSPECT", getStatus("B"));
    }

    @Test
    void testMultiHopRelease() {
        createNode("A", "CONFIRMED");
        createNode("B", "SUSPECT");
        createNode("C", "PROBABLE");
        createRelationship("A", "B");
        createRelationship("B", "C");

        healthStatusService.resolveStatus("A");

        assertEquals("ACTIVE", getStatus("B"));
        assertEquals("ACTIVE", getStatus("C"));
    }

    @Test
    void testPartialReleaseInMesh() {
        createNode("A", "CONFIRMED");
        createNode("B", "SUSPECT");
        createNode("C", "PROBABLE");
        createNode("D", "SUSPECT");
        createRelationship("A", "B");
        createRelationship("B", "C");
        createRelationship("D", "C");

        healthStatusService.resolveStatus("A");

        assertEquals("ACTIVE", getStatus("B"));
        assertEquals("PROBABLE", getStatus("C"));
    }

    private void createNode(String id, String status) {
        neo4jClient.query("CREATE (:User {anonymousId: $id, status: $status})")
                .bind(id).to("id")
                .bind(status).to("status")
                .run();
    }

    private void createRelationship(String id1, String id2) {
        neo4jClient.query(
                        "MATCH (u1:User {anonymousId: $id1}), (u2:User {anonymousId: $id2}) "
                                + "CREATE (u1)-[:ENCOUNTERED {startTime: timestamp()}]->(u2)")
                .bind(id1).to("id1")
                .bind(id2).to("id2")
                .run();
    }

    private String getStatus(String id) {
        return neo4jClient.query("MATCH (u:User {anonymousId: $id}) RETURN u.status as status")
                .bind(id).to("id")
                .fetchAs(String.class).one().orElse("NOT_FOUND");
    }
}
