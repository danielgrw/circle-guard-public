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
 * The glob is scoped to the specific service subproject to prevent cross-service
 * result contamination when multiple services share a single non-clean workspace.
 * JUnit XML output: services/{serviceName}/build/test-results/**\/*.xml
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
        try {
            sh "./gradlew :services:${serviceName}:integrationTest"
        } finally {
            // Scoped to this service's subproject — avoids contaminating results
            // from other services that may exist in the same workspace
            junit allowEmptyResults: true,
                  testResults: "services/${serviceName}/build/test-results/**/*.xml"
        }
    }
}
