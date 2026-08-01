# Code Coverage Report — URL Shortener

**Generated:** 2026-08-01
**Scope:** `url-shortener/src/main/java` (57 main types) against `url-shortener/src/test/java` (19 test classes, 137 test methods).

## 1. Important: how this report was produced

This environment has **no way to execute Gradle** (no committed `gradlew` wrapper, and `gradle -v` fails
with `CommandNotFoundException` — confirmed directly in this session), and the IDE's own test-discovery
(`runTests`) also returns `0 passed / 0 failed` here, meaning no instrumented run of JaCoCo could actually
happen in this sandbox. **The percentages an instrumented tool like JaCoCo would report are therefore not
available from this session.**

To still deliver something useful and honest, Section 3 below is a **static/structural coverage analysis**:
every main-source type was cross-referenced against the test suite to determine whether it has (a) a
dedicated test class exercising it directly, (b) no dedicated test but is exercised indirectly as a
collaborator inside another class's test (mocked) or a Testcontainers-backed integration test (real), or
(c) is a pure interface/enum with no independent logic to cover. This is a proxy for coverage, not a
replacement for it — it cannot detect branch/line-level gaps *within* a class that does have a test.

**To get a real, instrumented coverage report:** the `jacoco` plugin has now been added to
[`url-shortener/build.gradle`](url-shortener/build.gradle) as part of this task. Run:

```bash
gradle test integrationTest jacocoTestReport
```

(integration tests require a working Docker daemon). Output:
- HTML: `url-shortener/build/reports/jacoco/test/html/index.html`
- XML: `url-shortener/build/reports/jacoco/test/jacocoTestReport.xml`

CI (`.github/workflows/ci.yml`) already uploads `build/reports/**` as artifacts on every run
(`if: always()`), so once this change is merged the real per-commit coverage report will be available there
without any further setup.

## 2. Summary

| Category | Count | Meaning |
|---|---|---|
| Direct — dedicated test class | 12 | A test class exists whose primary target is this type. |
| Indirect only | 37 | No dedicated test class; exercised as a collaborator inside another test (mock) or a real integration test. |
| N/A | 7 | Pure interface or enum with no independent executable logic. |
| **Gap — not exercised at all** | **1** | `SnowflakeIdGenerator` — its real implementation is mocked out in the only test that touches it, so its actual logic never runs under any test. |

## 3. Type-by-Type Breakdown

