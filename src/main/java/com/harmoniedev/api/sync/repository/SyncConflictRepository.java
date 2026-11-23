package com.harmoniedev.api.sync.repository;

import com.harmoniedev.api.sync.domain.model.SyncConflictDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SyncConflictRepository extends MongoRepository<SyncConflictDocument, String> {
	List<SyncConflictDocument> findByAcknowledgedFalseOrderByCreatedDesc();
}
