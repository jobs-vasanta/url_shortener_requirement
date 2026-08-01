# Staff Engineer Code Review — URL Shortener

**Scope:** `url-shortener/` (Spring Boot 3.3.4, Java 21) as of the Premium Users feature.
**Method:** Full read of the main source tree (domain, repository, service, cache, config, controller,
ratelimit, logging, scheduler, exception, event packages) plus `application.yml` and `build.gradle`.
**Philosophy:** This is a well-built prototype with several genuinely good design decisions already in
place (called out below). The findings are prioritized, targeted improvements — not a rewrite.

---

## How to read this document

Each finding has a **Severity** (P1 = fix before real production traffic / real revenue depends on it,
P2 = should fix soon, P3 = worth doing, low urgency) and points at the exact file(s) involved.

---

## 1. Architecture

### Strengths (worth preserving as-is)
- Clean layering: controllers are thin, `UrlServiceImpl` orchestrates, validation/code-generation/caching
  are each a separate, single-purpose collaborator. Domain rules (`isRedirectable`) live on the entity,
  not scattered across services.
- Cache-aside on the redirect hot path with a circuit breaker (`CacheService`) means a Redis outage
  degrades to Postgres reads instead of failing requests — the right tradeoff for this system.
- Click recording is decoupled from the redirect response via `ApplicationEventPublisher` + `@Async`,
  keeping analytics persistence off the critical path.

### P1 — Async click pipeline can leak backpressure into the synchronous redirect path
[ClickRecordedEventListener.java](url-shortener/src/main/java/com/urlshortener/event/ClickRecordedEventListener.java) is `@Async("analyticsExecutor")`, backed by the bounded
[AsyncConfig.analyticsExecutor](url-shortener/src/main/java/com/urlshortener/config/AsyncConfig.java) (4-8 threads, queue capacity 500, default `AbortPolicy`).
Spring submits `@Async` void-returning tasks to the executor *synchronously*, inside the caller's thread —
only the method **body** runs asynchronously. If the queue is full and all threads are busy,
`executor.submit()` throws `TaskRejectedException` **synchronously**, and this exception propagates back
through `eventPublisher.publishEvent(...)` in [UrlServiceImpl.resolveForRedirect](url-shortener/src/main/java/com/urlshortener/service/impl/UrlServiceImpl.java), which has no
try/catch around that call. A sustained click burst that saturates the analytics executor would turn into
500s on the public redirect endpoint — exactly the failure mode this component was designed to prevent.
- **Fix:** wrap the `publishEvent` call in a try/catch (log + drop, consistent with the listener's own
  "never propagate back to the redirect caller" contract), or set a `CallerRunsPolicy`/custom rejection
  handler on the executor that degrades gracefully instead of throwing.

### P2 — API-key tier resolution is duplicated per request
[RateLimitInterceptor.preHandle](url-shortener/src/main/java/com/urlshortener/ratelimit/RateLimitInterceptor.java) and [LinkController.createLink/updateLink](url-shortener/src/main/java/com/urlshortener/controller/LinkController.java) each call
`apiKeyService.resolveTier(apiKey)` independently for the same incoming request — two separate DB
round-trips resolving the same fact, with no guarantee both resolutions see identical key state if it
changes mid-request. Beyond the extra load (see Performance §2 below), this is a "single source of truth"
architecture smell: tier resolution should happen once per request and be shared.
- **Fix:** resolve the tier once (e.g., in a filter placed before the rate limiter, stashed as a request
  attribute) and have both the interceptor and the controller read it from there.

---

## 2. Performance

### P1 — `ApiKeyService.resolveTier` has no caching and now sits on every request
[ApiKeyServiceImpl](url-shortener/src/main/java/com/urlshortener/service/impl/ApiKeyServiceImpl.java) hits Postgres (`findByKeyHashAndActiveTrue`) on every call, and
[RateLimitInterceptor](url-shortener/src/main/java/com/urlshortener/ratelimit/RateLimitInterceptor.java) is registered globally (minus health/docs), so **every request that carries an
`X-Api-Key` header — including hits on the public redirect endpoint — now depends on a synchronous
Postgres round-trip.** This directly undermines the architecture's own stated goal (Redis cache-aside
specifically to keep the redirect path off Postgres in the common case). It compounds with the duplicate
lookup in Architecture §2 above: a premium `POST /urls` call currently costs 2 DB round-trips just to
learn the caller's tier.
- **Fix:** cache resolved tier by key hash with a short TTL (Caffeine in-process cache is sufficient —
  tier changes are rare and a few seconds of staleness is an acceptable tradeoff), or fold into the
  request-scoped resolution from Architecture §2 so it's computed once per request rather than never
  cached across requests.

