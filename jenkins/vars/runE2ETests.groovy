/**
 * runE2ETests — Jenkins Shared Library step
 *
 * STUB — Full implementation in Story 3.2.
 *
 * Runs Playwright E2E tests against a deployed environment. This IS a hard
 * gate: a non-zero Playwright exit code propagates as a pipeline failure.
 * DO NOT wrap in a try/catch that swallows exceptions.
 *
 * URL resolution order: baseUrl arg → env.STAGE_BASE_URL → env.DEV_BASE_URL.
 * Fails fast if no URL resolves — prevents silent misconfiguration from being
 * masked while the stub is in place (and silently passing in every pipeline run
 * before Story 3.2 wires in the real Playwright invocation).
 *
 * @param baseUrl  Optional override for base URL (falls back to env vars)
 *
 * Usage in Jenkinsfile:
 *   runE2ETests()                          // reads env.STAGE_BASE_URL → env.DEV_BASE_URL
 *   runE2ETests("http://my-override:8087") // explicit override
 */
def call(String baseUrl = '') {
    def targetUrl = baseUrl ?: (env.STAGE_BASE_URL ?: env.DEV_BASE_URL ?: '')
    if (!targetUrl) {
        error("runE2ETests: no target URL configured — set STAGE_BASE_URL or DEV_BASE_URL, or pass a baseUrl argument")
    }
    echo "=== runE2ETests (stub) — target: ${targetUrl} ==="
    echo "Full Playwright implementation delivered in Story 3.2."
    // Story 3.2 replaces this stub with:
    //   sh "npx playwright test --config tests/e2e/playwright.config.ts"
    // and adds HTML report archiving via publishHTML.
}
