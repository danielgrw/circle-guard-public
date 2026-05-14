# Unit test analysis (CI pipeline services)

This document describes **unit** tests for the **six microservices built and gated by Jenkins** (`Jenkinsfile.dev` / `.stage` / `.master`): gateway, auth, identity, form, promotion, and notification. It is **not** a claim of full monorepo parity: **`circleguard-dashboard-service`** and **`circleguard-file-service`** ship unit tests in-tree but are **outside** the current six-service pipeline scope—see table footnote.

## Strategy

- **Fast feedback:** Tests under `src/test/java` run with `./gradlew :<module>:test` (no Docker).
- **Layering:** Controller tests use **`@WebMvcTest`** + `MockMvc` and **`@MockBean`** for services/clients; pure logic uses **JUnit 5 + Mockito** (`@ExtendWith(MockitoExtension.class)`) without loading Spring.
- **Privacy (AR-12):** Fixtures use **`com.circleguard.test.TestDataBuilder`** and **`TestJwtFactory`** from the shared **`test-support`** module—**no** real names, emails, or student identifiers.

| Service | Test classes | Style / notes |
|--------|----------------|----------------|
| **circleguard-gateway-service** | `GateControllerTest`, `QrValidationServiceTest` | `GateControllerTest`: `@WebMvcTest`. `QrValidationServiceTest`: manual Mockito + `StringRedisTemplate` stub—QR validation / Redis interaction. Uses `TestDataBuilder.randomAnonymousId()`. |
| **circleguard-auth-service** | `LoginControllerTest`, `CustomUserDetailsServiceTest`, `JwtTokenServiceTest`, `TestJwtFactoryAuthContractTest` | `LoginControllerTest`: `@WebMvcTest` + `SecurityConfig`. `CustomUserDetailsServiceTest`: Mockito. `JwtTokenServiceTest`: plain JUnit—token claims with `TestDataBuilder`. `TestJwtFactoryAuthContractTest`: small Spring context—**`TestJwtFactory.generateToken`** contract vs auth config. |
| **circleguard-identity-service** | `IdentityVaultControllerTest`, `IdentityMappingRepositoryTest`, `IdentityEncryptionConverterTest` | `IdentityVaultControllerTest`: `@WebMvcTest`. `IdentityMappingRepositoryTest`: `@DataJpaTest` + H2-style JPA slice (Postgres config in test profile). `IdentityEncryptionConverterTest`: pure unit. |
| **circleguard-form-service** | `AttachmentControllerTest`, `HealthSurveyControllerTest`, `QuestionnaireControllerTest`, `SymptomMapperTest` | Three `@WebMvcTest` controllers + **`SymptomMapperTest`** pure mapper logic. |
| **circleguard-promotion-service** | `HealthStatusControllerTest`, `HealthStatusServiceTest`, `StatusLifecycleTest`, `FloorServiceTest`, `SurveyListenerTest` | `HealthStatusControllerTest`: `@WebMvcTest`. Service/listener tests: Mockito (`@ExtendWith(MockitoExtension.class)`); `HealthStatusServiceTest` uses **`TestDataBuilder`**. |
| **circleguard-notification-service** | `LmsServiceTest`, `NotificationDispatcherTest`, `NotificationRetryTest`, `RoomReservationServiceTest`, `TemplateServiceTest`, `ExposureNotificationListenerTest`, `PriorityAlertListenerTest` | Several **`@SpringBootTest`**-scoped service tests (narrow slices with mocks). `PriorityAlertListenerTest`: Mockito + `ReflectionTestUtils`—no full app context. |

## Jenkins / reports

- **JUnit XML** (pipeline): aggregated from `**/build/test-results/test/**/*.xml` in each Jenkinsfile `post { always { junit ... } }`.
- **Gradle HTML:** under `services/<module>/build/reports/tests/test/index.html` locally; Jenkins **`post { always { archiveArtifacts 'services/*/build/reports/tests/**/*' } }`** (all three Jenkinsfiles) archives the same for each build.

## References

- Shared fixtures: `test-support/src/main/java/com/circleguard/test/`
- Project rules: `_bmad-output/project-context.md` (Testing Rules — Backend)
