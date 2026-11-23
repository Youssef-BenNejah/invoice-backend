package com.harmoniedev.api.stock.service;

import com.harmoniedev.api.catalog.domain.model.ProductDocument;
import com.harmoniedev.api.catalog.repository.ProductRepository;
import com.harmoniedev.api.stock.domain.dto.response.StockDashboardItemResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockDashboardResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockMovementResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockReportItemResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockReportResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockResponse;
import com.harmoniedev.api.stock.domain.model.StockMovementDocument;
import com.harmoniedev.api.stock.domain.model.StockMovementType;
import com.harmoniedev.api.stock.domain.model.StockSourceType;
import com.harmoniedev.api.stock.repository.StockMovementRepository;
import com.harmoniedev.api.tax.domain.model.TaxDocument;
import com.harmoniedev.api.tax.repository.TaxRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The stock ledger: every change to a product's quantity is written here first, as a signed,
 * append-only {@link StockMovementDocument}, and the product's cached quantity/averageCost are
 * derived from it. Valorisation uses CMP (coût moyen pondéré) — the weighted average cost is
 * recomputed on every entrée; a sortie never changes it.
 */
@Service
public class StockService {
	private static final double EPSILON = 1e-6;

	private final StockMovementRepository movementRepository;
	private final ProductRepository productRepository;
	private final TaxRepository taxRepository;

	public StockService(StockMovementRepository movementRepository, ProductRepository productRepository, TaxRepository taxRepository) {
		this.movementRepository = movementRepository;
		this.productRepository = productRepository;
		this.taxRepository = taxRepository;
	}

	public StockResponse getStock(String productId) {
		return toStockResponse(findProductOrThrow(productId));
	}

	public List<StockMovementResponse> listMovements(String productId) {
		findProductOrThrow(productId);
		return movementRepository.findByProductIdOrderByCreatedDesc(productId).stream().map(this::toResponse).toList();
	}

	/** Facture Achat validated: adds {@code quantity} at {@code unitCostHT}, recomputing the CMP. */
	public void recordAchatEntry(String productId, double quantity, double unitCostHT, String sourceId, String actorId) {
		requirePositive(quantity);
		ProductDocument product = findProductOrThrow(productId);
		double qtyBefore = product.getStockQuantity();
		double costBefore = product.getAverageCost();
		double qtyAfter = round(qtyBefore + quantity);
		double costAfter = qtyAfter > EPSILON ? round((qtyBefore * costBefore + quantity * unitCostHT) / qtyAfter) : 0;

		product.setStockQuantity(qtyAfter);
		product.setAverageCost(costAfter);
		productRepository.save(product);

		saveMovement(StockMovementDocument.builder()
				.productId(productId)
				.type(StockMovementType.ACHAT)
				.quantityDelta(quantity)
				.unitCost(unitCostHT)
				.quantityBefore(qtyBefore)
				.averageCostBefore(costBefore)
				.quantityAfter(qtyAfter)
				.averageCostAfter(costAfter)
				.sourceType(StockSourceType.FACTURE_ACHAT)
				.sourceId(sourceId)
				.createdBy(actorId)
				.build());
	}

