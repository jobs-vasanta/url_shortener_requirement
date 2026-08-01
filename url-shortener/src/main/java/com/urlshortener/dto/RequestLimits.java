package com.urlshortener.dto;

/** Shared upper bounds referenced by request DTO validation annotations. */
public final class RequestLimits {

	private RequestLimits() {
	}

	/**
	 * Free-tier link lifetime: every free-tier link must expire within 30 days, and omitting
	 * ttlSeconds on create defaults to exactly this. Premium callers bypass this cap entirely -
	 * see UrlServiceImpl#computeExpiry - omitting ttlSeconds for them means no expiration at all.
	 */
	public static final long FREE_MAX_TTL_SECONDS = 2_592_000L;
	public static final long FREE_DEFAULT_TTL_SECONDS = FREE_MAX_TTL_SECONDS;

	/** Sanity ceiling for premium callers who do specify an explicit ttlSeconds (10 years). */
	public static final long PREMIUM_MAX_TTL_SECONDS = 315_360_000L;

	/**
	 * Bean Validation's static {@code @Max} can't see the caller's resolved tier, so both DTOs are
	 * annotated against this outer (premium) bound; the stricter free-tier cap above is enforced
	 * afterward in UrlServiceImpl, where the tier is known.
	 */
	public static final long MAX_TTL_SECONDS = PREMIUM_MAX_TTL_SECONDS;
}
