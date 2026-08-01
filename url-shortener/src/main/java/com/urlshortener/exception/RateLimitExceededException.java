package com.urlshortener.exception;

/** Thrown when a client exceeds the configured create-link rate limit. */
public class RateLimitExceededException extends RuntimeException {

	public RateLimitExceededException(String message) {
		super(message);
	}
}
