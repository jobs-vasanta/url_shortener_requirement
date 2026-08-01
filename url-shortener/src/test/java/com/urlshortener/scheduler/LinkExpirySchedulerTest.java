package com.urlshortener.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urlshortener.repository.LinkRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * LinkExpiryScheduler only orchestrates a single repository call - these tests exist to lock in
 * that {@code sweepExpiredLinks} actually delegates to {@link LinkRepository#markExpiredLinks}
 * and returns its count, since that's the one thing that could silently regress (e.g. a future
 * refactor accidentally dropping the call, or forgetting to return the count for observability).
 */
@ExtendWith(MockitoExtension.class)
class LinkExpirySchedulerTest {

	@Mock
	private LinkRepository linkRepository;

	@Test
	void sweepExpiredLinks_delegatesToRepositoryAndReturnsUpdatedCount() {
		when(linkRepository.markExpiredLinks(any(Instant.class))).thenReturn(3);
		LinkExpiryScheduler scheduler = new LinkExpiryScheduler(linkRepository);

		int updated = scheduler.sweepExpiredLinks();

		assertThat(updated).isEqualTo(3);
		verify(linkRepository).markExpiredLinks(any(Instant.class));
	}

	@Test
	void sweepExpiredLinks_returnsZero_whenNothingIsExpired() {
		// Edge case: no matching rows must not be treated as an error - just a no-op sweep.
		when(linkRepository.markExpiredLinks(any(Instant.class))).thenReturn(0);
		LinkExpiryScheduler scheduler = new LinkExpiryScheduler(linkRepository);

		assertThat(scheduler.sweepExpiredLinks()).isZero();
	}

	@Test
	void run_delegatesToSweepExpiredLinks() {
		// The @Scheduled entry point must not contain any logic of its own that could diverge
		// from the directly-testable sweepExpiredLinks method.
		when(linkRepository.markExpiredLinks(any(Instant.class))).thenReturn(1);
		LinkExpiryScheduler scheduler = new LinkExpiryScheduler(linkRepository);

		scheduler.run();

		verify(linkRepository).markExpiredLinks(any(Instant.class));
	}
}
