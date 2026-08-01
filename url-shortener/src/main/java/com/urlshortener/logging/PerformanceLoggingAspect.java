package com.urlshortener.logging;

import com.urlshortener.config.LoggingProperties;
import net.logstash.logback.argument.StructuredArguments;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Times every service-layer call and logs the slow ones. A nanoTime diff is cheap enough to
 * leave enabled even on the hot redirect path; only calls over the configured threshold are
 * logged (at WARN), so normal-latency traffic doesn't drown the logs in noise.
 */
@Aspect
@Component
public class PerformanceLoggingAspect {

	private static final Logger log = LoggerFactory.getLogger("com.urlshortener.performance");

	private final long slowCallThresholdMs;

	public PerformanceLoggingAspect(LoggingProperties loggingProperties) {
		this.slowCallThresholdMs = loggingProperties.getSlowCallThresholdMs();
	}

	@Pointcut("execution(public * com.urlshortener.service.impl..*.*(..))")
	public void serviceLayer() {
	}

	@Around("serviceLayer()")
	public Object logSlowCalls(ProceedingJoinPoint joinPoint) throws Throwable {
		long startNanos = System.nanoTime();
		try {
			return joinPoint.proceed();
		} finally {
			long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
			String signature = joinPoint.getSignature().toShortString();
			if (durationMs >= slowCallThresholdMs) {
				log.warn("Slow call: {} took {} ms", signature, durationMs,
						StructuredArguments.kv("method", signature),
						StructuredArguments.kv("durationMs", durationMs));
			} else if (log.isDebugEnabled()) {
				log.debug("{} took {} ms", signature, durationMs);
			}
		}
	}
}
