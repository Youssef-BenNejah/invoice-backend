package com.harmoniedev.api.sync.service;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Hands out the outbox's sequence numbers. A plain counter document with findAndModify, not an
 * {@code Instant}, because two writes in the same millisecond must still get a strict, gap-free
 * order for pull cursors to page through reliably.
 */
@Service
public class SyncSequenceService {
	private static final String COUNTER_COLLECTION = "sync_counters";
	private static final String OUTBOX_COUNTER_ID = "outbox";

	private final MongoOperations mongoOperations;

	public SyncSequenceService(MongoOperations mongoOperations) {
		this.mongoOperations = mongoOperations;
	}

	public long next() {
		Query query = Query.query(Criteria.where("_id").is(OUTBOX_COUNTER_ID));
		// $inc with a bare int literal makes Mongo store/return the field as Int32 on a fresh
		// upsert — Document#getLong() then throws on it (strict Long-only cast). Number#longValue()
		// works for either width, so this doesn't care which one Mongo actually gave back.
		Update update = new Update().inc("value", 1L);
		FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);
		Document result = mongoOperations.findAndModify(query, update, options, Document.class, COUNTER_COLLECTION);
		return ((Number) result.get("value")).longValue();
	}
}
