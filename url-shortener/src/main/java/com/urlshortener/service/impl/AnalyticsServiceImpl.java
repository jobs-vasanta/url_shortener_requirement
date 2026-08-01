package com.urlshortener.service.impl;

import com.urlshortener.cache.CacheKeys;
import com.urlshortener.cache.CacheService;
import com.urlshortener.domain.ClickEvent;
import com.urlshortener.domain.Link;
import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.exception.LinkNotFoundException;
import com.urlshortener.repository.ClickAccessWindow;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.LinkRepository;
import com.urlshortener.service.AnalyticsService;
import com.urlshortener.util.HashUtil;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Raw click events are the source of truth; aggregates are computed on read
 * with a short-TTL cache to absorb bursty polling (see Architecture.md, Section 5).
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

	private final LinkRepository linkRepository;
	private final ClickEventRepository clickEventRepository;
	private final CacheService cacheService;
	private final Duration analyticsCacheTtl;

	public AnalyticsServiceImpl(LinkRepository linkRepository, ClickEventRepository clickEventRepository,
			CacheService cacheService,
			@Value("${app.cache.analytics-ttl-seconds}") long analyticsCacheTtlSeconds) {
		this.linkRepository = linkRepository;
		this.clickEventRepository = clickEventRepository;
		this.cacheService = cacheService;
		this.analyticsCacheTtl = Duration.ofSeconds(analyticsCacheTtlSeconds);
	}

	@Override
	public void recordClick(Long linkId, String referrer, String userAgent, String remoteIp) {
		Instant now = Instant.now();
		ClickEvent event = new ClickEvent(
				linkId,
				now,
				referrer,
				HashUtil.sha256Hex(userAgent),
				HashUtil.sha256Hex(remoteIp));
		clickEventRepository.save(event);
		linkRepository.incrementClickCount(linkId, now);
		cacheService.evict(CacheKeys.analytics(linkId));
	}

	@Override
	public AnalyticsResponse getAnalytics(String shortCode) {
		Link link = linkRepository.findByShortCode(shortCode)
				.orElseThrow(() -> new LinkNotFoundException(shortCode));

		String cacheKey = CacheKeys.analytics(link.getId());
		Optional<AnalyticsResponse> cached = cacheService.get(cacheKey, AnalyticsResponse.class);
		if (cached.isPresent()) {
			return cached.get();
		}

		// Denormalized counter (fast PK read) rather than COUNT(*) over click_events on every request.
		long totalClicks = link.getClickCount();
		ClickAccessWindow accessWindow = clickEventRepository.findAccessWindow(link.getId());

		AnalyticsResponse response = new AnalyticsResponse(
				shortCode, totalClicks, accessWindow.firstAccessedAt(), accessWindow.lastAccessedAt());
		cacheService.put(cacheKey, response, analyticsCacheTtl);
		return response;
	}
}
