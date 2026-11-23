package com.harmoniedev.api.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Regression coverage for a real bug: on a fresh upsert, Mongo's $inc stores (and findAndModify
 * returns) the counter as a plain Int32, not Int64 — a mock that stubs the real field type this
 * class actually gets back is what would have caught the {@code Document#getLong} ClassCastException
 * that a Mockito-mocked {@code Document} with a hand-built Long never exercises.
 */
@ExtendWith(MockitoExtension.class)
class SyncSequenceServiceTest {
	@Mock
	private MongoOperations mongoOperations;

	@Test
	void next_whenMongoReturnsTheCounterAsInt32_stillReturnsItCorrectly() {
		Document result = new Document("_id", "outbox").append("value", 1); // Integer, as a fresh upsert really returns
		when(mongoOperations.findAndModify(
				any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Document.class), anyString()))
				.thenReturn(result);

		long sequence = new SyncSequenceService(mongoOperations).next();

		assertThat(sequence).isEqualTo(1L);
	}

	@Test
	void next_whenMongoReturnsTheCounterAsInt64_stillReturnsItCorrectly() {
		Document result = new Document("_id", "outbox").append("value", 42L);
		when(mongoOperations.findAndModify(
				any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Document.class), anyString()))
				.thenReturn(result);

		long sequence = new SyncSequenceService(mongoOperations).next();

		assertThat(sequence).isEqualTo(42L);
	}
}
