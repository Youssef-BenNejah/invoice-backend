package com.harmoniedev.api.achat.domain.model;

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
public class AchatItemDocument {
	private String id;

	/** Required — every Achat line feeds the stock ledger. */
	private String productId;

	/** Product name snapshot at purchase time, so history reads correctly even if the product is later renamed/deleted. */
	private String article;

	private double quantity;

	/** Unit purchase price HT (stock cost basis — TVA is not included, see StockService). */
	private double unitPriceHT;

	private String taxId;
	private double taxRate;
	private double taxAmount;
	private double total;
}
