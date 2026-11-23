package com.harmoniedev.api.sync.domain.dto.response;

import com.harmoniedev.api.sync.domain.dto.SyncOutboxEntryDto;
import java.util.List;
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
public class SyncPullResponse {
	private List<SyncOutboxEntryDto> entries;

	/** Pass this back as {@code since} on the next pull. Unchanged from the request when entries is empty. */
	private long nextCursor;

	/** True if {@code entries} hit the page size cap — call pull again immediately with {@code nextCursor}. */
	private boolean hasMore;
}
