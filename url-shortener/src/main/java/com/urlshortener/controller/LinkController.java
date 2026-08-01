package com.urlshortener.controller;

import com.urlshortener.domain.ApiKeyTier;
import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.ErrorResponse;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.dto.UpdateLinkRequest;
import com.urlshortener.service.ApiKeyService;
import com.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Management API for creating, inspecting, updating, and deactivating short links.
 * Mounted at {@code /urls}, distinct from the public single-segment redirect
 * endpoint in {@link RedirectController} - see the REST design notes in chat history
 * for why the two are kept separate.
 */
@RestController
@RequestMapping("/urls")
@Tag(name = "Links", description = "Create, inspect, update, and deactivate short links")
public class LinkController {

	private static final String API_KEY_HEADER = "X-Api-Key";

	private final UrlService urlService;
	private final ApiKeyService apiKeyService;

	public LinkController(UrlService urlService, ApiKeyService apiKeyService) {
		this.urlService = urlService;
		this.apiKeyService = apiKeyService;
	}

	@Operation(summary = "Create a short link",
			description = "Validates the target URL, assigns a short code (or uses the supplied alias), and persists it. "
					+ "`ttlSeconds` is optional. Free-tier callers (no `X-Api-Key`, or an unrecognized/inactive one) get a "
					+ "mandatory 30-day cap and default; premium callers may omit it for a link that never expires, or set "
					+ "an explicit value up to a much larger ceiling.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Link created",
					content = @Content(schema = @Schema(implementation = LinkResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid URL/request body, or ttlSeconds exceeds the free-tier limit",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "Alias already in use",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "429", description = "Rate limit exceeded",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<LinkResponse> createLink(
			@Valid @RequestBody CreateLinkRequest request,
			@Parameter(description = "Optional API key; resolves the caller's plan tier (defaults to free if omitted/unrecognized)")
			@RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
		ApiKeyTier tier = apiKeyService.resolveTier(apiKey);
		LinkResponse response = urlService.createLink(request, tier);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "Get link metadata", description = "Returns link metadata (not analytics - see GET /analytics/{shortCode}).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Link found",
					content = @Content(schema = @Schema(implementation = LinkResponse.class))),
			@ApiResponse(responseCode = "404", description = "No link for this short code",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping(value = "/{shortCode}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<LinkResponse> getLink(
			@Parameter(description = "Short code identifying the link") @PathVariable String shortCode) {
		return ResponseEntity.ok(urlService.getLink(shortCode));
	}

	@Operation(summary = "Partially update a link",
			description = "Reactivates/deactivates via `active`, and/or replaces the expiry via `ttlSeconds` (subject to the "
					+ "same tier-dependent cap as create - see `X-Api-Key`). Omitted fields are left unchanged.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Link updated",
					content = @Content(schema = @Schema(implementation = LinkResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid request body, or ttlSeconds exceeds the free-tier limit",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "No link for this short code",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PatchMapping(value = "/{shortCode}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<LinkResponse> updateLink(
			@Parameter(description = "Short code identifying the link") @PathVariable String shortCode,
			@Valid @RequestBody UpdateLinkRequest request,
			@Parameter(description = "Optional API key; resolves the caller's plan tier (defaults to free if omitted/unrecognized)")
			@RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
		ApiKeyTier tier = apiKeyService.resolveTier(apiKey);
		return ResponseEntity.ok(urlService.updateLink(shortCode, request, tier));
	}

	@Operation(summary = "Deactivate a link", description = "Idempotent: marks the link DEACTIVATED so future redirects return 410 Gone.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Link deactivated"),
			@ApiResponse(responseCode = "404", description = "No link for this short code",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@DeleteMapping("/{shortCode}")
	public ResponseEntity<Void> deactivateLink(
			@Parameter(description = "Short code identifying the link") @PathVariable String shortCode) {
		urlService.deactivate(shortCode);
		return ResponseEntity.noContent().build();
	}
}
