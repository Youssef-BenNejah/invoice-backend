package com.harmoniedev.api.sync.service;

import com.harmoniedev.api.sync.SyncCollections;
import com.harmoniedev.api.sync.domain.model.SyncOperation;
import com.harmoniedev.api.sync.domain.model.SyncOutboxDocument;
import com.harmoniedev.api.sync.repository.SyncOutboxRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

/**
 * Appends an outbox entry for every save/delete against a syncable collection, generically —
 * no domain entity has to opt in or carry sync bookkeeping fields. This is what Phase 1's
 * /sync/push reads from.
 *
 * <p>Collections deliberately left out of sync: {@code users} and {@code refresh_tokens} (auth
 * state isn't synced as data — the desktop caches its own JWT instead, see Phase 2), and the
 * sync module's own bookkeeping collections (to avoid capturing the act of capturing).
 */
@Component
public class SyncOutboxEventListener extends AbstractMongoEventListener<Object> {
	private final SyncOutboxRepository outboxRepository;
	private final SyncSequenceService sequenceService;

	public SyncOutboxEventListener(SyncOutboxRepository outboxRepository, SyncSequenceService sequenceService) {
		this.outboxRepository = outboxRepository;
		this.sequenceService = sequenceService;
	}

	@Override
	public void onAfterSave(AfterSaveEvent<Object> event) {
		String collectionName = event.getCollectionName();
		if (!SyncCollections.isSyncable(collectionName)) {
			return;
		}
		Document document = event.getDocument();
		if (document == null || document.get("_id") == null) {
			return;
		}
		String documentId = document.get("_id").toString();
		// Stringify _id in the captured payload too — the raw field can be a BSON ObjectId (Spring
		// Data's auto-conversion for 24-hex-looking @Id strings), which doesn't survive the
		// extended-JSON round trip to another device as cleanly as a plain string id does.
		Document payload = new Document(document);
		payload.put("_id", documentId);
		outboxRepository.save(SyncOutboxDocument.builder()
				.sequence(sequenceService.next())
				.collectionName(collectionName)
				.documentId(documentId)
				.operation(SyncOperation.SAVE)
				.ownerId(document.getString("createdBy"))
				.payload(payload)
				.build());
	}

	@Override
	public void onAfterDelete(AfterDeleteEvent<Object> event) {
		String collectionName = event.getCollectionName();
		if (!SyncCollections.isSyncable(collectionName)) {
			return;
		}
		Document query = event.getDocument();
		Object idValue = query == null ? null : query.get("_id");
		if (idValue == null) {
			// Not a simple deleteById (e.g. a bulk/criteria delete) — nothing concrete to tombstone.
			// These are rare (see each module's repository); revisit if one shows up in practice.
			return;
		}
		outboxRepository.save(SyncOutboxDocument.builder()
				.sequence(sequenceService.next())
				.collectionName(collectionName)
				.documentId(idValue.toString())
				.operation(SyncOperation.DELETE)
				.build());
	}
}
