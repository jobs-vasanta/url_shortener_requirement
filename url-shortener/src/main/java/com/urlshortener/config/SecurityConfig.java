package com.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Baseline security posture for the prototype:
 * - Public redirect endpoint stays unauthenticated by design (it's the product's core UX).
 * - Actuator health/info exposed for load-balancer/orchestrator probes.
 * - Management endpoints (create/deactivate/analytics) are open in this prototype;
 *   production deployments should require an API key or OAuth2/JWT bearer token here
 *   (see Architecture.md, Section 7 - AuthN/AuthZ).
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
						.requestMatchers("/{code}").permitAll()
						.anyRequest().permitAll());
		return http.build();
	}
}
