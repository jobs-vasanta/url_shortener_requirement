package com.urlshortener.service.impl;

import com.urlshortener.cache.CacheKeys;
import com.urlshortener.cache.CacheService;
import com.urlshortener.domain.ApiKeyTier;
import com.urlshortener.domain.Link;
import com.urlshortener.domain.LinkStatus;
import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.dto.RequestLimits;
import com.urlshortener.dto.UpdateLinkRequest;
import com.urlshortener.event.ClickRecordedEvent;
import com.urlshortener.exception.LinkGoneException;
import com.urlshortener.exception.LinkNotFoundException;
import com.urlshortener.exception.TtlExceedsPlanLimitException;
import com.urlshortener.repository.LinkRepository;
import com.urlshortener.service.ClickContext;
import com.urlshortener.service.ShortCodeGeneratorService;
import com.urlshortener.service.UrlService;
import com.urlshortener.service.UrlValidationService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates create/redirect/deactivate use cases and owns the cache-aside
 * logic against Redis (read-through on redirect, write-through on create).
 * See Architecture.md, Sections 3-4 for the full request/redirect sequence.
 *
 * <p>Each public method is a short, single-purpose orchestration over collaborators
 * that each own one concern (validation, code generation, persistence, caching,
 * analytics), per single-responsibility/clean-code principles - this class contains
 * no validation rules, no code-generation logic, and no analytics logic of its own.
 */
@Service
public class UrlServiceImpl implements UrlService {

	private final LinkRepository linkRepository;
	private final ShortCodeGeneratorService shortCodeGeneratorService;
	private final UrlValidationService urlValidationService;
	private final CacheService cacheService;
	private final ApplicationEventPublisher eventPublisher;
	private final Duration linkCacheTtl;

	public UrlServiceImpl(LinkRepository linkRepository,
			ShortCodeGeneratorService shortCodeGeneratorService,
			UrlValidationService urlValidationService,
			CacheService cacheService,
			ApplicationEventPublisher eventPublisher,
			@Value("${app.cache.link-ttl-seconds}") long linkCacheTtlSeconds) {
		this.linkRepository = linkRepository;
		this.shortCodeGeneratorService = shortCodeGeneratorService;
		this.urlValidationService = urlValidationService;
		this.cacheService = cacheService;
		this.eventPublisher = eventPublisher;
		this.linkCacheTtl = Duration.ofSeconds(linkCacheTtlSeconds);
	}

	/**
	 * Create-link use case, read top-to-bottom as its own short story:
	 * validate -> assign a short code -> compute expiry -> persist -> cache -> respond.
	 * Each step delegates to a focused collaborator or private helper below.
	 */
	@Override
	@Transactional
	public LinkResponse createLink(CreateLinkRequest request, ApiKeyTier tier) {
		urlValidationService.validate(request.longUrl());

		String shortCode = resolveShortCode(request);
		Instant now = Instant.now();
		Instant expiresAt = computeExpiry(request.ttlSeconds(), tier, now);

		Link link = new Link(shortCode, request.longUrl(), now, expiresAt);
		linkRepository.save(link);
		cacheLink(link);

		return toResponse(link);
	}

	/**
	 * Resolves a short code for redirection. Loads the link (cache-aside), rejects
	 * expired/deactivated links with a 410-mapped exception, then publishes a
	 * {@link ClickRecordedEvent} - analytics recording happens asynchronously
	 * afterward (see ClickRecordedEventListener) so it never adds latency here.
	 */
	@Override
	@Transactional(readOnly = true)
	public String resolveForRedirect(String shortCode, ClickContext clickContext) {
		Link link = loadLink(shortCode);

		if (!link.isRedirectable(Instant.now())) {
			throw new LinkGoneException(shortCode);
		}

		eventPublisher.publishEvent(new ClickRecordedEvent(
				link.getId(), Instant.now(), clickContext.referrer(), clickContext.userAgent(), clickContext.remoteIp()));
		return link.getOriginalUrl();
	}

	/** Read-only lookup of a link's public metadata, going through the same cache-aside path as redirect. */
	@Override
	@Transactional(readOnly = true)
	public LinkResponse getLink(String shortCode) {
		return toResponse(loadLink(shortCode));
	}

