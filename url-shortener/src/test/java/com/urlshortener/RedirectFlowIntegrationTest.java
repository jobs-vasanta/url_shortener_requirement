package com.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.ErrorResponse;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.dto.UpdateLinkRequest;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.LinkRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Exercises the public, unauthenticated redirect endpoint end-to-end: the 302 itself, the two
 * "gone" failure modes (expired vs. deactivated), the 404 for an unknown code, and - because
 * click recording is fire-and-forget on a background executor (see ClickRecordedEventListener) -
 * that a click eventually lands in the database without the redirect ever waiting on it.
 */
class RedirectFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private LinkRepository linkRepository;

	@Autowired
	private ClickEventRepository clickEventRepository;

	@Test
	void redirect_returns302WithLocationHeader_forAnActiveLink() {
		LinkResponse created = createLink("https://example.com/redirect/happy-path");

		ResponseEntity<Void> response =
				restTemplate.exchange("/" + created.shortCode(), HttpMethod.GET, null, Void.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(response.getHeaders().getLocation()).hasToString("https://example.com/redirect/happy-path");
	}

	@Test
	void redirect_recordsClicksAsynchronously_thatEventuallyPersistToTheDatabase() {
		LinkResponse created = createLink("https://example.com/redirect/analytics");
		Long linkId = linkRepository.findByShortCode(created.shortCode()).orElseThrow().getId();

		restTemplate.exchange("/" + created.shortCode(), HttpMethod.GET, null, Void.class);
		restTemplate.exchange("/" + created.shortCode(), HttpMethod.GET, null, Void.class);

		// The redirect responds before the async click write completes, so poll the database
		// rather than asserting immediately (a naive immediate assertion would be flaky).
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(linkRepository.findByShortCode(created.shortCode()).orElseThrow().getClickCount())
					.isEqualTo(2L);
			assertThat(clickEventRepository.countByLinkId(linkId)).isEqualTo(2L);
		});
	}

	@Test
	void redirect_onAnUnknownShortCode_returns404() {
		ResponseEntity<ErrorResponse> response =
				restTemplate.getForEntity("/unknown-" + UUID.randomUUID(), ErrorResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void redirect_onADeactivatedLink_returns410Gone() {
		LinkResponse created = createLink("https://example.com/redirect/deactivated");
		restTemplate.exchange("/urls/" + created.shortCode(), HttpMethod.DELETE, null, Void.class);

		ResponseEntity<ErrorResponse> response =
				restTemplate.exchange("/" + created.shortCode(), HttpMethod.GET, null, ErrorResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
	}

	@Test
	void redirect_onAnExpiredLink_returns410Gone() {
		LinkResponse created = createLink("https://example.com/redirect/expired");
		// ttlSeconds must be positive per validation, so expire it via a 1-second TTL rather than a negative one.
		restTemplate.exchange("/urls/" + created.shortCode(), HttpMethod.PATCH,
				new HttpEntity<>(new UpdateLinkRequest(1L, null)), LinkResponse.class);

		await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
			ResponseEntity<ErrorResponse> response =
					restTemplate.exchange("/" + created.shortCode(), HttpMethod.GET, null, ErrorResponse.class);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
		});
	}

	private LinkResponse createLink(String longUrl) {
		return restTemplate.postForEntity("/urls", new CreateLinkRequest(longUrl, null, null), LinkResponse.class)
				.getBody();
	}
}
