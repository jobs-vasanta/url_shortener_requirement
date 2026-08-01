package com.urlshortener.dto;

import java.time.Instant;

/** Response body for {@code GET /api/v1/links/{code}/analytics}. */
public record AnalyticsResponse(
		String shortCode,
		long totalClicks,
		Instant firstAccessedAt,
		Instant lastAccessedAt
) {
}
