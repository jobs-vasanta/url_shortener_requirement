package com.urlshortener.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires {@link RateLimitInterceptor} onto every route except health/docs, which must stay reachable for probes/tooling. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

	private final RateLimitInterceptor rateLimitInterceptor;

	public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor) {
		this.rateLimitInterceptor = rateLimitInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimitInterceptor)
				.excludePathPatterns("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**");
	}
}
