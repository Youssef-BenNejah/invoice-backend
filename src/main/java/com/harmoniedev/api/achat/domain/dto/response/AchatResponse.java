package com.harmoniedev.api.achat.domain.dto.response;

import com.harmoniedev.api.currency.domain.dto.response.CurrencyResponse;
import com.harmoniedev.api.fournisseur.domain.dto.response.FournisseurResponse;
import java.time.Instant;
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
public class AchatResponse {
	private String id;
	private FournisseurResponse fournisseur;
	private CurrencyResponse currency;
	private int number;
	private int year;
	private String date;
	private String note;
	private List<AchatItemResponse> items;
	private double subtotal;
	private double taxAmount;
	private double total;
	private double retenueSourceRate;
	private double retenueSourceAmount;
	private double montantNetAPayer;
	private String createdBy;
	private Instant created;
}
