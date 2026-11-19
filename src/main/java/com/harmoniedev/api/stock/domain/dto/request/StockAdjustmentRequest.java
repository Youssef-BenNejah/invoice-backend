package com.harmoniedev.api.stock.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
public class StockAdjustmentRequest {
	@NotBlank
	private String productId;

	/** The counted (absolute) quantity after the adjustment, not a delta. */
	@NotNull
	@PositiveOrZero
	private Double quantity;

	@NotBlank
	private String reason;
}
