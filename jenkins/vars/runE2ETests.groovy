/**
 * runE2ETests — Jenkins Shared Library step
 *
 * Runs Playwright E2E tests against a deployed environment. This IS a hard gate:
 * on failure, HTML reports are archived first, then error("E2E tests failed") is called.
 * Do not wrap in try/catch that swallows the non-zero Playwright exit without that error().
 *
 * URL resolution order: baseUrl arg → env.STAGE_BASE_URL → env.DEV_BASE_URL.
 * playwright.config.ts prefers STAGE_BASE_URL, then DEV_BASE_URL, when CI=true. The sh
 * step runs under withEnv(STAGE_BASE_URL=resolved target) so the Groovy-resolved URL
 * (including baseUrl argument overrides) matches what Playwright reads.
 *
 * @param baseUrl  Optional override for base URL (falls back to env vars)
 *
 * Usage in Jenkinsfile:
 *   runE2ETests()
 *   runE2ETests("http://my-override:8087")
 */
def call(String baseUrl = '') {
    def targetUrl = baseUrl ?: (env.STAGE_BASE_URL ?: env.DEV_BASE_URL ?: '')
    if (!targetUrl) {
        error("runE2ETests: no target URL configured — set STAGE_BASE_URL or DEV_BASE_URL, or pass a baseUrl argument")
    }
    echo "=== runE2ETests — target: ${targetUrl} ==="

    def rc
    withEnv(["STAGE_BASE_URL=${targetUrl}"]) {
        rc = sh returnStatus: true, script: '''
            set -e
            cd tests/e2e
            npm ci
            npx playwright install chromium
            export CI=true
            npx playwright test
        '''
    }

    archiveArtifacts allowEmptyArchive: true, artifacts: 'tests/e2e/reports/**/*'

    if (rc != 0) {
        error("E2E tests failed")
    }
}
