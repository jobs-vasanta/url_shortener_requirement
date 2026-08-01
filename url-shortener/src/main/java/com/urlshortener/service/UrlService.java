package com.urlshortener.service;

import com.urlshortener.domain.ApiKeyTier;
import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.dto.UpdateLinkRequest;

/**
 * Core use cases for the URL shortener: create, redirect-resolution, lookup, and
 * deactivation. Implementations own URL validation, short-code assignment, Redis
 * caching, expiry/status enforcement, and triggering click analytics - see
 * {@link com.urlshortener.service.impl.UrlServiceImpl} for the single implementation.
 */
public interface UrlService {

	/**
	 * Creates a new short link: validates the target URL, assigns a short code
	 * (generated, or the caller's alias if one was supplied and is available),
	 * persists it, and populates the Redis cache so the very first redirect is
	 * already a cache hit. {@code tier} (resolved from the caller's API key) determines
	 * the link's expiration behavior - see UrlServiceImpl#computeExpiry.
	 */
	LinkResponse createLink(CreateLinkRequest request, ApiKeyTier tier);

	/**
	 * Resolves a short code to its original URL for redirection. Enforces active/
	 * not-expired status (raising a 404/410-mapped exception otherwise) and triggers
	 * asynchronous analytics recording for the click before returning.
	 */
	String resolveForRedirect(String shortCode, ClickContext clickContext);

	/** Fetches a link's public metadata (not the analytics - see AnalyticsService for that). */
	LinkResponse getLink(String shortCode);

	/**
	 * Applies a partial update: reactivates/deactivates when {@code active} is present,
	 * and/or replaces the expiry when {@code ttlSeconds} is present (subject to the same
	 * tier-dependent cap as create). Fields left null are unchanged.
	 */
	LinkResponse updateLink(String shortCode, UpdateLinkRequest request, ApiKeyTier tier);

	/** Marks a link DEACTIVATED so subsequent redirects return 410 Gone, and evicts it from cache. */
	void deactivate(String shortCode);
}
