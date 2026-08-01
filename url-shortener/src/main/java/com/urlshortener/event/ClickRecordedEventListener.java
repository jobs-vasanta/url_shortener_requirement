package com.urlshortener.event;

import com.urlshortener.service.AnalyticsService;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Handles {@link ClickRecordedEvent} on the dedicated {@code analyticsExecutor} pool
 * (see AsyncConfig), isolating analytics persistence from redirect request handling.
 * Failures here are logged and never propagate back to the redirect caller.
 */
@Component
public class ClickRecordedEventListener {

	private static final Logger log = LoggerFactory.getLogger(ClickRecordedEventListener.class);

	private final AnalyticsService analyticsService;

	public ClickRecordedEventListener(AnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	@Async("analyticsExecutor")
	@EventListener
	public void onClickRecorded(ClickRecordedEvent event) {
		try {
			analyticsService.recordClick(event.getLinkId(), event.getReferrer(), event.getUserAgent(), event.getRemoteIp());
		} catch (Exception ex) {
			// TODO: push to a retry queue / outbox table instead of dropping (see Architecture.md, Section 8).
			// The Throwable must be the last vararg - SLF4J only auto-extracts it for the stack trace from that position.
			log.error("Failed to persist click event for linkId={}", event.getLinkId(),
					StructuredArguments.kv("linkId", event.getLinkId()), ex);
		}
	}
}
