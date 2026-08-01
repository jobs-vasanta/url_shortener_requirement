package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized rate-limit configuration, enforced globally (all endpoints except
 * {@code /actuator/**}, {@code /swagger-ui/**}, {@code /v3/api-docs/**}) by
 * {@link com.urlshortener.ratelimit.RateLimitInterceptor}.
 * Bound from {@code app.rate-limit.*} in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

	/** Max requests allowed per window, per client IP. */
	private int limitForPeriod = 20;

	/** Max requests allowed per window, per premium-tier client - see ApiKeyService. */
	private int premiumLimitForPeriod = 200;

	/** Length of the sliding window, in seconds. */
	private int periodSeconds = 60;

	/** If true, requests are allowed through when Redis is unavailable (fail-open); otherwise rejected (fail-closed). */
	private boolean failOpen = true;

	public int getLimitForPeriod() {
		return limitForPeriod;
	}

	public void setLimitForPeriod(int limitForPeriod) {
		this.limitForPeriod = limitForPeriod;
	}

	public int getPremiumLimitForPeriod() {
		return premiumLimitForPeriod;
	}

	public void setPremiumLimitForPeriod(int premiumLimitForPeriod) {
		this.premiumLimitForPeriod = premiumLimitForPeriod;
	}

	public int getPeriodSeconds() {
		return periodSeconds;
	}

	public void setPeriodSeconds(int periodSeconds) {
		this.periodSeconds = periodSeconds;
	}

	public boolean isFailOpen() {
		return failOpen;
	}

	public void setFailOpen(boolean failOpen) {
		this.failOpen = failOpen;
	}
}
