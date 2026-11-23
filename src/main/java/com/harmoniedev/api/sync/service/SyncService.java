package com.harmoniedev.api.sync.service;

import com.harmoniedev.api.sync.domain.dto.SyncOutboxEntryDto;
import com.harmoniedev.api.sync.domain.dto.request.SyncPushRequest;
import com.harmoniedev.api.sync.domain.dto.response.SyncConflictResponse;
import com.harmoniedev.api.sync.domain.dto.response.SyncPullResponse;
import com.harmoniedev.api.sync.domain.dto.response.SyncPushResponse;
import com.harmoniedev.api.sync.domain.model.SyncConflictDocument;
import com.harmoniedev.api.sync.domain.model.SyncOperation;
import com.harmoniedev.api.sync.domain.model.SyncOutboxDocument;
import com.harmoniedev.api.sync.repository.SyncConflictRepository;
import com.harmoniedev.api.sync.repository.SyncOutboxRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class SyncService {
	private static final int PULL_PAGE_SIZE = 500;

	private static final String INVOICES_COLLECTION = "invoices";

	private final SyncOutboxRepository outboxRepository;
	private final SyncConflictRepository conflictRepository;
	private final SyncSequenceService sequenceService;
	private final SyncApplyService applyService;
	private final InvoiceNumberReconciler invoiceNumberReconciler;

	public SyncService(
			SyncOutboxRepository outboxRepository,
			SyncConflictRepository conflictRepository,
			SyncSequenceService sequenceService,
			SyncApplyService applyService,
			InvoiceNumberReconciler invoiceNumberReconciler) {
		this.outboxRepository = outboxRepository;
		this.conflictRepository = conflictRepository;
		this.sequenceService = sequenceService;
		this.applyService = applyService;
		this.invoiceNumberReconciler = invoiceNumberReconciler;
	}

	public SyncPushResponse applyPush(SyncPushRequest request) {
		for (SyncOutboxEntryDto entry : request.getEntries()) {
			applyPushedEntry(entry, request.getBasedOnSequence());
		}
		return SyncPushResponse.builder().appliedCount(request.getEntries().size()).build();
	}

	/**
	 * Applies one pushed entry to the real collection, then records it in the cloud's own outbox
	 * under a fresh cloud sequence — that's the entry another device's pull will later see. The
	 * two writes aren't currently transactional; see the module notes on Atlas replica-set
	 * transactions before this carries real traffic.
	 */
	private void applyPushedEntry(SyncOutboxEntryDto entry, long basedOnSequence) {
		if (INVOICES_COLLECTION.equals(entry.getCollectionName())
				&& entry.getOperation() == SyncOperation.SAVE
				&& entry.getPayloadJson() != null) {
			Document incoming = Document.parse(entry.getPayloadJson());
			invoiceNumberReconciler.reconcile(incoming);
			entry.setPayloadJson(incoming.toJson());
		}
		recordConflictIfStale(entry, basedOnSequence);
		applyService.apply(entry);
		Document payload = entry.getPayloadJson() == null ? null : Document.parse(entry.getPayloadJson());
		outboxRepository.save(SyncOutboxDocument.builder()
				.sequence(sequenceService.next())
				.collectionName(entry.getCollectionName())
				.documentId(entry.getDocumentId())
				.operation(entry.getOperation())
				.ownerId(payload == null ? null : payload.getString("createdBy"))
				.payload(payload)
				.build());
	}

	/**
	 * A push is "stale" when the cloud's outbox already has a later change to the same document
	 * than the cursor the pusher had seen — meaning this write (still applied, last-write-wins)
	 * may silently clobber an edit the pusher never saw. Recorded for human review, not blocked.
	 */
	private void recordConflictIfStale(SyncOutboxEntryDto entry, long basedOnSequence) {
		boolean staleBase = outboxRepository.existsByCollectionNameAndDocumentIdAndSequenceGreaterThan(
				entry.getCollectionName(), entry.getDocumentId(), basedOnSequence);
		if (!staleBase) {
			return;
		}
		Document previous = applyService.findCurrent(entry.getCollectionName(), entry.getDocumentId());
		log.warn("Sync conflict: {} {} changed on the cloud after the pusher's last known state",
				entry.getCollectionName(), entry.getDocumentId());
		conflictRepository.save(SyncConflictDocument.builder()
				.collectionName(entry.getCollectionName())
				.documentId(entry.getDocumentId())
				.previousCloudPayload(previous)
				.incomingPayload(entry.getPayloadJson() == null ? null : Document.parse(entry.getPayloadJson()))
				.build());
	}

	public int openConflictCount() {
		return conflictRepository.findByAcknowledgedFalseOrderByCreatedDesc().size();
	}

	public List<SyncConflictResponse> listOpenConflicts() {
		return conflictRepository.findByAcknowledgedFalseOrderByCreatedDesc().stream()
				.map(this::toConflictResponse)
				.toList();
	}

	public void acknowledgeConflict(String id) {
		SyncConflictDocument conflict = conflictRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conflict not found"));
		conflict.setAcknowledged(true);
		conflictRepository.save(conflict);
	}

	private SyncConflictResponse toConflictResponse(SyncConflictDocument doc) {
		return SyncConflictResponse.builder()
				.id(doc.getId())
				.collectionName(doc.getCollectionName())
				.documentId(doc.getDocumentId())
				.previousCloudPayload(doc.getPreviousCloudPayload())
				.incomingPayload(doc.getIncomingPayload())
				.created(doc.getCreated())
				.build();
	}

	public SyncPullResponse pull(long since) {
		List<SyncOutboxDocument> page = outboxRepository.findBySequenceGreaterThanOrderBySequenceAsc(
				since, PageRequest.of(0, PULL_PAGE_SIZE));

		List<SyncOutboxEntryDto> entries = page.stream().map(this::toDto).toList();
		long nextCursor = entries.isEmpty() ? since : page.get(page.size() - 1).getSequence();

		return SyncPullResponse.builder()
				.entries(entries)
				.nextCursor(nextCursor)
				.hasMore(page.size() == PULL_PAGE_SIZE)
				.build();
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
