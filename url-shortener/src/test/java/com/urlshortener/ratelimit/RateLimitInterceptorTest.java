package com.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urlshortener.config.RateLimitProperties;
import com.urlshortener.domain.ApiKeyTier;
import com.urlshortener.exception.RateLimitExceededException;
import com.urlshortener.service.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Covers the single most important gap found in the security review - rate limiting was
 * configured but never enforced. These tests pin down both the "happy path" limiting logic
 * and, just as importantly, the fail-open/fail-closed behavior when Redis itself is unavailable,
 * since getting that backwards would either lock out every client (fail-closed on a bug) or
 * silently disable rate limiting entirely (fail-open on a bug) during a Redis outage.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;
	@Mock
	private ValueOperations<String, String> valueOperations;
	@Mock
	private HttpServletRequest request;
	@Mock
	private HttpServletResponse response;
	@Mock
	private ApiKeyService apiKeyService;

	private RateLimitProperties properties;
	private RateLimitInterceptor interceptor;

	@BeforeEach
	void setUp() {
		properties = new RateLimitProperties();
		properties.setLimitForPeriod(5);
		properties.setPremiumLimitForPeriod(50);
		properties.setPeriodSeconds(60);
		properties.setFailOpen(true);
		interceptor = new RateLimitInterceptor(stringRedisTemplate, properties, apiKeyService);

		when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		// No X-Api-Key header by default -> resolves to FREE, preserving every existing test's
		// prior (pre-tier-aware) behavior.
		when(apiKeyService.resolveTier(any())).thenReturn(ApiKeyTier.FREE);
	}

	// --- Happy path -----------------------------------------------------------------------

	@Test
	void preHandle_allowsRequest_whenUnderLimit() {
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(anyString())).thenReturn(3L);

		boolean result = interceptor.preHandle(request, response, new Object());

		assertThat(result).isTrue();
	}

	@Test
	void preHandle_setsExpiry_onlyForFirstRequestInWindow() {
		// The TTL must only be (re)armed on the count==1 transition - re-setting it on every
		// request would let a steady stream of requests keep the key alive forever, defeating
		// the fixed-window reset.
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(anyString())).thenReturn(1L);

		interceptor.preHandle(request, response, new Object());

		verify(stringRedisTemplate, times(1)).expire(anyString(), any(Duration.class));
	}

	@Test
	void preHandle_doesNotResetExpiry_whenNotFirstRequestInWindow() {
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(anyString())).thenReturn(2L);

		interceptor.preHandle(request, response, new Object());

		verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
	}

	// --- Boundary / negative path -----------------------------------------------------------

	@Test
	void preHandle_allowsRequest_whenExactlyAtLimit() {
		// Boundary case: the limit is inclusive (count == limit must still pass; only
		// count > limit rejects) - an off-by-one here would either reject one request early
		// or allow one request too many.
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(anyString())).thenReturn(5L);

		boolean result = interceptor.preHandle(request, response, new Object());

		assertThat(result).isTrue();
	}

	@Test
	void preHandle_throwsRateLimitExceededException_whenOverLimit() {
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(anyString())).thenReturn(6L);

		assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
				.isInstanceOf(RateLimitExceededException.class);
	}

	@Test
	void preHandle_keysByRemoteAddress_soDifferentClientsAreTrackedIndependently() {
		// Exists to guard the partitioning key itself: if this ever silently changed to a
		// global key, one abusive client could rate-limit every other client.
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(anyString())).thenReturn(1L);

		interceptor.preHandle(request, response, new Object());

		verify(valueOperations).increment(org.mockito.ArgumentMatchers.contains("10.0.0.1"));
	}

	// --- Premium tier -----------------------------------------------------------------------

	@Test
	void preHandle_usesPremiumLimit_whenApiKeyResolvesToPremiumTier() {
		// A count that would exceed the free limit (5) must still be allowed once the caller
		// resolves to PREMIUM, because the interceptor should be checking against
		// getPremiumLimitForPeriod() (50) instead of getLimitForPeriod().
		when(request.getHeader("X-Api-Key")).thenReturn("premium-raw-key");
		when(apiKeyService.resolveTier("premium-raw-key")).thenReturn(ApiKeyTier.PREMIUM);
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(anyString())).thenReturn(20L);

		boolean result = interceptor.preHandle(request, response, new Object());

		assertThat(result).isTrue();
	}

	@Test
	void preHandle_premiumTier_stillThrows_whenOverThePremiumLimit() {
		when(request.getHeader("X-Api-Key")).thenReturn("premium-raw-key");
		when(apiKeyService.resolveTier("premium-raw-key")).thenReturn(ApiKeyTier.PREMIUM);
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(anyString())).thenReturn(51L);

		assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
				.isInstanceOf(RateLimitExceededException.class);
	}

	@Test
	void preHandle_partitionsByApiKey_ratherThanRemoteAddress_whenApiKeyPresent() {
		// Once a key is presented, quota should track the key's identity, not the (potentially
		// shared, e.g. behind a corporate NAT) egress IP.
		when(request.getHeader("X-Api-Key")).thenReturn("premium-raw-key");
		when(apiKeyService.resolveTier("premium-raw-key")).thenReturn(ApiKeyTier.PREMIUM);
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(anyString())).thenReturn(1L);

		interceptor.preHandle(request, response, new Object());

		verify(valueOperations).increment(org.mockito.ArgumentMatchers.contains("key:"));
	}

	// --- Redis-unavailable (fail-open / fail-closed) -----------------------------------------

	@Test
	void preHandle_failsOpen_whenRedisUnavailableAndFailOpenTrue() {
		properties.setFailOpen(true);
		when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("connection refused"));

		boolean result = interceptor.preHandle(request, response, new Object());

		assertThat(result).isTrue();
	}

	@Test
	void preHandle_failsClosed_whenRedisUnavailableAndFailOpenFalse() {
		properties.setFailOpen(false);
		when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("connection refused"));

		assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
				.isInstanceOf(RateLimitExceededException.class);
	}
}
