package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized logging thresholds. Bound from {@code app.logging.*} in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "app.logging")
public class LoggingProperties {

	/** Service-layer calls slower than this are logged at WARN by PerformanceLoggingAspect. */
	private long slowCallThresholdMs = 500;

	public long getSlowCallThresholdMs() {
		return slowCallThresholdMs;
	}

	public void setSlowCallThresholdMs(long slowCallThresholdMs) {
		this.slowCallThresholdMs = slowCallThresholdMs;
	}
}
