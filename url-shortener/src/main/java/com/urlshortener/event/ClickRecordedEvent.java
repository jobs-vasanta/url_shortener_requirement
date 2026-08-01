package com.urlshortener.event;

import java.time.Instant;

/** Published on every successful redirect; consumed asynchronously so it never delays the response. */
public class ClickRecordedEvent {

	private final Long linkId;
	private final Instant occurredAt;
	private final String referrer;
	private final String userAgent;
	private final String remoteIp;

	public ClickRecordedEvent(Long linkId, Instant occurredAt, String referrer, String userAgent, String remoteIp) {
		this.linkId = linkId;
		this.occurredAt = occurredAt;
		this.referrer = referrer;
		this.userAgent = userAgent;
		this.remoteIp = remoteIp;
	}

	public Long getLinkId() {
		return linkId;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getReferrer() {
		return referrer;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public String getRemoteIp() {
		return remoteIp;
	}
}
