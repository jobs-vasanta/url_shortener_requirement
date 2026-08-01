package com.urlshortener.controller;

import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.dto.ErrorResponse;
import com.urlshortener.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only click analytics for a short link, kept as its own top-level resource
 * (rather than nested under {@code /urls/{shortCode}}) since it's a distinct,
 * cacheable read model rather than link metadata.
 */
@RestController
@Tag(name = "Analytics", description = "Click analytics for short links")
public class AnalyticsController {

	private final AnalyticsService analyticsService;

	public AnalyticsController(AnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	@Operation(summary = "Get click analytics", description = "Returns total click count and first/last access times for a short code.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Analytics found",
					content = @Content(schema = @Schema(implementation = AnalyticsResponse.class))),
			@ApiResponse(responseCode = "404", description = "No link for this short code",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping(value = "/analytics/{shortCode}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<AnalyticsResponse> getAnalytics(
			@Parameter(description = "Short code identifying the link") @PathVariable String shortCode) {
		return ResponseEntity.ok(analyticsService.getAnalytics(shortCode));
	}
}
