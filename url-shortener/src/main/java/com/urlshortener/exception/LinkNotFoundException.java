package com.urlshortener.exception;

/** Thrown when a requested short code does not exist. */
public class LinkNotFoundException extends RuntimeException {

	public LinkNotFoundException(String shortCode) {
		super("No link found for short code: " + shortCode);
	}
}
