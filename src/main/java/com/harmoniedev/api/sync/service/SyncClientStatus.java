package com.harmoniedev.api.sync.service;

import java.time.Instant;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * This instance's own connectivity state — always present, regardless of whether real cloud sync
 * is configured ({@code sync.client.enabled}). Before a cloud is deployed, {@link ConnectivityMonitor}
 * keeps this updated with a plain internet-reachability check; once a cloud is configured,
 * {@link SyncClientRunner} takes over and {@code online} instead reflects whether the last actual
 * push/pull succeeded, which is the more meaningful signal at that point.
 */
@Component
@Getter
public class SyncClientStatus {
	private volatile boolean online;
	private volatile boolean syncing;
	private volatile Instant lastSuccessAt;
	private volatile Instant lastAttemptAt;
	private volatile String lastErrorMessage;

	public void startSyncing() {
		syncing = true;
	}

	public void recordSuccess() {
		online = true;
		syncing = false;
		lastAttemptAt = Instant.now();
		lastSuccessAt = lastAttemptAt;
		lastErrorMessage = null;
	}

	public void recordFailure(String message) {
		online = false;
		syncing = false;
		lastAttemptAt = Instant.now();
		lastErrorMessage = message;
	}
}
