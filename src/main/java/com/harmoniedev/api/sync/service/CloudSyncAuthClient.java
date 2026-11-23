package com.harmoniedev.api.sync.service;

import com.harmoniedev.api.auth.domain.dto.response.AuthResponse;
import com.harmoniedev.api.common.dto.ApiResponse;
import com.harmoniedev.api.sync.config.SyncClientProperties;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

/**
 * Logs the desktop into the cloud API as a configured service account (sync.client.email/password
 * in the desktop's bundled .env), for the sync endpoints only — this has nothing to do with
 * whichever human is logged into the desktop UI itself, which always authenticates against the
 * local backend regardless of connectivity.
 *
 * <p>Deliberately re-logs-in rather than using the refresh-token cookie flow: that cookie is meant
 * for a browser session, and a plain re-login every time the cached access token is rejected is
 * simpler and perfectly adequate for a background job polling every few seconds to minutes.
 */
public class CloudSyncAuthClient {
	private final RestClient restClient;
	private final SyncClientProperties properties;
	private volatile String accessToken;

	public CloudSyncAuthClient(RestClient restClient, SyncClientProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	public String currentAccessToken() {
		String token = accessToken;
		if (token == null) {
			token = login();
		}
		return token;
	}

	public void invalidate() {
		accessToken = null;
	}

	private synchronized String login() {
		if (accessToken != null) {
			return accessToken;
		}
		Map<String, Object> body = Map.of(
				"email", properties.getEmail(),
				"password", properties.getPassword(),
				"rememberMe", false);
		ApiResponse<AuthResponse> response = restClient.post()
				.uri("/api/v1/auth/login")
				.body(body)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {});
		accessToken = response.getData().getAccessToken();
		return accessToken;
	}
}
