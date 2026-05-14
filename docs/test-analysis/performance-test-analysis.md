# Performance test analysis (Locust)

Locust code: [`tests/performance/`](../../tests/performance/). Entrypoint [`locustfile.py`](../../tests/performance/locustfile.py) registers three **user classes** (Scenario ↔ Python module):

| Epic scenario | Module | Locust user class | HTTP `name` (CSV row) | Intent |
|-------------|--------|-------------------|------------------------|--------|
| Sync chain | `scenarios/sync_chain.py` | `SyncChainUser` | **`/api/v1/gate/validate`** | Login → QR → gateway validate (same shape as E2E auth flow). |
| Form flood | `scenarios/form_submission.py` | `FormSubmissionUser` | **`/api/v1/surveys`** | High-rate authenticated survey posts. |
| Async soak | `scenarios/async_soak.py` | `AsyncSoakUser` | **`/api/v1/surveys (soak)`**, **`/api/v1/health-status/stats`** | Sparse posts + promotion stats read (Kafka-related path under load). |

Weights in code: `FormSubmissionUser` 5, `SyncChainUser` 2, `AsyncSoakUser` 1.

## How to read Jenkins / CLI CSV

- Jenkins runs (`runLocust.groovy`): `locust ... --csv tests/performance/locust_results` then archives **`tests/performance/*.csv`**.
- Rows map to the **`Name`** column (request name), plus an **`Aggregated`** row.
- Extract **p95** from the **`95%`** column, **throughput** from **`Requests/s`**, **error rate** = `Failure Count` / `Request Count` per row (or from `%` fails column in Locust stdout).

## Documented run (authoring baseline)

| Field | Value |
|-------|--------|
| When | 2026-05-13 |
| Where | Developer workstation — **no** CircleGuard stack running |
| Command | `locust -f locustfile.py --headless -u 3 -r 1 -t 20s --host http://127.0.0.1:8087 --csv locust_results` |
| Outcome | All requests **failed** at `POST /api/v1/auth/login` (**Connection refused**); see per-scenario table below. |

### Per-scenario metrics (2026-05-13 local run)

No services behind `--host` → every virtual user stopped at **`POST /api/v1/auth/login`**. Scenario **named** requests never executed; counts below are **zero-sample** for endpoint-level stats.

| Scenario (module) | Locust `Name` row | Request count | p95 (ms) | Throughput (req/s) | Error rate |
|------------------|-------------------|---------------|----------|--------------------|----------------|
| Sync (`sync_chain.py`) | `/api/v1/gate/validate` | 0 | — | 0 | — |
| Form flood (`form_submission.py`) | `/api/v1/surveys` | 0 | — | 0 | — |
| Async soak (`async_soak.py`) | `/api/v1/surveys (soak)` | 0 | — | 0 | — |
| Async soak (`async_soak.py`) | `/api/v1/health-status/stats` | 0 | — | 0 | — |
| *Auth prerequisite* | `/api/v1/auth/login` | 52 | ~2 | ~2.77 | **100%** (connection refused) |

### Template: healthy Stage/Master run

Replace using archived `locust_results_*.csv` after a successful advisory stage:

| Scenario | p95 (ms) | Throughput (req/s) | Error rate |
|----------|----------|--------------------|------------|
| Sync chain | *CSV: `/api/v1/gate/validate`* | *…* | *…* |
| Form flood | *CSV: `/api/v1/surveys`* | *…* | *…* |
| Async soak | *aggregate CSV rows for `(soak)` + `stats`* | *…* | *…* |

**Note:** Advisory gate **AR-9** — Locust does **not** fail the pipeline; results are `WARNING` + CSV archive only.

## References

- `jenkins/vars/runLocust.groovy`
- `tests/performance/print_summary.py`
- `_bmad-output/planning-artifacts/epics.md` — Story 3.3
