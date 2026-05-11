/**
 * generateReleaseNotes — Jenkins Shared Library step
 *
 * STUB — Full implementation in Story 4.2.
 *
 * Generates RELEASE_NOTES.md from Conventional Commit history using git-cliff.
 * Full implementation runs:
 *   git-cliff --tag $(git describe --tags --abbrev=0) -o RELEASE_NOTES.md
 *
 * Requires:
 * - cliff.toml at repository root (created in Story 4.2)
 * - Conventional Commits enforced from day 1 (AR-8)
 * - git tags present on the branch
 *
 * Usage in Jenkinsfile:
 *   generateReleaseNotes()
 */
def call() {
    echo "=== generateReleaseNotes (stub) — full implementation in Story 4.2 ==="
    echo "Story 4.2 replaces this stub with git-cliff execution."
    // Story 4.2 replaces this stub with:
    //   sh "git-cliff --tag \$(git describe --tags --abbrev=0) -o RELEASE_NOTES.md"
    sh "echo 'Release notes stub — see Story 4.2' > RELEASE_NOTES.md"

    // allowEmptyArchive: true is the sole guard — no try/catch needed or permitted
    archiveArtifacts allowEmptyArchive: true, artifacts: 'RELEASE_NOTES.md'
}
