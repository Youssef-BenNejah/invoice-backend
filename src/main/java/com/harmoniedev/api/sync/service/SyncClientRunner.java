package com.harmoniedev.api.sync.service;

import com.harmoniedev.api.common.dto.ApiResponse;
import com.harmoniedev.api.sync.domain.dto.SyncOutboxEntryDto;
import com.harmoniedev.api.sync.domain.dto.request.SyncPushRequest;
import com.harmoniedev.api.sync.domain.dto.response.SyncPullResponse;
import com.harmoniedev.api.sync.domain.model.SyncOutboxDocument;
import com.harmoniedev.api.sync.domain.model.SyncStateDocument;
import com.harmoniedev.api.sync.repository.SyncOutboxRepository;
import com.harmoniedev.api.sync.repository.SyncStateRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The desktop's background sync loop: push whatever changed locally, then pull whatever changed
 * on the cloud, on a timer. Each tick is its own attempt — when the cloud is unreachable it just
 * logs and waits for the next tick, which is what makes this "offline, catches up when back
 * online" rather than something that needs to be told the network state changed.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "sync.client", name = "enabled", havingValue = "true")
public class SyncClientRunner {
	private static final int PAGE_SIZE = 500;

	private final SyncOutboxRepository outboxRepository;
	private final SyncStateRepository stateRepository;
	private final SyncApplyService applyService;
	private final CloudSyncAuthClient authClient;
	private final RestClient syncApiRestClient;
	private final SyncClientStatus status;

	public SyncClientRunner(
			SyncOutboxRepository outboxRepository,
			SyncStateRepository stateRepository,
			SyncApplyService applyService,
			CloudSyncAuthClient authClient,
			@Qualifier("syncApiRestClient") RestClient syncApiRestClient,
			SyncClientStatus status) {
		this.outboxRepository = outboxRepository;
		this.stateRepository = stateRepository;
		this.applyService = applyService;
		this.authClient = authClient;
		this.syncApiRestClient = syncApiRestClient;
		this.status = status;
	}

	@Scheduled(fixedDelayString = "${sync.client.poll-interval-ms:30000}")
	public void syncOnce() {
		status.startSyncing();
		try {
			runCycle();
			status.recordSuccess();
		} catch (HttpClientErrorException.Unauthorized e) {
			log.info("Sync credentials rejected, re-authenticating and retrying once");
			authClient.invalidate();
			try {
				runCycle();
				status.recordSuccess();
			} catch (Exception retryFailure) {
				log.warn("Sync cycle failed even after re-authenticating", retryFailure);
				status.recordFailure(retryFailure.getMessage());
			}
		} catch (ResourceAccessException e) {
			log.info("Cloud unreachable this cycle, will retry next tick ({})", e.getMessage());
			status.recordFailure("Cloud unreachable: " + e.getMessage());
		} catch (Exception e) {
			log.warn("Sync cycle failed", e);
			status.recordFailure(e.getMessage());
		}
	}

	private void runCycle() {
		SyncStateDocument state = stateRepository.findById(SyncStateDocument.SINGLETON_ID)
				.orElseGet(() -> SyncStateDocument.builder().id(SyncStateDocument.SINGLETON_ID).build());
		push(state);
		pull(state);
	}

	private void push(SyncStateDocument state) {
		List<SyncOutboxDocument> pending = outboxRepository.findBySequenceGreaterThanOrderBySequenceAsc(
				state.getLastPushedSequence(), PageRequest.of(0, PAGE_SIZE));
		if (pending.isEmpty()) {
			return;
		}
		SyncPushRequest request = new SyncPushRequest(pending.stream().map(this::toDto).toList(), state.getLastPulledSequence());
		syncApiRestClient.post().uri("/api/v1/sync/push").body(request).retrieve().toBodilessEntity();
		state.setLastPushedSequence(pending.get(pending.size() - 1).getSequence());
		stateRepository.save(state);
		log.info("Pushed {} change(s) to the cloud", pending.size());
	}

	private void pull(SyncStateDocument state) {
		boolean more = true;
		int totalApplied = 0;
		while (more) {
			ApiResponse<SyncPullResponse> response = syncApiRestClient.get()
					.uri(uriBuilder -> uriBuilder.path("/api/v1/sync/pull")
							.queryParam("since", state.getLastPulledSequence())
							.build())
					.retrieve()
					.body(new ParameterizedTypeReference<>() {});
			SyncPullResponse pulled = response.getData();
			for (SyncOutboxEntryDto entry : pulled.getEntries()) {
				applyService.apply(entry);
			}
			state.setLastPulledSequence(pulled.getNextCursor());
			stateRepository.save(state);
			totalApplied += pulled.getEntries().size();
			more = pulled.isHasMore();
		}
		if (totalApplied > 0) {
			log.info("Pulled and applied {} change(s) from the cloud", totalApplied);
		}
	}

	private SyncOutboxEntryDto toDto(SyncOutboxDocument doc) {
		return SyncOutboxEntryDto.builder()
				.sequence(doc.getSequence())
				.collectionName(doc.getCollectionName())
				.documentId(doc.getDocumentId())
				.operation(doc.getOperation())
				.payloadJson(doc.getPayload() == null ? null : doc.getPayload().toJson())
				.build();
	}
}