### P2 — Connection pool sizing mismatch under load
[application.yml](url-shortener/src/main/resources/application.yml) configures Hikari `maximum-pool-size: 10` against Tomcat
`threads.max: 200`. With up to 200 concurrent request threads and only 10 DB connections, any load that
pushes a meaningful fraction of requests through Postgres (cache misses, the tier-resolution DB hit above,
analytics reads) risks connection-pool queuing/starvation well before Tomcat itself is saturated.
- **Fix:** either raise the Hikari pool (bounded by Postgres's own `max_connections` and number of app
  instances) or intentionally document the 10-connection ceiling as a deliberate backpressure valve — right
  now it reads as an oversight rather than a decision.

### P3 — Blocking, un-timed-out DNS resolution on the create-link path
[UrlValidationServiceImpl.isDisallowedHost](url-shortener/src/main/java/com/urlshortener/service/impl/UrlValidationServiceImpl.java) calls `InetAddress.getByName(host)` with no
explicit timeout. A slow-to-respond or unresponsive DNS server for an attacker-supplied hostname can hang
the request thread for the OS resolver's default timeout (which can be tens of seconds), and there is no
per-call timeout to bound this. Low request volume today, but it's an easy DoS lever if `POST /urls`
becomes higher-traffic or intentionally abused.
- **Fix:** bound resolution time explicitly (e.g., run through an executor with a timeout, or use a
  resolver library that supports one) rather than relying on OS/JVM defaults.

---

## 3. Security

### P1 — IP address and User-Agent "anonymization" is not actually anonymization
[AnalyticsServiceImpl.recordClick](url-shortener/src/main/java/com/urlshortener/service/impl/AnalyticsServiceImpl.java) hashes `remoteIp` and `userAgent` with plain, unsalted
`HashUtil.sha256Hex` before storing them, with the stated intent of PII minimization. This does not achieve
that goal: IPv4 has only ~4.3 billion possible values, and User-Agent strings come from a small,
well-known catalog (a handful of thousand distinct strings observed in practice). Both are cheaply
enumerable — an attacker (or anyone with DB read access) can precompute `SHA-256(candidate)` for every
IPv4 address or every known UA string in minutes and build a full reverse lookup table. The hash provides
no real protection here; it's the same failure mode as unsalted-password-hash rainbow tables.
- **Fix:** use a keyed hash (HMAC-SHA256 with a server-held secret/pepper) so precomputation is
  infeasible without the key, or drop hashing in favor of explicit truncation/masking (e.g., zero the last
  IPv4 octet, bucket UA to browser+OS family) if the actual goal is aggregate analytics rather than exact
  reversibility prevention. Note this does **not** apply to API-key hashing in [ApiKeyServiceImpl](url-shortener/src/main/java/com/urlshortener/service/impl/ApiKeyServiceImpl.java) —
  that input is a high-entropy secret, so unsalted SHA-256 is fine there.

### P2 — Redis has no password by default, and the value serializer allows polymorphic deserialization
[application.yml](url-shortener/src/main/resources/application.yml): `password: ${REDIS_PASSWORD:}` defaults to blank. Combined with
[RedisConfig](url-shortener/src/main/java/com/urlshortener/config/RedisConfig.java) using `GenericJackson2JsonRedisSerializer` (which embeds `@class` type info and
deserializes polymorphically with no type allow-list), an unauthenticated or network-exposed Redis
instance becomes a meaningful attack surface: anyone who can write to the cache keys this app reads could
attempt a Jackson gadget-chain deserialization attack, not just cache poisoning.
- **Fix:** require `REDIS_PASSWORD` (no blank default) at minimum in non-dev profiles, and constrain
  `GenericJackson2JsonRedisSerializer`'s `ObjectMapper` with a `PolymorphicTypeValidator` allow-listing only
  `com.urlshortener.domain`/`com.urlshortener.dto` packages.

### P3 — Management endpoints remain fully open (already acknowledged, restating as #1 pre-production gap)
[SecurityConfig](url-shortener/src/main/java/com/urlshortener/config/SecurityConfig.java) permits all requests (`anyRequest().permitAll()`), including create/update/deactivate.
This is documented as an intentional prototype tradeoff in the README/Architecture doc, so it's not a new
finding — but it remains, by a wide margin, the single biggest gap before this could take real traffic:
anyone can deactivate or mutate any other caller's link. Flagging again here so it isn't lost among the
newer, smaller findings above.

---

## 4. Maintainability

