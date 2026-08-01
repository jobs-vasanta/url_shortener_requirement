package com.urlshortener.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a correlation ID to every request - taken from the inbound {@value #HEADER_NAME}
 * header if a caller/gateway already supplied one, otherwise freshly generated - so every log
 * line for a request (including async click processing, see MdcTaskDecorator) can be tied
 * together and echoed back to the caller for support/troubleshooting.
 *
 * <p>Runs at the very front of the filter chain, before any other logging can happen. The
 * inbound header value is validated against a strict allow-list pattern (rather than trusted
 * as-is) to prevent log forging/injection via crafted header values.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-Correlation-Id";
	public static final String MDC_KEY = "correlationId";

	private static final Pattern SAFE_ID = Pattern.compile("^[a-zA-Z0-9-]{1,100}$");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String inbound = request.getHeader(HEADER_NAME);
		String correlationId = (inbound != null && SAFE_ID.matcher(inbound).matches())
				? inbound
				: UUID.randomUUID().toString();

		MDC.put(MDC_KEY, correlationId);
		response.setHeader(HEADER_NAME, correlationId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}
}
