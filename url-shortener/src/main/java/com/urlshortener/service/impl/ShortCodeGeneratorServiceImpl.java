package com.urlshortener.service.impl;

import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.ReservedAliasException;
import com.urlshortener.repository.LinkRepository;
import com.urlshortener.service.ShortCodeGeneratorService;
import com.urlshortener.util.Base62Encoder;
import com.urlshortener.util.SnowflakeIdGenerator;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Generates short codes by Base62-encoding a Snowflake ID: unique by construction
 * (timestamp + node ID + sequence), so no existence-check/retry round trip is needed
 * before insert - unlike pure-random generation. The DB unique constraint on
 * short_code remains a defensive backstop (see GlobalExceptionHandler) for the
 * near-impossible case of a node-ID misconfiguration. See chat history for the
 * full UUID/Base62/Hashing/Snowflake comparison and rationale.
 */
@Service
public class ShortCodeGeneratorServiceImpl implements ShortCodeGeneratorService {

	// Top-level route segments the API owns; an alias equal to one of these would be unreachable or ambiguous.
	private static final Set<String> RESERVED_ALIASES = Set.of(
			"urls", "analytics", "actuator", "swagger-ui", "v3", "api-docs", "favicon.ico");

	private final SnowflakeIdGenerator snowflakeIdGenerator;
	private final LinkRepository linkRepository;

	public ShortCodeGeneratorServiceImpl(SnowflakeIdGenerator snowflakeIdGenerator, LinkRepository linkRepository) {
		this.snowflakeIdGenerator = snowflakeIdGenerator;
		this.linkRepository = linkRepository;
	}

	@Override
	public String generate() {
		return Base62Encoder.encode(snowflakeIdGenerator.nextId());
	}

	@Override
	public void validateAliasAvailable(String alias) {
		if (RESERVED_ALIASES.contains(alias.toLowerCase())) {
			throw new ReservedAliasException(alias);
		}
		if (linkRepository.existsByShortCode(alias)) {
			throw new AliasAlreadyExistsException(alias);
		}
	}
}
