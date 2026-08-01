package com.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.urlshortener.cache.CacheKeys;
import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.dto.UpdateLinkRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Verifies the cache-aside contract directly against the real Redis container: creation writes
 * through with a bounded TTL, an evicted entry is transparently reloaded from the database on
 * the next read, and every mutation (update/deactivate) refreshes or evicts the entry so a
 * subsequent read/redirect can never observe stale data. The cache is an implementation detail
 * from the API's point of view, so these tests assert on the actual Redis keys, not just on
 * HTTP responses (which would pass even if caching were silently broken, since every operation
 * also has a correct database fallback).
 */
class RedisCacheIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	@Test
	void createLink_writesThroughToTheCache_withABoundedTtl() {
		LinkResponse created = createLink("https://example.com/cache/create-writes-through");
		String cacheKey = CacheKeys.link(created.shortCode());

		assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
		Long ttl = redisTemplate.getExpire(cacheKey);
		assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(21_600L); // app.cache.link-ttl-seconds default
	}

	@Test
	void getLink_afterTheCacheEntryIsEvicted_reloadsFromDatabaseAndRepopulatesTheCache() {
		LinkResponse created = createLink("https://example.com/cache/reload-after-eviction");
		String cacheKey = CacheKeys.link(created.shortCode());
		redisTemplate.delete(cacheKey);
		assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

		ResponseEntity<LinkResponse> response =
				restTemplate.getForEntity("/urls/" + created.shortCode(), LinkResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().originalUrl()).isEqualTo("https://example.com/cache/reload-after-eviction");
		assertThat(redisTemplate.hasKey(cacheKey)).isTrue(); // read-through repopulated it
	}

	@Test
	void updateLink_refreshesTheCache_soASubsequentReadNeverServesThePreUpdateState() {
		LinkResponse created = createLink("https://example.com/cache/update-refreshes");
		String cacheKey = CacheKeys.link(created.shortCode());

		restTemplate.exchange("/urls/" + created.shortCode(), HttpMethod.PATCH,
				new HttpEntity<>(new UpdateLinkRequest(120L, null)), LinkResponse.class);

		assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
		ResponseEntity<LinkResponse> afterUpdate =
				restTemplate.getForEntity("/urls/" + created.shortCode(), LinkResponse.class);
		assertThat(afterUpdate.getBody().expiresAt()).isNotNull();
	}

	@Test
	void deactivateLink_evictsTheCache_soARedirectCanNeverServeAStaleActiveLink() {
		LinkResponse created = createLink("https://example.com/cache/deactivate-evicts");
		String cacheKey = CacheKeys.link(created.shortCode());
		assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

		restTemplate.exchange("/urls/" + created.shortCode(), HttpMethod.DELETE, null, Void.class);

		assertThat(redisTemplate.hasKey(cacheKey)).isFalse();
	}

	private LinkResponse createLink(String longUrl) {
		return restTemplate.postForEntity("/urls", new CreateLinkRequest(longUrl, null, null), LinkResponse.class)
				.getBody();
	}
}
