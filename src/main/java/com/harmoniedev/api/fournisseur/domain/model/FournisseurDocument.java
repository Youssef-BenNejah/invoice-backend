package com.harmoniedev.api.fournisseur.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "fournisseurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FournisseurDocument {
	@Id
	private String id;
	private String nom;
	private String email;
	private String telephone;

	/** Matricule fiscal tunisien. */
	private String fisc;

	private String adresse;
	private String createdBy;

	@CreatedDate
	private Instant created;
}
