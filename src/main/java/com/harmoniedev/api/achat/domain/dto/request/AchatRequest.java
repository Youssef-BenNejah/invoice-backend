package com.harmoniedev.api.achat.domain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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
public class AchatRequest {
	@NotBlank
	private String fournisseurId;

	@NotBlank
	private String currencyId;

	@NotBlank
	private String date;

	private String note;

	/** Retenue à la source (%) — 0, 1, 1.5 ou 3. Calculée sur le total TTC. */
	private double retenueSourceRate;

	@NotNull
	@NotEmpty
	@Valid
	private List<AchatItemRequest> items;
}
