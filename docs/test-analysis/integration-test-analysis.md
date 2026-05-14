# Integration test analysis

`integrationTest` source sets live under `services/*/src/integrationTest/java/`. They use **Testcontainers** (Kafka, PostgreSQL, Neo4j, Redis as needed), **`@SpringBootTest`**, and **`Awaitility`** for async assertions—**never** `Thread.sleep()` (AR-11).

Chains match **Epic 2 Story 2.4**:

- **Sync:** gateway → auth → identity (HTTP / JWT issuance path).
- **Async:** form → promotion → notification (Kafka events).

## Class → chain → infrastructure

| Class | Primary chain | Containers / notes |
|-------|---------------|-------------------|
| `LoginIdentityChainIntegrationTest` (auth) | **Sync** — login obtains `anonymousId` from configurable identity vault (local stub); JDBC + Postgres | **PostgreSQL** |
| `IdentityMapEndpointIntegrationTest` (identity) | **Sync** — vault mapping `realIdentity` → UUID `anonymousId` | **PostgreSQL** |
| `GatewaySpringContextIntegrationTest` (gateway) | **Sync** — gateway + Redis, MockMvc hits gate routes | **Redis** (GenericContainer) |
| `AuthSpringContextIntegrationTest` | Wiring / Postgres only | **PostgreSQL** |
| `IdentitySpringContextIntegrationTest` | Wiring / Postgres only | **PostgreSQL** |
| `FormSpringContextIntegrationTest` | Startup order **Kafka → Postgres** (AC / Epic 2.2) | **Kafka**, **PostgreSQL** |
| `HealthSurveyKafkaSubmitIntegrationTest` (form) | **Async** — survey persistence + **Kafka** consumer verification | **Kafka**, **PostgreSQL** |
| `SurveySubmittedKafkaIntegrationTest` (promotion) | **Async** — consumes survey event, Neo4j + Postgres + Redis where configured | **Kafka**, **Neo4j**, **PostgreSQL**, **Redis** |
| `PromotionStatusChangedNotificationIntegrationTest` (notification) | **Async** — `promotion.status.changed` to `ExposureNotificationListener` (LMS/dispatcher mocked) | **Kafka** |
| `HealthStatusReevaluationTest` | Promotion graph + SQL **reevaluation** flows | **Neo4j**, **PostgreSQL**, **Redis** (extends `AbstractPromotionIntegrationTest`) |
| `AdministrativeCorrectionTest` | Administrative graph correction | **Neo4j**, **PostgreSQL**, **Redis** |
| `PromotionPerformanceTest` | Heavy-path promotion / graph performance (integrationTest tier) | **Neo4j**, **PostgreSQL**, **Redis** |
| `NotificationSpringContextIntegrationTest` | Notification app + Kafka bootstrap | **Kafka** |

## Docker smoke (infra-only)

These tests **do not** assert full HTTP chains; they prove **Testcontainers wiring** and **startup ordering** constraints from Epic 2.2:

| Class | Purpose |
|-------|---------|
| `GatewayDockerSmokeIntegrationTest` | Redis-only smoke |
| `AuthDockerSmokeIntegrationTest` | Postgres-only smoke |
| `IdentityDockerSmokeIntegrationTest` | Postgres-only smoke |
| `FormDockerSmokeIntegrationTest` | **Kafka → Postgres** field order |
| `NotificationDockerSmokeIntegrationTest` | Kafka-only smoke |

## CI

- JUnit XML: `**/build/test-results/integrationTest/**/*.xml` (see `jenkins/vars/runIntegrationTests.groovy`).
- On agents: `TESTCONTAINERS_REUSE_ENABLE=false` (fresh containers per build); local dev may use reuse via `~/.testcontainers.properties`—see `AbstractPromotionIntegrationTest` Javadoc.

## References

- `_bmad-output/planning-artifacts/epics.md` — Story 2.4
- `_bmad-output/project-context.md` — Testcontainers / Awaitility rules