	/** Facture Vente validated: removes {@code quantity} at the current CMP. The CMP itself is unaffected by a sortie. */
	public void recordVenteExit(String productId, double quantity, String sourceId, String actorId) {
		requirePositive(quantity);
		ProductDocument product = findProductOrThrow(productId);
		double qtyBefore = product.getStockQuantity();
		if (qtyBefore - quantity < -EPSILON) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuffisant pour \"" + product.getName()
					+ "\" (disponible: " + qtyBefore + ", demandé: " + quantity + ")");
		}
		double costBefore = product.getAverageCost();
		double qtyAfter = round(qtyBefore - quantity);

		product.setStockQuantity(qtyAfter);
		productRepository.save(product);

		saveMovement(StockMovementDocument.builder()
				.productId(productId)
				.type(StockMovementType.VENTE)
				.quantityDelta(-quantity)
				.unitCost(costBefore)
				.quantityBefore(qtyBefore)
				.averageCostBefore(costBefore)
				.quantityAfter(qtyAfter)
				.averageCostAfter(costBefore)
				.sourceType(StockSourceType.FACTURE_VENTE)
				.sourceId(sourceId)
				.createdBy(actorId)
				.build());
	}

	/**
	 * Reverses the currently-live effect of one source document (an invoice deleted, or edited out
	 * of stock-moving state) on each product it touched. An invoice that's been edited before may
	 * have several historical movements under the same sourceId — an ACHAT/VENTE immediately
	 * superseded by its own ANNULATION_* during that earlier edit, then a fresh one applied — so this
	 * only reverses the most recent movement per product, which is the only one still "live"; older,
	 * already-superseded entries for the same product are left alone as pure history. Processed
	 * newest-first: a VENTE reversal is always safe; an ACHAT reversal is only safe while it is still
	 * the latest movement for its product (otherwise a later movement already used the CMP it set,
	 * and undoing it would corrupt the valorisation) — in that case this throws and nothing is
	 * changed for that product.
	 */
	public void reverseMovementsForSource(String sourceId, StockSourceType sourceType, String actorId) {
		List<StockMovementDocument> movements = movementRepository.findBySourceIdAndSourceTypeOrderByCreatedDesc(sourceId, sourceType);
		java.util.Map<String, StockMovementDocument> latestPerProduct = new java.util.LinkedHashMap<>();
		for (StockMovementDocument movement : movements) {
			latestPerProduct.putIfAbsent(movement.getProductId(), movement);
		}
		for (StockMovementDocument movement : latestPerProduct.values()) {
			if (movement.getType() == StockMovementType.ACHAT) {
				reverseAchatMovement(movement, actorId);
			} else if (movement.getType() == StockMovementType.VENTE) {
				reverseVenteMovement(movement, actorId);
			}
		}
	}

	/** Manual stock-take correction: sets the counted (absolute) quantity. A reason is mandatory for the audit trail. */
	public StockResponse adjust(String productId, double newQuantity, String reason, String actorId) {
		if (newQuantity < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity cannot be negative");
		}
		if (reason == null || reason.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required for a manual stock adjustment");
		}
		ProductDocument product = findProductOrThrow(productId);
		double qtyBefore = product.getStockQuantity();
		double costBefore = product.getAverageCost();
		double qtyAfter = round(newQuantity);

		product.setStockQuantity(qtyAfter);
		productRepository.save(product);

		saveMovement(StockMovementDocument.builder()
				.productId(productId)
				.type(StockMovementType.AJUSTEMENT)
				.quantityDelta(round(qtyAfter - qtyBefore))
				.unitCost(costBefore)
				.quantityBefore(qtyBefore)
				.averageCostBefore(costBefore)
				.quantityAfter(qtyAfter)
				.averageCostAfter(costBefore)
				.sourceType(StockSourceType.AJUSTEMENT)
				.reason(reason)
				.createdBy(actorId)
				.build());
		return toStockResponse(product);
	}

	/**
	 * Stock ledger: one line per article that has had at least one Facture Achat applied — each
	 * achat/vente since then keeps that line's quantity and valorisation (HT and TTC, at CMP) up
	 * to date. An article never bought in is simply absent, not shown at zero.
	 */
	public StockDashboardResponse dashboard() {
		List<StockDashboardItemResponse> items = productRepository.findAll().stream()
				.filter(product -> movementRepository.existsByProductId(product.getId()))
				.map(this::toDashboardItem)
				.toList();
		double totalHT = round(items.stream().mapToDouble(StockDashboardItemResponse::getTotalHT).sum());
		double totalTTC = round(items.stream().mapToDouble(StockDashboardItemResponse::getTotalTTC).sum());
		return StockDashboardResponse.builder()
				.totalValueHT(totalHT)
				.totalValueTTC(totalTTC)
				.items(items)
				.build();
	}

	/**
	 * Stock remaining at the end of a given month: for each article that had any stock movement by
	 * then, the quantity and CMP as left by its last movement at or before that month's last instant.
	 * An article with no movement yet by that date is simply absent, not shown at zero. Tax rates are
	 * applied as currently configured (the ledger does not keep a historical tax rate per movement).
	 */
	public StockReportResponse report(int year, int month) {
		Instant cutoff = YearMonth.of(year, month).atEndOfMonth().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();
		List<StockReportItemResponse> items = productRepository.findAll().stream()
				.filter(product -> movementRepository.existsByProductId(product.getId()))
				.map(product -> toReportItem(product, cutoff))
				.filter(java.util.Objects::nonNull)
				.toList();
		double totalHT = round(items.stream().mapToDouble(StockReportItemResponse::getTotalHT).sum());
		double totalTTC = round(items.stream().mapToDouble(StockReportItemResponse::getTotalTTC).sum());
		return StockReportResponse.builder()
				.year(year)
				.month(month)
				.totalHT(totalHT)
				.totalTVA(round(totalTTC - totalHT))
				.totalTTC(totalTTC)
				.items(items)
				.build();
	}

	private StockReportItemResponse toReportItem(ProductDocument product, Instant cutoff) {
		Optional<StockMovementDocument> asOf =
				movementRepository.findTopByProductIdAndCreatedLessThanEqualOrderByCreatedDesc(product.getId(), cutoff);
		if (asOf.isEmpty()) {
			return null;
		}
		StockMovementDocument movement = asOf.get();
		TaxDocument tax = (product.getDefaultTaxId() != null && !product.getDefaultTaxId().isBlank())
				? taxRepository.findById(product.getDefaultTaxId()).orElse(null) : null;
		double taxRate = tax != null ? tax.getTaxvalue() : 0;
		double quantite = movement.getQuantityAfter();
		double puHT = movement.getAverageCostAfter();
		double puTTC = round(puHT * (1 + taxRate / 100));
		double totalHT = round(quantite * puHT);
		double totalTTC = round(quantite * puTTC);
		return StockReportItemResponse.builder()
				.productId(product.getId())
				.article(product.getName())
				.ref(product.getReference())
				.quantite(quantite)
				.puHT(puHT)
				.totalHT(totalHT)
				.puTTC(puTTC)
				.totalTTC(totalTTC)
				.build();
	}

	private StockDashboardItemResponse toDashboardItem(ProductDocument product) {
		TaxDocument tax = (product.getDefaultTaxId() != null && !product.getDefaultTaxId().isBlank())
				? taxRepository.findById(product.getDefaultTaxId()).orElse(null) : null;
		double taxRate = tax != null ? tax.getTaxvalue() : 0;
		double puHT = product.getAverageCost();
		double puTTC = round(puHT * (1 + taxRate / 100));
		double totalHT = round(product.getStockQuantity() * puHT);
		double totalTTC = round(product.getStockQuantity() * puTTC);
		return StockDashboardItemResponse.builder()
				.productId(product.getId())
				.article(product.getName())
				.ref(product.getReference())
				.quantite(product.getStockQuantity())
				.puHT(puHT)
				.totalHT(totalHT)
				.puTTC(puTTC)
				.totalTTC(totalTTC)
				.build();
	}

	private void reverseAchatMovement(StockMovementDocument movement, String actorId) {
		ProductDocument product = findProductOrThrow(movement.getProductId());
		StockMovementDocument latest = movementRepository.findTopByProductIdOrderByCreatedDesc(movement.getProductId()).orElse(null);
		if (latest == null || !latest.getId().equals(movement.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Le stock de \"" + product.getName()
					+ "\" a évolué depuis cet achat : annulation impossible, utilisez un ajustement manuel.");
		}

		product.setStockQuantity(movement.getQuantityBefore());
		product.setAverageCost(movement.getAverageCostBefore());
		productRepository.save(product);

		saveMovement(StockMovementDocument.builder()
				.productId(movement.getProductId())
				.type(StockMovementType.ANNULATION_ACHAT)
				.quantityDelta(-movement.getQuantityDelta())
				.unitCost(movement.getUnitCost())
				.quantityBefore(movement.getQuantityAfter())
				.averageCostBefore(movement.getAverageCostAfter())
				.quantityAfter(movement.getQuantityBefore())
				.averageCostAfter(movement.getAverageCostBefore())
				.sourceType(movement.getSourceType())
				.sourceId(movement.getSourceId())
				.reason("Annulation achat")
				.createdBy(actorId)
				.build());
	}

	private void reverseVenteMovement(StockMovementDocument movement, String actorId) {
		ProductDocument product = findProductOrThrow(movement.getProductId());
		double qtyBefore = product.getStockQuantity();
		double reinstated = -movement.getQuantityDelta();
		double qtyAfter = round(qtyBefore + reinstated);

		product.setStockQuantity(qtyAfter);
		productRepository.save(product);

		saveMovement(StockMovementDocument.builder()
				.productId(movement.getProductId())
				.type(StockMovementType.ANNULATION_VENTE)
				.quantityDelta(reinstated)
				.unitCost(product.getAverageCost())
				.quantityBefore(qtyBefore)
				.averageCostBefore(product.getAverageCost())
				.quantityAfter(qtyAfter)
				.averageCostAfter(product.getAverageCost())
				.sourceType(movement.getSourceType())
				.sourceId(movement.getSourceId())
				.reason("Annulation vente")
				.createdBy(actorId)
				.build());
	}

	private void requirePositive(double quantity) {
		if (quantity <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
		}
	}

	private void saveMovement(StockMovementDocument movement) {
		movementRepository.save(movement);
	}

	private ProductDocument findProductOrThrow(String productId) {
		return productRepository.findById(productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
	}

	private StockResponse toStockResponse(ProductDocument product) {
		return StockResponse.builder()
				.productId(product.getId())
				.quantity(product.getStockQuantity())
				.averageCost(product.getAverageCost())
				.stockValue(round(product.getStockQuantity() * product.getAverageCost()))
				.build();
	}

	private StockMovementResponse toResponse(StockMovementDocument m) {
		return StockMovementResponse.builder()
				.id(m.getId())
				.productId(m.getProductId())
				.type(m.getType())
				.quantityDelta(m.getQuantityDelta())
				.unitCost(m.getUnitCost())
				.quantityBefore(m.getQuantityBefore())
				.averageCostBefore(m.getAverageCostBefore())
				.quantityAfter(m.getQuantityAfter())
				.averageCostAfter(m.getAverageCostAfter())
				.sourceType(m.getSourceType())
				.sourceId(m.getSourceId())
				.reason(m.getReason())
				.createdBy(m.getCreatedBy())
				.created(m.getCreated())
				.build();
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
