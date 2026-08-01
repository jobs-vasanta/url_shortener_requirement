package com.urlshortener.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.ReservedAliasException;
import com.urlshortener.repository.LinkRepository;
import com.urlshortener.util.Base62Encoder;
import com.urlshortener.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShortCodeGeneratorServiceImplTest {

	@Mock
	private SnowflakeIdGenerator snowflakeIdGenerator;

	@Mock
	private LinkRepository linkRepository;

	private ShortCodeGeneratorServiceImpl service;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		service = new ShortCodeGeneratorServiceImpl(snowflakeIdGenerator, linkRepository);
	}

	// --- Happy path -----------------------------------------------------------------------

	@Test
	void generate_encodesSnowflakeIdAsBase62() {
		// Exists to lock in the collaboration contract: generate() must be exactly
		// Base62Encoder.encode(snowflakeIdGenerator.nextId()), nothing more.
		when(snowflakeIdGenerator.nextId()).thenReturn(125_000L);

		String shortCode = service.generate();

		assertThat(shortCode).isEqualTo(Base62Encoder.encode(125_000L));
	}

	@Test
	void validateAliasAvailable_passesSilently_whenAliasIsAvailable() {
		// Happy path: an available, non-reserved alias must not throw and requires no return value.
		when(linkRepository.existsByShortCode("my-alias")).thenReturn(false);

		assertThatCode(() -> service.validateAliasAvailable("my-alias")).doesNotThrowAnyException();
	}

	// --- Negative path ----------------------------------------------------------------------

	@ParameterizedTest
	@ValueSource(strings = {"urls", "analytics", "actuator", "swagger-ui", "v3", "api-docs", "favicon.ico"})
	void validateAliasAvailable_throwsReservedAliasException_forEveryReservedWord(String reserved) {
		// One test per reserved word: each is an existing API route segment that would become
		// unreachable/ambiguous if claimed as a custom alias - a regression here would silently
		// break routing, not just fail loudly, so each is guarded individually.
		assertThatThrownBy(() -> service.validateAliasAvailable(reserved))
				.isInstanceOf(ReservedAliasException.class);
	}

	@Test
	void validateAliasAvailable_reservedCheckIsCaseInsensitive() {
		// Edge case: "URLS"/"Actuator" etc. must be caught too - the reserved check lowercases
		// before comparing, but a future refactor could easily drop that and only be caught by this test.
		assertThatThrownBy(() -> service.validateAliasAvailable("URLS"))
				.isInstanceOf(ReservedAliasException.class);
	}

	@Test
	void validateAliasAvailable_shortCircuitsBeforeRepositoryLookup_forReservedAlias() {
		// Exists to document and enforce ordering: the (cheap, in-memory) reserved-word check must
		// run before the (DB round-trip) existence check, so a reserved-word request never even
		// touches the repository.
		assertThatThrownBy(() -> service.validateAliasAvailable("actuator"))
				.isInstanceOf(ReservedAliasException.class);

		verify(linkRepository, never()).existsByShortCode(any());
	}

	@Test
	void validateAliasAvailable_throwsAliasAlreadyExistsException_whenAliasTaken() {
		// Happy-path collaborator wiring for the duplicate-alias case, distinct from the
		// reserved-word case above (different exception type, mapped to a different HTTP status).
		when(linkRepository.existsByShortCode("taken")).thenReturn(true);

		assertThatThrownBy(() -> service.validateAliasAvailable("taken"))
				.isInstanceOf(AliasAlreadyExistsException.class);
	}
}
