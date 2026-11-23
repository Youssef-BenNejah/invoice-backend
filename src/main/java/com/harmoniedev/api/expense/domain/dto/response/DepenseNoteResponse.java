package com.harmoniedev.api.expense.domain.dto.response;

import com.harmoniedev.api.expense.domain.enums.NoteStatus;
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
public class DepenseNoteResponse {
	private String id;
	private String fournisseurNom;
	private String telephone;
	private double montant;
	private Instant date;
	private NoteStatus status;
	private String description;
	private String createdBy;
	private Instant created;
}
