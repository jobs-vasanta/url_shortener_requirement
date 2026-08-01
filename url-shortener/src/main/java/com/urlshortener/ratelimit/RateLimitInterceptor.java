package com.urlshortener.ratelimit;

import com.urlshortener.config.RateLimitProperties;
import com.urlshortener.domain.ApiKeyTier;
import com.urlshortener.exception.RateLimitExceededException;
import com.urlshortener.service.ApiKeyService;
import com.urlshortener.util.HashUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Fixed-window rate limiter backed by Redis (shared across instances, unlike an in-memory
 * limiter) - see RequirementAnalysis.md risk R3 and the DoS-hardening security review in chat
 * history. Registered globally (minus health/docs) by {@link WebMvcConfig}; throws
 * {@link RateLimitExceededException}, which {@code GlobalExceptionHandler} maps to a 429
 * response. Resolves the caller's {@link ApiKeyTier} from the {@code X-Api-Key} header (via
 * {@link ApiKeyService}) so premium callers get a higher per-window allowance.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
	private static final String KEY_PREFIX = "ratelimit:";
	private static final String API_KEY_HEADER = "X-Api-Key";

	private final StringRedisTemplate stringRedisTemplate;
	private final RateLimitProperties properties;
	private final ApiKeyService apiKeyService;

	public RateLimitInterceptor(StringRedisTemplate stringRedisTemplate, RateLimitProperties properties,
			ApiKeyService apiKeyService) {
		this.stringRedisTemplate = stringRedisTemplate;
		this.properties = properties;
		this.apiKeyService = apiKeyService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String apiKey = request.getHeader(API_KEY_HEADER);
		ApiKeyTier tier = apiKeyService.resolveTier(apiKey);
		int limitForPeriod = tier == ApiKeyTier.PREMIUM ? properties.getPremiumLimitForPeriod() : properties.getLimitForPeriod();

		// NOTE: getRemoteAddr() is the direct TCP peer. If this app sits behind a trusted reverse
		// proxy/load balancer, replace with a vetted forwarded-for header via ForwardedHeaderFilter -
		// never trust X-Forwarded-For directly, since any client can set it. When an API key is
		// presented, partition by its hash instead of IP so unrelated clients sharing an egress IP
		// (e.g. behind a corporate NAT) don't share one another's quota.
		String clientKey = (apiKey != null && !apiKey.isBlank())
				? "key:" + HashUtil.sha256Hex(apiKey)
				: request.getRemoteAddr();
		long windowStart = Instant.now().getEpochSecond() / properties.getPeriodSeconds();
		String redisKey = KEY_PREFIX + clientKey + ":" + windowStart;

		Long count;
		try {
			count = stringRedisTemplate.opsForValue().increment(redisKey);
			if (count != null && count == 1L) {
				stringRedisTemplate.expire(redisKey, Duration.ofSeconds(properties.getPeriodSeconds()));
			}
		} catch (Exception ex) {
			log.warn("Rate limiter backend unavailable, applying fail-{} policy: {}",
					properties.isFailOpen() ? "open" : "closed", ex.toString());
			if (properties.isFailOpen()) {
				return true;
			}
			throw new RateLimitExceededException("Rate limiting is temporarily unavailable; please retry later");
		}

		if (count != null && count > limitForPeriod) {
			throw new RateLimitExceededException("Rate limit exceeded; please retry later");
		}
		return true;
	}
}
