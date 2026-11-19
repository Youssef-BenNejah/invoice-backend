package com.harmoniedev.api.stock.service;

import com.harmoniedev.api.catalog.domain.model.ProductDocument;
import com.harmoniedev.api.catalog.repository.ProductRepository;
import com.harmoniedev.api.stock.domain.dto.response.StockMovementResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockResponse;
import com.harmoniedev.api.stock.domain.model.StockMovementDocument;
import com.harmoniedev.api.stock.domain.model.StockMovementType;
import com.harmoniedev.api.stock.domain.model.StockSourceType;
import com.harmoniedev.api.stock.repository.StockMovementRepository;
import java.time.LocalDate;
import java.util.List;
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

	public StockService(StockMovementRepository movementRepository, ProductRepository productRepository) {
		this.movementRepository = movementRepository;
		this.productRepository = productRepository;
	}

	public StockResponse getStock(String productId) {
		return toStockResponse(findProductOrThrow(productId));
	}

	public List<StockMovementResponse> listMovements(String productId) {
		findProductOrThrow(productId);
		return movementRepository.findByProductIdOrderByCreatedDesc(productId).stream().map(this::toResponse).toList();
	}

	/** CMV (coût des marchandises vendues) over a period: every VENTE movement valued at the CMP it was sold at. */
	public double computeCmv(String dateFrom, String dateTo) {
		LocalDate from = parseOrNull(dateFrom);
		LocalDate to = parseOrNull(dateTo);
		return round(movementRepository.findAll().stream()
				.filter(m -> m.getType() == StockMovementType.VENTE)
				.filter(m -> inRange(m.getCreated(), from, to))
				.mapToDouble(m -> Math.abs(m.getQuantityDelta()) * m.getUnitCost())
				.sum());
	}

	private static LocalDate parseOrNull(String date) {
		if (date == null || date.isBlank()) return null;
		try {
			return LocalDate.parse(date);
		} catch (Exception ex) {
			return null;
		}
	}

	private static boolean inRange(java.time.Instant instant, LocalDate from, LocalDate to) {
		if (instant == null) return false;
		LocalDate date = instant.atZone(java.time.ZoneOffset.UTC).toLocalDate();
		return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
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
	 * Reverses every movement recorded for one source document (an Achat deleted, or a sales
	 * Facture deleted/edited out of stock-moving state). Processed newest-first: a VENTE reversal is
	 * always safe; an ACHAT reversal is only safe while it is still the latest movement for its
	 * product (otherwise a later movement already used the CMP it set, and undoing it would corrupt
	 * the valorisation) — in that case this throws and nothing is changed for that product.
	 */
	public void reverseMovementsForSource(String sourceId, StockSourceType sourceType, String actorId) {
		List<StockMovementDocument> movements = movementRepository.findBySourceIdAndSourceTypeOrderByCreatedDesc(sourceId, sourceType);
		for (StockMovementDocument movement : movements) {
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
