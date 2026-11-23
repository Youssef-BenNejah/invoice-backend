package com.harmoniedev.api.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harmoniedev.api.sync.domain.dto.SyncOutboxEntryDto;
import com.harmoniedev.api.sync.domain.dto.request.SyncPushRequest;
import com.harmoniedev.api.sync.domain.dto.response.SyncPullResponse;
import com.harmoniedev.api.sync.domain.model.SyncConflictDocument;
import com.harmoniedev.api.sync.domain.model.SyncOperation;
import com.harmoniedev.api.sync.domain.model.SyncOutboxDocument;
import com.harmoniedev.api.sync.repository.SyncConflictRepository;
import com.harmoniedev.api.sync.repository.SyncOutboxRepository;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {
	@Mock
	private SyncOutboxRepository outboxRepository;
	@Mock
	private SyncConflictRepository conflictRepository;
	@Mock
	private SyncSequenceService sequenceService;
	@Mock
	private SyncApplyService applyService;
	@Mock
	private InvoiceNumberReconciler invoiceNumberReconciler;

	private SyncService syncService;

	@BeforeEach
	void setUp() {
		syncService = new SyncService(outboxRepository, conflictRepository, sequenceService, applyService, invoiceNumberReconciler);
	}

	private SyncPushRequest pushOf(SyncOutboxEntryDto entry, long basedOnSequence) {
		return new SyncPushRequest(List.of(entry), basedOnSequence);
	}

	@Test
	void applyPush_appliesToTargetCollection_thenRecordsItUnderAFreshCloudSequence() {
		SyncOutboxEntryDto entry = SyncOutboxEntryDto.builder()
				.collectionName("invoices")
				.documentId("inv-1")
				.operation(SyncOperation.SAVE)
				.payloadJson(new Document("_id", "inv-1").append("createdBy", "user-1").toJson())
				.build();
		when(sequenceService.next()).thenReturn(42L);

		syncService.applyPush(pushOf(entry, 0));

		verify(applyService).apply(entry);
		ArgumentCaptor<SyncOutboxDocument> captor = ArgumentCaptor.forClass(SyncOutboxDocument.class);
		verify(outboxRepository).save(captor.capture());
		SyncOutboxDocument saved = captor.getValue();
		assertThat(saved.getSequence()).isEqualTo(42L);
		assertThat(saved.getCollectionName()).isEqualTo("invoices");
		assertThat(saved.getDocumentId()).isEqualTo("inv-1");
		assertThat(saved.getOwnerId()).isEqualTo("user-1");
	}

	@Test
	void applyPush_forAnInvoiceSave_runsNumberReconciliation_beforeApplying() {
		SyncOutboxEntryDto entry = SyncOutboxEntryDto.builder()
				.collectionName("invoices")
				.documentId("inv-1")
				.operation(SyncOperation.SAVE)
				.payloadJson(new Document("_id", "inv-1").append("type", "Standard").append("year", 2026).append("number", 7).toJson())
				.build();
		when(sequenceService.next()).thenReturn(1L);
		Mockito.doAnswer(invocation -> {
			Document doc = invocation.getArgument(0);
			doc.put("number", 8); // simulate a collision bumping the number
			return null;
		}).when(invoiceNumberReconciler).reconcile(any(Document.class));

		syncService.applyPush(pushOf(entry, 0));

		ArgumentCaptor<SyncOutboxEntryDto> appliedCaptor = ArgumentCaptor.forClass(SyncOutboxEntryDto.class);
		verify(applyService).apply(appliedCaptor.capture());
		Document appliedPayload = Document.parse(appliedCaptor.getValue().getPayloadJson());
		assertThat(appliedPayload.getInteger("number")).isEqualTo(8);
	}

	@Test
	void applyPush_forANonInvoiceCollection_skipsNumberReconciliation() {
		SyncOutboxEntryDto entry = SyncOutboxEntryDto.builder()
				.collectionName("stock_movements")
				.documentId("mv-1")
				.operation(SyncOperation.SAVE)
				.payloadJson(new Document("_id", "mv-1").toJson())
				.build();
		when(sequenceService.next()).thenReturn(1L);

		syncService.applyPush(pushOf(entry, 0));

		verify(invoiceNumberReconciler, never()).reconcile(any(Document.class));
	}

	@Test
	void applyPush_rejectingACollectionOutsideTheSyncAllowlist_neverTouchesTheOutbox() {
		SyncOutboxEntryDto entry = SyncOutboxEntryDto.builder()
				.collectionName("users")
				.documentId("user-1")
				.operation(SyncOperation.DELETE)
				.build();
		Mockito.doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "nope"))
				.when(applyService).apply(entry);

		SyncPushRequest request = pushOf(entry, 0);
		org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class, () -> syncService.applyPush(request));

		verify(outboxRepository, never()).save(any());
	}

	@Test
	void applyPush_withNoNewerChangeSinceThePushersCursor_recordsNoConflict() {
		SyncOutboxEntryDto entry = SyncOutboxEntryDto.builder()
				.collectionName("clients")
				.documentId("client-1")
				.operation(SyncOperation.SAVE)
				.payloadJson(new Document("_id", "client-1").toJson())
				.build();
		when(sequenceService.next()).thenReturn(5L);
		when(outboxRepository.existsByCollectionNameAndDocumentIdAndSequenceGreaterThan("clients", "client-1", 3L))
				.thenReturn(false);

		syncService.applyPush(pushOf(entry, 3L));

		verify(conflictRepository, never()).save(any());
	}

	@Test
	void applyPush_whenTheDocumentChangedOnTheCloudAfterThePushersCursor_recordsAConflict_butStillApplies() {
		SyncOutboxEntryDto entry = SyncOutboxEntryDto.builder()
				.collectionName("clients")
				.documentId("client-1")
				.operation(SyncOperation.SAVE)
				.payloadJson(new Document("_id", "client-1").append("name", "Offline edit").toJson())
				.build();
		when(sequenceService.next()).thenReturn(5L);
		when(outboxRepository.existsByCollectionNameAndDocumentIdAndSequenceGreaterThan("clients", "client-1", 3L))
				.thenReturn(true);
		Document currentCloudDoc = new Document("_id", "client-1").append("name", "Cloud edit");
		when(applyService.findCurrent("clients", "client-1")).thenReturn(currentCloudDoc);

		syncService.applyPush(pushOf(entry, 3L));

		verify(applyService).apply(any(SyncOutboxEntryDto.class)); // still applied — last-write-wins
		ArgumentCaptor<SyncConflictDocument> captor = ArgumentCaptor.forClass(SyncConflictDocument.class);
		verify(conflictRepository).save(captor.capture());
		SyncConflictDocument conflict = captor.getValue();
		assertThat(conflict.getCollectionName()).isEqualTo("clients");
		assertThat(conflict.getDocumentId()).isEqualTo("client-1");
		assertThat(conflict.getPreviousCloudPayload().getString("name")).isEqualTo("Cloud edit");
		assertThat(conflict.getIncomingPayload().getString("name")).isEqualTo("Offline edit");
	}

	@Test
	void pull_returnsEntriesAfterTheCursor_withNextCursorAtTheLastSequenceReturned() {
		SyncOutboxDocument doc = SyncOutboxDocument.builder()
				.sequence(7L)
				.collectionName("stock_movements")
				.documentId("mv-1")
				.operation(SyncOperation.SAVE)
				.payload(new Document("_id", "mv-1").append("quantityDelta", 5.0))
				.build();
		when(outboxRepository.findBySequenceGreaterThanOrderBySequenceAsc(any(Long.class), any(Pageable.class)))
				.thenReturn(List.of(doc));

		SyncPullResponse response = syncService.pull(3L);

		assertThat(response.getEntries()).hasSize(1);
		assertThat(response.getEntries().get(0).getDocumentId()).isEqualTo("mv-1");
		assertThat(response.getEntries().get(0).getPayloadJson()).contains("mv-1");
		assertThat(response.getNextCursor()).isEqualTo(7L);
		assertThat(response.isHasMore()).isFalse();
	}

	@Test
	void pull_withNothingNew_echoesTheRequestedCursorBack() {
		when(outboxRepository.findBySequenceGreaterThanOrderBySequenceAsc(any(Long.class), any(Pageable.class)))
				.thenReturn(List.of());

		SyncPullResponse response = syncService.pull(99L);

		assertThat(response.getEntries()).isEmpty();
		assertThat(response.getNextCursor()).isEqualTo(99L);
	}
}
