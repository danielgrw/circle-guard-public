# E2E test analysis (Playwright)

Specs live in [`tests/e2e/specs/`](../../tests/e2e/specs/) (paths relative to repo root `circle-guard-public/`). Shared helpers: [`utils.ts`](../../tests/e2e/specs/utils.ts).

## Base URL and CI

- [`playwright.config.ts`](../../tests/e2e/playwright.config.ts): in **`CI=true`**, **`STAGE_BASE_URL` or `DEV_BASE_URL` is required** (throws if both missing).
- **Jenkins** (`jenkins/vars/runE2ETests.groovy`): resolves URL as `baseUrl` argument → `STAGE_BASE_URL` → `DEV_BASE_URL`, then runs Playwright under `withEnv(["STAGE_BASE_URL=${targetUrl}"])`.
- **Master pipeline** (`Jenkinsfile.master`): passes **gateway** URL explicitly via `runE2ETests(<masterUrl>)` (same mechanism—all resolve into `STAGE_BASE_URL` inside the step).
- **Local dev:** defaults to `http://localhost:8087` when not in CI (gateway hint only; specs use `deriveServiceOrigin` per service).

## Spec catalog (Epic 3 Story 3.2 alignment)

| Spec file | User flow / intent | AC / risk covered |
|-----------|--------------------|-------------------|
| **`auth-flow.spec.ts`** | Login via auth, obtain JWT and `anonymousId`, fetch QR token, **POST** gateway `/api/v1/gate/validate` | **Story 3.2:** full **gateway→auth→identity** JWT issuance chain end-to-end; JWT subject matches `anonymousId`. |
| **`form-submission.spec.ts`** | Authenticated **POST** `form-service` `/api/v1/surveys` with symptom payload | Health survey submission path; validates form API accepts canonical survey JSON. |
| **`notification-received.spec.ts`** | Submit survey (similar payload), **poll** promotion `suspectCount` (via utils) until it **increases** vs baseline | **Async chain** health: form → promotion visibility within 30s polling window. |
| **`unauthenticated-rejected.spec.ts`** | **GET** gateway `/api/v1/gate/validate` **without** JWT | **Story 3.2:** **401** when unauthenticated—security gate at gateway. |
| **`promotion-risk-assessed.spec.ts`** | Submit high-symptom survey; poll until gate validation returns **non-GREEN** or `valid === false` | Risk assessment / promotion state reflected through **gateway validate** (end-to-end promotion + gate read path). |

## Artifacts

- Playwright HTML: `tests/e2e/reports/**/*` — archived by `runE2ETests.groovy` even on failure (gate still fails the stage).

## References

- `tests/e2e/playwright.config.ts`
- `_bmad-output/planning-artifacts/epics.md` — Story 3.2
