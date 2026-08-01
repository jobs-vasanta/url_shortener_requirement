package com.urlshortener.dto;

import jakarta.validation.constraints.Positive;

/**
 * Partial-update body for {@code PATCH /urls/{shortCode}}. Both fields are optional;
 * only fields present (non-null) are applied - {@code active} toggles deactivate/reactivate,
 * {@code ttlSeconds} replaces the expiry with "now + ttlSeconds" (send 0 to expire immediately).
 */
public record UpdateLinkRequest(

		@Positive(message = "ttlSeconds must be positive")
		Long ttlSeconds,

		Boolean active
) {
}
