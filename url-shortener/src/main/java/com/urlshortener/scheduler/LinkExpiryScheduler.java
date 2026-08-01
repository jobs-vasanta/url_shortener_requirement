package com.urlshortener.scheduler;

import com.urlshortener.repository.LinkRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically flips ACTIVE links past their {@code expiresAt} to EXPIRED via
 * {@link LinkRepository#markExpiredLinks}. This does NOT affect redirect correctness -
 * {@code Link.isRedirectable} already rejects an expired link in real time regardless of the
 * stored status column - it exists purely to keep {@code status} accurate for callers that
 * read it directly (GET /urls/{shortCode}, future reporting/admin features) within a bounded
 * delay, rather than only being corrected lazily on next read (see UrlServiceImpl#effectiveStatus).
 */
@Component
public class LinkExpiryScheduler {

	private static final Logger log = LoggerFactory.getLogger(LinkExpiryScheduler.class);

	private final LinkRepository linkRepository;

	public LinkExpiryScheduler(LinkRepository linkRepository) {
		this.linkRepository = linkRepository;
	}

	@Scheduled(fixedDelayString = "${app.link-expiry.sweep-interval-ms}")
	public void run() {
		sweepExpiredLinks();
	}

	/** Separated from the {@code @Scheduled} trigger so tests can invoke the sweep synchronously. */
	@Transactional
	public int sweepExpiredLinks() {
		int updated = linkRepository.markExpiredLinks(Instant.now());
		if (updated > 0) {
			log.info("Marked {} link(s) as EXPIRED", updated);
		}
		return updated;
	}
}
