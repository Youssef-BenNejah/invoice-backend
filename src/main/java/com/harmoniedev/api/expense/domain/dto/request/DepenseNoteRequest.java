package com.harmoniedev.api.expense.domain.dto.request;

import com.harmoniedev.api.expense.domain.enums.NoteStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Used for both create and update. All fields are optional. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepenseNoteRequest {
	private String fournisseurNom;

	private String telephone;

	private double montant;

	private Instant date;

	private NoteStatus status;

	private String description;
}
