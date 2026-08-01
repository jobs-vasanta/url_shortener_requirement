package com.urlshortener.dto;

import com.urlshortener.logging.CorrelationIdFilter;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;

/**
 * Consistent error response shape returned by {@link com.urlshortener.exception.GlobalExceptionHandler}.
 * Never includes stack traces or internal implementation details. Includes the request's
 * correlation ID so a caller can quote it back when reporting an issue.
 */
public record ErrorResponse(
		Instant timestamp,
		int status,
		String error,
		String message,
		List<String> details,
		String correlationId
) {
	public static ErrorResponse of(int status, String error, String message) {
		return new ErrorResponse(Instant.now(), status, error, message, List.of(), currentCorrelationId());
	}

	public static ErrorResponse of(int status, String error, String message, List<String> details) {
		return new ErrorResponse(Instant.now(), status, error, message, details, currentCorrelationId());
	}

	private static String currentCorrelationId() {
		return MDC.get(CorrelationIdFilter.MDC_KEY);
	}
}
