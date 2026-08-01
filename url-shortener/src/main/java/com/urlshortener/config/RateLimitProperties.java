package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized rate-limit configuration for the link-creation endpoint.
 * Bound from {@code app.rate-limit.*} in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

	/** Max create-link requests allowed per window, per client key (IP or API key). */
	private int limitForPeriod = 20;

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
