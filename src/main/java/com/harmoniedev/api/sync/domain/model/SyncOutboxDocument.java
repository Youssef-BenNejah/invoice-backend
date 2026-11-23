package com.harmoniedev.api.sync.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One captured change to a syncable collection, in the order it happened. {@code sequence} is the
 * authoritative cursor other devices/the cloud page through — never {@code created}, which isn't
 * guaranteed unique or monotonic across concurrent writers.
 */
@Document(collection = "sync_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncOutboxDocument {
	@Id
	private String id;

	private long sequence;

	private String collectionName;
	private String documentId;
	private SyncOperation operation;

	/** The {@code createdBy} owner of the changed document, when it has one — used to scope pull/push per account. */
	private String ownerId;

	/** The full document as written, for SAVE. Null for DELETE — the receiver just removes {@code documentId}. */
	private org.bson.Document payload;

	@CreatedDate
	private Instant created;
}
