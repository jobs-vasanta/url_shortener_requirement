package com.urlshortener.service;

public interface UrlValidationService {

	/** Validates scheme allow-list and blocks private/loopback/link-local targets (SSRF/open-redirect mitigation). */
	void validate(String url);
}
