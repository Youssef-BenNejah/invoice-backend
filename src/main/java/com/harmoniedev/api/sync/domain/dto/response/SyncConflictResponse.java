package com.harmoniedev.api.sync.domain.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncConflictResponse {
	private String id;
	private String collectionName;
	private String documentId;
	private Object previousCloudPayload;
	private Object incomingPayload;
	private Instant created;
}
