/**
 * runLocust — Jenkins Shared Library step
 *
 * STUB — Full implementation in Story 3.3.
 *
 * ⚠️  ADVISORY GATE — IMMUTABLE CONTRACT (AR-9):
 * Locust results are NEVER a hard gate. This step must NEVER call error().
 * Performance issues are logged as WARNING; the pipeline always continues.
 * This contract must be preserved in Story 3.3's full implementation.
 *
 * @param targetUrl  Base URL for load test target.
 *                   Fallback chain: argument → env.STAGE_BASE_URL → env.DEV_BASE_URL → ''
 *
 * Usage in Jenkinsfile:
 *   runLocust()                         // reads env.STAGE_BASE_URL, falls back to env.DEV_BASE_URL
 *   runLocust("http://my-host:8087")    // explicit override
 */
def call(String targetUrl = '') {
    def url = targetUrl ?: (env.STAGE_BASE_URL ?: env.DEV_BASE_URL ?: '')
    echo "=== runLocust (stub) — target: ${url ?: '(env var not set)'} ==="
    echo "WARNING: Locust advisory gate — full implementation in Story 3.3."
    // Story 3.3 replaces this stub with real Locust execution:
    //   sh "locust -f tests/performance/locustfile.py --headless --users 50 ..."
    // Results are logged as WARNING; error() is NEVER called here.

    // allowEmptyArchive: true is the sole guard against missing CSVs — no try/catch needed
    archiveArtifacts allowEmptyArchive: true, artifacts: 'tests/performance/*.csv'
}