### P2 — Tier resolution duplicated (see Architecture §2 / Performance §1)
Same finding, different lens: two call sites implementing "how do we know the caller's tier" means any
future change (e.g., adding a third tier, changing fallback behavior) has to be updated in both the
interceptor and the controller, with no compiler-enforced link between them.

### P3 — `RequestLimits`'s `@Max` bound is a documented but easy-to-miss trap
[RequestLimits](url-shortener/src/main/java/com/urlshortener/dto/RequestLimits.java) sizes the DTO's static `@Max` to the **premium** ceiling because Bean
Validation can't see per-request tier, with the real free-tier cap enforced only in
`UrlServiceImpl.computeExpiry`. This is well-commented at both ends, but it's the kind of split-brain
validation that's easy for a future contributor to "fix" by tightening the `@Max` back down and silently
breaking premium — worth a short note in `Architecture.md` so it survives beyond code comments.

---

## 5. Readability

No significant findings. Javadoc is consistently used to explain *why*, not just *what* (e.g., the
`clickCount` bulk-update rationale on [Link.java](url-shortener/src/main/java/com/urlshortener/domain/Link.java), the tier-blind-validation note on
`RequestLimits`), which is the right level of comment density for a codebase like this.

---

## 6. Testability

### P3 — `UrlValidationServiceImpl`'s DNS check has no seam for deterministic testing
`InetAddress.getByName(host)` is called directly with no injected resolver abstraction, so unit tests
either need real network access for non-loopback hostnames or are limited to cases that resolve locally.
Contrast with [SnowflakeIdGenerator.currentTimeMillis()](url-shortener/src/main/java/com/urlshortener/util/SnowflakeIdGenerator.java), which is deliberately package-private and
overridable specifically so tests can control time — the same pattern (a thin, overridable/injectable seam)
would make the SSRF-guard branch of `UrlValidationServiceImpl` easier to test deterministically and in CI
without network dependencies.

---

## 7. Thread Safety

### Strengths
- `SnowflakeIdGenerator.nextId()` is correctly `synchronized` over its mutable `lastTimestamp`/`sequence`
  state — correct and simple. At up to 4096 IDs/ms per node this is very unlikely to become a real
  contention point; not a finding, just confirming it's sound.
- `MdcTaskDecorator` correctly copies (not shares) the MDC context map onto the executor thread and
  restores the previous context in a `finally` block — no cross-request MDC leakage between pooled threads.
- `LinkRepository.incrementClickCount` / `markExpiredLinks` deliberately bypass `@Version` via bulk JPQL
  `UPDATE`s to avoid optimistic-lock contention on the hot redirect path. This is a correct, well-reasoned
  tradeoff (documented in `Link`'s Javadoc), not an oversight — worth calling out explicitly so it isn't
  "fixed" into an optimistic-locked load-modify-save by a future contributor who doesn't know why it's
  written this way.

### P3 — Snowflake's overflow wait busy-spins
`SnowflakeIdGenerator.waitForNextMillis` spins in a tight `while` loop with no `Thread.onSpinWait()` or
backoff when the per-millisecond sequence overflows (>4096 IDs/ms on one node). Given the throughput
ceiling this only triggers under extreme load, but a `Thread.onSpinWait()` call in the loop body is a
one-line, free improvement (JIT/CPU hint, no behavior change) worth making opportunistically.

---

## Summary — Prioritized Action List

| # | Finding | Area | Severity |
|---|---|---|---|
| 1 | Async click-executor rejection can throw back into the redirect request | Architecture | P1 |
| 2 | `ApiKeyService.resolveTier` uncached, now on every request via global rate limiter | Performance | P1 |
| 3 | IP/User-Agent "hashing" doesn't actually anonymize (unsalted, low-entropy input) | Security | P1 |
| 4 | Redis: blank password default + unconstrained polymorphic deserialization | Security | P2 |
| 5 | Tier resolved twice per request (interceptor + controller), no shared source of truth | Architecture/Maintainability | P2 |
| 6 | Hikari pool (10) vs. Tomcat threads (200) sizing mismatch | Performance | P2 |
| 7 | Unbounded/un-timed-out DNS resolution in URL validation | Performance | P3 |
| 8 | Management endpoints fully open (restated, already known) | Security | P3 |
| 9 | No injectable seam for DNS check in tests | Testability | P3 |
| 10 | Snowflake overflow-wait busy-spins without `Thread.onSpinWait()` | Thread Safety | P3 |

Recommended order of attack: **#1 and #3 first** (both are correctness/security gaps with real production
impact and are small, isolated fixes), then **#2 and #5 together** (same root cause — a request-scoped,
cached tier resolution fixes both at once), then the rest opportunistically.
