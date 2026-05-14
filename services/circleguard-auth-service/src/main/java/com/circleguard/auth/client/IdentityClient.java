package com.circleguard.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Component
public class IdentityClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${circleguard.identity.map-url:http://localhost:8083/api/v1/identities/map}")
    private String identityMapUrl;

    public UUID getAnonymousId(String realIdentity) {
        Map<String, String> request = Map.of("realIdentity", realIdentity);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(identityMapUrl, request, Map.class);
        if (response == null || response.get("anonymousId") == null) {
            throw new IllegalStateException("identity /map returned no anonymousId");
        }
        Object raw = response.get("anonymousId");
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(raw.toString());
    }
}
