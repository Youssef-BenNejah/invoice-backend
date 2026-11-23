package com.harmoniedev.api.sync.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Only set on the desktop build's bundled .env — on the cloud deployment {@code enabled} stays
 * false and none of the sync-client beans are created at all.
 */
@ConfigurationProperties(prefix = "sync.client")
@Getter
@Setter
public class SyncClientProperties {
	private boolean enabled = false;
	private String cloudBaseUrl;
	private String email;
	private String password;
	private long pollIntervalMs = 30000;
	private int connectTimeoutMs = 5000;
	private int readTimeoutMs = 15000;
}
