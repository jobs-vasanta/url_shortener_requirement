package com.urlshortener.service.impl;

import com.urlshortener.domain.Link;
import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.event.ClickRecordedEvent;
import com.urlshortener.exception.LinkGoneException;
import com.urlshortener.exception.LinkNotFoundException;
import com.urlshortener.repository.LinkRepository;
import com.urlshortener.service.LinkService;
import com.urlshortener.service.ShortCodeGeneratorService;
import com.urlshortener.service.UrlValidationService;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates create/redirect/deactivate use cases and owns the cache-aside
 * logic against Redis (read-through on redirect, write-through on create).
 * See Architecture.md, Sections 3-4 for the full request/redirect sequence.
 */
@Service
public class LinkServiceImpl implements LinkService {

	private static final String LINK_CACHE_PREFIX = "link:";
	private static final Duration LINK_CACHE_TTL = Duration.ofHours(6);

	private final LinkRepository linkRepository;
	private final ShortCodeGeneratorService shortCodeGeneratorService;
	private final UrlValidationService urlValidationService;
	private final RedisTemplate<String, Object> redisTemplate;
	private final ApplicationEventPublisher eventPublisher;

	public LinkServiceImpl(LinkRepository linkRepository,
			ShortCodeGeneratorService shortCodeGeneratorService,
			UrlValidationService urlValidationService,
			RedisTemplate<String, Object> redisTemplate,
			ApplicationEventPublisher eventPublisher) {
		this.linkRepository = linkRepository;
		this.shortCodeGeneratorService = shortCodeGeneratorService;
		this.urlValidationService = urlValidationService;
		this.redisTemplate = redisTemplate;
		this.eventPublisher = eventPublisher;
	}

	@Override
	@Transactional
	public LinkResponse createLink(CreateLinkRequest request) {
		urlValidationService.validate(request.longUrl());

		String shortCode;
		if (request.alias() != null && !request.alias().isBlank()) {
			shortCodeGeneratorService.validateAliasAvailable(request.alias());
			shortCode = request.alias();
		} else {
			shortCode = shortCodeGeneratorService.generate();
		}

		Instant now = Instant.now();
		Instant expiresAt = request.ttlSeconds() != null ? now.plusSeconds(request.ttlSeconds()) : null;

		Link link = new Link(shortCode, request.longUrl(), now, expiresAt);
		linkRepository.save(link);

		cacheLink(link);

		return toResponse(link);
	}

	@Override
	@Transactional(readOnly = true)
	public String resolveForRedirect(String shortCode, String referrer, String userAgent, String remoteIp) {
		Link link = loadLink(shortCode);

		if (!link.isRedirectable(Instant.now())) {
			throw new LinkGoneException(shortCode);
		}

		eventPublisher.publishEvent(new ClickRecordedEvent(link.getId(), Instant.now(), referrer, userAgent, remoteIp));
		return link.getOriginalUrl();
	}

	@Override
	@Transactional(readOnly = true)
	public LinkResponse getLink(String shortCode) {
		return toResponse(loadLink(shortCode));
	}

	@Override
	@Transactional
	public void deactivate(String shortCode) {
		Link link = linkRepository.findByShortCode(shortCode)
				.orElseThrow(() -> new LinkNotFoundException(shortCode));
		link.deactivate();
		linkRepository.save(link);
		redisTemplate.delete(LINK_CACHE_PREFIX + shortCode);
	}

	private Link loadLink(String shortCode) {
		Object cached = redisTemplate.opsForValue().get(LINK_CACHE_PREFIX + shortCode);
		if (cached instanceof Link cachedLink) {
			return cachedLink;
		}
		Link link = linkRepository.findByShortCode(shortCode)
				.orElseThrow(() -> new LinkNotFoundException(shortCode));
		cacheLink(link);
		return link;
	}

	private void cacheLink(Link link) {
		redisTemplate.opsForValue().set(
				LINK_CACHE_PREFIX + link.getShortCode(), link, LINK_CACHE_TTL.toSeconds(), TimeUnit.SECONDS);
	}

	private LinkResponse toResponse(Link link) {
		return new LinkResponse(
				link.getShortCode(),
				"/" + link.getShortCode(),
				link.getOriginalUrl(),
				link.getStatus().name(),
				link.getCreatedAt(),
				link.getExpiresAt());
	}
}
