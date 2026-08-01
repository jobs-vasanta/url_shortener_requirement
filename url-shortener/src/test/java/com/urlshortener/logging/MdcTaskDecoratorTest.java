package com.urlshortener.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Without this decorator, every log line emitted from the analytics executor's threads would
 * be missing a correlation ID (MDC is thread-local, and pooled threads don't inherit the
 * submitting thread's context by default) - these tests exist to prove propagation actually
 * happens, and that reused pooled threads don't bleed context between unrelated tasks.
 */
class MdcTaskDecoratorTest {

	private final MdcTaskDecorator decorator = new MdcTaskDecorator();

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void decorate_propagatesCallerMdcOntoTheExecutingThread() throws Exception {
		MDC.put("correlationId", "req-42");
		AtomicReference<String> observed = new AtomicReference<>();
		CountDownLatch latch = new CountDownLatch(1);
		Runnable decorated = decorator.decorate(() -> {
			observed.set(MDC.get("correlationId"));
			latch.countDown();
		});

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			executor.submit(decorated);
			assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
			assertThat(observed.get()).isEqualTo("req-42");
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void decorate_leavesMdcEmpty_whenCallerHadNoContext() throws Exception {
		// Edge case: nothing set on the calling thread must not somehow fabricate a context
		// on the executing thread either.
		AtomicReference<String> observed = new AtomicReference<>();
		CountDownLatch latch = new CountDownLatch(1);
		Runnable decorated = decorator.decorate(() -> {
			observed.set(MDC.get("correlationId"));
			latch.countDown();
		});

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			executor.submit(decorated);
			assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
			assertThat(observed.get()).isNull();
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void decorate_doesNotBleedContext_intoTheNextTaskOnAReusedThread() throws Exception {
		// The critical thread-pool-reuse scenario: task A runs with correlation ID "req-A" on a
		// pooled thread; task B (a *different*, unrelated request, submitted with no MDC context
		// of its own) later reuses that same thread. Task B must never observe "req-A".
		ExecutorService singleThreadPool = Executors.newSingleThreadExecutor();
		try {
			MDC.put("correlationId", "req-A");
			CountDownLatch firstDone = new CountDownLatch(1);
			singleThreadPool.submit(decorator.decorate(firstDone::countDown)).get(2, TimeUnit.SECONDS);
			assertThat(firstDone.await(2, TimeUnit.SECONDS)).isTrue();
			MDC.clear(); // back on the test thread, simulating request A's filter cleanup

			AtomicReference<String> observedByTaskB = new AtomicReference<>();
			CountDownLatch secondDone = new CountDownLatch(1);
			// Task B is decorated without ever calling MDC.put - it has no context of its own.
			singleThreadPool.submit(decorator.decorate(() -> {
				observedByTaskB.set(MDC.get("correlationId"));
				secondDone.countDown();
			}));

			assertThat(secondDone.await(2, TimeUnit.SECONDS)).isTrue();
			assertThat(observedByTaskB.get()).isNull();
		} finally {
			singleThreadPool.shutdownNow();
		}
	}
}
