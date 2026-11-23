package com.harmoniedev.api.expense.domain.dto.request;

import com.harmoniedev.api.expense.domain.enums.NoteStatus;
import jakarta.validation.constraints.NotNull;
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
public class DepenseNoteStatusRequest {
	@NotNull
	private NoteStatus status;
}
