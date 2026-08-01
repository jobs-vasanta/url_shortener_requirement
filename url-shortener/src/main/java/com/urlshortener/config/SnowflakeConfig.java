package com.urlshortener.config;

import com.urlshortener.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the per-instance Snowflake node ID from {@code app.snowflake.node-id}
 * (env var {@code SNOWFLAKE_NODE_ID}). Every running instance MUST have a distinct
 * value (0-1023) or two instances can mint colliding IDs in the same millisecond.
 * In Kubernetes, derive this from the pod's StatefulSet ordinal or a Downward API
 * field; a single fixed value is fine for local/dev with one instance.
 */
@Configuration
public class SnowflakeConfig {

	@Bean
	public SnowflakeIdGenerator snowflakeIdGenerator(@Value("${app.snowflake.node-id:0}") long nodeId) {
		return new SnowflakeIdGenerator(nodeId);
	}
}
