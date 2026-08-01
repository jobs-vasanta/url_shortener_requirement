package com.urlshortener.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated, bounded executor for analytics event processing, isolated from
 * the request-handling thread pool so a backlog in analytics never starves
 * redirect/create request handling (see Architecture.md, Section 8).
 */
@Configuration
public class AsyncConfig {

	@Bean(name = "analyticsExecutor")
	public Executor analyticsExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(500);
		executor.setThreadNamePrefix("analytics-");
		executor.initialize();
		return executor;
	}
}
