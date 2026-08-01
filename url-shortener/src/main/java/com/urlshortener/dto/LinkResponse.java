package com.urlshortener.dto;

import java.time.Instant;

/** Response body for a created or retrieved link. */
public record LinkResponse(
		String shortCode,
		String shortUrl,
		String originalUrl,
		String status,
		Instant createdAt,
		Instant expiresAt
) {
}
