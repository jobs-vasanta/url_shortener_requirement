package com.urlshortener.dto;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error response shape returned by {@link com.urlshortener.exception.GlobalExceptionHandler}.
 * Never includes stack traces or internal implementation details.
 */
public record ErrorResponse(
		Instant timestamp,
		int status,
		String error,
		String message,
		List<String> details
) {
	public static ErrorResponse of(int status, String error, String message) {
		return new ErrorResponse(Instant.now(), status, error, message, List.of());
	}

	public static ErrorResponse of(int status, String error, String message, List<String> details) {
		return new ErrorResponse(Instant.now(), status, error, message, details);
	}
}
