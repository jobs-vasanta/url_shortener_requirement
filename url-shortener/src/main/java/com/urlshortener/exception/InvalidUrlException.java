package com.urlshortener.exception;

/** Thrown when a submitted URL fails scheme allow-list or SSRF/private-IP validation. */
public class InvalidUrlException extends RuntimeException {

	public InvalidUrlException(String message) {
		super(message);
	}
}
