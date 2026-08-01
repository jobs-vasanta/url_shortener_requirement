package com.urlshortener.domain;

/** Plan tier resolved from a caller's API key; gates rate limits and link-expiration rules. */
public enum ApiKeyTier {
	FREE,
	PREMIUM
}
