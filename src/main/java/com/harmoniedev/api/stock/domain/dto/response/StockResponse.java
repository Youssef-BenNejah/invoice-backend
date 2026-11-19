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
public class StockResponse {
	private String productId;
	private double quantity;
	private double averageCost;
	private double stockValue;
}
