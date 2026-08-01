package com.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Aggregate root for a shortened link. Domain rules (e.g. redirectability)
 * live here so the check exists in exactly one place across the codebase.
 */
@Entity
@Table(name = "links", indexes = {
		@Index(name = "idx_links_short_code", columnList = "short_code", unique = true)
})
public class Link {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "short_code", nullable = false, unique = true, length = 32)
	private String shortCode;

	@Column(name = "original_url", nullable = false, length = 2048)
	private String originalUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private LinkStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	protected Link() {
		// JPA
	}

	public Link(String shortCode, String originalUrl, Instant createdAt, Instant expiresAt) {
		this.shortCode = shortCode;
		this.originalUrl = originalUrl;
		this.status = LinkStatus.ACTIVE;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	/** True if this link can currently be redirected: active status and not past its expiry. */
	public boolean isRedirectable(Instant now) {
		if (status != LinkStatus.ACTIVE) {
			return false;
		}
		return expiresAt == null || now.isBefore(expiresAt);
	}

	public void deactivate() {
		this.status = LinkStatus.DEACTIVATED;
	}

	public void markExpired() {
		this.status = LinkStatus.EXPIRED;
	}

	public Long getId() {
		return id;
	}

	public String getShortCode() {
		return shortCode;
	}

	public String getOriginalUrl() {
		return originalUrl;
	}

	public LinkStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
