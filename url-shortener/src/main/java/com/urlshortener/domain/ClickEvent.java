package com.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Append-only click event. IP and user-agent are stored hashed (see
 * com.urlshortener.util.HashUtil), never raw, to minimize PII exposure.
 * The underlying table is time-partitioned in production (see
 * Architecture.md, Section 6) to bound index size and simplify retention.
 */
@Entity
@Table(name = "click_events", indexes = {
		@Index(name = "idx_click_events_link_id_occurred_at", columnList = "link_id, occurred_at"),
		@Index(name = "idx_click_events_occurred_at", columnList = "occurred_at")
})
public class ClickEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "link_id", nullable = false)
	private Long linkId;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "referrer", length = 2048)
	private String referrer;

	@Column(name = "user_agent_hash", length = 64)
	private String userAgentHash;

	@Column(name = "ip_hash", length = 64)
	private String ipHash;

	protected ClickEvent() {
		// JPA
	}

	public ClickEvent(Long linkId, Instant occurredAt, String referrer, String userAgentHash, String ipHash) {
		this.linkId = linkId;
		this.occurredAt = occurredAt;
		this.referrer = referrer;
		this.userAgentHash = userAgentHash;
		this.ipHash = ipHash;
	}

	public Long getId() {
		return id;
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

	public String getUserAgentHash() {
		return userAgentHash;
	}

	public String getIpHash() {
		return ipHash;
	}
}
