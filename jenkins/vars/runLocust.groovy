/**
 * runLocust — Jenkins Shared Library step
 *
 * ⚠️  ADVISORY GATE — IMMUTABLE CONTRACT (AR-9):
 * Locust results are NEVER a hard gate. This step must NEVER call error().
 * Performance issues are logged as WARNING; the pipeline always continues.
 *
 * @param targetUrl  Optional hint only (logged). Python resolves URLs from
 *                   STAGE_BASE_URL / DEV_BASE_URL / per-service env vars.
 *
 * Usage:
 *   runLocust()
 *   runLocust(env.STAGE_BASE_URL)
 */
def call(String targetUrl = '') {
    def hint = targetUrl ?: (env.STAGE_BASE_URL ?: env.DEV_BASE_URL ?: '')
    echo "=== runLocust (advisory) — URL hint: ${hint ?: '(unset — derived in tests/performance/utils/config.py)'} ==="

    sh '''
        set +e
        python3 -m pip install -q -r tests/performance/requirements.txt
        USERS="${LOCUST_USERS:-20}"
        SPAWN="${LOCUST_SPAWN_RATE:-4}"
        DUR="${LOCUST_RUN_TIME:-60s}"
        locust -f tests/performance/locustfile.py --headless \\
            -u "${USERS}" -r "${SPAWN}" -t "${DUR}" \\
            --csv tests/performance/locust_results
        LOCUST_RC=$?
        set -e
        python3 tests/performance/print_summary.py || true
        echo "WARNING: Locust finished with exit code ${LOCUST_RC} (advisory only — AR-9; pipeline continues)."
    '''

    archiveArtifacts allowEmptyArchive: true, artifacts: 'tests/performance/*.csv'
}
