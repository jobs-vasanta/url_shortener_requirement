package com.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.urlshortener.domain.ApiKey;
import com.urlshortener.domain.ApiKeyTier;
import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.ErrorResponse;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.dto.RequestLimits;
import com.urlshortener.repository.ApiKeyRepository;
import com.urlshortener.util.HashUtil;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end proof of the Premium Users feature over the real HTTP stack, Postgres, and Redis:
 * a caller's {@code X-Api-Key} header must resolve to a tier that changes link-expiration
 * behavior (see {@code UrlServiceImpl#computeExpiry}). Tier-aware rate limiting is covered at
 * the unit level ({@code RateLimitInterceptorTest}) rather than here, to avoid needing yet
 * another isolated, tightly-configured Spring context just for this class.
 */
class PremiumTierIntegrationTest extends AbstractIntegrationTest {

	private static final String PREMIUM_RAW_KEY = "integration-test-premium-key";

	@Autowired
	private ApiKeyRepository apiKeyRepository;

	private String premiumApiKeyHeaderValue() {
		String keyHash = HashUtil.sha256Hex(PREMIUM_RAW_KEY);
		if (apiKeyRepository.findByKeyHashAndActiveTrue(keyHash).isEmpty()) {
			apiKeyRepository.save(new ApiKey(keyHash, ApiKeyTier.PREMIUM, "integration test", Instant.now()));
		}
		return PREMIUM_RAW_KEY;
	}

	@Test
	void createLink_freeTier_withTtlSecondsOverThirtyDays_returns400() {
		// No X-Api-Key header -> resolves to FREE, so the mandatory 30-day cap still applies
		// even though the DTO-level @Max is sized for premium.
		CreateLinkRequest request = new CreateLinkRequest(
				"https://example.com/free-tier-over-cap", null, RequestLimits.FREE_MAX_TTL_SECONDS + 1);

		ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/urls", request, ErrorResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error()).isEqualTo("TTL_EXCEEDS_PLAN_LIMIT");
	}

	@Test
	void createLink_premiumTier_withoutTtlSeconds_neverExpires() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Api-Key", premiumApiKeyHeaderValue());
		CreateLinkRequest request = new CreateLinkRequest("https://example.com/premium-no-ttl", null, null);

		ResponseEntity<LinkResponse> response = restTemplate.postForEntity(
				"/urls", new HttpEntity<>(request, headers), LinkResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().expiresAt()).isNull();
	}

	@Test
	void createLink_premiumTier_withTtlSecondsOverFreeCap_isAllowed() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Api-Key", premiumApiKeyHeaderValue());
		long ttlSeconds = RequestLimits.FREE_MAX_TTL_SECONDS + 1;
		CreateLinkRequest request = new CreateLinkRequest("https://example.com/premium-long-ttl", null, ttlSeconds);

		ResponseEntity<LinkResponse> response = restTemplate.postForEntity(
				"/urls", new HttpEntity<>(request, headers), LinkResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().expiresAt()).isAfter(Instant.now().plusSeconds(RequestLimits.FREE_MAX_TTL_SECONDS));
	}

	@Test
	void createLink_unrecognizedApiKey_fallsBackToFreeTierBehavior() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Api-Key", "not-a-real-key");
		CreateLinkRequest request = new CreateLinkRequest(
				"https://example.com/unrecognized-key", null, RequestLimits.FREE_MAX_TTL_SECONDS + 1);

		ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
				"/urls", new HttpEntity<>(request, headers), ErrorResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error()).isEqualTo("TTL_EXCEEDS_PLAN_LIMIT");
	}
}
