package com.urlshortener.controller;

import com.urlshortener.service.ClickContext;
import com.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated redirect endpoint - the highest-traffic, most
 * latency-sensitive path in the system (see Architecture.md, Section 4).
 */
@RestController
public class RedirectController {

	private final UrlService urlService;

	public RedirectController(UrlService urlService) {
		this.urlService = urlService;
	}

	@GetMapping("/{code}")
	public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
		ClickContext clickContext = new ClickContext(
				request.getHeader(HttpHeaders.REFERER),
				request.getHeader(HttpHeaders.USER_AGENT),
				request.getRemoteAddr());

		String originalUrl = urlService.resolveForRedirect(code, clickContext);

		return ResponseEntity.status(HttpStatus.FOUND)
				.header(HttpHeaders.LOCATION, originalUrl)
				.build();
	}
}
