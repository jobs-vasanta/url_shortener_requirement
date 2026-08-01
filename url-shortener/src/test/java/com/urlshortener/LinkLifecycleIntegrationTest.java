package com.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.urlshortener.domain.Link;
import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.ErrorResponse;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.dto.UpdateLinkRequest;
import com.urlshortener.repository.LinkRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Full create -> read -> update -> deactivate/reactivate lifecycle over real HTTP, verified both
 * through the API response AND by reading the row back directly out of the real Postgres
 * container - proving the REST layer, service layer, and persistence layer all agree on the
 * same state, not just that the service layer mocks were told the right things.
 */
class LinkLifecycleIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private LinkRepository linkRepository;

	@Test
	void createThenGet_persistsToDatabase_andReturnsMatchingMetadata() {
		ResponseEntity<LinkResponse> createResponse = createLink(
				new CreateLinkRequest("https://example.com/lifecycle/create-then-get", null, null));

		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		LinkResponse created = createResponse.getBody();
		assertThat(created).isNotNull();
		assertThat(created.status()).isEqualTo("ACTIVE");
		assertThat(created.originalUrl()).isEqualTo("https://example.com/lifecycle/create-then-get");
		assertThat(created.expiresAt()).isNotNull();

		// Round-trips through the real database, not a mock repository.
		Link persisted = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
		assertThat(persisted.getOriginalUrl()).isEqualTo("https://example.com/lifecycle/create-then-get");
		assertThat(persisted.getStatus().name()).isEqualTo("ACTIVE");

		ResponseEntity<LinkResponse> getResponse =
				restTemplate.getForEntity("/urls/" + created.shortCode(), LinkResponse.class);
		assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(getResponse.getBody().shortCode()).isEqualTo(created.shortCode());
	}

	@Test
	void createWithCustomAlias_usesTheAliasAsTheShortCode() {
		String alias = "alias" + uniqueSuffix();
		ResponseEntity<LinkResponse> response =
				createLink(new CreateLinkRequest("https://example.com/lifecycle/alias", alias, null));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().shortCode()).isEqualTo(alias);
		assertThat(linkRepository.existsByShortCode(alias)).isTrue();
	}

	@Test
	void createWithADuplicateAlias_returns409_andDoesNotOverwriteTheOriginal() {
		String alias = "dup" + uniqueSuffix();
		createLink(new CreateLinkRequest("https://example.com/first", alias, null));

		ResponseEntity<ErrorResponse> conflict = restTemplate.postForEntity(
				"/urls", new CreateLinkRequest("https://example.com/second", alias, null), ErrorResponse.class);

		assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		Link stillOriginal = linkRepository.findByShortCode(alias).orElseThrow();
		assertThat(stillOriginal.getOriginalUrl()).isEqualTo("https://example.com/first");
	}

	@Test
	void createWithTtl_computesAnAbsoluteExpiryPersistedToTheDatabase() {
		Instant before = Instant.now();
		LinkResponse created =
				createLink(new CreateLinkRequest("https://example.com/lifecycle/ttl", null, 3600L)).getBody();

		assertThat(created.expiresAt()).isAfter(before.plusSeconds(3500));
		Link persisted = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
		assertThat(persisted.getExpiresAt()).isEqualTo(created.expiresAt());
	}

	@Test
	void updateTtl_replacesTheExpiryInPlace_inBothTheResponseAndTheDatabase() {
		LinkResponse created =
				createLink(new CreateLinkRequest("https://example.com/lifecycle/update-ttl", null, null)).getBody();

		ResponseEntity<LinkResponse> updated = restTemplate.exchange(
				"/urls/" + created.shortCode(), HttpMethod.PATCH,
				new HttpEntity<>(new UpdateLinkRequest(60L, null)), LinkResponse.class);

		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updated.getBody().expiresAt()).isNotNull();
		Link persisted = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
		assertThat(persisted.getExpiresAt()).isEqualTo(updated.getBody().expiresAt());
	}

	@Test
	void deactivateThenReactivate_flipsStatusInBothTheDatabaseAndTheResponse() {
		LinkResponse created =
				createLink(new CreateLinkRequest("https://example.com/lifecycle/deactivate", null, null)).getBody();

		ResponseEntity<Void> deleteResponse =
				restTemplate.exchange("/urls/" + created.shortCode(), HttpMethod.DELETE, null, Void.class);
		assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(linkRepository.findByShortCode(created.shortCode()).orElseThrow().getStatus().name())
				.isEqualTo("DEACTIVATED");

		ResponseEntity<LinkResponse> reactivated = restTemplate.exchange(
				"/urls/" + created.shortCode(), HttpMethod.PATCH,
				new HttpEntity<>(new UpdateLinkRequest(null, true)), LinkResponse.class);
		assertThat(reactivated.getBody().status()).isEqualTo("ACTIVE");
		assertThat(linkRepository.findByShortCode(created.shortCode()).orElseThrow().getStatus().name())
				.isEqualTo("ACTIVE");
	}

	@Test
	void getUpdateAndDeactivate_onAnUnknownShortCode_allReturn404() {
		String unknown = "missing-" + uniqueSuffix();

		assertThat(restTemplate.getForEntity("/urls/" + unknown, ErrorResponse.class).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<ErrorResponse> patchResponse = restTemplate.exchange(
				"/urls/" + unknown, HttpMethod.PATCH,
				new HttpEntity<>(new UpdateLinkRequest(60L, null)), ErrorResponse.class);
		assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<ErrorResponse> deleteResponse =
				restTemplate.exchange("/urls/" + unknown, HttpMethod.DELETE, null, ErrorResponse.class);
		assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private ResponseEntity<LinkResponse> createLink(CreateLinkRequest request) {
		return restTemplate.postForEntity("/urls", request, LinkResponse.class);
	}

	private static String uniqueSuffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
