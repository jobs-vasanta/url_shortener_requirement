package com.urlshortener.dto;

/** Shared upper bounds referenced by request DTO validation annotations. */
public final class RequestLimits {

	private RequestLimits() {
	}

	/**
	 * Longest expiry any link may be given, whether at creation or via PATCH. All links must
	 * expire within 30 days - there is no "permanent link" option any more.
	 */
	public static final long MAX_TTL_SECONDS = 2_592_000L;

	/** Expiry applied when {@code ttlSeconds} is omitted on create: exactly the 30-day maximum. */
	public static final long DEFAULT_TTL_SECONDS = MAX_TTL_SECONDS;
}
