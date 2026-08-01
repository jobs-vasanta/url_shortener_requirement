package com.urlshortener.repository;

import java.time.Instant;

/** Combined first/last access timestamps for one link, fetched in a single round trip. */
public record ClickAccessWindow(Instant firstAccessedAt, Instant lastAccessedAt) {
}
