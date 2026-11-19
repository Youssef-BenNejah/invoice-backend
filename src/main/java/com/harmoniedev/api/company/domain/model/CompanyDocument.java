package com.harmoniedev.api.company.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyDocument {
	@Id
	private String id;

	@Indexed(unique = true)
	private String ownerId;

	private String name;
	private String matriculeFisc;
	private String address;
	private String state;
	private String country;
	private String email;
	private String phone;
	private String website;
	private String taxNumber;
	private String vatNumber;
	private String registrationNumber;
	private String logoUrl;
	private String logoPublicId;

	/**
	 * Assujetti à la TVA : si faux, la TVA payée sur les achats n'est pas récupérable et doit
	 * entrer dans le coût d'acquisition du stock (voir AchatService). Vrai par défaut (régime réel).
	 */
	@Default
	private boolean assujettiTva = true;

	/** "Réel" | "Forfaitaire" | "Suspension TVA" — informatif, utilisé dans le rapport fiscal. */
	@Default
	private String regimeFiscal = "Réel";
}
