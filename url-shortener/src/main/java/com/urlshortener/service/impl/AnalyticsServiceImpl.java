package com.urlshortener.service.impl;

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
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Raw click events are the source of truth; aggregates are computed on read
 * with a short-TTL cache to absorb bursty polling (see Architecture.md, Section 5).
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

	private static final String ANALYTICS_CACHE_PREFIX = "analytics:";
	private static final Duration ANALYTICS_CACHE_TTL = Duration.ofSeconds(30);

	private final LinkRepository linkRepository;
	private final ClickEventRepository clickEventRepository;
	private final RedisTemplate<String, Object> redisTemplate;

	public AnalyticsServiceImpl(LinkRepository linkRepository, ClickEventRepository clickEventRepository,
			RedisTemplate<String, Object> redisTemplate) {
		this.linkRepository = linkRepository;
		this.clickEventRepository = clickEventRepository;
		this.redisTemplate = redisTemplate;
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
		redisTemplate.delete(ANALYTICS_CACHE_PREFIX + linkId);
	}

	@Override
	public AnalyticsResponse getAnalytics(String shortCode) {
		Link link = linkRepository.findByShortCode(shortCode)
				.orElseThrow(() -> new LinkNotFoundException(shortCode));

		String cacheKey = ANALYTICS_CACHE_PREFIX + link.getId();
		AnalyticsResponse cached = (AnalyticsResponse) redisTemplate.opsForValue().get(cacheKey);
		if (cached != null) {
			return cached;
		}

		// Denormalized counter (fast PK read) rather than COUNT(*) over click_events on every request.
		long totalClicks = link.getClickCount();
		ClickAccessWindow accessWindow = clickEventRepository.findAccessWindow(link.getId());

		AnalyticsResponse response = new AnalyticsResponse(
				shortCode, totalClicks, accessWindow.firstAccessedAt(), accessWindow.lastAccessedAt());
		redisTemplate.opsForValue().set(cacheKey, response, ANALYTICS_CACHE_TTL.toSeconds(), TimeUnit.SECONDS);
		return response;
	}
}
