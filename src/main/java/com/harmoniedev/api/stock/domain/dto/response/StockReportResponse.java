package com.harmoniedev.api.stock.domain.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Stock remaining at the end of a given month, article by article, with HT/TVA/TTC totals. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReportResponse {
	private int year;
	private int month;
	private double totalHT;
	private double totalTVA;
	private double totalTTC;
	private List<StockReportItemResponse> items;
}
