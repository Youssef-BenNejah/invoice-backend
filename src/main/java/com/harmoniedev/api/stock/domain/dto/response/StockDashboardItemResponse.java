package com.harmoniedev.api.stock.domain.dto.response;

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
public class StockDashboardItemResponse {
	private String productId;
	private String article;
	private String ref;
	private double quantite;
	private double puHT;
	private double totalHT;
	private double puTTC;
	private double totalTTC;
}