	/**
	 * Applies a partial update directly against the DB copy (not the cache), then refreshes the
	 * cache with the new state so a subsequent redirect never serves stale pre-update data.
	 */
	@Override
	@Transactional
	public LinkResponse updateLink(String shortCode, UpdateLinkRequest request, ApiKeyTier tier) {
		Link link = linkRepository.findByShortCode(shortCode)
				.orElseThrow(() -> new LinkNotFoundException(shortCode));

		if (request.ttlSeconds() != null) {
			link.updateExpiresAt(computeExpiry(request.ttlSeconds(), tier, Instant.now()));
		}
		if (request.active() != null) {
			if (request.active()) {
				link.reactivate();
			} else {
				link.deactivate();
			}
		}

		linkRepository.save(link);
		cacheLink(link);
		return toResponse(link);
	}

	/**
	 * Deactivates a link. Loads directly from the DB (bypassing the read cache, since we're
	 * about to invalidate it anyway) to keep the mutation and its cache eviction unambiguous.
	 */
	@Override
	@Transactional
	public void deactivate(String shortCode) {
		Link link = linkRepository.findByShortCode(shortCode)
				.orElseThrow(() -> new LinkNotFoundException(shortCode));
		link.deactivate();
		linkRepository.save(link);
		cacheService.evict(CacheKeys.link(shortCode));
	}

	/**
	 * Decides how a link gets its short code: a caller-supplied alias (validated for
	 * availability - this is what surfaces {@link com.urlshortener.exception.AliasAlreadyExistsException}
	 * for duplicates) if one was given, otherwise a freshly generated code.
	 */
	private String resolveShortCode(CreateLinkRequest request) {
		if (request.alias() != null && !request.alias().isBlank()) {
			shortCodeGeneratorService.validateAliasAvailable(request.alias());
			return request.alias();
		}
		return shortCodeGeneratorService.generate();
	}

	/**
	 * Translates an optional relative TTL into an absolute expiry instant, tier-dependent: a
	 * premium caller may go without an expiry entirely (null = never), while a free-tier caller
	 * always gets one - defaulting to, and capped at, {@link RequestLimits#FREE_MAX_TTL_SECONDS}
	 * regardless of what the DTO's more permissive {@code @Max} validation let through (that
	 * annotation is sized for premium; the free-tier cap can only be enforced here, where the
	 * resolved tier is actually known).
	 */
	private Instant computeExpiry(Long ttlSeconds, ApiKeyTier tier, Instant now) {
		if (tier == ApiKeyTier.PREMIUM) {
			return ttlSeconds != null ? now.plusSeconds(ttlSeconds) : null;
		}
		long effectiveTtlSeconds = ttlSeconds != null ? ttlSeconds : RequestLimits.FREE_DEFAULT_TTL_SECONDS;
		if (effectiveTtlSeconds > RequestLimits.FREE_MAX_TTL_SECONDS) {
			throw new TtlExceedsPlanLimitException(
					"ttlSeconds exceeds the free-tier limit of " + RequestLimits.FREE_MAX_TTL_SECONDS
							+ " seconds (30 days); upgrade to Premium for a longer or non-expiring link");
		}
		return now.plusSeconds(effectiveTtlSeconds);
	}

	/** Cache-aside read: serve from Redis when present, otherwise load from Postgres and populate the cache. */
	private Link loadLink(String shortCode) {
		Optional<Link> cached = cacheService.get(CacheKeys.link(shortCode), Link.class);
		if (cached.isPresent()) {
			return cached.get();
		}
		Link link = linkRepository.findByShortCode(shortCode)
				.orElseThrow(() -> new LinkNotFoundException(shortCode));
		cacheLink(link);
		return link;
	}

	/** Write-through cache population, keyed by short code, with a TTL longer than any realistic redirect burst. */
	private void cacheLink(Link link) {
		cacheService.put(CacheKeys.link(link.getShortCode()), link, linkCacheTtl);
	}

	/**
	 * Maps the persistence-layer entity to the API-facing response shape. Recomputes an
	 * effective status rather than trusting the stored column verbatim: the scheduled sweep
	 * (see LinkExpiryScheduler) that flips ACTIVE -> EXPIRED in the database runs periodically,
	 * not on every request, so without this a link could still read back as "ACTIVE" here for a
	 * short window after its expiresAt has actually passed.
	 */
	private LinkResponse toResponse(Link link) {
		return new LinkResponse(
				link.getShortCode(),
				"/" + link.getShortCode(),
				link.getOriginalUrl(),
				effectiveStatus(link).name(),
				link.getCreatedAt(),
				link.getExpiresAt());
	}

	/** The status this link should be reported as *right now*, independent of the sweep job's cadence. */
	private LinkStatus effectiveStatus(Link link) {
		if (link.getStatus() == LinkStatus.ACTIVE && link.getExpiresAt() != null && !Instant.now().isBefore(link.getExpiresAt())) {
			return LinkStatus.EXPIRED;
		}
		return link.getStatus();
	}
}
