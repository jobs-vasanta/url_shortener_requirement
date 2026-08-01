package com.urlshortener.controller;

import com.urlshortener.service.LinkService;
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

	private final LinkService linkService;

	public RedirectController(LinkService linkService) {
		this.linkService = linkService;
	}

	@GetMapping("/{code}")
	public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
		String originalUrl = linkService.resolveForRedirect(
				code,
				request.getHeader(HttpHeaders.REFERER),
				request.getHeader(HttpHeaders.USER_AGENT),
				request.getRemoteAddr());

		return ResponseEntity.status(HttpStatus.FOUND)
				.header(HttpHeaders.LOCATION, originalUrl)
				.build();
	}
}
