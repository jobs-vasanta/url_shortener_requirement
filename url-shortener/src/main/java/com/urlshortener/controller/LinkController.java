package com.urlshortener.controller;

import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.service.LinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Management API for creating, inspecting, and deactivating short links. */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

	private final LinkService linkService;

	public LinkController(LinkService linkService) {
		this.linkService = linkService;
	}

	@PostMapping
	public ResponseEntity<LinkResponse> createLink(@Valid @RequestBody CreateLinkRequest request) {
		LinkResponse response = linkService.createLink(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/{code}")
	public ResponseEntity<LinkResponse> getLink(@PathVariable String code) {
		return ResponseEntity.ok(linkService.getLink(code));
	}

	@DeleteMapping("/{code}")
	public ResponseEntity<Void> deactivateLink(@PathVariable String code) {
		linkService.deactivate(code);
		return ResponseEntity.noContent().build();
	}
}
