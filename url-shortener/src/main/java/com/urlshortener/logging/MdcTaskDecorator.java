package com.urlshortener.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Copies the calling thread's MDC (correlation ID) onto the pooled thread that executes an
 * {@code @Async} task, so click-processing log lines can still be tied back to the originating
 * request. Without this, MDC is empty on executor threads since it's a thread-local.
 */
public class MdcTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable runnable) {
		Map<String, String> callerContext = MDC.getCopyOfContextMap();
		return () -> {
			Map<String, String> previousContext = MDC.getCopyOfContextMap();
			try {
				if (callerContext != null) {
					MDC.setContextMap(callerContext);
				}
				runnable.run();
			} finally {
				if (previousContext != null) {
					MDC.setContextMap(previousContext);
				} else {
					MDC.clear();
				}
			}
		};
	}
}
