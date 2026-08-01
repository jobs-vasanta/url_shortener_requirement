package com.urlshortener.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urlshortener.cache.CacheKeys;
import com.urlshortener.cache.CacheService;
import com.urlshortener.domain.Link;
import com.urlshortener.domain.LinkStatus;
import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.dto.RequestLimits;
import com.urlshortener.dto.UpdateLinkRequest;
import com.urlshortener.event.ClickRecordedEvent;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.LinkGoneException;
import com.urlshortener.exception.LinkNotFoundException;
import com.urlshortener.repository.LinkRepository;
import com.urlshortener.service.ClickContext;
import com.urlshortener.service.ShortCodeGeneratorService;
import com.urlshortener.service.UrlValidationService;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Core orchestration logic of the whole app - every use case (create/redirect/get/update/
 * deactivate) is exercised here against mocked collaborators, since the real behavior
 * (validation rules, code generation, caching, persistence) is each independently unit-tested
 * in its own class. These tests exist to lock in the *sequencing and decision-making* that
 * UrlServiceImpl itself owns (e.g. "cache miss falls back to DB then repopulates the cache",
 * "an expired link is LinkGone, not LinkNotFound").
 */
@ExtendWith(MockitoExtension.class)
class UrlServiceImplTest {

	private static final long LINK_CACHE_TTL_SECONDS = 3600L;

	@Mock
	private LinkRepository linkRepository;
	@Mock
	private ShortCodeGeneratorService shortCodeGeneratorService;
	@Mock
	private UrlValidationService urlValidationService;
	@Mock
	private CacheService cacheService;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private UrlServiceImpl urlService;

	@BeforeEach
	void setUp() {
		urlService = new UrlServiceImpl(linkRepository, shortCodeGeneratorService, urlValidationService,
				cacheService, eventPublisher, LINK_CACHE_TTL_SECONDS);
	}

	// --- createLink ---------------------------------------------------------------------------

	@Test
	void createLink_withoutAlias_generatesShortCodeAndPersistsAndCaches() {
		// Happy path for the default (no custom alias) create flow: generator is consulted,
		// the link is saved, and the cache is populated write-through so an immediate redirect
		// doesn't miss the cache.
		when(shortCodeGeneratorService.generate()).thenReturn("gen123");
		CreateLinkRequest request = new CreateLinkRequest("https://example.com/page", null, null);

		LinkResponse response = urlService.createLink(request);

		assertThat(response.shortCode()).isEqualTo("gen123");
		assertThat(response.originalUrl()).isEqualTo("https://example.com/page");
		assertThat(response.expiresAt()).isNotNull();
		verify(urlValidationService).validate("https://example.com/page");
		verify(linkRepository).save(any(Link.class));
		verify(cacheService).put(eq(CacheKeys.link("gen123")), any(Link.class), any());
		verify(shortCodeGeneratorService, never()).validateAliasAvailable(any());
	}

	@Test
	void createLink_withAlias_usesAliasAndSkipsCodeGeneration() {
		// When a custom alias is supplied, it must be validated for availability and used
		// as-is - the generator must never be invoked in this path.
		CreateLinkRequest request = new CreateLinkRequest("https://example.com/page", "my-alias", null);

		LinkResponse response = urlService.createLink(request);

		assertThat(response.shortCode()).isEqualTo("my-alias");
		verify(shortCodeGeneratorService).validateAliasAvailable("my-alias");
		verify(shortCodeGeneratorService, never()).generate();
	}

	@Test
	void createLink_withTtlSeconds_computesAbsoluteExpiryFromNow() {
		// The DTO carries a *relative* TTL; UrlServiceImpl is responsible for converting it to
		// an absolute instant - this test pins that conversion down.
		when(shortCodeGeneratorService.generate()).thenReturn("gen123");
		CreateLinkRequest request = new CreateLinkRequest("https://example.com/page", null, 60L);

		Instant before = Instant.now();
		LinkResponse response = urlService.createLink(request);
		Instant after = Instant.now();

		assertThat(response.expiresAt()).isNotNull();
		assertThat(response.expiresAt()).isBetween(before.plusSeconds(60), after.plusSeconds(60));
	}

	@Test
	void createLink_withoutTtlSeconds_defaultsToThirtyDayExpiry() {
		// Edge case: every link must expire within 30 days now - omitting ttlSeconds must default
		// to that 30-day ceiling, not "never expires".
		when(shortCodeGeneratorService.generate()).thenReturn("gen123");
		CreateLinkRequest request = new CreateLinkRequest("https://example.com/page", null, null);

		Instant before = Instant.now();
		LinkResponse response = urlService.createLink(request);
		Instant after = Instant.now();

		assertThat(response.expiresAt()).isNotNull();
		assertThat(response.expiresAt()).isBetween(
				before.plusSeconds(RequestLimits.DEFAULT_TTL_SECONDS),
				after.plusSeconds(RequestLimits.DEFAULT_TTL_SECONDS));
	}

