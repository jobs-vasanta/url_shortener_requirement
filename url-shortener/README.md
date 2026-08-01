# URL Shortener Service

Production-grade Spring Boot URL shortener: create short links, redirect at scale, and track click analytics. See [../Architecture.md](../Architecture.md), [../RequirementAnalysis.md](../RequirementAnalysis.md), and [../TaskBreakdown.md](../TaskBreakdown.md) at the workspace root for the original design/requirements docs this implementation follows.

## Table of Contents

- [Architecture](#architecture)
- [Setup](#setup)
- [Running](#running)
- [Docker](#docker)
- [APIs](#apis)
- [Testing](#testing)
- [Future Enhancements](#future-enhancements)
- [Tradeoffs](#tradeoffs)
- [Assumptions](#assumptions)

## Architecture

**Stack:** Java 21, Spring Boot 3.3.4 (Gradle, Groovy DSL), PostgreSQL + Flyway, Redis, springdoc-openapi, Spring Boot Actuator, Resilience4j, logstash-logback-encoder.

```
com.urlshortener
├── UrlShortenerApplication.java
├── config       # RedisConfig, SecurityConfig, AsyncConfig, OpenApiConfig, SnowflakeConfig, WebMvcConfig
├── controller   # LinkController (/urls), RedirectController (/{shortCode}), AnalyticsController (/analytics/{shortCode})
├── service      # UrlService, ShortCodeGeneratorService, UrlValidationService, AnalyticsService (+ impl/)
├── domain       # Link, LinkStatus, ClickEvent (JPA entities)
├── repository   # LinkRepository, ClickEventRepository (Spring Data JPA)
├── dto          # CreateLinkRequest, UpdateLinkRequest, LinkResponse, AnalyticsResponse, ErrorResponse
├── cache        # CacheService (circuit-breaker-wrapped Redis get/put/evict), CacheKeys
├── ratelimit    # RateLimitInterceptor (Redis fixed-window counter), RateLimitProperties
├── logging      # CorrelationIdFilter, RequestLoggingFilter, MdcTaskDecorator, PerformanceLoggingAspect
├── event        # ClickRecordedEvent + async listener (decouples redirect latency from analytics writes)
├── exception    # Domain exceptions + GlobalExceptionHandler (-> ErrorResponse)
└── util         # Base62Encoder, SnowflakeIdGenerator
```

**Request flow:**

1. `POST /urls` validates the target URL (`UrlValidationService` - scheme allow-list, length limits, CRLF/control-char rejection to block header/open-redirect injection), generates a short code via `ShortCodeGeneratorService` (Base62-encoded Snowflake ID - unique by construction, no collision retry needed) or uses a caller-supplied alias, and persists a `Link` row.
2. `GET /{shortCode}` (`RedirectController`) is the hot path: `UrlService.resolveForRedirect` reads through `CacheService` (Redis cache-aside, resilience4j circuit breaker with a DB fallback if Redis is unavailable), returns a `302` immediately, and publishes a `ClickRecordedEvent` that an `@Async` listener persists off the request thread - so a slow/contended analytics write never adds latency to the redirect itself.
3. `GET /analytics/{shortCode}` reads an aggregated click count/timestamps, cached briefly (30s default) since it's a rollup, not the click log itself.
4. Every request passes through `CorrelationIdFilter` (propagates/generates `X-Correlation-Id`, into MDC and the response) and `RequestLoggingFilter` (one structured access-log line per request); `RateLimitInterceptor` enforces a per-IP fixed-window limit ahead of the controllers (excluding `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`).

## Setup

Prerequisites:

- **JDK 21**
- **Gradle 8.14+** on `PATH` - this repo has no committed Gradle wrapper (`gradlew`), so a local Gradle install is required to build/run outside Docker. Alternatively, run everything through [Docker](#docker), which doesn't need Gradle on the host at all.
- **PostgreSQL 16** and **Redis 7** - either via Docker (below) or local installs.

Without Docker, start the two dependencies directly:

```powershell
docker run -d --name urlshortener-pg -e POSTGRES_DB=urlshortener -e POSTGRES_USER=urlshortener -e POSTGRES_PASSWORD=urlshortener -p 5432:5432 postgres:16-alpine
docker run -d --name urlshortener-redis -p 6379:6379 redis:7.2-alpine
```

Database schema is managed by Flyway migrations under `src/main/resources/db/migration` and applies automatically on startup (`spring.flyway.enabled: true`).

## Running

```powershell
gradle bootRun
```

The app reads its configuration from `src/main/resources/application.yml`, with every value overridable via environment variable (defaults shown):

| Env var | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` = plain console logs; `prod` = JSON logs, Swagger disabled |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/urlshortener` / `urlshortener` / `urlshortener` | PostgreSQL connection |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis connection |
| `SNOWFLAKE_NODE_ID` | `0` | Must be unique per running instance (0-1023) when scaled horizontally |
| `APP_BASE_URL` | `http://localhost:8080` | Used to build the `shortUrl` field in responses |
| `RATE_LIMIT_COUNT` / `RATE_LIMIT_PERIOD_SECONDS` | `20` / `60` | Requests per client IP per window |
| `LINK_CACHE_TTL_SECONDS` / `ANALYTICS_CACHE_TTL_SECONDS` | `21600` (6h) / `30` | Redis TTLs |
| `SERVER_PORT` | `8080` | HTTP port |

Once running: Swagger UI at `http://localhost:8080/swagger-ui.html`, health at `http://localhost:8080/actuator/health`.

## Docker

Build and run the full stack (app + PostgreSQL + Redis) with Docker Compose - no local JDK/Gradle needed:

```powershell
docker compose up --build
```

- **[Dockerfile](Dockerfile)**: multi-stage build. Build stage uses the official `gradle:8.14-jdk21-alpine` image (no wrapper exists, so the build tool itself is pinned here rather than assumed present on the host); dependencies are resolved in their own layer first so source-only changes don't invalidate that cache. Runtime stage is `eclipse-temurin:21-jre-alpine` (JRE-only, smaller/less attack surface), runs as a non-root `spring` user, and its `HEALTHCHECK` hits `/actuator/health` (which itself reports DB/Redis reachability).
- **[docker-compose.yml](docker-compose.yml)**: `postgres` (16-alpine, named volume, `pg_isready` healthcheck), `redis` (7.2-alpine, no volume - it's cache-only and every key carries its own TTL), and `app`, which only starts once both dependencies report `service_healthy` (not just "started" - both accept TCP connections before they're actually ready to serve). Override the local-only default credentials via a git-ignored `.env` file or real secrets in any shared environment.

## APIs

Full interactive docs are served at `/swagger-ui.html` (springdoc-openapi); summary below.

| Method | Path | Purpose | Success | Failure modes |
|---|---|---|---|---|
| `POST` | `/urls` | Create a short link | `201` `LinkResponse` | `400` invalid URL/body, `409` alias in use, `429` rate limited |
| `GET` | `/urls/{shortCode}` | Get link metadata | `200` `LinkResponse` | `404` not found |
| `PATCH` | `/urls/{shortCode}` | Update `active` and/or `ttlSeconds` (omitted fields unchanged) | `200` `LinkResponse` | `400` invalid body, `404` not found |
| `DELETE` | `/urls/{shortCode}` | Deactivate (idempotent) | `204` | `404` not found |
| `GET` | `/{shortCode}` | Public redirect (root path, separate from `/urls` on purpose) | `302` with `Location` header | `404` not found, `410` expired/deactivated |
| `GET` | `/analytics/{shortCode}` | Click analytics | `200` `AnalyticsResponse` | `404` not found |

**`CreateLinkRequest`**: `longUrl` (required, ≤2048 chars), `alias` (optional, `^[A-Za-z0-9_-]{3,32}$`), `ttlSeconds` (optional, positive, ≤365 days).

**`UpdateLinkRequest`**: `ttlSeconds` (optional) and/or `active` (optional) - at least one must be present; `ttlSeconds: 0` expires immediately.

**`LinkResponse`**: `shortCode`, `shortUrl`, `originalUrl`, `status`, `createdAt`, `expiresAt`.

**`AnalyticsResponse`**: `shortCode`, `totalClicks`, `firstAccessedAt`, `lastAccessedAt`.

**`ErrorResponse`** (all non-2xx bodies): `timestamp`, `status`, `error`, `message`, `details` (list), `correlationId` (quote this back when reporting an issue - never contains a stack trace or internal details).

## Testing

- **Unit tests** (`src/test/java`, mirroring the main package layout, `*Test.java` suffix): JUnit 5 + Mockito + AssertJ, covering `UrlServiceImpl`, `ShortCodeGeneratorServiceImpl`, `UrlValidationServiceImpl`, `AnalyticsServiceImpl`, `RateLimitInterceptor`, `GlobalExceptionHandler`, `CorrelationIdFilter`, `MdcTaskDecorator`, and the `Link` domain entity - happy path, negative path, exceptions, and edge cases.
- **Integration tests** (`src/test/java/com/urlshortener/*IntegrationTest.java`, flat under the root package): real Postgres + Redis via Testcontainers (`AbstractIntegrationTest` base class), full HTTP stack via `TestRestTemplate` against a random port. Covers link lifecycle, the redirect-and-click-recording flow, Redis cache behavior, rate limiting, and failure scenarios (e.g. Redis down -> circuit breaker falls back to the DB).
- Run them separately (see [.github/workflows/ci.yml](.github/workflows/ci.yml)):

  ```powershell
  gradle test              # unit tests only
  gradle integrationTest   # Testcontainers-backed tests only (needs Docker)
  ```

  Integration test classes are tagged `@Tag("integration")` (inherited from `AbstractIntegrationTest`); `build.gradle`'s default `test` task excludes that tag, and a separate `integrationTest` task includes only it, so a plain `gradle build` stays fast and Docker-free.
- **CI**: GitHub Actions runs Build → Lint (Spotless) → Unit Tests → Integration Tests → Upload Reports on every push/PR, with JUnit XML/HTML reports uploaded as artifacts even on failure.

## Future Enhancements

- Custom/vanity domains per link, QR code generation for a short URL.
- Authentication + per-user link ownership (today every link is anonymous/global - anyone with the short code can inspect/update/deactivate it).
- Bulk create/import endpoint for links.
- A scheduled job to hard-delete or archive long-expired/deactivated links instead of leaving them in the table indefinitely.
- Richer analytics: per-referrer/device/geo breakdowns (today only aggregate count + first/last access are tracked), exportable click logs.
- Move click recording off the in-JVM `@Async` executor onto a durable queue (Kafka/SQS) so click events survive an app crash between the redirect and the async write, and so analytics can scale independently of the redirect service.
- API versioning (e.g. `/v1/...`) before any breaking change to the current endpoint shapes.
- Distributed rate limiting refinements (sliding window instead of fixed window, to avoid burst-at-boundary allowance).

## Tradeoffs

- **Base62(Snowflake ID) short codes over random/hashed codes**: guarantees uniqueness by construction (no collision-check/retry loop needed on the write path) and stays roughly time-ordered, at the cost of short codes being sequential/guessable rather than opaque - mitigated by allowing a custom `alias` when unguessability matters to the caller.
- **Cache-aside Redis with a circuit breaker, not a required dependency**: `resilience4j` wraps every cache call so a Redis outage degrades to hitting PostgreSQL directly (slower, but still correct) instead of failing every request - traded raw latency-under-failure for availability.
- **Async click recording via Spring `@Async` + an in-process listener, not a message queue**: much simpler to build/operate for this scope, but click events for the last request(s) before a crash can be lost, and analytics throughput is bounded by this one JVM (see Future Enhancements).
- **Fixed-window rate limiting (Redis `INCR`+`EXPIRE`) over a sliding-window/token-bucket algorithm**: simpler and cheaper (one round trip per request), at the cost of allowing up to 2x the configured limit across a window boundary.
- **No authentication**: every management endpoint (`/urls/**`) is open - kept out of scope to focus on the shortening/redirect/analytics core; see Future Enhancements.
- **Gradle without a committed wrapper**: keeps the repo smaller and avoids a binary `gradle-wrapper.jar`, at the cost of requiring a matching local Gradle install (or Docker) to build - the Dockerfile and CI workflow both pin an explicit Gradle version (`8.14`) to keep builds reproducible despite this.

## Assumptions

- Short links do not require per-user ownership/authentication in this scope - anyone holding a short code can look up, update, or deactivate it via the management API.
- A single Redis instance and single PostgreSQL instance are sufficient (no read replicas, no Redis Cluster) - `SNOWFLAKE_NODE_ID` exists to support horizontally scaling the *application* tier, but the datastores themselves are assumed single-node.
- Click analytics only need an aggregate count plus first/last-seen timestamps, not a full per-click event log exposed via the API (per-click rows are persisted internally in `ClickEvent` for that aggregation, but there's no endpoint to list them individually).
- A short/bounded 30-second staleness window on `GET /analytics/{shortCode}` is acceptable, in exchange for not recomputing the aggregate on every read.
- Target URLs are assumed to be provided in good faith by the same trust boundary as the API caller; validation defends against obviously malformed/dangerous input (bad scheme, CRLF injection, excessive length) but does not attempt full malware/phishing URL reputation checking.
- Deployment target is a container-friendly environment (Docker/Kubernetes-style) where `SPRING_PROFILES_ACTIVE=prod`, JSON logging, and externalized config via environment variables are the expected production posture (see `application-prod.yml`).


## Configuration

All external config is environment-variable driven (see `application.yml`): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `RATE_LIMIT_COUNT`, `RATE_LIMIT_PERIOD_SECONDS`, `APP_BASE_URL`. Profiles: `dev` (default, verbose logging) and `prod` (quiet logging, locked-down actuator details).
