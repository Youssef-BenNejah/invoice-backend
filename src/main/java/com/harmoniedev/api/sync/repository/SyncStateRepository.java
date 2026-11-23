package com.harmoniedev.api.sync.repository;

import com.harmoniedev.api.sync.domain.model.SyncStateDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SyncStateRepository extends MongoRepository<SyncStateDocument, String> {}
