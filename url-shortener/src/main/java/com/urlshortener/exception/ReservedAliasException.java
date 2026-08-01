package com.urlshortener.exception;

/** Thrown when a client-supplied alias collides with a reserved top-level route segment. */
public class ReservedAliasException extends RuntimeException {

	public ReservedAliasException(String alias) {
		super("Alias '" + alias + "' is reserved and cannot be used");
	}
}
