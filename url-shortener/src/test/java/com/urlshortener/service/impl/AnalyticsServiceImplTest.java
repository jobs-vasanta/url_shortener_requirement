package com.urlshortener.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urlshortener.cache.CacheKeys;
import com.urlshortener.cache.CacheService;
import com.urlshortener.domain.ClickEvent;
import com.urlshortener.domain.Link;
import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.exception.LinkNotFoundException;
import com.urlshortener.repository.ClickAccessWindow;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.LinkRepository;
import com.urlshortener.util.HashUtil;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers both responsibilities of AnalyticsServiceImpl: writing click events (with the PII
 * minimization rules established in the security review) and reading cached/aggregated analytics.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

	private static final long ANALYTICS_CACHE_TTL_SECONDS = 30L;

	@Mock
	private LinkRepository linkRepository;
	@Mock
	private ClickEventRepository clickEventRepository;
	@Mock
	private CacheService cacheService;

	private AnalyticsServiceImpl analyticsService;

	@BeforeEach
	void setUp() {
		analyticsService = new AnalyticsServiceImpl(linkRepository, clickEventRepository, cacheService,
				ANALYTICS_CACHE_TTL_SECONDS);
	}

	// --- recordClick ---------------------------------------------------------------------------

	@Test
	void recordClick_happyPath_hashesPiiAndEvictsAnalyticsCache() {
		// Core PII-minimization contract: raw IP/user-agent must never reach persistence -
		// only their SHA-256 hashes. Also verifies the click-count/analytics cache is
		// invalidated so a subsequent read reflects the new click, not a stale cached value.
		ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);

		analyticsService.recordClick(42L, "https://ref.example/page", "Mozilla/5.0", "203.0.113.5");

		verify(clickEventRepository).save(captor.capture());
		ClickEvent saved = captor.getValue();
		assertThat(saved.getLinkId()).isEqualTo(42L);
		assertThat(saved.getUserAgentHash()).isEqualTo(HashUtil.sha256Hex("Mozilla/5.0"));
		assertThat(saved.getIpHash()).isEqualTo(HashUtil.sha256Hex("203.0.113.5"));
		verify(linkRepository).incrementClickCount(eq(42L), any(Instant.class));
		verify(cacheService).evict(CacheKeys.analytics(42L));
	}

	@Test
	void recordClick_stripsQueryStringAndFragmentFromReferrer() {
		// Regression guard for the referrer-sanitization security fix: query params/fragments
		// (which can carry session tokens) must never reach the stored referrer.
		ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);

		analyticsService.recordClick(1L, "https://ref.example/page?token=secret#section", "ua", "1.2.3.4");

		verify(clickEventRepository).save(captor.capture());
		assertThat(captor.getValue().getReferrer()).isEqualTo("https://ref.example/page");
	}

	@Test
	void recordClick_setsReferrerNull_whenReferrerIsNull() {
		// Edge case: no Referer header sent at all (e.g. direct navigation, or a privacy-respecting browser).
		ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);

		analyticsService.recordClick(1L, null, "ua", "1.2.3.4");

		verify(clickEventRepository).save(captor.capture());
		assertThat(captor.getValue().getReferrer()).isNull();
	}

	@Test
	void recordClick_setsReferrerNull_whenReferrerIsBlank() {
		// Edge case: an empty (but non-null) Referer header must be treated the same as absent.
		ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);

		analyticsService.recordClick(1L, "   ", "ua", "1.2.3.4");

		verify(clickEventRepository).save(captor.capture());
		assertThat(captor.getValue().getReferrer()).isNull();
	}

	@Test
	void recordClick_setsReferrerNull_whenReferrerIsMalformed() {
		// Edge case: a syntactically invalid referrer must be dropped, not propagate a parser
		// exception up through the (fire-and-forget, async) click-recording path.
		ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);

		analyticsService.recordClick(1L, "http://[not-closed", "ua", "1.2.3.4");

		verify(clickEventRepository).save(captor.capture());
		assertThat(captor.getValue().getReferrer()).isNull();
	}

	@Test
	void recordClick_hashesNullUserAgentAndIp_withoutThrowing() {
		// Edge case: HashUtil.sha256Hex(null) is defined to return null rather than throw -
		// this test exists to catch a regression if that null-handling contract ever changes.
		ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);

		analyticsService.recordClick(1L, null, null, null);

		verify(clickEventRepository).save(captor.capture());
		assertThat(captor.getValue().getUserAgentHash()).isNull();
		assertThat(captor.getValue().getIpHash()).isNull();
	}

	// --- getAnalytics --------------------------------------------------------------------------

	@Test
	void getAnalytics_returnsCachedResponse_withoutQueryingClickEvents() {
		// Happy path for the cache-hit branch: the (relatively expensive) access-window query
		// must be skipped entirely when a cached response is available.
		Link link = linkWithId(7L);
		when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
		AnalyticsResponse cached = new AnalyticsResponse("abc", 10L, Instant.now(), Instant.now());
		when(cacheService.get(CacheKeys.analytics(7L), AnalyticsResponse.class)).thenReturn(Optional.of(cached));

		AnalyticsResponse response = analyticsService.getAnalytics("abc");

		assertThat(response).isEqualTo(cached);
		verify(clickEventRepository, never()).findAccessWindow(any());
	}

	@Test
	void getAnalytics_computesAndCachesResponse_onCacheMiss() {
		// Happy path for the cache-miss branch: falls back to the denormalized click count plus
		// the DB access-window query, then writes the computed result back into the cache.
		Link link = linkWithId(7L);
		link.deactivate(); // status is irrelevant to analytics; ensures no accidental coupling
		when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
		when(cacheService.get(CacheKeys.analytics(7L), AnalyticsResponse.class)).thenReturn(Optional.empty());
		Instant first = Instant.parse("2026-01-01T00:00:00Z");
		Instant last = Instant.parse("2026-01-02T00:00:00Z");
		when(clickEventRepository.findAccessWindow(7L)).thenReturn(new ClickAccessWindow(first, last));

		AnalyticsResponse response = analyticsService.getAnalytics("abc");

		assertThat(response.shortCode()).isEqualTo("abc");
		assertThat(response.firstAccessedAt()).isEqualTo(first);
		assertThat(response.lastAccessedAt()).isEqualTo(last);
		verify(cacheService).put(eq(CacheKeys.analytics(7L)), eq(response), any());
	}

	@Test
	void getAnalytics_throwsLinkNotFoundException_whenShortCodeMissing() {
		when(linkRepository.findByShortCode("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> analyticsService.getAnalytics("missing")).isInstanceOf(LinkNotFoundException.class);

		verify(cacheService, never()).get(any(), eq(AnalyticsResponse.class));
	}

	// --- test helpers --------------------------------------------------------------------------

	private static Link linkWithId(long id) {
		Link link = new Link("abc", "https://example.com", Instant.now(), null);
		try {
			Field idField = Link.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(link, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return link;
	}
}
