package com.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.urlshortener.dto.ErrorResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves the 429 failure path actually engages end-to-end against a real Redis-backed counter.
 * Declares its own {@code @DynamicPropertySource} (in addition to the one inherited from
 * {@link AbstractIntegrationTest}) purely to force Spring to start a fresh, isolated context for
 * this class - otherwise it would share the generous rate limit (and the context) that every
 * other integration test class relies on to avoid being incidentally throttled.
 */
class RateLimitIntegrationTest extends AbstractIntegrationTest {

	@DynamicPropertySource
	static void tightenRateLimitForThisTestClass(DynamicPropertyRegistry registry) {
		registry.add("app.rate-limit.limit-for-period", () -> 3);
		registry.add("app.rate-limit.period-seconds", () -> 60);
	}

	@Test
	void exceedingTheLimit_returns429WithARetryAfterHeader() {
		for (int i = 0; i < 3; i++) {
			ResponseEntity<ErrorResponse> response =
					restTemplate.getForEntity("/rl-probe-" + i + "-" + UUID.randomUUID(), ErrorResponse.class);
			// Under the configured limit: a normal 404 (unknown code), not a throttled response.
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		ResponseEntity<ErrorResponse> limited =
				restTemplate.getForEntity("/rl-probe-over-" + UUID.randomUUID(), ErrorResponse.class);

		assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(limited.getHeaders().getFirst("Retry-After")).isEqualTo("60");
		assertThat(limited.getBody().error()).isEqualTo("TOO_MANY_REQUESTS");
	}
}