	@Test
	void createLink_propagatesInvalidUrlException_andNeverPersists() {
		// Negative path: validation must run before any persistence side effect - a failed
		// validation must leave no trace in the repository or cache.
		CreateLinkRequest request = new CreateLinkRequest("ftp://example.com", null, null);
		org.mockito.Mockito.doThrow(new InvalidUrlException("URL scheme must be http or https"))
				.when(urlValidationService).validate("ftp://example.com");

		assertThatThrownBy(() -> urlService.createLink(request)).isInstanceOf(InvalidUrlException.class);

		verify(linkRepository, never()).save(any());
		verify(cacheService, never()).put(any(), any(), any());
	}

	// --- resolveForRedirect ---------------------------------------------------------------------

	@Test
	void resolveForRedirect_cacheHit_returnsUrlAndPublishesClickEvent() {
		// Happy path + the most latency-sensitive path in the app: a cache hit must skip the
		// repository entirely and still publish the click event for async analytics.
		Link link = activeLink("abc", "https://example.com", null);
		when(cacheService.get(CacheKeys.link("abc"), Link.class)).thenReturn(Optional.of(link));
		ClickContext clickContext = new ClickContext("https://ref.example", "test-agent", "1.2.3.4");

		String url = urlService.resolveForRedirect("abc", clickContext);

		assertThat(url).isEqualTo("https://example.com");
		verify(linkRepository, never()).findByShortCode(any());
		ArgumentCaptor<ClickRecordedEvent> captor = ArgumentCaptor.forClass(ClickRecordedEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		assertThat(captor.getValue().getReferrer()).isEqualTo("https://ref.example");
		assertThat(captor.getValue().getUserAgent()).isEqualTo("test-agent");
		assertThat(captor.getValue().getRemoteIp()).isEqualTo("1.2.3.4");
	}

	@Test
	void resolveForRedirect_cacheMiss_loadsFromRepositoryAndRepopulatesCache() {
		// Cache-aside behavior: on a miss, fall back to the DB, then write the result back
		// into the cache so the *next* redirect for this code is a cache hit.
		Link link = activeLink("abc", "https://example.com", null);
		when(cacheService.get(CacheKeys.link("abc"), Link.class)).thenReturn(Optional.empty());
		when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));

		String url = urlService.resolveForRedirect("abc", new ClickContext(null, null, "1.2.3.4"));

