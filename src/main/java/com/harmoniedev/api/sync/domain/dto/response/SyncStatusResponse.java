package com.harmoniedev.api.sync.domain.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Always meaningful, on every deployment: {@code online}/{@code syncing} reflect either a plain
 * internet-reachability check (before a cloud is configured) or the last real push/pull attempt
 * (once it is) — see {@link com.harmoniedev.api.sync.service.ConnectivityMonitor} and
 * {@link com.harmoniedev.api.sync.service.SyncClientRunner}. {@code cloudConfigured} distinguishes
 * the two for UI copy only; the frontend doesn't need to hide anything based on it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncStatusResponse {
	private boolean cloudConfigured;
	private boolean online;
	private boolean syncing;
	private Instant lastSuccessAt;
	private Instant lastAttemptAt;
	private String lastErrorMessage;
	private int openConflictCount;
}
