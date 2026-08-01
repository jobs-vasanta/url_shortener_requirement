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
 * A caller credential presented via the {@code X-Api-Key} request header, resolved to an
 * {@link ApiKeyTier} that gates plan-specific behavior (rate limit, link expiration) - see
 * {@code ApiKeyService}. Only the SHA-256 hash of the raw key is ever persisted, the same
 * PII-minimization approach {@code HashUtil} already applies to click-event IP/user-agent data.
 */
@Entity
@Table(name = "api_keys", indexes = {
		@Index(name = "idx_api_keys_key_hash", columnList = "key_hash", unique = true)
})
public class ApiKey {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "key_hash", nullable = false, unique = true, length = 64)
	private String keyHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "tier", nullable = false, length = 16)
	private ApiKeyTier tier;

	@Column(name = "label", length = 128)
	private String label;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ApiKey() {
		// JPA
	}

	public ApiKey(String keyHash, ApiKeyTier tier, String label, Instant createdAt) {
		this.keyHash = keyHash;
		this.tier = tier;
		this.label = label;
		this.active = true;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public String getKeyHash() {
		return keyHash;
	}

	public ApiKeyTier getTier() {
		return tier;
	}

	public String getLabel() {
		return label;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void deactivate() {
		this.active = false;
	}
}
