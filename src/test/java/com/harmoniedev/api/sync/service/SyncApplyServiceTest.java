package com.harmoniedev.api.sync.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.harmoniedev.api.sync.domain.dto.SyncOutboxEntryDto;
import com.harmoniedev.api.sync.domain.model.SyncOperation;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SyncApplyServiceTest {
	@Mock
	private MongoTemplate mongoTemplate;

	private SyncApplyService applyService;

	@BeforeEach
	void setUp() {
		applyService = new SyncApplyService(mongoTemplate);
	}

	@Test
	void apply_toAnExcludedCollection_isRejected_withoutTouchingMongo() {
		SyncOutboxEntryDto entry = SyncOutboxEntryDto.builder()
				.collectionName("users")
				.documentId("user-1")
				.operation(SyncOperation.DELETE)
				.build();

		assertThrows(ResponseStatusException.class, () -> applyService.apply(entry));

		verify(mongoTemplate, never()).getCollection(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void apply_saveWithNoPayload_isRejected() {
		SyncOutboxEntryDto entry = SyncOutboxEntryDto.builder()
				.collectionName("invoices")
				.documentId("inv-1")
				.operation(SyncOperation.SAVE)
				.payloadJson(null)
				.build();
		org.mockito.Mockito.when(mongoTemplate.getCollection("invoices"))
				.thenReturn(org.mockito.Mockito.mock(com.mongodb.client.MongoCollection.class));

		assertThrows(ResponseStatusException.class, () -> applyService.apply(entry));
	}

	@Test
	void apply_delete_removesByIdOnTheTargetCollection() {
		com.mongodb.client.MongoCollection<Document> collection = org.mockito.Mockito.mock(com.mongodb.client.MongoCollection.class);
		org.mockito.Mockito.when(mongoTemplate.getCollection("invoices")).thenReturn(collection);
		SyncOutboxEntryDto entry = SyncOutboxEntryDto.builder()
				.collectionName("invoices")
				.documentId("inv-1")
				.operation(SyncOperation.DELETE)
				.build();

		applyService.apply(entry);

		verify(collection).deleteOne(org.mockito.ArgumentMatchers.any(org.bson.conversions.Bson.class));
	}
}
