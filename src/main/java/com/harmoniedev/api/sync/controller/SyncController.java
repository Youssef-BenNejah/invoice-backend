package com.harmoniedev.api.sync.controller;

import com.harmoniedev.api.common.dto.ApiResponse;
import com.harmoniedev.api.sync.config.SyncClientProperties;
import com.harmoniedev.api.sync.domain.dto.request.SyncPushRequest;
import com.harmoniedev.api.sync.domain.dto.response.SyncConflictResponse;
import com.harmoniedev.api.sync.domain.dto.response.SyncPullResponse;
import com.harmoniedev.api.sync.domain.dto.response.SyncPushResponse;
import com.harmoniedev.api.sync.domain.dto.response.SyncStatusResponse;
import com.harmoniedev.api.sync.service.SyncClientStatus;
import com.harmoniedev.api.sync.service.SyncService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cloud side of desktop sync. All endpoints require authentication (standard
 * {@code anyRequest().authenticated()} in SecurityConfig) but aren't scoped per caller — this app
 * is single-tenant per deployment, so any authenticated user of this business syncs all of it,
 * same as the rest of the API (e.g. {@code GET /invoices} isn't filtered by caller either).
 *
 * <p>This same controller runs on both the cloud deployment and the desktop's local backend — see
 * {@link SyncStatusResponse} and {@link SyncClientStatus} for how {@code /status} tells them apart.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {
	private final SyncService syncService;
	private final SyncClientStatus clientStatus;
	private final SyncClientProperties clientProperties;

	public SyncController(SyncService syncService, SyncClientStatus clientStatus, SyncClientProperties clientProperties) {
		this.syncService = syncService;
		this.clientStatus = clientStatus;
		this.clientProperties = clientProperties;
	}

	@PostMapping("/push")
	public ResponseEntity<ApiResponse<SyncPushResponse>> push(@Valid @RequestBody SyncPushRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Synced", syncService.applyPush(request)));
	}

	@GetMapping("/pull")
	public ResponseEntity<ApiResponse<SyncPullResponse>> pull(@RequestParam(defaultValue = "0") long since) {
		return ResponseEntity.ok(ApiResponse.success("Pulled", syncService.pull(since)));
	}

	/** Always meaningful: online/offline/syncing reflect this instance's connectivity either way — see SyncStatusResponse. */
	@GetMapping("/status")
	public ResponseEntity<ApiResponse<SyncStatusResponse>> status() {
		SyncStatusResponse response = SyncStatusResponse.builder()
				.cloudConfigured(clientProperties.isEnabled())
				.online(clientStatus.isOnline())
				.syncing(clientStatus.isSyncing())
				.lastSuccessAt(clientStatus.getLastSuccessAt())
				.lastAttemptAt(clientStatus.getLastAttemptAt())
				.lastErrorMessage(clientStatus.getLastErrorMessage())
				.openConflictCount(syncService.openConflictCount())
				.build();
		return ResponseEntity.ok(ApiResponse.success("Sync status", response));
	}

	/** Conflicts only ever appear on the instance that received the colliding push — see SyncService. */
	@GetMapping("/conflicts")
	public ResponseEntity<ApiResponse<List<SyncConflictResponse>>> conflicts() {
		return ResponseEntity.ok(ApiResponse.success("Sync conflicts", syncService.listOpenConflicts()));
	}

	@PostMapping("/conflicts/{id}/acknowledge")
	public ResponseEntity<ApiResponse<Void>> acknowledgeConflict(@PathVariable String id) {
		syncService.acknowledgeConflict(id);
		return ResponseEntity.ok(ApiResponse.success("Conflict acknowledged", null));
	}
}
