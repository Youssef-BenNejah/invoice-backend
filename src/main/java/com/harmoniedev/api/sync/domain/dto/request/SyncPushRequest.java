package com.harmoniedev.api.sync.domain.dto.request;

import com.harmoniedev.api.sync.domain.dto.SyncOutboxEntryDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncPushRequest {
	@NotEmpty
	@Valid
	private List<SyncOutboxEntryDto> entries;

	/**
	 * The pusher's own {@code lastPulledSequence} at the time these entries were written locally —
	 * i.e. "this is what I knew about the cloud when I made these changes". Used purely to flag a
	 * push that turns out to have been based on stale data (see {@link com.harmoniedev.api.sync.domain.model.SyncConflictDocument}); it
	 * doesn't block anything.
	 */
	private long basedOnSequence;
}
