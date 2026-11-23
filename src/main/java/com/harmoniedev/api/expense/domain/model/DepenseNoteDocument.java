package com.harmoniedev.api.expense.domain.model;

import com.harmoniedev.api.expense.domain.enums.NoteStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** A manually-tracked unpaid-to-supplier note, shown on the Dépenses page. */
@Document(collection = "depense_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepenseNoteDocument {
	@Id
	private String id;
	private String fournisseurNom;
	private String telephone;
	private double montant;
	private Instant date;
	@Default
	private NoteStatus status = NoteStatus.NON_PAYE;
	private String description;
	private String createdBy;
	@CreatedDate
	private Instant created;
}
