package com.urlshortener.exception;

/** Thrown when a free-tier caller requests a ttlSeconds beyond the free-tier cap (30 days). */
public class TtlExceedsPlanLimitException extends RuntimeException {

	public TtlExceedsPlanLimitException(String message) {
		super(message);
	}
}
