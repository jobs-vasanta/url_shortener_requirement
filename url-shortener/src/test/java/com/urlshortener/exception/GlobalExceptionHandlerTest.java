package com.urlshortener.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.urlshortener.config.RateLimitProperties;
import com.urlshortener.dto.ErrorResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Every handler here maps a domain/framework exception to a specific HTTP status and a
 * client-safe body. The two "does not leak" tests exist specifically because this handler's
 * whole purpose is to keep internal exception details away from API responses (OWASP A09/A05
 * concerns) - a regression there is a security regression, not just a UX one.
 */
class GlobalExceptionHandlerTest {

	private static final int PERIOD_SECONDS = 60;

	private GlobalExceptionHandler handler;

	@BeforeEach
	void setUp() {
		RateLimitProperties properties = new RateLimitProperties();
		properties.setPeriodSeconds(PERIOD_SECONDS);
		handler = new GlobalExceptionHandler(properties);
	}

	@Test
	void handleNotFound_returns404WithMessage() {
		ResponseEntity<ErrorResponse> response = handler.handleNotFound(new LinkNotFoundException("abc"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().error()).isEqualTo("NOT_FOUND");
		assertThat(response.getBody().message()).contains("abc");
	}

	@Test
	void handleGone_returns410() {
		ResponseEntity<ErrorResponse> response = handler.handleGone(new LinkGoneException("abc"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
	}

	@Test
	void handleInvalidUrl_returns400() {
		ResponseEntity<ErrorResponse> response = handler.handleInvalidUrl(new InvalidUrlException("bad url"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().message()).isEqualTo("bad url");
	}

	@Test
	void handleAliasConflict_returns409() {
		ResponseEntity<ErrorResponse> response = handler.handleAliasConflict(new AliasAlreadyExistsException("taken"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void handleReservedAlias_returns400() {
		ResponseEntity<ErrorResponse> response = handler.handleReservedAlias(new ReservedAliasException("urls"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void handleRateLimit_returns429WithRetryAfterHeaderMatchingConfiguredPeriod() {
		// The Retry-After header value must track the *configured* period, not a hardcoded
		// number - this test would fail if that wiring were ever broken.
		ResponseEntity<ErrorResponse> response = handler.handleRateLimit(new RateLimitExceededException("slow down"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo(String.valueOf(PERIOD_SECONDS));
	}

	@Test
	void handleDataIntegrityViolation_returns409WithGenericMessage_notLeakingConstraintDetails() {
		// Negative/security test: the raw DB exception (which can contain constraint/column/table
		// names) must never reach the response body - only our own generic message.
		DataIntegrityViolationException ex = new DataIntegrityViolationException(
				"duplicate key value violates unique constraint \"idx_links_short_code\"");

		ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().message()).doesNotContain("idx_links_short_code");
	}

	@Test
	void handleValidation_returns400WithFieldErrorDetails() {
		// Happy path for the bean-validation handler: every field error's message must surface
		// in `details` so the client knows exactly which fields to fix.
		MethodArgumentNotValidException ex = Mockito.mock(MethodArgumentNotValidException.class);
		BindingResult bindingResult = Mockito.mock(BindingResult.class);
		Mockito.when(ex.getBindingResult()).thenReturn(bindingResult);
		Mockito.when(bindingResult.getFieldErrors()).thenReturn(List.of(
				new FieldError("createLinkRequest", "longUrl", "longUrl must not be blank")));

		ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().details()).containsExactly("longUrl must not be blank");
	}

	@Test
	void handleMalformedBody_returns400WithGenericMessage() {
		// Edge case: missing/unparseable JSON body must not echo the underlying Jackson parser
		// exception message (which can reveal internal DTO field names/types).
		HttpMessageNotReadableException ex = Mockito.mock(HttpMessageNotReadableException.class);

		ResponseEntity<ErrorResponse> response = handler.handleMalformedBody(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().message()).isEqualTo("Request body is missing or not valid JSON");
	}

	@Test
	void handleUnexpected_returns500WithGenericMessage_notLeakingExceptionMessage() {
		// The most important "does not leak" test in this class: any uncaught exception's
		// message/type/stack trace must never reach the client, regardless of what it says.
		Exception ex = new IllegalStateException("Connection to internal-db-host:5432 refused");

		ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
		assertThat(response.getBody().message()).doesNotContain("internal-db-host");
	}
}
