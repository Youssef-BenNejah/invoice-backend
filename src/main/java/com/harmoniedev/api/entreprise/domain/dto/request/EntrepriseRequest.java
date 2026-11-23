package com.harmoniedev.api.entreprise.domain.dto.request;

import jakarta.validation.constraints.Email;
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
public class EntrepriseRequest {
	private String nom;

	@Email
	private String email;

	private String telephone;

	private String pays;

	private String siteweb;

	private String rib;

	private String fisc;

	private String adresse;

	private String mainContactId;
}
