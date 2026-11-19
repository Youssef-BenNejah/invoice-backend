package com.harmoniedev.api.fournisseur.domain.dto.response;

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
public class FournisseurResponse {
	private String id;
	private String nom;
	private String email;
	private String telephone;
	private String fisc;
	private String adresse;
	private String createdBy;
	private Instant created;
}
