package com.urlshortener.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs one access-log line per completed request with method/path/status/duration as
 * structured fields. Deliberately excludes query strings, headers, and bodies - any of
 * which may carry tokens or other sensitive data (see the security-review notes on
 * sensitive logging).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger("com.urlshortener.access");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		long startNanos = System.nanoTime();
		try {
			filterChain.doFilter(request, response);
		} finally {
			long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
			String method = request.getMethod();
			String path = request.getRequestURI();
			int status = response.getStatus();

			log.info("{} {} -> {} ({} ms)", method, path, status, durationMs,
					StructuredArguments.kv("httpMethod", method),
					StructuredArguments.kv("path", path),
					StructuredArguments.kv("status", status),
					StructuredArguments.kv("durationMs", durationMs));
		}
	}
}
