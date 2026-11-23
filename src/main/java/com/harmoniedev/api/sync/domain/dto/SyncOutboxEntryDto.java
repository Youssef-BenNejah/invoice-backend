package com.harmoniedev.api.sync.domain.dto;

import com.harmoniedev.api.sync.domain.model.SyncOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One change on the wire. {@code payloadJson} is MongoDB extended JSON (from
 * {@link org.bson.Document#toJson()}), not plain JSON — that's what lets dates, and any BSON type
 * a future entity introduces, round-trip exactly instead of degrading to numbers/strings. Null for
 * DELETE, where only the id matters.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncOutboxEntryDto {
	private Long sequence;

	@NotBlank
	private String collectionName;

	@NotBlank
	private String documentId;

	@NotNull
	private SyncOperation operation;

	private String payloadJson;
}
