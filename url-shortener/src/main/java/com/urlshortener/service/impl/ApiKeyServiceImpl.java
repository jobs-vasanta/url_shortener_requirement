package com.urlshortener.service.impl;

import com.urlshortener.domain.ApiKey;
import com.urlshortener.domain.ApiKeyTier;
import com.urlshortener.repository.ApiKeyRepository;
import com.urlshortener.service.ApiKeyService;
import com.urlshortener.util.HashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyServiceImpl implements ApiKeyService {

	private final ApiKeyRepository apiKeyRepository;

	public ApiKeyServiceImpl(ApiKeyRepository apiKeyRepository) {
		this.apiKeyRepository = apiKeyRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public ApiKeyTier resolveTier(String rawApiKey) {
		if (rawApiKey == null || rawApiKey.isBlank()) {
			return ApiKeyTier.FREE;
		}
		return apiKeyRepository.findByKeyHashAndActiveTrue(HashUtil.sha256Hex(rawApiKey))
				.map(ApiKey::getTier)
				.orElse(ApiKeyTier.FREE);
	}
}
