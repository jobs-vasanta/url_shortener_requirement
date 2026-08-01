package com.urlshortener.exception;

/** Thrown when a short code exists but is expired or deactivated. */
public class LinkGoneException extends RuntimeException {

	public LinkGoneException(String shortCode) {
		super("Link is no longer active: " + shortCode);
	}
}
