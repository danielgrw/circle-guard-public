package com.circleguard.test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * HS256 JWTs aligned with {@code JwtTokenService} / {@code JwtAuthenticationFilter}: subject = anonymousId string,
 * optional {@code permissions} claim.
 */
public final class TestJwtFactory {

    /** Default test TTL (ms), matches typical {@code jwt.expiration} in service test {@code application.yml}. */
    public static final long DEFAULT_EXPIRATION_MS = 3_600_000L;

    private TestJwtFactory() {}

    public static String generateToken(UUID anonymousId, String secret) {
        return generateToken(anonymousId, secret, DEFAULT_EXPIRATION_MS, List.of());
    }

    public static String generateToken(
            UUID anonymousId, String secret, long expirationMillis, List<String> permissions) {
        Objects.requireNonNull(anonymousId, "anonymousId");
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(permissions, "permissions");
        Key key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new HashMap<>();
        claims.put("permissions", permissions);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(anonymousId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
