# URL Shortener — Service

Production-grade Spring Boot project scaffold. See [../Architecture.md](../Architecture.md) for the full design and [../TaskBreakdown.md](../TaskBreakdown.md) for the implementation plan this structure supports.

## Stack

- Java 21, Spring Boot 3.3.x, Gradle (Groovy DSL)
- PostgreSQL (persistence), Flyway (migrations)
- Redis (cache-aside for link lookups, analytics aggregate cache)
- springdoc-openapi, Spring Boot Actuator, Resilience4j

## Package Structure

```
com.urlshortener
├── config       # RedisConfig, SecurityConfig, AsyncConfig, OpenApiConfig, RateLimitProperties
├── controller    # LinkController, RedirectController, AnalyticsController
├── service       # Interfaces (LinkService, ShortCodeGeneratorService, UrlValidationService, AnalyticsService)
│   └── impl      # Implementations
├── domain        # Link, LinkStatus, ClickEvent (JPA entities)
├── repository    # LinkRepository, ClickEventRepository (Spring Data JPA)
├── dto           # CreateLinkRequest, LinkResponse, AnalyticsResponse, ErrorResponse
├── exception     # Domain exceptions + GlobalExceptionHandler
├── event         # ClickRecordedEvent + async listener (analytics decoupling)
└── util          # Base62Encoder, HashUtil
```

## Local Setup

Requires a local PostgreSQL and Redis instance (or run via Docker):

```powershell
docker run -d --name urlshortener-pg -e POSTGRES_DB=urlshortener -e POSTGRES_USER=urlshortener -e POSTGRES_PASSWORD=urlshortener -p 5432:5432 postgres:16
docker run -d --name urlshortener-redis -p 6379:6379 redis:7
```

Run the app:

```powershell
./gradlew bootRun
```

Run tests:

```powershell
./gradlew test
```

Note: this scaffold does not include the Gradle wrapper jar; run `gradle wrapper --gradle-version 8.10` once (with a local Gradle install) to generate `gradlew`/`gradlew.bat`, or substitute a local Gradle 8.10+ install for the commands above.

## Configuration

All external config is environment-variable driven (see `application.yml`): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `RATE_LIMIT_COUNT`, `RATE_LIMIT_PERIOD_SECONDS`, `APP_BASE_URL`. Profiles: `dev` (default, verbose logging) and `prod` (quiet logging, locked-down actuator details).
