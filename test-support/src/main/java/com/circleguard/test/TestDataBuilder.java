package com.circleguard.test;

import java.util.UUID;

/**
 * Privacy-safe anonymous identifiers for tests. Never use real names, emails, or student IDs.
 */
public final class TestDataBuilder {

    private TestDataBuilder() {}

    /** Random type-4 UUID suitable as {@code anonymousId} in fixtures. */
    public static UUID randomAnonymousId() {
        return UUID.randomUUID();
    }
}
