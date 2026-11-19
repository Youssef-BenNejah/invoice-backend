package com.harmoniedev.api.stock.domain.dto.response;

import com.harmoniedev.api.stock.domain.model.StockMovementType;
import com.harmoniedev.api.stock.domain.model.StockSourceType;
import java.time.Instant;
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
public class StockMovementResponse {
	private String id;
	private String productId;
	private StockMovementType type;
	private double quantityDelta;
	private double unitCost;
	private double quantityBefore;
	private double averageCostBefore;
	private double quantityAfter;
	private double averageCostAfter;
	private StockSourceType sourceType;
	private String sourceId;
	private String reason;
	private String createdBy;
	private Instant created;
}
