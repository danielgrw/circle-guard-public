package com.circleguard.auth;

import com.circleguard.auth.support.YamlPropertySourceFactory;
import com.circleguard.test.TestDataBuilder;
import com.circleguard.test.TestJwtFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ensures tokens from {@code test-support} match auth-service JWT parsing (secret from test {@code application.yml}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestJwtFactoryAuthContractTest.Config.class)
@TestPropertySource(factory = YamlPropertySourceFactory.class, value = "classpath:application.yml")
class TestJwtFactoryAuthContractTest {

    @Autowired
    private Environment environment;

    @Configuration
    static class Config {
    }

    @Test
    void factoryToken_subjectMatchesAnonymousId_usingAuthJwtParser() {
        String jwtSecret = environment.getRequiredProperty("jwt.secret");

        UUID anonymousId = TestDataBuilder.randomAnonymousId();
        String token = TestJwtFactory.generateToken(anonymousId, jwtSecret);

        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();

        assertEquals(anonymousId.toString(), claims.getSubject());
    }
}