		assertThat(url).isEqualTo("https://example.com");
		verify(cacheService).put(eq(CacheKeys.link("abc")), eq(link), any());
	}

	@Test
	void resolveForRedirect_throwsLinkNotFoundException_whenCodeDoesNotExist() {
		// Negative path: neither cache nor DB has this code.
		when(cacheService.get(any(), eq(Link.class))).thenReturn(Optional.empty());
		when(linkRepository.findByShortCode("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> urlService.resolveForRedirect("missing", new ClickContext(null, null, "1.2.3.4")))
				.isInstanceOf(LinkNotFoundException.class);

		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void resolveForRedirect_throwsLinkGoneException_whenLinkIsExpired() {
		// Edge case distinguishing "gone" from "not found": the code exists, but its expiry
		// instant is in the past - must be 410, not 404, and must not record a click.
		Link link = activeLink("abc", "https://example.com", Instant.now().minus(1, ChronoUnit.DAYS));
		when(cacheService.get(any(), eq(Link.class))).thenReturn(Optional.of(link));

		assertThatThrownBy(() -> urlService.resolveForRedirect("abc", new ClickContext(null, null, "1.2.3.4")))
				.isInstanceOf(LinkGoneException.class);

		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void resolveForRedirect_throwsLinkGoneException_whenLinkIsDeactivated() {
		// Same "gone" outcome via the other trigger: status != ACTIVE, regardless of expiry.
		Link link = activeLink("abc", "https://example.com", null);
		link.deactivate();
		when(cacheService.get(any(), eq(Link.class))).thenReturn(Optional.of(link));

		assertThatThrownBy(() -> urlService.resolveForRedirect("abc", new ClickContext(null, null, "1.2.3.4")))
				.isInstanceOf(LinkGoneException.class);
	}

	// --- getLink -------------------------------------------------------------------------------

	@Test
	void getLink_returnsMappedResponse_whenFound() {
		// Happy path for the read-only metadata lookup - must map every Link field through to the DTO.
		Link link = activeLink("abc", "https://example.com", null);
		when(cacheService.get(any(), eq(Link.class))).thenReturn(Optional.of(link));

		LinkResponse response = urlService.getLink("abc");

		assertThat(response.shortCode()).isEqualTo("abc");
		assertThat(response.shortUrl()).isEqualTo("/abc");
		assertThat(response.status()).isEqualTo("ACTIVE");
	}

	@Test
	void getLink_reportsExpiredStatus_evenBeforeTheScheduledSweepFlipsTheDbColumn() {
		// The scheduled LinkExpiryScheduler only runs periodically, so a link whose expiresAt has
		// passed must still read back as EXPIRED here immediately - it can't be left showing the
		// stale, not-yet-swept "ACTIVE" status stored in the DB.
		Link link = activeLink("abc", "https://example.com", Instant.now().minus(1, ChronoUnit.DAYS));
		when(cacheService.get(any(), eq(Link.class))).thenReturn(Optional.of(link));

		LinkResponse response = urlService.getLink("abc");

		assertThat(response.status()).isEqualTo("EXPIRED");
	}

	@Test
	void getLink_throwsLinkNotFoundException_whenMissing() {
		when(cacheService.get(any(), eq(Link.class))).thenReturn(Optional.empty());
		when(linkRepository.findByShortCode("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> urlService.getLink("missing")).isInstanceOf(LinkNotFoundException.class);
	}

	// --- updateLink ----------------------------------------------------------------------------

	@Test
	void updateLink_appliesTtlOnly_leavesStatusUnchanged() {
		// Partial update: only ttlSeconds supplied - active must be left untouched.
		Link link = activeLink("abc", "https://example.com", null);
		when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
		UpdateLinkRequest request = new UpdateLinkRequest(120L, null);

		LinkResponse response = urlService.updateLink("abc", request);

		assertThat(response.status()).isEqualTo("ACTIVE");
		assertThat(link.getExpiresAt()).isAfter(Instant.now().plusSeconds(100));
		verify(linkRepository).save(link);
		verify(cacheService).put(eq(CacheKeys.link("abc")), eq(link), any());
	}

	@Test
	void updateLink_appliesActiveFalse_deactivatesLink() {
		Link link = activeLink("abc", "https://example.com", null);
		when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
		UpdateLinkRequest request = new UpdateLinkRequest(null, false);

		LinkResponse response = urlService.updateLink("abc", request);

		assertThat(response.status()).isEqualTo("DEACTIVATED");
	}

	@Test
	void updateLink_appliesActiveTrue_reactivatesLink() {
		// Edge case: reactivating a previously-deactivated link via PATCH {"active": true}.
		Link link = activeLink("abc", "https://example.com", null);
		link.deactivate();
		when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
		UpdateLinkRequest request = new UpdateLinkRequest(null, true);

		LinkResponse response = urlService.updateLink("abc", request);

		assertThat(response.status()).isEqualTo("ACTIVE");
	}

	@Test
	void updateLink_throwsLinkNotFoundException_whenMissing() {
		when(linkRepository.findByShortCode("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> urlService.updateLink("missing", new UpdateLinkRequest(60L, null)))
				.isInstanceOf(LinkNotFoundException.class);
	}

	// --- deactivate ----------------------------------------------------------------------------

	@Test
	void deactivate_happyPath_savesAndEvictsCache() {
		Link link = activeLink("abc", "https://example.com", null);
		when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));

		urlService.deactivate("abc");

		assertThat(link.getStatus()).isEqualTo(LinkStatus.DEACTIVATED);
		verify(linkRepository, times(1)).save(link);
		verify(cacheService).evict(CacheKeys.link("abc"));
	}

	@Test
	void deactivate_throwsLinkNotFoundException_whenMissing() {
		when(linkRepository.findByShortCode("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> urlService.deactivate("missing")).isInstanceOf(LinkNotFoundException.class);

		verify(cacheService, never()).evict(any());
	}

	// --- test helpers --------------------------------------------------------------------------

	/** Builds a persistence-shaped Link with a deterministic id, since the real one is DB-assigned. */
	private static Link activeLink(String shortCode, String originalUrl, Instant expiresAt) {
		Link link = new Link(shortCode, originalUrl, Instant.now(), expiresAt);
		setId(link, 1L);
		return link;
	}

	private static void setId(Link link, long id) {
		try {
			Field idField = Link.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(link, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
