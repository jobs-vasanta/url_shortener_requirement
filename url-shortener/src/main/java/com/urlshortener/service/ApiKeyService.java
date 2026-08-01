package com.urlshortener.service;

import com.urlshortener.domain.ApiKeyTier;

/** Resolves the caller's plan tier from the raw {@code X-Api-Key} header value, if any. */
public interface ApiKeyService {

	/**
	 * Resolves a tier for the given raw key. Never throws and never blocks a request: a missing,
	 * blank, unrecognized, or deactivated key all resolve to {@link ApiKeyTier#FREE} rather than
	 * an error, so this feature is purely additive over the previously key-less API.
	 */
	ApiKeyTier resolveTier(String rawApiKey);
}
