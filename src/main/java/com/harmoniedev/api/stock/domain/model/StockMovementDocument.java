package com.harmoniedev.api.stock.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Append-only stock ledger entry. Never updated or deleted after creation — corrections are made by
 * writing a new, opposite movement (ANNULATION_*), so the ledger always reflects exactly what happened.
 */
@Document(collection = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementDocument {
	@Id
	private String id;
	private String productId;
	private StockMovementType type;

	/** Signed: positive = entrée, negative = sortie. */
	private double quantityDelta;

	/** Unit cost HT applied to this movement (purchase price for ACHAT, CMP at the time for VENTE/AJUSTEMENT). */
	private double unitCost;

	/** Product stock snapshot immediately before/after this movement — enables exact, order-safe reversal. */
	private double quantityBefore;
	private double averageCostBefore;
	private double quantityAfter;
	private double averageCostAfter;

	private StockSourceType sourceType;

	/** The Achat/Invoice id this movement came from, null for a manual AJUSTEMENT. */
	private String sourceId;

	/** Required for AJUSTEMENT and ANNULATION_* — the audit-trail "why". */
	private String reason;

	private String createdBy;

	@CreatedDate
	private Instant created;
}
