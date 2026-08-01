package com.urlshortener.cache;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin, resilient wrapper around {@link RedisTemplate}. Every operation is guarded by the
 * {@code redisCache} circuit breaker (see application.yml) so a Redis outage degrades callers
 * to their database fallback path instead of failing the request - critical on the redirect
 * hot path, which must never go down because a non-authoritative cache is unavailable.
 *
 * <p>Cache is always best-effort: reads fall back to {@link Optional#empty()} (caller reloads
 * from the system of record), writes/evictions are logged and swallowed. Every entry written
 * here MUST carry a TTL - it is the backstop that bounds staleness when an eviction is missed
 * or fails (see {@link #evict}).
 */
@Component
public class CacheService {

	private static final Logger log = LoggerFactory.getLogger(CacheService.class);

	private final RedisTemplate<String, Object> redisTemplate;

	public CacheService(RedisTemplate<String, Object> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@CircuitBreaker(name = "redisCache", fallbackMethod = "getFallback")
	public <T> Optional<T> get(String key, Class<T> type) {
		Object value = redisTemplate.opsForValue().get(key);
		return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
	}

	@SuppressWarnings("unused") // invoked reflectively by resilience4j
	private <T> Optional<T> getFallback(String key, Class<T> type, Throwable ex) {
		log.warn("Redis GET failed for key '{}', falling back to source of truth: {}", key, ex.toString());
		return Optional.empty();
	}

	/** Writes a value with a mandatory TTL - there is no unbounded-lifetime cache entry in this system. */
	@CircuitBreaker(name = "redisCache", fallbackMethod = "putFallback")
	public void put(String key, Object value, Duration ttl) {
		redisTemplate.opsForValue().set(key, value, ttl.toSeconds(), TimeUnit.SECONDS);
	}

	@SuppressWarnings("unused")
	private void putFallback(String key, Object value, Duration ttl, Throwable ex) {
		log.warn("Redis SET failed for key '{}', continuing without caching this write: {}", key, ex.toString());
	}

	@CircuitBreaker(name = "redisCache", fallbackMethod = "evictFallback")
	public void evict(String key) {
		redisTemplate.delete(key);
	}

	@SuppressWarnings("unused")
	private void evictFallback(String key, Throwable ex) {
		log.warn("Redis DELETE failed for key '{}' - stale entry will still expire via TTL: {}", key, ex.toString());
	}
}
