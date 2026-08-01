package com.urlshortener.controller;

import com.urlshortener.dto.ErrorResponse;
import com.urlshortener.service.ClickContext;
import com.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Redirect", description = "Public short-link redirection")
public class RedirectController {

	private final UrlService urlService;

	public RedirectController(UrlService urlService) {
		this.urlService = urlService;
	}

	@Operation(summary = "Redirect to the original URL",
			description = "Resolves a short code and responds with a 302 redirect. Records the click asynchronously.")
	@ApiResponses({
			@ApiResponse(responseCode = "302", description = "Redirect to the original URL"),
			@ApiResponse(responseCode = "404", description = "No link for this short code",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "410", description = "Link is expired or deactivated",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping("/{shortCode}")
	public ResponseEntity<Void> redirect(
			@Parameter(description = "Short code identifying the link") @PathVariable String shortCode,
			HttpServletRequest request) {
		ClickContext clickContext = new ClickContext(
				request.getHeader(HttpHeaders.REFERER),
				request.getHeader(HttpHeaders.USER_AGENT),
				request.getRemoteAddr());

		String originalUrl = urlService.resolveForRedirect(shortCode, clickContext);

		return ResponseEntity.status(HttpStatus.FOUND)
				.header(HttpHeaders.LOCATION, originalUrl)
				.build();
	}
}
