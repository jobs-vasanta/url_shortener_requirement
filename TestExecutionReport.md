# Test Execution Report — URL Shortener

**Generated:** 2026-08-01
**Suite:** 19 test classes, 137 test methods (`url-shortener/src/test/java`)

## 1. Execution status — read this before the tables below

**These tests were NOT executed in this session, and no fabricated pass/fail results are reported below.**

Verified directly in this session:
- No `gradlew` wrapper is committed and no `gradle` binary is on `PATH` (`gradle -v` →
  `CommandNotFoundException`).
- The IDE's own test runner (`runTests`) reports `0 passed / 0 failed` for this project — no test runner is
  discoverable without Gradle.
- 6 of the 19 test classes extend `AbstractIntegrationTest` and require a live Docker daemon
  (Testcontainers-backed Postgres + Redis), which is also unavailable here.

What **was** verified in this session: `get_errors` (JDT-based static analysis) reports **zero compile
errors** across the entire `src` tree (both `main` and `test`) — the only diagnostics present are two
informational Spring Boot 3.3.x end-of-support advisories on `build.gradle`, unrelated to test correctness.
So every test method listed below is confirmed to **compile and be discoverable**; whether it **passes at
runtime** can only be confirmed by actually running the suite.

**To get real pass/fail results:**
```bash
gradle test                # 108 unit tests, no Docker required
gradle integrationTest      # 28 integration tests, requires Docker
```
Results land in `url-shortener/build/test-results/*/` (JUnit XML) and
`url-shortener/build/reports/tests/*/` (HTML). CI (`.github/workflows/ci.yml`) already runs both as
separate jobs on every push/PR and publishes a JUnit summary via `dorny/test-reporter` — that is the
authoritative source for real, executed pass/fail status for any given commit.

## 2. Summary

| Suite | Test classes | Test methods | Requires Docker | Status |
|---|---|---|---|---|
| Unit | 11 | 108 | No | Not executed in this session |
| Integration | 6 | 28 | Yes | Not executed in this session |
| Context load | 1 | 1 | No | Not executed in this session |
| **Total** | **18*** | **137** | — | — |

*`AbstractIntegrationTest` is a 19th test source file but contains no `@Test` methods itself (shared base class only).

## 3. Unit Tests (108 methods, 11 classes — no Docker required)

| Test Class | Methods | Target |
|---|---|---|
| `domain.LinkTest` | 10 | `Link` state transitions (`isRedirectable`, `deactivate`, `reactivate`, `markExpired`, `updateExpiresAt`) |
| `exception.GlobalExceptionHandlerTest` | 10 | Every exception → HTTP status/body mapping (404/410/400/409/429/500) |
| `logging.CorrelationIdFilterTest` | 6 | Correlation-ID reuse/generation/validation/MDC lifecycle |
| `logging.MdcTaskDecoratorTest` | 3 | MDC propagation onto async executor threads |
| `ratelimit.RateLimitInterceptorTest` | 11 | Fixed-window limiting, tier-aware limits, fail-open/fail-closed, per-key vs per-IP partitioning |
| `scheduler.LinkExpirySchedulerTest` | 3 | Scheduled expiry sweep delegation and count reporting |
| `service.impl.AnalyticsServiceImplTest` | 9 | Click recording, PII hashing, referrer sanitization, analytics caching |
| `service.impl.ApiKeyServiceImplTest` | 6 | Tier resolution (null/blank/unrecognized/inactive → FREE; active premium/free) |
| `service.impl.ShortCodeGeneratorServiceImplTest` | 6 | Base62 encoding, alias availability/reservation checks |
| `service.impl.UrlServiceImplTest` | 24 | Create/resolve/get/update/deactivate — largest suite; covers free/premium TTL rules, cache read/write, expiry |
| `service.impl.UrlValidationServiceImplTest` | 20 | URL scheme/host/length/control-character validation, SSRF guards |

## 4. Integration Tests (28 methods, 6 classes — Testcontainers Postgres + Redis, Docker required)

| Test Class | Methods | Target |
|---|---|---|
| `FailureScenariosIntegrationTest` | 7 | End-to-end 400/409 error responses, malformed body, SSRF rejection, correlation ID on errors |
| `LinkLifecycleIntegrationTest` | 7 | Create → persist → get → update → deactivate/reactivate, custom alias, duplicate alias 409 |
| `PremiumTierIntegrationTest` | 4 | Free-tier TTL cap enforcement vs. premium bypass, unrecognized-key fallback, real DB-backed `ApiKey` |
| `RateLimitIntegrationTest` | 1 | 429 + `Retry-After` header over the real HTTP stack |
| `RedirectFlowIntegrationTest` | 5 | 302 redirect, async click persistence (via Awaitility polling), 404/410 on redirect |
| `RedisCacheIntegrationTest` | 4 | Real Redis write-through, eviction-then-reload, cache refresh on update/deactivate |

## 5. Context / Smoke Test (1 method)

| Test Class | Methods | Target |
|---|---|---|
| `UrlShortenerApplicationTests` | 1 | `contextLoads()` — full Spring context boots without error |

## 6. Honesty note

No test in this report is marked "Pass" or "Fail" because none were executed here. Marking them otherwise
would misrepresent the actual state of verification. This mirrors the standard already set in
[`AI_TRACEABILITY.md`](AI_TRACEABILITY.md): findings/limitations are stated plainly rather than glossed
over. Once run via `gradle test integrationTest` (locally or in CI), replace Section 2's status column with
real counts from the JUnit XML output.
