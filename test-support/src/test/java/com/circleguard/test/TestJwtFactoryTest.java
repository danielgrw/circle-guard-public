package com.circleguard.test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestJwtFactoryTest {

    private static final String SECRET = "my-super-secret-test-key-32-chars-long";

    @Test
    void generateToken_subjectMatchesAnonymousId() {
        UUID id = TestDataBuilder.randomAnonymousId();
        String token = TestJwtFactory.generateToken(id, SECRET);

        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();

        assertEquals(id.toString(), claims.getSubject());
        assertNotNull(claims.get("permissions", List.class));
    }

    @Test
    void generateToken_withPermissions_roundTrip() {
        UUID id = TestDataBuilder.randomAnonymousId();
        String token = TestJwtFactory.generateToken(id, SECRET, 60_000L, List.of("ROLE_USER"));

        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();

        @SuppressWarnings("unchecked")
        List<String> perms = claims.get("permissions", List.class);
        assertEquals(List.of("ROLE_USER"), perms);
    }
}
