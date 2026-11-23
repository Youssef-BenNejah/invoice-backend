package com.harmoniedev.api.sync.repository;

import com.harmoniedev.api.sync.domain.model.SyncOutboxDocument;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SyncOutboxRepository extends MongoRepository<SyncOutboxDocument, String> {
	List<SyncOutboxDocument> findBySequenceGreaterThanOrderBySequenceAsc(long sequence, Pageable pageable);

	List<SyncOutboxDocument> findByOwnerIdAndSequenceGreaterThanOrderBySequenceAsc(String ownerId, long sequence, Pageable pageable);

	/** Used to detect a push based on stale data: has this document changed since the pusher's cursor? */
	boolean existsByCollectionNameAndDocumentIdAndSequenceGreaterThan(String collectionName, String documentId, long sequence);
}
