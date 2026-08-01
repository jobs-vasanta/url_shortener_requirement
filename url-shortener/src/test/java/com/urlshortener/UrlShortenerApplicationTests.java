package com.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the Spring application context loads. Deeper integration tests
 * (Testcontainers-backed Postgres/Redis) belong under a dedicated
 * `integration` source set or `@Tag("integration")`, per TaskBreakdown.md T15.
 */
@SpringBootTest
@ActiveProfiles("test")
class UrlShortenerApplicationTests {

	@Test
	void contextLoads() {
	}
}
