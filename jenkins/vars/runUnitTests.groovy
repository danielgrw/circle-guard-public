/**
 * runUnitTests — Jenkins Shared Library step
 *
 * Runs the unit test task for a single service and always publishes JUnit XML
 * results (even when tests fail), satisfying AC-1.4.4.
 *
 * Unit tests live under src/test/java/ and do NOT spin up containers.
 * JUnit XML output: services/{serviceName}/build/test-results/test/**/*.xml
 *
 * The glob is scoped to the unit-test Gradle task name to avoid mixing integration
 * results and to surface separate checks in Jenkins (Story 2.5 / AC4).
 *
 * On non-zero Gradle exit: publishes reports then error("Tests failed") (architecture
 * pipeline failure contract).
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
    def rc = 1
    try {
        rc = sh(script: "./gradlew :services:${serviceName}:test", returnStatus: true)
    } finally {
        junit allowEmptyResults: true,
              testResults: "services/${serviceName}/build/test-results/test/**/*.xml",
              checksName: "Unit — ${serviceName}"
    }
    if (rc != 0) {
        error("Tests failed")
    }
}
