/**
 * buildAndPush — Jenkins Shared Library step
 *
 * Builds the bootJar for a service, constructs a Docker image tagged with the
 * git short-SHA, and pushes it to the internal CI registry.
 *
 * @param serviceName  Full Gradle subproject name, e.g. "circleguard-auth-service"
 * @return             The git short-SHA used as the image tag (exactly 7 chars)
 *
 * Usage in Jenkinsfile:
 *   def gitSha = buildAndPush("circleguard-auth-service")
 *   deployToNamespace("circleguard-auth-service", "dev", "circleguard-dev", gitSha)
 */
def call(String serviceName) {
    if (!serviceName?.trim()) {
        error("buildAndPush: serviceName must not be blank")
    }
    if (serviceName =~ /[^a-zA-Z0-9_-]/) {
        error("buildAndPush: serviceName '${serviceName}' contains invalid characters (allowed: a-z A-Z 0-9 _ -)")
    }

    // --short=7 guarantees exactly 7 characters regardless of repo size
    def gitSha   = sh(script: "git rev-parse --short=7 HEAD", returnStdout: true).trim()
    def registry = "docker-registry:5000"
    def imageTag = "${registry}/${serviceName}:${gitSha}"

    echo "=== buildAndPush: ${serviceName} (sha=${gitSha}) ==="

    echo "Step 1/3 — Building bootJar for :services:${serviceName}..."
    sh "./gradlew :services:${serviceName}:bootJar"

    echo "Step 2/3 — Building Docker image ${imageTag}..."
    sh "docker build -t ${imageTag} -f services/${serviceName}/Dockerfile ."

    echo "Step 3/3 — Pushing ${imageTag} to registry..."
    sh "docker push ${imageTag}"

    echo "=== buildAndPush complete: ${imageTag} ==="
    return gitSha
}
