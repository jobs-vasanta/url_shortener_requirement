package com.urlshortener.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urlshortener.domain.ApiKey;
import com.urlshortener.domain.ApiKeyTier;
import com.urlshortener.repository.ApiKeyRepository;
import com.urlshortener.util.HashUtil;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers ApiKeyServiceImpl.resolveTier - the single gate deciding whether a caller gets
 * free or premium behavior (rate limit, link expiration). It must fail safe: any key that
 * is missing, blank, unrecognized, or deactivated resolves to FREE rather than an error,
 * so this feature stays purely additive over the previously key-less API.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceImplTest {

	@Mock
	private ApiKeyRepository apiKeyRepository;

	private ApiKeyServiceImpl apiKeyService;

	@BeforeEach
	void setUp() {
		apiKeyService = new ApiKeyServiceImpl(apiKeyRepository);
	}

	@Test
	void resolveTier_returnsFree_whenRawKeyIsNull() {
		assertThat(apiKeyService.resolveTier(null)).isEqualTo(ApiKeyTier.FREE);
	}

	@Test
	void resolveTier_returnsFree_whenRawKeyIsBlank() {
		assertThat(apiKeyService.resolveTier("   ")).isEqualTo(ApiKeyTier.FREE);
	}

	@Test
	void resolveTier_returnsFree_whenKeyIsUnrecognized() {
		when(apiKeyRepository.findByKeyHashAndActiveTrue(HashUtil.sha256Hex("unknown-key")))
				.thenReturn(Optional.empty());

		assertThat(apiKeyService.resolveTier("unknown-key")).isEqualTo(ApiKeyTier.FREE);
	}

	@Test
	void resolveTier_returnsPremium_whenKeyIsActiveAndPremium() {
		String rawKey = "premium-raw-key";
		ApiKey apiKey = new ApiKey(HashUtil.sha256Hex(rawKey), ApiKeyTier.PREMIUM, "test", Instant.now());
		when(apiKeyRepository.findByKeyHashAndActiveTrue(eq(HashUtil.sha256Hex(rawKey))))
				.thenReturn(Optional.of(apiKey));

		assertThat(apiKeyService.resolveTier(rawKey)).isEqualTo(ApiKeyTier.PREMIUM);
	}

	@Test
	void resolveTier_returnsFree_whenKeyIsActiveAndFreeTier() {
		String rawKey = "free-raw-key";
		ApiKey apiKey = new ApiKey(HashUtil.sha256Hex(rawKey), ApiKeyTier.FREE, "test", Instant.now());
		when(apiKeyRepository.findByKeyHashAndActiveTrue(eq(HashUtil.sha256Hex(rawKey))))
				.thenReturn(Optional.of(apiKey));

		assertThat(apiKeyService.resolveTier(rawKey)).isEqualTo(ApiKeyTier.FREE);
	}

	@Test
	void resolveTier_looksUpByHash_neverByTheRawKey() {
		// The repository must only ever be queried by hash - never by the raw secret value.
		String rawKey = "some-raw-key";
		when(apiKeyRepository.findByKeyHashAndActiveTrue(eq(HashUtil.sha256Hex(rawKey))))
				.thenReturn(Optional.empty());

		apiKeyService.resolveTier(rawKey);

		verify(apiKeyRepository).findByKeyHashAndActiveTrue(HashUtil.sha256Hex(rawKey));
		verify(apiKeyRepository, never()).findByKeyHashAndActiveTrue(rawKey);
	}
}
