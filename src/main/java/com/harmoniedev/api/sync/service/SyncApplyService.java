package com.harmoniedev.api.sync.service;

import com.harmoniedev.api.sync.SyncCollections;
import com.harmoniedev.api.sync.domain.dto.SyncOutboxEntryDto;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Writes an incoming sync entry straight to the target collection via the low-level driver
 * ({@link MongoTemplate#getCollection}), deliberately bypassing Spring Data's save/delete events.
 * That's what stops {@link SyncOutboxEventListener} from re-capturing a change sync itself is
 * propagating — applying a pull on the desktop must not re-queue that same change for push, and
 * applying a push on the cloud must not double-record it in the cloud's own outbox (the caller
 * does that once, explicitly, with its own cloud-assigned sequence).
 */
@Service
public class SyncApplyService {
	private final MongoTemplate mongoTemplate;

	public SyncApplyService(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public void apply(SyncOutboxEntryDto entry) {
		if (!SyncCollections.isSyncable(entry.getCollectionName())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Collection is not syncable: " + entry.getCollectionName());
		}
		MongoCollection<Document> collection = mongoTemplate.getCollection(entry.getCollectionName());
		switch (entry.getOperation()) {
			case DELETE -> collection.deleteOne(Filters.eq("_id", entry.getDocumentId()));
			case SAVE -> {
				if (entry.getPayloadJson() == null) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAVE entry missing payloadJson");
				}
				Document document = Document.parse(entry.getPayloadJson());
				document.put("_id", entry.getDocumentId());
				collection.replaceOne(Filters.eq("_id", entry.getDocumentId()), document, new ReplaceOptions().upsert(true));
			}
		}
	}

	/** The document as it currently stands in the target collection, or null if it doesn't exist yet. */
	public Document findCurrent(String collectionName, String documentId) {
		return mongoTemplate.getCollection(collectionName).find(Filters.eq("_id", documentId)).first();
	}
}
