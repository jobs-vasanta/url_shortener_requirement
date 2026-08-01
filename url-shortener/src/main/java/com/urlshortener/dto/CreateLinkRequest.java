package com.urlshortener.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /urls}.
 * {@code alias} is optional. {@code ttlSeconds} is optional too, but its meaning depends on the
 * caller's plan tier (resolved from the {@code X-Api-Key} header): free-tier callers get a
 * mandatory 30-day cap and default; premium callers may omit it for no expiration at all, or
 * supply an explicit value up to a generous sanity ceiling.
 */
public record CreateLinkRequest(

		@NotBlank(message = "longUrl must not be blank")
		@Size(max = 2048, message = "longUrl must be at most 2048 characters")
		String longUrl,

		@Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$", message = "alias must be 3-32 chars of letters, digits, - or _")
		String alias,

		@Positive(message = "ttlSeconds must be positive")
		@Max(value = RequestLimits.MAX_TTL_SECONDS, message = "ttlSeconds must be at most " + RequestLimits.MAX_TTL_SECONDS + " seconds")
		Long ttlSeconds
) {
}
