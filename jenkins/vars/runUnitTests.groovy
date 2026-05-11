/**
 * runUnitTests — Jenkins Shared Library step
 *
 * Runs the unit test task for a single service and always publishes JUnit XML
 * results (even when tests fail), satisfying AC-1.4.4.
 *
 * Unit tests live under src/test/java/ and do NOT spin up containers.
 * JUnit XML output: services/{serviceName}/build/test-results/**\/*.xml
 *
 * The glob is scoped to the specific service subproject to prevent cross-service
 * result contamination when multiple services share a single non-clean workspace.
 *
 * @param serviceName  Full Gradle subproject name, e.g. "circleguard-auth-service"
 *
 * Usage in Jenkinsfile:
 *   runUnitTests("circleguard-auth-service")
 */
def call(String serviceName) {
    if (!serviceName?.trim()) {
        error("runUnitTests: serviceName must not be blank")
    }
    if (serviceName =~ /[^a-zA-Z0-9_-]/) {
        error("runUnitTests: serviceName '${serviceName}' contains invalid characters (allowed: a-z A-Z 0-9 _ -)")
    }

    echo "=== runUnitTests: ${serviceName} ==="
    try {
        sh "./gradlew :services:${serviceName}:test"
    } finally {
        // Scoped to this service's subproject — avoids contaminating results
        // from other services that may exist in the same workspace
        junit allowEmptyResults: true,
              testResults: "services/${serviceName}/build/test-results/**/*.xml"
    }
}
