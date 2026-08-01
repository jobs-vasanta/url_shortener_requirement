package com.urlshortener.exception;

/** Thrown when a client-supplied custom alias is already in use. */
public class AliasAlreadyExistsException extends RuntimeException {

	public AliasAlreadyExistsException(String alias) {
		super("Alias already in use: " + alias);
	}
}
