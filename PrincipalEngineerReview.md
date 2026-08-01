# Principal Engineer Production Readiness Review — URL Shortener

**Reviewer stance:** Principal Engineer sign-off review prior to production traffic.
**Scope:** `url-shortener/` full source tree + test suite (19 test classes: 9 unit, 8 integration,
2 filter/decorator unit tests).
**Format:** Findings only — no code changes made in this pass, per request.

---

## Overall Score: 7.5 / 10

**Justification:** This is an unusually well-documented, deliberately-designed prototype for its stage —
layering is clean, the team has clearly thought about failure modes (circuit breakers, fail-open rate
limiting, bulk updates to avoid lock contention), and the test suite is broad (19 classes covering unit,
integration, and cross-cutting concerns like MDC propagation and correlation IDs). That combination is
genuinely rare and pulls the score up.

It is not a 9-10 because there are three findings below (D1, S1, P1) that are the kind of thing a Principal
sign-off exists to catch: a hot-path backpressure leak that can turn an analytics backlog into public-facing
500s, a "privacy" control that doesn't achieve its stated goal, and a new feature (premium tiers) that
silently reintroduced a synchronous DB dependency onto the path the whole caching architecture was built to
protect. None of these are hard to fix, but shipping them today would mean discovering them in an incident,
not in review. It is not below 7 because none of them are structural — every one is a contained, well-scoped
fix, not a rearchitecture.

---

## 1. Design Flaws

### D1 — Async isolation is incomplete: executor saturation leaks into the request path
`ClickRecordedEventListener` is `@Async` on a bounded executor (`AsyncConfig`: 4-8 threads, queue 500,
default `AbortPolicy`). Spring submits `@Async` tasks to the executor **synchronously**, in the caller's
thread. If the queue is full and all threads are busy, `executor.submit()` throws synchronously, and
`UrlServiceImpl.resolveForRedirect` has no try/catch around `eventPublisher.publishEvent(...)` — so a
saturated analytics pipeline becomes a 500 on the public redirect endpoint. This is the single most
important finding in this review: it inverts the component's own design intent (isolate analytics failures
from redirects) under exactly the load condition (a click burst) it was built to survive.
**Production-readiness impact:** an availability incident on the core product path, caused by a subsystem
(click analytics) that has no business being able to affect it. This is the difference between "analytics
degraded" and "the product is down."

### D2 — No single source of truth for API-key tier resolution
`RateLimitInterceptor` and `LinkController` (create/update) each independently call
`apiKeyService.resolveTier(apiKey)` for the same request. Two problems in one: (a) it's done twice
(performance, below), and (b) there is no request-scoped, authoritative answer to "what tier is this
caller" — a future third call site (or a future third tier) has no natural place to plug in without
repeating the same resolution logic a third time.
**Production-readiness impact:** this is exactly the kind of duplication that drifts. The two call sites
will diverge the first time someone fixes a bug in one and not the other.

### D3 — Tier-dependent limits split across two layers with only comments enforcing consistency
`RequestLimits`'s `@Max` bound is deliberately sized to the **premium** ceiling (Bean Validation can't see
per-request tier), with the actual free-tier cap enforced only in `UrlServiceImpl.computeExpiry`. This is
well-commented at both ends today, but it's a split-brain validation rule held together by code comments,
not by the compiler or a test that would fail if one side were "corrected" independently.
**Production-readiness impact:** low probability, high blast radius — if someone tightens the DTO's `@Max`
back down without knowing why, premium customers silently lose the feature they're paying for, and nothing
fails loudly.

---

## 2. Security Issues

### S1 — IP/User-Agent "hashing" does not achieve its stated privacy goal
`AnalyticsServiceImpl.recordClick` hashes `remoteIp` and `userAgent` with plain, unsalted SHA-256, stated as
a PII-minimization measure. It doesn't work: IPv4 has ~4.3B possible values and User-Agent strings come
from a small, well-known catalog. Both are cheaply enumerable — SHA-256 over the entire IPv4 space or a
UA-string dictionary is a minutes-long precomputation, after which every stored hash is trivially reversible
by lookup. This is the identical failure mode as unsalted password hashing, just applied to a different
input domain.
**Production-readiness impact:** if this data model is ever cited to justify a privacy/compliance claim
(GDPR pseudonymization, etc.), that claim is false as implemented. This is a compliance risk, not just a
theoretical one.

