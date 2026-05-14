package com.circleguard.promotion.integration;

/**
 * Base type for promotion-service integration tests (Testcontainers).
 *
 * <p>CI: Jenkins {@code runIntegrationTests.groovy} sets {@code TESTCONTAINERS_REUSE_ENABLE=false}
 * so containers are always fresh.
 *
 * <p>Local reuse: add {@code testcontainers.reuse.enable=true} to {@code ~/.testcontainers.properties}
 * (containers below use {@code .withReuse(true)}).
 */
public abstract class AbstractPromotionIntegrationTest {}
