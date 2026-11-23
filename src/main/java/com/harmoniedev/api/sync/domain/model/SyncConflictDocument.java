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
 * Recorded when an offline push overwrites a document that changed on the cloud after this
 * device's last pull — i.e. the push was based on stale data. The write is still applied
 * (last-write-wins, same as any other non-ledger entity), this is purely a surfaced-for-review
 * record so a human can check nothing important was silently clobbered.
 */
@Document(collection = "sync_conflicts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncConflictDocument {
	@Id
	private String id;

	private String collectionName;
	private String documentId;

	/** The cloud document as it stood just before the push overwrote it. */
	private org.bson.Document previousCloudPayload;

	/** What the push replaced it with. */
	private org.bson.Document incomingPayload;

	@Builder.Default
	private boolean acknowledged = false;

	@CreatedDate
	private Instant created;
}
