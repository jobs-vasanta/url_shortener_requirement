package com.urlshortener.util;

/**
 * Encodes non-negative longs (e.g. a DB sequence value) into a Base62 string.
 * Monotonic DB-sequence + Base62 avoids cross-instance coordination for
 * short-code generation (see Architecture.md, Section 6 - Scalability).
 */
public final class Base62Encoder {

	private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
	private static final int BASE = ALPHABET.length();

	private Base62Encoder() {
	}

	public static String encode(long value) {
		if (value < 0) {
			throw new IllegalArgumentException("value must be non-negative: " + value);
		}
		if (value == 0) {
			return String.valueOf(ALPHABET.charAt(0));
		}
		StringBuilder sb = new StringBuilder();
		long remaining = value;
		while (remaining > 0) {
			int digit = (int) (remaining % BASE);
			sb.append(ALPHABET.charAt(digit));
			remaining /= BASE;
		}
		return sb.reverse().toString();
	}
}
