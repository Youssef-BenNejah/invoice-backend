package com.harmoniedev.api.achat.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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
public class AchatItemRequest {
	@NotBlank
	private String productId;

	@Positive
	private double quantity;

	private double unitPriceHT;

	/** Empty/null means no tax on this line. */
	private String taxId;
}
