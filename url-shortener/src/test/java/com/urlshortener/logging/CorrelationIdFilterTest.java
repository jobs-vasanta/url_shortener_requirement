package com.urlshortener.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Correlation IDs are the backbone of the whole logging/tracing story: every other test in
 * this suite (and every log line in production) assumes MDC is populated correctly by this
 * filter. The invalid-header tests exist because this value comes directly from an untrusted
 * client - without the allow-list check, a caller could forge log lines (CRLF injection) or
 * poison downstream systems that key off this header.
 */
class CorrelationIdFilterTest {

	private final CorrelationIdFilter filter = new CorrelationIdFilter();
	private final HttpServletRequest request = mock(HttpServletRequest.class);
	private final HttpServletResponse response = mock(HttpServletResponse.class);

	@AfterEach
	void clearMdc() {
		// Defensive cleanup: a failing assertion mid-test must never leak MDC state into other tests.
		MDC.clear();
	}

	@Test
	void doFilterInternal_reusesInboundCorrelationId_whenValid() throws Exception {
		when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("caller-supplied-id-123");
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(response).setHeader(CorrelationIdFilter.HEADER_NAME, "caller-supplied-id-123");
	}

	@Test
	void doFilterInternal_generatesNewId_whenHeaderMissing() throws Exception {
		when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(null);
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(response).setHeader(eq(CorrelationIdFilter.HEADER_NAME), org.mockito.ArgumentMatchers.argThat(
				value -> value != null && !value.isBlank()));
	}

	@Test
	void doFilterInternal_rejectsHeaderContainingControlCharacters_andGeneratesNewIdInstead() throws Exception {
		// The core security test: a header value carrying an embedded newline (attempting to
		// forge a second, fake log line/field) must never be propagated as-is.
		String maliciousHeader = "abc\r\nSet-Cookie: evil=1";
		when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(maliciousHeader);
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(response).setHeader(eq(CorrelationIdFilter.HEADER_NAME), org.mockito.ArgumentMatchers.argThat(
				value -> !value.equals(maliciousHeader)));
	}

	@Test
	void doFilterInternal_rejectsOverlongHeader_andGeneratesNewIdInstead() throws Exception {
		// Edge case / lightweight DoS guard: an absurdly long header must not be trusted as-is
		// (e.g. propagated into logs or downstream calls unbounded).
		String overlong = "a".repeat(101);
		when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(overlong);
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(response).setHeader(eq(CorrelationIdFilter.HEADER_NAME), org.mockito.ArgumentMatchers.argThat(
				value -> !value.equals(overlong)));
	}

	@Test
	void doFilterInternal_populatesMdcDuringChainExecution_andClearsItAfterward() throws Exception {
		// This is the entire point of the filter: the MDC key must be set while downstream
		// code (controllers, other filters, async listeners) runs, and must be cleared
		// afterward so it never leaks onto a pooled thread that later serves an unrelated request.
		when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("req-1");
		AtomicReference<String> observedDuringChain = new AtomicReference<>();
		FilterChain chain = (req, res) -> observedDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

		filter.doFilter(request, response, chain);

		assertThat(observedDuringChain.get()).isEqualTo("req-1");
		assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
	}

	@Test
	void doFilterInternal_clearsMdc_evenWhenChainThrows() {
		// Edge case: an exception further down the filter chain must not leave MDC populated -
		// otherwise a thread-pool-reused thread would keep logging under a stale correlation ID.
		when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("req-1");
		FilterChain chain = (req, res) -> {
			throw new IOException("downstream failure");
		};

		assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isInstanceOf(IOException.class);

		assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
	}
}
