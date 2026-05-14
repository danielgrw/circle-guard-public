package com.circleguard.promotion.listener;

import com.circleguard.promotion.service.HealthStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SurveyListener {
    private final HealthStatusService healthStatusService;

    @KafkaListener(topics = "survey.submitted", groupId = "promotion-service-group")
    public void onSurveySubmitted(Map<String, Object> event) {
        log.info("Received survey submission event: {}", event);
        
        try {
            Object rawId = event.get("anonymousId");
            String anonymousId = rawId == null ? null : rawId.toString();
            Object rawSymptoms = event.get("hasSymptoms");
            boolean hasSymptoms =
                    Boolean.TRUE.equals(rawSymptoms)
                            || "true".equalsIgnoreCase(String.valueOf(rawSymptoms));

            if (anonymousId != null && hasSymptoms) {
                log.info("Promoting user {} to SUSPECT due to symptoms", anonymousId);
                healthStatusService.updateStatus(anonymousId, "SUSPECT");
            }
        } catch (Exception e) {
            log.error("Failed to process survey event: {}", event, e);
        }
    }

    @KafkaListener(topics = "certificate.validated", groupId = "promotion-service-group")
    public void onCertificateValidated(Map<String, Object> event) {
        log.info("Received certificate validation event: {}", event);
        
        try {
            Object rawId = event.get("anonymousId");
            String anonymousId = rawId == null ? null : rawId.toString();
            Object rawStatus = event.get("status");
            String status = rawStatus == null ? null : rawStatus.toString();

            if (anonymousId != null && "APPROVED".equals(status)) {
                log.info("Restoring user {} to ACTIVE due to approved certificate", anonymousId);
                healthStatusService.updateStatus(anonymousId, "ACTIVE");
            }
        } catch (Exception e) {
            log.error("Failed to process certificate validation event: {}", event, e);
        }
    }
}
