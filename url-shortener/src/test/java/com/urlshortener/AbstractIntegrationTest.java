package com.urlshortener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers-backed base for all integration tests: one real Postgres and one real
 * Redis, started once in a static initializer and reused for the whole JVM (a fresh pair of
 * containers per test class would make the suite unacceptably slow, and the resource-reaper
 * that ships with Testcontainers tears them down automatically when the JVM exits, so no
 * explicit shutdown is needed here).
 *
 * <p>Each subclass gets a full Spring web server on a random port plus a {@link TestRestTemplate}
 * wired to it, so tests exercise the real HTTP stack (servlet filters, JSON (de)serialization,
 * {@code @RestControllerAdvice} exception mapping) end-to-end rather than mocked layers.
 * {@code TestRestTemplate} - unlike a plain {@code RestTemplate} - does not throw on 4xx/5xx and
 * does not follow redirects automatically, which is exactly what's needed to assert on error
 * bodies and on the redirect endpoint's raw 302/Location header.
 *
 * <p>Test classes that don't declare their own {@code @DynamicPropertySource} share one Spring
 * context (and therefore start-up cost) with every other such class; {@code RateLimitIntegrationTest}
 * deliberately declares an extra one to force a separate, isolated context with a tighter limit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

	protected static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
					.withDatabaseName("urlshortener")
					.withUsername("urlshortener")
					.withPassword("urlshortener");

	protected static final GenericContainer<?> REDIS =
			new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
					.withExposedPorts(6379);

	static {
		POSTGRES.start();
		REDIS.start();
	}

	@Autowired
	protected TestRestTemplate restTemplate;

	@DynamicPropertySource
	static void registerContainerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);

		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

		// Generous by default so CRUD/cache/redirect tests aren't incidentally rate-limited;
		// RateLimitIntegrationTest overrides this down to a tiny value in its own Spring context.
		registry.add("app.rate-limit.limit-for-period", () -> 100_000);
	}
}