### S2 — Redis has no password by default and uses unconstrained polymorphic deserialization
`application.yml`: `password: ${REDIS_PASSWORD:}` defaults to blank. `RedisConfig` uses
`GenericJackson2JsonRedisSerializer`, which embeds `@class` type metadata and deserializes polymorphically
with no type allow-list. An unauthenticated or network-exposed Redis instance is therefore not just a
cache-poisoning risk but a potential deserialization-gadget attack surface.
**Production-readiness impact:** this is a defense-in-depth gap that costs little to close (require a
password outside `dev`, constrain the deserializer's allowed types) but currently relies entirely on network
isolation being perfect, with no application-layer backstop.

### S3 — Management endpoints remain fully open (known, restated for completeness)
`SecurityConfig` permits all requests, including create/update/deactivate on someone else's link. This is
already documented as an intentional prototype gap in the README, so it's not a new finding — but a
Principal sign-off review would be negligent not to restate it as the top blocker before any real account
model exists.

---

## 3. Performance Issues

### P1 — Premium-tier feature silently reintroduced a synchronous DB dependency onto the redirect hot path
`ApiKeyService.resolveTier` hits Postgres on every call with no caching. `RateLimitInterceptor` runs
globally (minus health/docs), so **any request bearing `X-Api-Key` — including hits on `GET /{shortCode}`
— now depends on a synchronous Postgres round-trip**, undoing the specific architectural investment
(Redis cache-aside) made to keep the redirect path off Postgres in the common case. This combines with D2:
a premium `POST /urls` currently pays for two separate DB round-trips just to learn the caller's tier.
**Production-readiness impact:** this is the kind of regression that won't show up in functional testing
(the feature works correctly) and won't show up in light load testing (Postgres is fast when idle) — it
shows up the first time a premium customer's traffic spikes, as elevated redirect latency and connection
pool pressure on exactly the endpoint that must stay fast.

### P2 — Hikari pool (10) vs. Tomcat thread pool (200) sizing mismatch
With up to 200 concurrent request threads and only 10 DB connections, any load that pushes a meaningful
fraction of traffic through Postgres (cache misses, the P1 tier-resolution hit, analytics reads) risks
connection-pool queuing well before Tomcat itself saturates.
**Production-readiness impact:** this determines actual throughput ceiling under load, and today it looks
like an unconsidered default rather than a deliberate backpressure valve — it should be one or the other,
explicitly.

### P3 — Unbounded, un-timed-out DNS resolution on the create-link path
`UrlValidationServiceImpl.isDisallowedHost` calls `InetAddress.getByName(host)` with no explicit timeout,
relying entirely on OS/JVM resolver defaults (which can be tens of seconds for an unresponsive server).
**Production-readiness impact:** an easy, cheap DoS lever against `POST /urls` if that endpoint sees
meaningful or adversarial traffic — a handful of concurrent requests against slow-to-respond hostnames can
tie up request threads for a long time each.

---

## 4. Missing Tests

The suite is broad, but several gaps stand out precisely because they cover the riskiest code:

1. **`SnowflakeIdGenerator` has no direct unit test.** It has a deliberately overridable `currentTimeMillis()`
   seam (package-private, clearly designed for test injection) that is never actually used by any test.
   Nothing verifies: uniqueness under repeated calls, monotonic ordering, the clock-rollback
   `IllegalStateException`, or the sequence-overflow `waitForNextMillis` path. This is the correctness
   foundation of every short code the system issues, and it's currently verified only by inspection.
2. **No concurrency test proves `SnowflakeIdGenerator.nextId()` is actually collision-free under parallel
   load.** The class's entire value proposition is "safe under concurrent access without coordination" —
   that property is asserted in a Javadoc comment, not by a test that spins up N threads and checks for
   duplicate IDs.
3. **`Base62Encoder` has no direct unit test.** It's only exercised indirectly (mocked-through) in
   `ShortCodeGeneratorServiceImplTest`. Boundary cases (0, very large `Long.MAX_VALUE`-adjacent values,
   negative-input rejection) aren't independently verified.
4. **`CacheService` has no direct unit test.** Its three `@CircuitBreaker` fallback methods
   (`getFallback`/`putFallback`/`evictFallback`) are only ever invoked reflectively by Resilience4j at
   runtime and are never called directly in a test. `RedisCacheIntegrationTest` verifies the *happy-path*
   cache-aside contract against a real Redis container, but never forces a failure (killing the container,
   or forcing the circuit into OPEN) to prove the fallback path actually degrades correctly end-to-end.
5. **No test exercises the D1 backpressure scenario.** Nothing saturates the analytics executor's queue to
   confirm what actually happens to a redirect request when it does — today that behavior is undefined by
   test, not just unhandled by code.
6. **No controller-layer (`@WebMvcTest`/MockMvc) unit tests.** All controller behavior is verified only
   through full `TestRestTemplate` + Testcontainers integration tests. This means testing "does this DTO
   validate correctly" or "is this header parsed correctly" always pays the cost of a full Postgres+Redis
   stack, which slows the feedback loop and discourages adding more of these smaller, faster checks.

---

## 5. Code Smells

- **Duplicated knowledge (D2 above):** tier resolution logic exists in two call sites with no shared
  abstraction — a textbook case of knowledge that should live in exactly one place.
- **Split validation logic (D3 above):** the free-tier TTL cap is a rule that exists partly as a comment
  on a `@Max` annotation and partly as executable code elsewhere. The DTO looks like it enforces a limit
  it doesn't actually enforce for every caller.
- **Magic-number-adjacent constant naming:** `RequestLimits.MAX_TTL_SECONDS` (used as the DTO's `@Max` bound)
  is actually the *premium* ceiling, not a universal maximum — the name reads as more general than its real
  meaning, which is exactly how D3 becomes a trap for a future maintainer.
- **Untested seam:** `SnowflakeIdGenerator.currentTimeMillis()` is a well-designed test seam (package-private,
  overridable) that nobody uses — a smell in the "we built the extension point but never validated it works"
  sense, and a low-cost signal that the class's test coverage was written without revisiting the class's own
  design affordances.

---

## 6. Refactoring Recommendations

Each recommendation below is intentionally small and isolated — none of them require rearchitecting a
working system, consistent with "don't rewrite everything."

1. **Wrap `eventPublisher.publishEvent(...)` in `UrlServiceImpl.resolveForRedirect` in a try/catch**, logging
   and swallowing a submission failure exactly like the listener's own catch block already does for
   in-flight failures. *Improves production readiness by:* closing the one gap where a backlog in a
   best-effort subsystem (analytics) can take down the core product path (redirects).
2. **Resolve API-key tier once per request** (e.g., in a filter ordered before the rate limiter, stored as a
   request attribute) and have both `RateLimitInterceptor` and `LinkController` read that single resolution
   instead of each calling `ApiKeyService` independently. *Improves production readiness by:* eliminating a
   duplicated DB call per request and removing a source of code drift (D2) in one change.
3. **Cache resolved tier by key hash with a short TTL** (in-process Caffeine cache is sufficient — tier
   changes are rare, so a few seconds of staleness is an acceptable tradeoff). *Improves production
   readiness by:* removing the newly-introduced synchronous Postgres dependency from the redirect hot path
   (P1), restoring the latency/availability guarantee the caching architecture was built to provide.
4. **Move the free-tier cap comment into an executable test** (e.g., a test that fails if the DTO's `@Max`
   value is ever changed to something inconsistent with `RequestLimits`'s premium/free split) rather than
   relying on the comment alone to prevent regression (D3). *Improves production readiness by:* turning a
   "please don't break this" comment into something CI actually enforces.
5. **Require `REDIS_PASSWORD` (no blank default) outside the `dev` profile, and constrain
   `GenericJackson2JsonRedisSerializer`'s `ObjectMapper` with a `PolymorphicTypeValidator`** scoped to
   `com.urlshortener.domain`/`com.urlshortener.dto`. *Improves production readiness by:* adding an
   application-layer backstop so a network-isolation mistake doesn't become a deserialization vulnerability.
6. **Replace unsalted `SHA-256` with HMAC-SHA256 (server-held secret) for IP/User-Agent hashing in
   `AnalyticsServiceImpl`**, or replace hashing with explicit masking/bucketing if exact-match aggregation
   isn't actually required. *Improves production readiness by:* making the "PII minimization" claim actually
   true, which matters the moment this system is subject to any privacy review.
7. **Add a direct timeout around `InetAddress.getByName` in `UrlValidationServiceImpl`** (e.g., run through
   an executor with a bounded wait). *Improves production readiness by:* removing an easy request-thread-
   exhaustion DoS lever on `POST /urls`.
8. **Add the missing unit tests listed in Section 4**, especially a concurrency test for
   `SnowflakeIdGenerator.nextId()` and a `CacheServiceTest` that exercises the fallback methods directly.
   *Improves production readiness by:* converting two "verified by inspection" correctness claims (ID
   uniqueness, cache degradation) into claims verified by a test that runs on every build.
9. **Reconsider Hikari pool sizing relative to Tomcat's thread pool** — either raise it (bounded by
   Postgres's own connection ceiling across all app instances) or document it as an intentional
   backpressure valve. *Improves production readiness by:* making the system's real throughput ceiling a
   documented decision instead of an unexamined default.

---

## Summary Table

| # | Finding | Category | Priority |
|---|---|---|---|
| D1 | Async executor rejection can throw back into the redirect request | Design | P1 |
| P1 | `ApiKeyService.resolveTier` uncached, now on the redirect hot path | Performance | P1 |
| S1 | IP/User-Agent hashing doesn't achieve real anonymization | Security | P1 |
| D2 | Tier resolved twice per request, no single source of truth | Design | P2 |
| S2 | Redis blank-password default + unconstrained polymorphic deserialization | Security | P2 |
| P2 | Hikari (10) vs. Tomcat (200) pool sizing mismatch | Performance | P2 |
| D3 | Free-tier TTL cap enforced only by comment, not compiler/test | Design | P2 |
| — | Missing tests: Snowflake concurrency/unit, Base62 unit, CacheService fallback, controller-layer, backpressure | Testing | P2 |
| P3 | Unbounded DNS resolution timeout on create-link path | Performance | P3 |
| S3 | Management endpoints fully open (known, restated) | Security | P3 |

**Recommended order of attack:** D1 and S1 first (small, isolated, real production risk); then the
D2/P1 pair together (one fix — a cached, request-scoped tier resolution — closes both); then the rest
opportunistically alongside the missing-test additions.
