package com.urlshortener.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis is used as a read-through cache for link lookups and as the backing
 * store for distributed rate-limit counters (see {@link RateLimitProperties}).
 * Connection pooling (Lettuce + commons-pool2) and timeouts are configured via
 * {@code spring.data.redis.*} properties (application.yml); all access beyond this
 * template goes through {@link com.urlshortener.cache.CacheService}, which adds the
 * circuit-breaker fallback that keeps a Redis outage from failing requests.
 */
@Configuration
public class RedisConfig {

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
		// Copy the app's ObjectMapper so modules registered by Spring Boot's auto-configuration
		// (notably JavaTimeModule) carry over - without it, any cached type with a java.time
		// field (e.g. Link.createdAt/expiresAt) fails to serialize on every write. Default
		// typing is then activated on this copy only, never on the shared web-facing mapper,
		// since GenericJackson2JsonRedisSerializer needs the embedded type info to deserialize
		// back into the original POJO type. EVERYTHING (not NON_FINAL) is required because
		// cached DTOs like AnalyticsResponse are records, which are implicitly final - NON_FINAL
		// would silently omit their type wrapper on write and break deserialization on read.
		ObjectMapper redisObjectMapper = objectMapper.copy();
		redisObjectMapper.activateDefaultTyping(
				BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
				ObjectMapper.DefaultTyping.EVERYTHING);
		GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(valueSerializer);
		template.setHashKeySerializer(new StringRedisSerializer());
		template.setHashValueSerializer(valueSerializer);
		template.afterPropertiesSet();
		return template;
	}
}
