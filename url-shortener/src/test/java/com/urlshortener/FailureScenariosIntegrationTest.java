package com.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Negative-path/HTTP-layer failure scenarios that don't naturally belong to the lifecycle,
 * redirect, or cache test classes: malformed requests, security-sensitive validation (SSRF-blocked
 * hosts, reserved route names, disallowed schemes), and the shape of the error body itself.
 * These exist to lock in the security-review fixes made earlier in this project - it would be
 * easy for a future refactor to accidentally reopen one of these without a regression test.
 */
class FailureScenariosIntegrationTest extends AbstractIntegrationTest {

	@Test
	void createLink_withABlankLongUrl_returns400WithFieldValidationDetails() {
		ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
				"/urls", new CreateLinkRequest("", null, null), ErrorResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.getBody().details()).isNotEmpty();
	}

	@Test
	void createLink_targetingALoopbackHost_isRejectedAsAnSsrfRisk() {
		ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
				"/urls", new CreateLinkRequest("http://127.0.0.1/admin", null, null), ErrorResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		// The message must stay generic - it must never echo the blocked host back to the caller.
		assertThat(response.getBody().message()).doesNotContain("127.0.0.1");
	}

	@Test
	void createLink_withADisallowedScheme_isRejected() {
		ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
				"/urls", new CreateLinkRequest("ftp://example.com/file", null, null), ErrorResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void createLink_withAReservedAliasWord_returns400() {
		ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
				"/urls", new CreateLinkRequest("https://example.com/reserved", "actuator", null), ErrorResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void createLink_withAMalformedJsonBody_returns400_notA500() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> malformed = new HttpEntity<>("{ \"longUrl\": ", headers);

		ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/urls", malformed, ErrorResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error()).isEqualTo("MALFORMED_REQUEST");
	}

	@Test
	void everyErrorResponse_carriesACorrelationIdForSupportTriage() {
		ResponseEntity<ErrorResponse> response =
				restTemplate.getForEntity("/urls/definitely-does-not-exist", ErrorResponse.class);

		assertThat(response.getBody().correlationId()).isNotBlank();
	}
}
