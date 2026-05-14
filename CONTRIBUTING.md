# Contributing to CircleGuard

## Commit messages (Conventional Commits)

This repository expects [**Conventional Commits**](https://www.conventionalcommits.org/en/v1.0.0/) on every commit. The Master pipeline will use **git-cliff** (and similar tooling) to build release notes from history (see project FR-5).

### Allowed type prefixes

Use one of these at the start of the subject line, followed by a colon and a space:

| Prefix | Typical use |
|--------|-------------|
| `feat:` | New user-facing capability |
| `fix:` | Bug fix |
| `test:` | Tests only |
| `ci:` | Jenkins, Docker, Helm, pipeline, or shared-library changes |
| `chore:` | Maintenance that is not a feature or fix (deps, tooling cleanup) |

**Examples (good):**

- `feat: add exposure window filter to promotion query`
- `fix: reject expired QR tokens at gateway`
- `ci: tighten JUnit aggregation in Stage pipeline`
- `test: cover identity bridge with integration test`
- `chore: bump spring dependency patch version`

### Anti-pattern: free-form or vague subjects

**Do not** use unstructured subjects such as `fix stuff`, `WIP`, `updates`, or commits without a type prefix. Those messages:

- Break automated categorization for release notes and changelogs.
- Hide intent from reviewers and `git bisect`.
- Cause **silent omission** or mis-sorting when generating `RELEASE_NOTES.md` with Conventional-Commits-aware tools.

If you are unsure, pick the closest prefix above and keep the description imperative and specific (`feat: …`, `fix: …`).

### Optional enforcement

Hooks or `commitlint` are **not required** for contribution; consistency is enforced by team review and this document. Add tooling locally if you want stricter guardrails.
