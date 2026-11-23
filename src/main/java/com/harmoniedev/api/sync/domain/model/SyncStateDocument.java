package com.harmoniedev.api.sync.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Tracks this desktop's sync progress against the cloud. A single document (id fixed to
 * {@link #SINGLETON_ID}) — one desktop installation, one cursor pair.
 */
@Document(collection = "sync_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncStateDocument {
	public static final String SINGLETON_ID = "desktop";

	@Id
	private String id;

	/** Highest local sync_outbox sequence already pushed to the cloud. */
	private long lastPushedSequence;

	/** Highest cloud sync_outbox sequence already pulled and applied locally. */
	private long lastPulledSequence;

	@LastModifiedDate
	private Instant updatedAt;
}
