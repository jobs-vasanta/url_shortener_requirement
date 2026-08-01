package com.urlshortener.repository;

import com.urlshortener.domain.ApiKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

	/** Only active keys resolve - a deactivated/revoked key must fall back to FREE, not an error. */
	Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);
}
