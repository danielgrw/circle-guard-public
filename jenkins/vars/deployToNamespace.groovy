/**
 * deployToNamespace — Jenkins Shared Library step
 *
 * Idempotently creates the target Kubernetes namespace and runs
 * `helm upgrade --install --atomic` with the correct values file and SHA override.
 *
 * Helm release name convention: {serviceName}-{envLabel}
 *   e.g. "circleguard-auth-service-dev"
 *
 * Values file path (relative to workspace root):
 *   k8s/charts/{serviceName}/values-{envLabel}.yaml
 *
 * --atomic rolls back automatically on failure and implies --wait.
 * Without --atomic, a partial upgrade leaves the Helm release in a broken state
 * that subsequent pipeline runs would attempt to upgrade against.
 * --timeout bounds both the readiness wait and the rollback window.
 *
 * @param serviceName  Full service name, e.g. "circleguard-auth-service"
 * @param envLabel     Environment label: "dev" | "stage" | "master"
 *                     NOTE: deliberately NOT named 'env' — that name shadows
 *                     Jenkins' global env binding and causes silent resolution
 *                     failures on any env.VAR access inside this function.
 * @param namespace    K8s namespace, e.g. "circleguard-dev" (must be RFC-1123 label)
 * @param gitSha       Git short-SHA for image tag, e.g. "a1b2c3d"
 *
 * Usage in Jenkinsfile:
 *   def gitSha = buildAndPush("circleguard-auth-service")
 *   deployToNamespace("circleguard-auth-service", "dev", "circleguard-dev", gitSha)
 */
def call(String serviceName, String envLabel, String namespace, String gitSha) {
    if (!serviceName?.trim()) {
        error("deployToNamespace: serviceName must not be blank")
    }
    if (serviceName =~ /[^a-zA-Z0-9_-]/) {
        error("deployToNamespace: serviceName '${serviceName}' contains invalid characters")
    }
    if (!gitSha?.trim()) {
        // Empty gitSha would set image.tag= on the Helm --set flag, causing ImagePullBackOff
        error("deployToNamespace: gitSha must not be blank — cannot deploy with an empty image tag")
    }
    if (!(namespace ==~ /^[a-z0-9][a-z0-9-]{0,61}[a-z0-9]$/)) {
        // Catches uppercase, dots, slashes, and other chars that produce obscure kubectl YAML errors
        error("deployToNamespace: namespace '${namespace}' is not a valid RFC-1123 DNS label")
    }

    echo "=== deployToNamespace: ${serviceName} → ${namespace} (env=${envLabel}, sha=${gitSha}) ==="

    echo "Step 1/2 — Ensuring namespace ${namespace} exists (idempotent)..."
    sh "kubectl create namespace ${namespace} --dry-run=client -o yaml | kubectl apply -f -"

    echo "Step 2/2 — Deploying ${serviceName}-${envLabel} via Helm..."
    sh """helm upgrade --install ${serviceName}-${envLabel} \
        k8s/charts/${serviceName} \
        --values k8s/charts/${serviceName}/values-${envLabel}.yaml \
        -n ${namespace} \
        --set image.tag=${gitSha} \
        --atomic \
        --timeout 3m"""

    echo "=== deployToNamespace complete: ${serviceName}-${envLabel} in ${namespace} ==="
}
