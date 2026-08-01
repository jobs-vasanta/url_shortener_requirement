package com.urlshortener.exception;

import com.urlshortener.dto.ErrorResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain/validation exceptions to consistent HTTP responses. Client-facing
 * messages are always generic/actionable; full details are logged server-side only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(LinkNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(LinkNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of(404, "NOT_FOUND", ex.getMessage()));
	}

	@ExceptionHandler(LinkGoneException.class)
	public ResponseEntity<ErrorResponse> handleGone(LinkGoneException ex) {
		return ResponseEntity.status(HttpStatus.GONE)
				.body(ErrorResponse.of(410, "GONE", ex.getMessage()));
	}

	@ExceptionHandler(InvalidUrlException.class)
	public ResponseEntity<ErrorResponse> handleInvalidUrl(InvalidUrlException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of(400, "BAD_REQUEST", ex.getMessage()));
	}

	@ExceptionHandler(AliasAlreadyExistsException.class)
	public ResponseEntity<ErrorResponse> handleAliasConflict(AliasAlreadyExistsException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ErrorResponse.of(409, "CONFLICT", ex.getMessage()));
	}

	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(ErrorResponse.of(429, "TOO_MANY_REQUESTS", ex.getMessage()));
	}

	/**
	 * Defensive backstop for the DB's unique constraint on short_code: should only ever fire if a
	 * Snowflake node ID is misconfigured (see SnowflakeConfig) or a custom alias races another request.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation, likely a short_code uniqueness conflict", ex);
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ErrorResponse.of(409, "CONFLICT", "The request could not be completed due to a conflict; please retry"));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		List<String> details = ex.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getDefaultMessage)
				.toList();
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of(400, "VALIDATION_FAILED", "Request validation failed", details));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponse.of(500, "INTERNAL_ERROR", "An unexpected error occurred"));
	}
}
