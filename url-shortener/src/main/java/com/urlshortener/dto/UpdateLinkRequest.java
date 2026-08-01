package com.urlshortener.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/**
 * Partial-update body for {@code PATCH /urls/{shortCode}}. Both fields are optional;
 * only fields present (non-null) are applied - {@code active} toggles deactivate/reactivate,
 * {@code ttlSeconds} replaces the expiry with "now + ttlSeconds" (send 0 to expire immediately).
 */
public record UpdateLinkRequest(

		@Positive(message = "ttlSeconds must be positive")
		@Max(value = RequestLimits.MAX_TTL_SECONDS, message = "ttlSeconds must be at most " + RequestLimits.MAX_TTL_SECONDS + " seconds")
		Long ttlSeconds,

		Boolean active
) {

	@AssertTrue(message = "At least one of ttlSeconds or active must be provided")
	boolean isAtLeastOneFieldPresent() {
		return ttlSeconds != null || active != null;
	}
}
