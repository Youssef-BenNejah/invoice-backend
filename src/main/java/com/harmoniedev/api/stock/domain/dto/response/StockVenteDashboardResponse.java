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
public class StockVenteDashboardResponse {
	private double totalValueHTVente;
	private double totalValueTTCVente;
	private List<StockVenteDashboardItemResponse> items;
}
