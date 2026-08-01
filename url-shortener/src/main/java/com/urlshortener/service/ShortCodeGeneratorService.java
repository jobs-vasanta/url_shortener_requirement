package com.urlshortener.service;

public interface ShortCodeGeneratorService {

	/** Generates a new, collision-free short code (Base62-encoded sequence value). */
	String generate();

	/** Validates a client-supplied alias's format and uniqueness; throws if already taken. */
	void validateAliasAvailable(String alias);
}
