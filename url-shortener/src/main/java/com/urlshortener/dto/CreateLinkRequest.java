package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/links}.
 * {@code alias} and {@code ttlSeconds} are optional; omit for default behavior
 * (auto-generated code, no expiration).
 */
public record CreateLinkRequest(

		@NotBlank(message = "longUrl must not be blank")
		@Size(max = 2048, message = "longUrl must be at most 2048 characters")
		String longUrl,

		@Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$", message = "alias must be 3-32 chars of letters, digits, - or _")
		String alias,

		@Positive(message = "ttlSeconds must be positive")
		Long ttlSeconds
) {
}
