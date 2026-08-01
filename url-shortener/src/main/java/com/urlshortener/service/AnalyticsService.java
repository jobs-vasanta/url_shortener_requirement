package com.urlshortener.service;

import com.urlshortener.dto.AnalyticsResponse;

public interface AnalyticsService {

	/**
	 * Persists a single click event. Invoked from {@link com.urlshortener.event.ClickRecordedEventListener}
	 * on the dedicated analytics executor, so callers never block the redirect response on this call.
	 */
	void recordClick(Long linkId, String referrer, String userAgent, String remoteIp);

	AnalyticsResponse getAnalytics(String shortCode);
}
