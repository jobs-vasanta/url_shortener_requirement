package com.urlshortener.service.impl;

import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.service.UrlValidationService;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Enforces the http/https scheme allow-list and blocks private/loopback/link-local
 * targets before a URL is ever persisted (SSRF / open-redirect mitigation; see
 * Architecture.md, Section 7 and RequirementAnalysis.md risk R2).
 */
@Service
public class UrlValidationServiceImpl implements UrlValidationService {

	private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
	private static final int MAX_LENGTH = 2048;

	@Override
	public void validate(String url) {
		if (url == null || url.isBlank()) {
			throw new InvalidUrlException("URL must not be blank");
		}
		if (url.length() > MAX_LENGTH) {
			throw new InvalidUrlException("URL exceeds maximum length of " + MAX_LENGTH);
		}

		URI uri;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			throw new InvalidUrlException("URL is malformed: " + url);
		}

		String scheme = uri.getScheme();
		if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
			throw new InvalidUrlException("URL scheme must be http or https");
		}

		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new InvalidUrlException("URL must include a valid host");
		}

		if (isDisallowedHost(host)) {
			throw new InvalidUrlException("URL targets a disallowed private/internal network address");
		}
	}

	private boolean isDisallowedHost(String host) {
		try {
			InetAddress address = InetAddress.getByName(host);
			return address.isLoopbackAddress()
					|| address.isLinkLocalAddress()
					|| address.isSiteLocalAddress()
					|| address.isAnyLocalAddress()
					|| address.isMulticastAddress();
		} catch (UnknownHostException e) {
			// Cannot resolve at validation time (e.g., offline test env) - fail closed on unresolved hosts.
			throw new InvalidUrlException("URL host could not be resolved: " + host);
		}
	}
}
