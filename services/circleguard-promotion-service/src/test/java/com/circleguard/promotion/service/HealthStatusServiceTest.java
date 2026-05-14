package com.circleguard.promotion.service;

import com.circleguard.promotion.exception.FenceException;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import com.circleguard.test.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HealthStatusServiceTest {

    private static final String RESOLVE_USER_ACTIVE =
            "MATCH (u:User {anonymousId: $id}) SET u.status = 'ACTIVE', u.statusUpdatedAt = timestamp()";
    private static final String RESOLVE_PHASE1 =
            "MATCH (source:User {anonymousId: $id}) "
                    + "OPTIONAL MATCH (source)-[:ENCOUNTERED|MEMBER_OF]-(target:User) "
                    + "WHERE target.status = 'SUSPECT' "
                    + "AND NOT EXISTS { "
                    + "  MATCH (target)-[:ENCOUNTERED|MEMBER_OF]-(risk:User) "
                    + "  WHERE risk.status = 'CONFIRMED' AND risk.anonymousId <> $id "
                    + "} "
                    + "SET target.status = 'ACTIVE', target.statusUpdatedAt = timestamp() "
                    + "RETURN collect(DISTINCT target.anonymousId) as releasedIds";
    private static final String RESOLVE_PHASE2 =
            "MATCH (l1:User) WHERE l1.anonymousId IN $l1Ids "
                    + "OPTIONAL MATCH (l1)-[:ENCOUNTERED|MEMBER_OF]-(target:User) "
                    + "WHERE target.status = 'PROBABLE' "
                    + "AND NOT EXISTS { "
                    + "  MATCH (target)-[:ENCOUNTERED|MEMBER_OF]-(risk:User) "
                    + "  WHERE (risk.status = 'CONFIRMED' OR risk.status = 'SUSPECT') "
                    + "  AND NOT risk.anonymousId IN $l1Ids "
                    + "  AND risk.anonymousId <> $sourceId "
                    + "} "
                    + "SET target.status = 'ACTIVE', target.statusUpdatedAt = timestamp() "
                    + "RETURN collect(DISTINCT target.anonymousId) as releasedIds";

    @Mock
    private UserNodeRepository userNodeRepository;

    @Mock
    private Neo4jClient neo4jClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private SystemSettingsRepository systemSettingsRepository;

    @Mock
    private CircleNodeRepository circleNodeRepository;

    private HealthStatusService healthStatusService;

    @BeforeEach
    void setUp() {
        healthStatusService = new HealthStatusService(
                userNodeRepository,
                neo4jClient,
                redisTemplate,
                kafkaTemplate,
                systemSettingsRepository,
                circleNodeRepository
        );
    }

    @Test
    void shouldUpdateStatusSuccessfully() {
        String anonymousId = TestDataBuilder.randomAnonymousId().toString();
        String status = "SUSPECT";

        Neo4jClient.UnboundRunnableSpec runnableSpec =
                Mockito.mock(Neo4jClient.UnboundRunnableSpec.class, Mockito.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("sourceId", anonymousId);
        resultMap.put("affectedContacts", Collections.emptyList());

        when(runnableSpec.bind(anyString()).to(anyString())
                .bind(anyString()).to(anyString())
                .bind(ArgumentMatchers.anyLong()).to(anyString())
                .fetch().one())
                .thenReturn(Optional.of(resultMap));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(circleNodeRepository.findNewlyFencedCircles(anonymousId)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> healthStatusService.updateStatus(anonymousId, status));

        verify(kafkaTemplate).send(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());
    }

    @Test
    void shouldThrowExceptionWhenUpdatingStatusToActiveWithinFenceWindow() {
        String anonymousId = TestDataBuilder.randomAnonymousId().toString();

        long fiveDaysAgo = System.currentTimeMillis() - (5L * 24 * 60 * 60 * 1000);
        UserNode user = UserNode.builder()
                .anonymousId(anonymousId)
                .status("SUSPECT")
                .statusUpdatedAt(fiveDaysAgo)
                .build();

        when(userNodeRepository.findById(anonymousId)).thenReturn(Optional.of(user));

        SystemSettings settings = SystemSettings.builder()
                .mandatoryFenceDays(14)
                .build();
        when(systemSettingsRepository.getSettings()).thenReturn(Optional.of(settings));

        assertThrows(FenceException.class, () -> healthStatusService.resolveStatus(anonymousId));
    }

    @Test
    void shouldAllowOverrideWhenWithinFenceWindow() {
        String anonymousId = TestDataBuilder.randomAnonymousId().toString();

        long fiveDaysAgo = System.currentTimeMillis() - (5L * 24 * 60 * 60 * 1000);
        UserNode user = UserNode.builder()
                .anonymousId(anonymousId)
                .status("SUSPECT")
                .statusUpdatedAt(fiveDaysAgo)
                .build();

        when(userNodeRepository.findById(anonymousId)).thenReturn(Optional.of(user));

        SystemSettings settings = SystemSettings.builder()
                .mandatoryFenceDays(14)
                .build();
        when(systemSettingsRepository.getSettings()).thenReturn(Optional.of(settings));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        stubNeo4jForResolveStatus(anonymousId);

        assertDoesNotThrow(() -> healthStatusService.resolveStatus(anonymousId, true));
    }

    private void stubNeo4jForResolveStatus(String anonymousId) {
        when(neo4jClient.query(eq(RESOLVE_USER_ACTIVE)))
                .thenAnswer(invocation -> {
                    Neo4jClient.UnboundRunnableSpec spec =
                            mock(Neo4jClient.UnboundRunnableSpec.class, Mockito.RETURNS_DEEP_STUBS);
                    when(spec.bind(eq(anonymousId)).to("id").run()).thenReturn(null);
                    return spec;
                });
        when(neo4jClient.query(eq(RESOLVE_PHASE1)))
                .thenAnswer(invocation -> {
                    Neo4jClient.UnboundRunnableSpec spec =
                            mock(Neo4jClient.UnboundRunnableSpec.class, Mockito.RETURNS_DEEP_STUBS);
                    when(spec.bind(eq(anonymousId)).to("id").fetch().one())
                            .thenReturn(Optional.of(Map.of("releasedIds", Collections.<String>emptyList())));
                    return spec;
                });
        when(neo4jClient.query(eq(RESOLVE_PHASE2)))
                .thenAnswer(invocation -> {
                    Neo4jClient.UnboundRunnableSpec spec =
                            mock(Neo4jClient.UnboundRunnableSpec.class, Mockito.RETURNS_DEEP_STUBS);
                    when(spec.bind(ArgumentMatchers.<List<String>>any()).to("l1Ids").bind(eq(anonymousId)).to("sourceId").fetch().one())
                            .thenReturn(Optional.of(Map.of("releasedIds", Collections.emptyList())));
                    return spec;
                });
    }
}
