package com.harmoniedev.api.sync.config;

import com.harmoniedev.api.sync.service.CloudSyncAuthClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wiring for the desktop's background sync client — only present when {@code sync.client.enabled}
 * is true (set in the desktop build's bundled .env; left false, and absent, on the cloud
 * deployment). Two RestClients on purpose: {@code syncApiRestClient} attaches a bearer token via
 * {@link CloudSyncAuthClient}, which itself needs a plain, un-intercepted client to log in with —
 * wiring the auth client's own login call through the token-attaching client would recurse forever.
 */
@Configuration
@ConditionalOnProperty(prefix = "sync.client", name = "enabled", havingValue = "true")
public class SyncClientConfig {
	@Bean
	public ClientHttpRequestFactory syncClientHttpRequestFactory(SyncClientProperties properties) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(properties.getConnectTimeoutMs());
		factory.setReadTimeout(properties.getReadTimeoutMs());
		return factory;
	}

	@Bean
	public RestClient syncAuthRestClient(SyncClientProperties properties, ClientHttpRequestFactory syncClientHttpRequestFactory) {
		return RestClient.builder()
				.baseUrl(properties.getCloudBaseUrl())
				.requestFactory(syncClientHttpRequestFactory)
				.build();
	}

	@Bean
	public CloudSyncAuthClient cloudSyncAuthClient(RestClient syncAuthRestClient, SyncClientProperties properties) {
		return new CloudSyncAuthClient(syncAuthRestClient, properties);
	}

	@Bean
	public RestClient syncApiRestClient(
			SyncClientProperties properties, ClientHttpRequestFactory syncClientHttpRequestFactory, CloudSyncAuthClient authClient) {
		return RestClient.builder()
				.baseUrl(properties.getCloudBaseUrl())
				.requestFactory(syncClientHttpRequestFactory)
				.requestInterceptor((request, body, execution) -> {
					request.getHeaders().setBearerAuth(authClient.currentAccessToken());
					return execution.execute(request, body);
				})
				.build();
	}
}
