package com.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter.XFrameOptionsMode;

/**
 * Baseline security posture for the prototype:
 * - Public redirect endpoint stays unauthenticated by design (it's the product's core UX).
 * - Actuator health/info exposed for load-balancer/orchestrator probes.
 * - Management endpoints (create/deactivate/analytics) are open in this prototype;
 *   production deployments should require an API key or OAuth2/JWT bearer token here
 *   (see Architecture.md, Section 7 - AuthN/AuthZ).
 * - Security headers (X-Content-Type-Options, X-Frame-Options, HSTS) are made explicit
 *   below rather than left as implicit framework defaults, so the posture is auditable.
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.headers(headers -> headers
						.contentTypeOptions(Customizer.withDefaults())
						.frameOptions(frame -> frame.mode(XFrameOptionsMode.DENY))
						.httpStrictTransportSecurity(hsts -> hsts
								.includeSubDomains(true)
								.maxAgeInSeconds(31_536_000)))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
						.requestMatchers("/{code}").permitAll()
						.anyRequest().permitAll());
		return http.build();
	}
}
