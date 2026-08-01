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
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Aggregate root for a shortened link. Domain rules (e.g. redirectability)
 * live here so the check exists in exactly one place across the codebase.
 *
 * <p>{@code clickCount} is a denormalized, eventually-updated counter maintained
 * via an atomic {@code UPDATE ... SET click_count = click_count + 1} (see
 * {@link com.urlshortener.repository.LinkRepository#incrementClickCount}) rather
 * than load-modify-save, so hot links never trigger optimistic-lock contention
 * on the redirect path. {@code version} guards the lower-frequency
 * status/metadata mutations (deactivate, expiry sweep) instead.
 */
@Entity
@Table(name = "links", indexes = {
		@Index(name = "idx_links_short_code", columnList = "short_code", unique = true),
		@Index(name = "idx_links_status_expires_at", columnList = "status, expires_at")
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

	@Column(name = "click_count", nullable = false)
	private long clickCount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected Link() {
		// JPA
	}

	public Link(String shortCode, String originalUrl, Instant createdAt, Instant expiresAt) {
		this.shortCode = shortCode;
		this.originalUrl = originalUrl;
		this.status = LinkStatus.ACTIVE;
		this.clickCount = 0L;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
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
		this.updatedAt = Instant.now();
	}

	public void markExpired() {
		this.status = LinkStatus.EXPIRED;
		this.updatedAt = Instant.now();
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

	public long getClickCount() {
		return clickCount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public long getVersion() {
		return version;
	}
}
