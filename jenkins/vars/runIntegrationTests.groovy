/**
 * runIntegrationTests — Jenkins Shared Library step
 *
 * Runs the integrationTest Gradle task for a single service with
 * TESTCONTAINERS_REUSE_ENABLE=false to prevent stale container reuse in CI.
 * Always publishes JUnit XML results from the integrationTest source set.
 *
 * Integration tests live under src/integrationTest/java/ and start real
 * Testcontainers (Kafka → Neo4j → Postgres → Redis order as applicable).
 *
 * JUnit XML output: services/{serviceName}/build/test-results/integrationTest/ ... /*.xml
 *
 * On non-zero Gradle exit: publishes reports then error("Tests failed").
 *
 * @param serviceName  Full Gradle subproject name, e.g. "circleguard-auth-service"
 *
 * Usage in Jenkinsfile:
 *   runIntegrationTests("circleguard-auth-service")
 */
def call(String serviceName) {
    if (!serviceName?.trim()) {
        error("runIntegrationTests: serviceName must not be blank")
    }
    if (serviceName =~ /[^a-zA-Z0-9_-]/) {
        error("runIntegrationTests: serviceName '${serviceName}' contains invalid characters (allowed: a-z A-Z 0-9 _ -)")
    }

    echo "=== runIntegrationTests: ${serviceName} ==="
    withEnv(["TESTCONTAINERS_REUSE_ENABLE=false"]) {
        def rc = 1
        try {
            rc = sh(script: "./gradlew :services:${serviceName}:integrationTest", returnStatus: true)
        } finally {
            junit allowEmptyResults: true,
                  testResults: "services/${serviceName}/build/test-results/integrationTest/**/*.xml",
                  checksName: "Integration — ${serviceName}"
        }
        if (rc != 0) {
            error("Tests failed")
        }
    }
}
