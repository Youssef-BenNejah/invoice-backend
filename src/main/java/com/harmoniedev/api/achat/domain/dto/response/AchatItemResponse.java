package com.harmoniedev.api.achat.domain.dto.response;

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
public class AchatItemResponse {
	private String id;
	private String productId;
	private String article;
	private double quantity;
	private double unitPriceHT;
	private String taxId;
	private double taxRate;
	private double taxAmount;
	private double total;
}
