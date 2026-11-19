package com.harmoniedev.api.fournisseur.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class FournisseurRequest {
	@NotBlank
	private String nom;

	private String email;
	private String telephone;
	private String fisc;
	private String adresse;
}
