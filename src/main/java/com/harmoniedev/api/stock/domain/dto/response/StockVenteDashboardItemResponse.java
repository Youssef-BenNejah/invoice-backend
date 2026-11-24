package com.harmoniedev.api.stock.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One article's stock valued at its selling price (prix d'achat + marge), not its cost — shows the gain if sold as-is. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockVenteDashboardItemResponse {
	private String productId;
	private String article;
	private String ref;
	private double quantite;
	private double puHTVente;
	private double totalHTVente;
	private double puTTCVente;
	private double totalTTCVente;
}
