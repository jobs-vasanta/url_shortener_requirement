package com.urlshortener.service;

import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.LinkResponse;

public interface UrlService {

	LinkResponse createLink(CreateLinkRequest request);

	/** Resolves a short code to its original URL for redirection; enforces active/not-expired status. */
	String resolveForRedirect(String shortCode, String referrer, String userAgent, String remoteIp);

	LinkResponse getLink(String shortCode);

	void deactivate(String shortCode);
}
