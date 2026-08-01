package com.urlshortener.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urlshortener.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * No mocking here - {@link UrlValidationServiceImpl} is pure logic (no collaborators), so these
 * are plain state-based tests. Every disallowed-input test exists because it corresponds to a
 * specific vulnerability class identified in the security review (open redirect / SSRF / log or
 * header injection) - each one is a regression guard against silently loosening that rule later.
 */
class UrlValidationServiceImplTest {

	private final UrlValidationServiceImpl validationService = new UrlValidationServiceImpl();

	// --- Happy path -----------------------------------------------------------------------

	@Test
	void validate_acceptsPublicHttpsUrl() {
		// Baseline happy path: a well-formed, public, https URL must never be rejected.
		// Uses a literal IP (no live DNS lookup) so the test is deterministic offline.
		assertThatCode(() -> validationService.validate("https://8.8.8.8/some/path"))
				.doesNotThrowAnyException();
	}

	@Test
	void validate_acceptsPublicHttpUrl() {
		// http is explicitly allowed alongside https, not just https - guards against
		// someone "tightening" the scheme allow-list and breaking legitimate http targets.
		assertThatCode(() -> validationService.validate("http://8.8.8.8/"))
				.doesNotThrowAnyException();
	}

	@Test
	void validate_isCaseInsensitiveForScheme() {
		// Edge case: RFC 3986 schemes are case-insensitive; the allow-list check must
		// lowercase before comparing or it would wrongly reject "HTTPS://...".
		assertThatCode(() -> validationService.validate("HTTPS://8.8.8.8/"))
				.doesNotThrowAnyException();
	}

	// --- Negative path: structurally invalid input -----------------------------------------

	@Test
	void validate_throwsWhenUrlIsNull() {
		// Null must fail validation, not NPE - callers rely on InvalidUrlException for all bad input.
		assertThatThrownBy(() -> validationService.validate(null))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL must not be blank");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void validate_throwsWhenUrlIsBlank(String blank) {
		// Edge case: empty string and whitespace-only are both "blank" and must be rejected identically.
		assertThatThrownBy(() -> validationService.validate(blank))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL must not be blank");
	}

	@Test
	void validate_throwsWhenUrlExceedsMaxLength() {
		// Boundary case: one character over the documented 2048 limit must be rejected -
		// this is also a lightweight DoS guard against unbounded-length inputs.
		String tooLong = "https://8.8.8.8/" + "a".repeat(2048);
		assertThatThrownBy(() -> validationService.validate(tooLong))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessageContaining("exceeds maximum length");
	}

	@Test
	void validate_acceptsUrlAtExactMaxLength() {
		// Boundary case: exactly 2048 chars must still be accepted (off-by-one check on the other side).
		String path = "a".repeat(2048 - "https://8.8.8.8/".length());
		assertThatCode(() -> validationService.validate("https://8.8.8.8/" + path))
				.doesNotThrowAnyException();
	}

	@Test
	void validate_throwsWhenUrlIsMalformed() {
		// A syntactically broken URI (unterminated IPv6 literal) must map to a generic message,
		// never propagate a raw parser exception to the caller.
		assertThatThrownBy(() -> validationService.validate("http://[not-closed"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL is malformed");
	}

	@Test
	void validate_malformedUrlMessage_doesNotEchoRawInput() {
		// Regression guard for the XSS/reflected-input fix: the exception message must never
		// contain the attacker-supplied string verbatim.
		String malicious = "http://[<script>not-closed";
		assertThatThrownBy(() -> validationService.validate(malicious))
				.isInstanceOf(InvalidUrlException.class)
				.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("<script>"));
	}

	// --- Negative path: control-character / injection defense --------------------------------

	@Test
	void validate_throwsWhenUrlContainsCarriageReturn() {
		// Defense-in-depth against HTTP response-splitting via the redirect's Location header.
		assertThatThrownBy(() -> validationService.validate("https://8.8.8.8/\r\nSet-Cookie: evil=1"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL must not contain control characters");
	}

	@Test
	void validate_throwsWhenUrlContainsLineFeed() {
		// Same defense as above, isolated to a bare \n (some parsers only reject \r\n pairs, not lone \n).
		assertThatThrownBy(() -> validationService.validate("https://8.8.8.8/a\nb"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL must not contain control characters");
	}

	@Test
	void validate_throwsWhenUrlContainsNulByte() {
		// Edge case: a NUL byte is a classic string-truncation attack vector in native/C-backed parsers.
		assertThatThrownBy(() -> validationService.validate("https://8.8.8.8/a\0b"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL must not contain control characters");
	}

	// --- Negative path: scheme / host allow-list --------------------------------------------

	@Test
	void validate_throwsWhenSchemeIsDisallowed() {
		// Only http/https are permitted; any other scheme (e.g. ftp, file, javascript) must be rejected.
		assertThatThrownBy(() -> validationService.validate("ftp://8.8.8.8/file"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL scheme must be http or https");
	}

	@Test
	void validate_throwsWhenSchemeIsMissing() {
		// Edge case: a scheme-less string like "example.com/path" parses as a relative URI with a
		// null scheme - must be rejected the same way as an explicitly disallowed scheme.
		assertThatThrownBy(() -> validationService.validate("example.com/path"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL scheme must be http or https");
	}

	@Test
	void validate_throwsWhenHostIsMissing() {
		// "http:///path" parses with a null host - must be rejected before any host-based checks run.
		assertThatThrownBy(() -> validationService.validate("http:///path"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL must include a valid host");
	}

	@Test
	void validate_throwsWhenHostIsLoopback() {
		// SSRF guard: loopback must never be reachable via a shortened link (e.g. hitting a
		// local admin endpoint on the app server itself).
		assertThatThrownBy(() -> validationService.validate("http://127.0.0.1/admin"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL targets a disallowed private/internal network address");
	}

	@Test
	void validate_throwsWhenHostIsLinkLocal() {
		// SSRF guard: 169.254.0.0/16 commonly exposes cloud-provider instance metadata endpoints.
		assertThatThrownBy(() -> validationService.validate("http://169.254.169.254/latest/meta-data"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL targets a disallowed private/internal network address");
	}

	@Test
	void validate_throwsWhenHostIsSiteLocal() {
		// SSRF guard: RFC 1918 private ranges (internal network) must not be reachable either.
		assertThatThrownBy(() -> validationService.validate("http://192.168.1.1/"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL targets a disallowed private/internal network address");
	}

	@Test
	void validate_throwsWhenHostIsAnyLocal() {
		// Edge case: 0.0.0.0 is the wildcard/any-local address - blocking it prevents ambiguous
		// binding-address targeting.
		assertThatThrownBy(() -> validationService.validate("http://0.0.0.0/"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL targets a disallowed private/internal network address");
	}

	@Test
	void validate_throwsWhenHostIsMulticast() {
		// Edge case: multicast addresses are never a legitimate redirect target.
		assertThatThrownBy(() -> validationService.validate("http://224.0.0.1/"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage("URL targets a disallowed private/internal network address");
	}
}
