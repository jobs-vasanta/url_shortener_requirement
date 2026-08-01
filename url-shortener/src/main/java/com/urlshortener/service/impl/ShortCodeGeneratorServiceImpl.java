package com.urlshortener.service.impl;

import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.repository.LinkRepository;
import com.urlshortener.service.ShortCodeGeneratorService;
import com.urlshortener.util.Base62Encoder;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

/**
 * Generates short codes via random-with-retry, falling back to the DB's unique
 * constraint on short_code as the ultimate correctness backstop (RequirementAnalysis.md
 * risk R1). At higher write volume, prefer a DB sequence + Base62Encoder instead
 * (see Architecture.md, Section 6) to avoid retry contention.
 */
@Service
public class ShortCodeGeneratorServiceImpl implements ShortCodeGeneratorService {

	private static final int CODE_LENGTH_CHARS = 7;
	private static final int MAX_ATTEMPTS = 5;
	// ~62^7 keyspace; random long bounded to keep Base62Encoder output around CODE_LENGTH_CHARS.
	private static final long RANDOM_UPPER_BOUND = (long) Math.pow(62, CODE_LENGTH_CHARS);

	private final LinkRepository linkRepository;

	public ShortCodeGeneratorServiceImpl(LinkRepository linkRepository) {
		this.linkRepository = linkRepository;
	}

	@Override
	public String generate() {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String candidate = Base62Encoder.encode(ThreadLocalRandom.current().nextLong(RANDOM_UPPER_BOUND));
			if (!linkRepository.existsByShortCode(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Unable to generate a unique short code after " + MAX_ATTEMPTS + " attempts");
	}

	@Override
	public void validateAliasAvailable(String alias) {
		if (linkRepository.existsByShortCode(alias)) {
			throw new AliasAlreadyExistsException(alias);
		}
	}
}
