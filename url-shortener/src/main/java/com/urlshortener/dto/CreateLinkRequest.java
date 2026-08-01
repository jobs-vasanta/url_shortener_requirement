package com.urlshortener.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /urls}.
 * {@code alias} is optional. {@code ttlSeconds} is optional too, but omitting it does NOT mean
 * "never expires" - every link expires within 30 days, so omitting it defaults to the full
 * 30-day maximum ({@link RequestLimits#DEFAULT_TTL_SECONDS}).
 */
public record CreateLinkRequest(

		@NotBlank(message = "longUrl must not be blank")
		@Size(max = 2048, message = "longUrl must be at most 2048 characters")
		String longUrl,

		@Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$", message = "alias must be 3-32 chars of letters, digits, - or _")
		String alias,

		@Positive(message = "ttlSeconds must be positive")
		@Max(value = RequestLimits.MAX_TTL_SECONDS, message = "ttlSeconds must be at most " + RequestLimits.MAX_TTL_SECONDS + " (30 days)")
		Long ttlSeconds
) {
}