| Package | Type | Status | Covered by |
|---|---|---|---|
| (root) | `UrlShortenerApplication` | Direct | `UrlShortenerApplicationTests` (context load) |
| cache | `CacheService` | Indirect | Mocked in `UrlServiceImplTest`; real Redis in `RedisCacheIntegrationTest` |
| cache | `CacheKeys` | Indirect | Exercised wherever `CacheService`/`UrlServiceImpl` runs |
| config | `AsyncConfig` | Indirect | Spring context load (integration tests, `UrlShortenerApplicationTests`) |
| config | `LoggingProperties` | Indirect | Spring context load |
| config | `OpenApiConfig` | Indirect | Spring context load |
| config | `RateLimitProperties` | Indirect | `RateLimitInterceptorTest`, `RateLimitIntegrationTest` |
| config | `RedisConfig` | Indirect | Spring context load, `RedisCacheIntegrationTest` |
| config | `SecurityConfig` | Indirect | Spring context load (all integration tests hit real HTTP filter chain) |
| config | `SnowflakeConfig` | Indirect | Spring context load |
| config | `WebMvcConfig` | Indirect | Spring context load (registers `RateLimitInterceptor`) |
| controller | `AnalyticsController` | Indirect | Hit via HTTP in integration tests; no `@WebMvcTest` |
| controller | `LinkController` | Indirect | Hit via HTTP in integration tests; no `@WebMvcTest` |
| controller | `RedirectController` | Indirect | Hit via HTTP in `RedirectFlowIntegrationTest`; no `@WebMvcTest` |
| domain | `ApiKey` | Indirect | Persisted/read in `PremiumTierIntegrationTest`, `ApiKeyServiceImplTest` |
| domain | `ClickEvent` | Indirect | Asserted in `RedirectFlowIntegrationTest`, `AnalyticsServiceImplTest` |
| domain | `Link` | **Direct** | `LinkTest` (state-transition logic) |
| domain | `LinkStatus` | N/A | Enum, no logic |
| domain | `ApiKeyTier` | N/A | Enum, no logic |
| dto | `AnalyticsResponse` | Indirect | Returned/asserted in `AnalyticsServiceImplTest` |
| dto | `CreateLinkRequest` | Indirect | Validation exercised in `FailureScenariosIntegrationTest` |
| dto | `ErrorResponse` | Indirect | Asserted in `GlobalExceptionHandlerTest` |
| dto | `LinkResponse` | Indirect | Asserted throughout `UrlServiceImplTest`, integration tests |
| dto | `RequestLimits` | Indirect | Constants referenced by TTL-cap tests |
| dto | `UpdateLinkRequest` | Indirect | Asserted in `UrlServiceImplTest` update cases |
| event | `ClickRecordedEvent` | Indirect | Published/consumed in `RedirectFlowIntegrationTest` |
| event | `ClickRecordedEventListener` | Indirect | Exercised via the async pipeline in `RedirectFlowIntegrationTest` |
| exception | `AliasAlreadyExistsException` | Indirect | Thrown/asserted in `ShortCodeGeneratorServiceImplTest`, `GlobalExceptionHandlerTest` |
| exception | `GlobalExceptionHandler` | **Direct** | `GlobalExceptionHandlerTest` |
| exception | `InvalidUrlException` | Indirect | Thrown in `UrlValidationServiceImplTest` |
| exception | `LinkGoneException` | Indirect | Thrown in `UrlServiceImplTest`, `RedirectFlowIntegrationTest` |
| exception | `LinkNotFoundException` | Indirect | Thrown throughout `UrlServiceImplTest` |
| exception | `RateLimitExceededException` | Indirect | Thrown in `RateLimitInterceptorTest` |
| exception | `ReservedAliasException` | Indirect | Thrown in `ShortCodeGeneratorServiceImplTest` |
| exception | `TtlExceedsPlanLimitException` | Indirect | Thrown in `UrlServiceImplTest`, `PremiumTierIntegrationTest` |
| logging | `CorrelationIdFilter` | **Direct** | `CorrelationIdFilterTest` |
| logging | `MdcTaskDecorator` | **Direct** | `MdcTaskDecoratorTest` |
| logging | `PerformanceLoggingAspect` | Indirect | Runs on every service call in every integration test (no dedicated assertions) |
| logging | `RequestLoggingFilter` | Indirect | Runs on every HTTP request in every integration test (no dedicated assertions) |
| ratelimit | `RateLimitInterceptor` | **Direct** | `RateLimitInterceptorTest` |
| repository | `ApiKeyRepository` | Indirect | Used directly in `PremiumTierIntegrationTest` (real Postgres) |
| repository | `ClickEventRepository` | Indirect | Used in `RedirectFlowIntegrationTest` (real Postgres) |
| repository | `LinkRepository` | Indirect | Used throughout integration tests (real Postgres) |
| scheduler | `LinkExpiryScheduler` | **Direct** | `LinkExpirySchedulerTest` |
| service | `AnalyticsService` | N/A | Interface |
| service | `ApiKeyService` | N/A | Interface |
| service | `ShortCodeGeneratorService` | N/A | Interface |
| service | `UrlService` | N/A | Interface |
| service | `UrlValidationService` | N/A | Interface |
| service.impl | `AnalyticsServiceImpl` | **Direct** | `AnalyticsServiceImplTest` |
| service.impl | `ApiKeyServiceImpl` | **Direct** | `ApiKeyServiceImplTest` |
| service.impl | `ShortCodeGeneratorServiceImpl` | **Direct** | `ShortCodeGeneratorServiceImplTest` |
| service.impl | `UrlServiceImpl` | **Direct** | `UrlServiceImplTest` (24 methods — largest suite in the project) |
| service.impl | `UrlValidationServiceImpl` | **Direct** | `UrlValidationServiceImplTest` |
| util | `Base62Encoder` | Indirect | Real implementation runs inside `ShortCodeGeneratorServiceImplTest` (only the ID generator is mocked there) |
| util | `HashUtil` | Indirect | Real implementation runs inside `AnalyticsServiceImplTest` |
| util | `SnowflakeIdGenerator` | **Gap** | Mocked out in `ShortCodeGeneratorServiceImplTest` — its real `nextId()`/timestamp/sequence logic never executes under any test in this suite |

## 4. Known Gaps (cross-referenced with `PrincipalEngineerReview.md`)

These were already flagged in the prior Principal Engineer review and are confirmed again here by this
independent structural pass:
- No direct test for `SnowflakeIdGenerator`, and specifically no concurrency/uniqueness test for `nextId()`.
- No `@WebMvcTest` for `LinkController` / `AnalyticsController` / `RedirectController` (controller-layer
  concerns like request-mapping/serialization are only covered transitively through full-stack integration
  tests).
- No dedicated `CacheService` fallback/circuit-breaker-open test (only the happy path is exercised
  transitively).
- No dedicated `Base62Encoder` edge-case test (e.g. `0`, negative input, max `long`).
