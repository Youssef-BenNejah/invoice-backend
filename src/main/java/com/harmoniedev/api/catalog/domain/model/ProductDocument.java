package com.harmoniedev.api.catalog.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocument {
	@Id
	private String id;
	private String name;
	private String currency;
	private double price;
	private String description;
	private String reference;

	/** Tax (TVA) applied by default when this product is picked on an invoice line. */
	private String defaultTaxId;

	private double longueur;
	private double largeur;
	private double hauteur;

	/** Cost price — computed and stored on every save as longueur * largeur * hauteur * price. */
	@Default
	private double prixAchat = 0;

	/** Target profit margin, as a percentage. */
	private double margeGain;

	private String createdBy;
	@CreatedDate
	private Instant created;

	/** Units on hand. Mutated only by StockService (never set directly from a product edit). */
	@Default
	private double stockQuantity = 0;

	/** CMP (coût moyen pondéré) — weighted average unit cost HT. Mutated only by StockService. */
	@Default
	private double averageCost = 0;
}
