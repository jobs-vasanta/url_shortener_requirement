package com.urlshortener.dto;

/** Shared upper bounds referenced by request DTO validation annotations. */
public final class RequestLimits {

	private RequestLimits() {
	}

	/** Longest expiry any link may be given, whether at creation or via PATCH (365 days). */
	public static final long MAX_TTL_SECONDS = 31_536_000L;
}
