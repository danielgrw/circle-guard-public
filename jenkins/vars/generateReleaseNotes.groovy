/**
 * generateReleaseNotes — Jenkins Shared Library step
 *
 * Generates RELEASE_NOTES.md from Conventional Commit history using git-cliff.
 * Canonical command (also used for local verification):
 *   git-cliff --tag $(git describe --tags --abbrev=0) -o RELEASE_NOTES.md
 *
 * Requires:
 * - cliff.toml at repository root (mirrored under jenkins/resources/cliff.toml for visibility only)
 * - git-cliff on agent PATH, or GIT_CLIFF_BIN pointing at the binary
 * - at least one reachable Git tag (git describe --tags --abbrev=0)
 * - Conventional Commits (AR-8) for commits that should appear in the notes
 *
 * Release notes are also stored as git notes on the tagged commit (ref refs/notes/cliff-releases).
 * To publish notes: `git push origin refs/notes/cliff-releases` using a Jenkins credentialed step
 * or a service account (see README § Release notes).
 *
 * Usage in Jenkinsfile:
 *   generateReleaseNotes()
 */

def call() {
    echo '=== generateReleaseNotes — git-cliff ==='

    def cliffBin = env.GIT_CLIFF_BIN?.trim() ?: 'git-cliff'
    if (!(cliffBin ==~ /^[A-Za-z0-9._\/+-]+$/)) {
        error('generateReleaseNotes: GIT_CLIFF_BIN must be a single path with no spaces (use a symlink on the agent if needed).')
    }

    def repoRoot = sh(script: 'git rev-parse --show-toplevel', returnStdout: true).trim()
    if (!repoRoot) {
        error('generateReleaseNotes: not inside a Git repository (git rev-parse --show-toplevel failed).')
    }

    def workspaceRoot = env.WORKSPACE?.trim()
    if (!workspaceRoot) {
        error('generateReleaseNotes: WORKSPACE is unset; cannot resolve artifact paths.')
    }

    String artifactPrefix = ''
    def wsDir = new File(workspaceRoot).canonicalFile
    def rrDir = new File(repoRoot).canonicalFile
    def rrPath = rrDir.path
    def wsPath = wsDir.path
    if (rrPath != wsPath) {
        if (!rrPath.startsWith(wsPath + File.separator)) {
            error('generateReleaseNotes: git repository root is not under WORKSPACE; cannot archive artifacts.')
        }
        artifactPrefix = rrPath.substring(wsPath.length() + 1).replace(File.separator, '/') + '/'
    }

    dir(repoRoot) {
        if (!fileExists('cliff.toml')) {
            error('generateReleaseNotes: cliff.toml not found at repository root.')
        }

        def tagProbe = sh(script: 'git describe --tags --abbrev=0 >/dev/null 2>&1', returnStatus: true)
        if (tagProbe != 0) {
            error('generateReleaseNotes: no reachable Git tag (git describe --tags --abbrev=0 failed). Create a release tag on the target commit before running this step.')
        }

        def releaseTag = sh(script: 'git describe --tags --abbrev=0', returnStdout: true).trim()
        if (!(releaseTag ==~ /^[A-Za-z0-9._\/@+:-]+$/)) {
            error("generateReleaseNotes: resolved tag contains unexpected characters: ${releaseTag}")
        }

        def taggedCommit = sh(script: 'git rev-list -n 1 "$(git describe --tags --abbrev=0)"', returnStdout: true).trim()
        if (!(taggedCommit ==~ /^[0-9a-f]{7,40}$/)) {
            error('generateReleaseNotes: could not resolve a 40-hex (or short) commit SHA for the release tag.')
        }

        echo "=== generateReleaseNotes: scope tag=${releaseTag} commit=${taggedCommit} cliff=${cliffBin} ==="

        withEnv(["CG_CLIFF_BIN=${cliffBin}", "CG_TAGGED_COMMIT=${taggedCommit}"]) {
            sh '''#!/bin/bash
                set -euo pipefail
                exec "${CG_CLIFF_BIN}" --tag "$(git describe --tags --abbrev=0)" -o RELEASE_NOTES.md
            '''

            writeFile file: 'release-notes.properties', text: """release.git.tag=${releaseTag}
release.git.commit=${taggedCommit}
release.notes.file=RELEASE_NOTES.md
"""

            sh '''#!/bin/bash
                set -euo pipefail
                if git notes --ref=refs/notes/cliff-releases show "$CG_TAGGED_COMMIT" >/dev/null 2>&1; then
                  git notes --ref=refs/notes/cliff-releases remove "$CG_TAGGED_COMMIT"
                fi
                git notes --ref=refs/notes/cliff-releases add -f -F RELEASE_NOTES.md "$CG_TAGGED_COMMIT"
            '''
        }

        echo "Release notes attached as git notes on ${taggedCommit} (refs/notes/cliff-releases). Push with: git push origin refs/notes/cliff-releases"
    }

    archiveArtifacts allowEmptyArchive: false,
                     artifacts: "${artifactPrefix}RELEASE_NOTES.md,${artifactPrefix}release-notes.properties",
                     fingerprint: true
}
