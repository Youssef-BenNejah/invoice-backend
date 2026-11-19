package com.harmoniedev.api.achat.domain.model;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** A purchase invoice from a supplier. Validating one (create) feeds stock in; deleting it reverses that entry. */
@Document(collection = "achats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AchatDocument {
	@Id
	private String id;
	private String fournisseurId;
	private String currencyId;
	private int number;
	private int year;
	private String date;
	private String note;

	@Default
	private List<AchatItemDocument> items = List.of();

	private double subtotal;
	private double taxAmount;
	private double total;

	private String createdBy;

	@CreatedDate
	private Instant created;
}
