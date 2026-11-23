package com.harmoniedev.api.sync.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps {@link SyncClientStatus} showing a real online/offline state before any cloud backend is
 * configured to sync against — the user still wants to see "online"/"offline" on the desktop app
 * from day one, not just once a cloud deployment and sync credentials exist. Once
 * {@code sync.client.enabled} is true, {@link SyncClientRunner} takes over this same status
 * object with a more meaningful signal (did the last push/pull actually succeed), so this backs
 * off — {@code matchIfMissing = true} means this is the default until sync is configured.
 */
@Component
@ConditionalOnProperty(prefix = "sync.client", name = "enabled", havingValue = "false", matchIfMissing = true)
public class ConnectivityMonitor {
	/** Cloudflare's public resolver — picked only as a stable, low-overhead TCP reachability probe; no data is sent. */
	private static final String PROBE_HOST = "1.1.1.1";
	private static final int PROBE_PORT = 443;
	private static final int TIMEOUT_MS = 3000;

	private final SyncClientStatus status;

	public ConnectivityMonitor(SyncClientStatus status) {
		this.status = status;
	}

	@Scheduled(fixedDelay = 15000)
	public void checkOnce() {
		if (isReachable()) {
			status.recordSuccess();
		} else {
			status.recordFailure("No internet connection detected");
		}
	}

	private boolean isReachable() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(PROBE_HOST, PROBE_PORT), TIMEOUT_MS);
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
