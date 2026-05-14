package com.circleguard.promotion.service;

import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatusLifecycleTest {

    private static final String LIFECYCLE_LOG_QUERY =
            "MATCH (u:User) "
                    + "WHERE u.status IN ['SUSPECT', 'PROBABLE'] "
                    + "RETURN u.anonymousId as id, u.statusUpdatedAt as updated, $threshold as thresh";
    private static final String LIFECYCLE_UPDATE_QUERY =
            "MATCH (u:User) "
                    + "WHERE u.status IN ['SUSPECT', 'PROBABLE'] "
                    + "  AND coalesce(u.statusUpdatedAt, 0) < $threshold "
                    + "SET u.status = 'ACTIVE', u.statusUpdatedAt = timestamp() "
                    + "RETURN collect(u.anonymousId) as releasedIds";

    @Mock
    private Neo4jClient neo4jClient;

    @Mock
    private SystemSettingsRepository settingsRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private StatusLifecycleService lifecycleService;

    @BeforeEach
    void setup() {
        lifecycleService = new StatusLifecycleService(
                neo4jClient, settingsRepository, redisTemplate, kafkaTemplate);

        SystemSettings settings = SystemSettings.builder()
                .unconfirmedFencingEnabled(true)
                .autoThresholdSeconds(3600L)
                .mandatoryFenceDays(14)
                .encounterWindowDays(14)
                .build();
        when(settingsRepository.getSettings()).thenReturn(Optional.of(settings));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void automaticTransition_ReleasesExpiredUsers() {
        Map<String, Object> resultMap = Map.of(
                "releasedIds", List.of("EXPIRED_USER")
        );
        stubNeo4jQueriesForLifecycle(resultMap);
        lifecycleService.processAutomaticTransitions();

        verify(valueOperations).multiSet(ArgumentMatchers.anyMap());
        verify(kafkaTemplate).send(ArgumentMatchers.eq("promotion.status.changed"), ArgumentMatchers.eq("EXPIRED_USER"), ArgumentMatchers.anyMap());
    }

    @Test
    void automaticTransition_HandlesEmptyResults() {
        Map<String, Object> resultMap = Map.of(
                "releasedIds", Collections.emptyList()
        );
        stubNeo4jQueriesForLifecycle(resultMap);
        lifecycleService.processAutomaticTransitions();

        verify(valueOperations, Mockito.never()).multiSet(ArgumentMatchers.anyMap());
    }

    private void stubNeo4jQueriesForLifecycle(Map<String, Object> updateResult) {
        when(neo4jClient.query(eq(LIFECYCLE_LOG_QUERY)))
                .thenAnswer(inv -> {
                    Neo4jClient.UnboundRunnableSpec spec =
                            mock(Neo4jClient.UnboundRunnableSpec.class, Mockito.RETURNS_DEEP_STUBS);
                    when(spec.bind(anyLong()).to("threshold").fetch().all()).thenReturn(Collections.emptyList());
                    return spec;
                });
        when(neo4jClient.query(eq(LIFECYCLE_UPDATE_QUERY)))
                .thenAnswer(inv -> {
                    Neo4jClient.UnboundRunnableSpec spec =
                            mock(Neo4jClient.UnboundRunnableSpec.class, Mockito.RETURNS_DEEP_STUBS);
                    when(spec.bind(anyLong()).to("threshold").fetch().one()).thenReturn(Optional.of(updateResult));
                    return spec;
                });
    }
}
