package com.urlshortener.cache;

/** Single source of truth for Redis key naming, so prefixes can't drift or collide between callers. */
public final class CacheKeys {

	private static final String LINK_PREFIX = "link:";
	private static final String ANALYTICS_PREFIX = "analytics:";

	private CacheKeys() {
	}

	/** Keyed by short code - this is the exact lookup key used on the redirect hot path. */
	public static String link(String shortCode) {
		return LINK_PREFIX + shortCode;
	}

	/** Keyed by the numeric link id (not short code) since analytics are read/invalidated via the loaded Link. */
	public static String analytics(Long linkId) {
		return ANALYTICS_PREFIX + linkId;
	}
}
