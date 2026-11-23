package com.harmoniedev.api.invoice.domain.dto.request;

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
public class InvoiceRequest {
	/** Required unless type is Achat (checked in InvoiceService, not here — the two are mutually exclusive). */
	private String clientId;

	/** Required only when type is Achat. */
	private String fournisseurId;

	@NotBlank
	private String currencyId;

	/** "Facture" | "Devis" | "Bon de livraison" */
	@NotBlank
	private String status;

	/** "Standard" | "Proforma" | "Achat" — only read on create; immutable afterwards. */
	private String type;

	@NotBlank
	private String date;

	private double timbre;

	@NotNull
	@NotEmpty
	@Valid
	private List<InvoiceItemRequest> items;

	private String factureImage;
}
