package com.urlshortener.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * Link.isRedirectable() is the single source of truth for "can this link still be used" -
 * every test here exists because a bug in this one method would silently either redirect
 * expired/deactivated links (a correctness/security issue) or block valid ones (an availability issue).
 */
class LinkTest {

	@Test
	void isRedirectable_true_whenActiveAndNoExpiry() {
		// Happy path: the common case of a link created without a TTL.
		Link link = new Link("abc", "https://example.com", Instant.now(), null);

		assertThat(link.isRedirectable(Instant.now())).isTrue();
	}

	@Test
	void isRedirectable_true_whenActiveAndExpiryInFuture() {
		Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
		Link link = new Link("abc", "https://example.com", Instant.now(), future);

		assertThat(link.isRedirectable(Instant.now())).isTrue();
	}

	@Test
	void isRedirectable_false_whenExpiryInPast() {
		Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
		Link link = new Link("abc", "https://example.com", Instant.now(), past);

		assertThat(link.isRedirectable(Instant.now())).isFalse();
	}

	@Test
	void isRedirectable_false_atExactExpiryInstant() {
		// Boundary case: the check is `now.isBefore(expiresAt)`, so the exact expiry instant
		// itself must already be considered expired (exclusive boundary), not one tick later.
		Instant expiry = Instant.now();
		Link link = new Link("abc", "https://example.com", expiry.minusSeconds(10), expiry);

		assertThat(link.isRedirectable(expiry)).isFalse();
	}

	@Test
	void isRedirectable_false_whenDeactivated_evenWithoutExpiry() {
		// Deactivation must short-circuit the check regardless of expiry - a link with no
		// expiry is not "redirectable forever" once deactivated.
		Link link = new Link("abc", "https://example.com", Instant.now(), null);

		link.deactivate();

		assertThat(link.isRedirectable(Instant.now())).isFalse();
	}

	@Test
	void deactivate_setsStatusToDeactivatedAndBumpsUpdatedAt() {
		Instant createdAt = Instant.now().minusSeconds(60);
		Link link = new Link("abc", "https://example.com", createdAt, null);

		link.deactivate();

		assertThat(link.getStatus()).isEqualTo(LinkStatus.DEACTIVATED);
		assertThat(link.getUpdatedAt()).isAfter(createdAt);
	}

	@Test
	void reactivate_restoresActiveStatus() {
		// Edge case: reactivating a link that was previously deactivated (PATCH active=true flow).
		Link link = new Link("abc", "https://example.com", Instant.now(), null);
		link.deactivate();

		link.reactivate();

		assertThat(link.getStatus()).isEqualTo(LinkStatus.ACTIVE);
		assertThat(link.isRedirectable(Instant.now())).isTrue();
	}

	@Test
	void markExpired_setsStatusToExpired() {
		// Used by the scheduled expiry sweep (LinkRepository.markExpiredLinks) - not exercised
		// through that repository query here, but the state transition itself is unit-tested.
		Link link = new Link("abc", "https://example.com", Instant.now(), Instant.now());

		link.markExpired();

		assertThat(link.getStatus()).isEqualTo(LinkStatus.EXPIRED);
	}

	@Test
	void updateExpiresAt_replacesExpiryInstant() {
		Link link = new Link("abc", "https://example.com", Instant.now(), null);
		Instant newExpiry = Instant.now().plusSeconds(3600);

		link.updateExpiresAt(newExpiry);

		assertThat(link.getExpiresAt()).isEqualTo(newExpiry);
	}

	@Test
	void updateExpiresAt_withNull_clearsExpiry() {
		// Edge case explicitly called out in the javadoc: passing null means "never expires".
		Instant original = Instant.now().plusSeconds(60);
		Link link = new Link("abc", "https://example.com", Instant.now(), original);

		link.updateExpiresAt(null);

		assertThat(link.getExpiresAt()).isNull();
		assertThat(link.isRedirectable(Instant.now())).isTrue();
	}
}
