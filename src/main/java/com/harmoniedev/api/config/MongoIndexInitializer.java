package com.harmoniedev.api.config;

import com.harmoniedev.api.audit.domain.model.AuditLogDocument;
import com.harmoniedev.api.auth.domain.model.RefreshTokenDocument;
import com.harmoniedev.api.auth.domain.model.UserDocument;
import com.harmoniedev.api.sync.domain.model.SyncConflictDocument;
import com.harmoniedev.api.sync.domain.model.SyncOutboxDocument;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MongoIndexInitializer {
	private final MongoTemplate mongoTemplate;

	@EventListener(ApplicationReadyEvent.class)
	public void createIndexes() {
		IndexOperations userIndexes = mongoTemplate.indexOps(UserDocument.class);
		userIndexes.createIndex(new Index().on("email", Sort.Direction.ASC).unique().named("users_email_unique"));

		IndexOperations refreshIndexes = mongoTemplate.indexOps(RefreshTokenDocument.class);
		refreshIndexes.createIndex(new Index().on("tokenHash", Sort.Direction.ASC).unique().named("refresh_token_hash_unique"));
		refreshIndexes.createIndex(new Index().on("expiresAt", Sort.Direction.ASC)
				.expire(0L, TimeUnit.SECONDS).named("refresh_token_expires_ttl"));
		refreshIndexes.createIndex(new Index().on("userId", Sort.Direction.ASC).on("revoked", Sort.Direction.ASC)
				.named("refresh_token_user_revoked"));

		IndexOperations auditIndexes = mongoTemplate.indexOps(AuditLogDocument.class);
		auditIndexes.createIndex(new Index().on("createdAt", Sort.Direction.ASC)
				.expire(7776000L, TimeUnit.SECONDS).named("audit_logs_ttl"));
		auditIndexes.createIndex(new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.ASC)
				.named("audit_logs_user_created"));

		IndexOperations syncOutboxIndexes = mongoTemplate.indexOps(SyncOutboxDocument.class);
		syncOutboxIndexes.createIndex(new Index().on("sequence", Sort.Direction.ASC).unique().named("sync_outbox_sequence_unique"));
		syncOutboxIndexes.createIndex(new Index().on("ownerId", Sort.Direction.ASC).on("sequence", Sort.Direction.ASC)
				.named("sync_outbox_owner_sequence"));
		syncOutboxIndexes.createIndex(new Index().on("collectionName", Sort.Direction.ASC).on("documentId", Sort.Direction.ASC)
				.on("sequence", Sort.Direction.ASC).named("sync_outbox_collection_document_sequence"));

		IndexOperations syncConflictIndexes = mongoTemplate.indexOps(SyncConflictDocument.class);
		syncConflictIndexes.createIndex(new Index().on("acknowledged", Sort.Direction.ASC).on("created", Sort.Direction.DESC)
				.named("sync_conflicts_acknowledged_created"));
	}
}
