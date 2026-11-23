package com.harmoniedev.api.stock.domain.dto.response;

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
public class StockDashboardResponse {
	private double totalValueHT;
	private double totalValueTTC;
	private List<StockDashboardItemResponse> items;
}
