package com.urlshortener.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hashing for click-event fields (IP address, user agent) so raw
 * PII is never persisted (see Architecture.md, Section 7 - PII minimization).
 */
public final class HashUtil {

	private HashUtil() {
	}

	public static String sha256Hex(String input) {
		if (input == null) {
			return null;
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e);
		}
	}
}
