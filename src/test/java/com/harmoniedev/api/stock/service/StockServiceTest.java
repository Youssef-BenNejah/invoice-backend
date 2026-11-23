package com.harmoniedev.api.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harmoniedev.api.catalog.domain.model.ProductDocument;
import com.harmoniedev.api.catalog.repository.ProductRepository;
import com.harmoniedev.api.stock.domain.dto.response.StockDashboardResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockReportResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockResponse;
import com.harmoniedev.api.stock.domain.model.StockMovementDocument;
import com.harmoniedev.api.stock.domain.model.StockMovementType;
import com.harmoniedev.api.stock.domain.model.StockSourceType;
import com.harmoniedev.api.stock.repository.StockMovementRepository;
import com.harmoniedev.api.tax.domain.model.TaxDocument;
import com.harmoniedev.api.tax.repository.TaxRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {
	@Mock
	private StockMovementRepository movementRepository;
	@Mock
	private ProductRepository productRepository;
	@Mock
	private TaxRepository taxRepository;

	private StockService stockService;

	@BeforeEach
	void setUp() {
		stockService = new StockService(movementRepository, productRepository, taxRepository);
	}

	private ProductDocument product(double quantity, double averageCost) {
		return ProductDocument.builder()
				.id("prod-1")
				.name("Oud du Cambodge")
				.stockQuantity(quantity)
				.averageCost(averageCost)
				.build();
	}

	@Test
	void recordAchatEntry_onEmptyStock_setsAverageCostToPurchasePrice() {
		ProductDocument product = product(0, 0);
		when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

		stockService.recordAchatEntry("prod-1", 10, 20.0, "invoice-1", "user-1");

		assertThat(product.getStockQuantity()).isEqualTo(10);
		assertThat(product.getAverageCost()).isEqualTo(20.0);

		ArgumentCaptor<StockMovementDocument> captor = ArgumentCaptor.forClass(StockMovementDocument.class);
		verify(movementRepository).save(captor.capture());
		StockMovementDocument movement = captor.getValue();
		assertThat(movement.getType()).isEqualTo(StockMovementType.ACHAT);
		assertThat(movement.getQuantityDelta()).isEqualTo(10);
		assertThat(movement.getSourceType()).isEqualTo(StockSourceType.FACTURE_ACHAT);
		assertThat(movement.getSourceId()).isEqualTo("invoice-1");
	}

	@Test
	void recordAchatEntry_onExistingStock_recomputesWeightedAverageCost() {
		// 10 @ 20 already in stock -> buy 5 more @ 30 => CMP = (200+150)/15 = 23.33
		ProductDocument product = product(10, 20.0);
		when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

		stockService.recordAchatEntry("prod-1", 5, 30.0, "invoice-2", "user-1");

		assertThat(product.getStockQuantity()).isEqualTo(15);
		assertThat(product.getAverageCost()).isEqualTo(23.33);
	}

	@Test
	void dashboard_computesHTandTTCvaluesPerProductAndTotals() {
		ProductDocument withTax = ProductDocument.builder()
				.id("prod-1").name("Oud").stockQuantity(10).averageCost(20.0).defaultTaxId("tax-19").build();
		ProductDocument withoutTax = ProductDocument.builder()
				.id("prod-2").name("Musc").stockQuantity(5).averageCost(10.0).build();
		when(productRepository.findAll()).thenReturn(List.of(withTax, withoutTax));
		when(taxRepository.findById("tax-19")).thenReturn(Optional.of(TaxDocument.builder().id("tax-19").name("TVA").taxvalue(19.0).build()));
		when(movementRepository.existsByProductId("prod-1")).thenReturn(true);
		when(movementRepository.existsByProductId("prod-2")).thenReturn(true);

		StockDashboardResponse dashboard = stockService.dashboard();

		assertThat(dashboard.getItems()).hasSize(2);
		assertThat(dashboard.getItems().get(0).getTotalHT()).isEqualTo(200.0);
		assertThat(dashboard.getItems().get(0).getTotalTTC()).isEqualTo(238.0);
		assertThat(dashboard.getItems().get(1).getTotalHT()).isEqualTo(50.0);
		assertThat(dashboard.getItems().get(1).getTotalTTC()).isEqualTo(50.0);
		assertThat(dashboard.getTotalValueHT()).isEqualTo(250.0);
		assertThat(dashboard.getTotalValueTTC()).isEqualTo(288.0);
	}

	@Test
	void dashboard_excludesProductsNeverTouchedByAnAchat() {
		ProductDocument untouched = ProductDocument.builder().id("prod-3").name("Ambre").stockQuantity(0).averageCost(0).build();
		when(productRepository.findAll()).thenReturn(List.of(untouched));
		when(movementRepository.existsByProductId("prod-3")).thenReturn(false);

		StockDashboardResponse dashboard = stockService.dashboard();

		assertThat(dashboard.getItems()).isEmpty();
	}

	@Test
	void recordVenteExit_reducesQuantity_butLeavesAverageCostUnchanged() {
		ProductDocument product = product(15, 23.33);
		when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

		stockService.recordVenteExit("prod-1", 4, "invoice-1", "user-1");

		assertThat(product.getStockQuantity()).isEqualTo(11);
		assertThat(product.getAverageCost()).isEqualTo(23.33);

		ArgumentCaptor<StockMovementDocument> captor = ArgumentCaptor.forClass(StockMovementDocument.class);
		verify(movementRepository).save(captor.capture());
		StockMovementDocument movement = captor.getValue();
		assertThat(movement.getType()).isEqualTo(StockMovementType.VENTE);
		assertThat(movement.getQuantityDelta()).isEqualTo(-4);
		assertThat(movement.getUnitCost()).isEqualTo(23.33);
		assertThat(movement.getSourceType()).isEqualTo(StockSourceType.FACTURE_VENTE);
		assertThat(movement.getSourceId()).isEqualTo("invoice-1");
	}

	@Test
	void recordVenteExit_insufficientStock_throwsConflict_andDoesNotMutate() {
		ProductDocument product = product(3, 20.0);
		when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

		assertThatThrownBy(() -> stockService.recordVenteExit("prod-1", 5, "invoice-1", "user-1"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("409")
				.hasMessageContaining("Stock insuffisant");

		assertThat(product.getStockQuantity()).isEqualTo(3);
		verify(productRepository, never()).save(any());
		verify(movementRepository, never()).save(any());
	}

	@Test
	void recordVenteExit_exactlyDepletingStock_isAllowed() {
		ProductDocument product = product(5, 20.0);
		when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

		stockService.recordVenteExit("prod-1", 5, "invoice-1", "user-1");

		assertThat(product.getStockQuantity()).isEqualTo(0);
	}

	@Test
	void adjust_setsAbsoluteQuantity_andRecordsReasonForAudit() {
		ProductDocument product = product(10, 20.0);
		when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

		StockResponse response = stockService.adjust("prod-1", 7, "Inventaire physique du 19/11", "user-1");

		assertThat(response.getQuantity()).isEqualTo(7);
		assertThat(product.getStockQuantity()).isEqualTo(7);
		assertThat(product.getAverageCost()).isEqualTo(20.0); // cost untouched by a count correction

		ArgumentCaptor<StockMovementDocument> captor = ArgumentCaptor.forClass(StockMovementDocument.class);
		verify(movementRepository).save(captor.capture());
		StockMovementDocument movement = captor.getValue();
		assertThat(movement.getType()).isEqualTo(StockMovementType.AJUSTEMENT);
		assertThat(movement.getQuantityDelta()).isEqualTo(-3);
		assertThat(movement.getReason()).isEqualTo("Inventaire physique du 19/11");
	}

	@Test
	void adjust_withoutReason_isRejected() {
		assertThatThrownBy(() -> stockService.adjust("prod-1", 7, " ", "user-1"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("reason");
		verify(productRepository, never()).findById(any());
	}

	@Test
	void adjust_negativeQuantity_isRejected() {
		assertThatThrownBy(() -> stockService.adjust("prod-1", -1, "oops", "user-1"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("400");
	}

	@Test
	void reverseMovementsForSource_achat_whenStillLatest_restoresExactPriorState() {
		ProductDocument product = product(15, 23.33);
		StockMovementDocument achatMovement = StockMovementDocument.builder()
				.id("mv-1")
				.productId("prod-1")
				.type(StockMovementType.ACHAT)
				.quantityDelta(5)
				.unitCost(30.0)
				.quantityBefore(10)
				.averageCostBefore(20.0)
				.quantityAfter(15)
				.averageCostAfter(23.33)
				.sourceType(StockSourceType.FACTURE_ACHAT)
				.sourceId("achat-2")
				.build();
		when(movementRepository.findBySourceIdAndSourceTypeOrderByCreatedDesc("achat-2", StockSourceType.FACTURE_ACHAT))
				.thenReturn(List.of(achatMovement));
		when(movementRepository.findTopByProductIdOrderByCreatedDesc("prod-1")).thenReturn(Optional.of(achatMovement));
		when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

		stockService.reverseMovementsForSource("achat-2", StockSourceType.FACTURE_ACHAT, "user-1");

		assertThat(product.getStockQuantity()).isEqualTo(10);
		assertThat(product.getAverageCost()).isEqualTo(20.0);

		ArgumentCaptor<StockMovementDocument> captor = ArgumentCaptor.forClass(StockMovementDocument.class);
		verify(movementRepository).save(captor.capture());
		assertThat(captor.getValue().getType()).isEqualTo(StockMovementType.ANNULATION_ACHAT);
	}

	@Test
	void reverseMovementsForSource_achat_whenStockMovedSince_throwsConflict_andDoesNotMutate() {
		ProductDocument product = product(11, 23.33);
		StockMovementDocument achatMovement = StockMovementDocument.builder()
				.id("mv-1")
				.productId("prod-1")
				.type(StockMovementType.ACHAT)
				.quantityDelta(5)
				.unitCost(30.0)
				.quantityBefore(10)
				.averageCostBefore(20.0)
				.quantityAfter(15)
				.averageCostAfter(23.33)
				.sourceType(StockSourceType.FACTURE_ACHAT)
				.sourceId("achat-2")
				.build();
		// A later VENTE movement is now the top of the ledger for this product.
		StockMovementDocument laterVente = StockMovementDocument.builder().id("mv-2").productId("prod-1").build();

		when(movementRepository.findBySourceIdAndSourceTypeOrderByCreatedDesc("achat-2", StockSourceType.FACTURE_ACHAT))
				.thenReturn(List.of(achatMovement));
		when(movementRepository.findTopByProductIdOrderByCreatedDesc("prod-1")).thenReturn(Optional.of(laterVente));
		when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

		assertThatThrownBy(() -> stockService.reverseMovementsForSource("achat-2", StockSourceType.FACTURE_ACHAT, "user-1"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("409");

		assertThat(product.getStockQuantity()).isEqualTo(11);
		assertThat(product.getAverageCost()).isEqualTo(23.33);
		verify(productRepository, never()).save(any());
	}

	@Test
	void reverseMovementsForSource_vente_isAlwaysSafe_regardlessOfLaterMovements() {
		ProductDocument product = product(2, 23.33);
		StockMovementDocument venteMovement = StockMovementDocument.builder()
				.id("mv-5")
				.productId("prod-1")
				.type(StockMovementType.VENTE)
				.quantityDelta(-4)
				.unitCost(23.33)
				.sourceType(StockSourceType.FACTURE_VENTE)
				.sourceId("invoice-1")
				.build();
		when(movementRepository.findBySourceIdAndSourceTypeOrderByCreatedDesc("invoice-1", StockSourceType.FACTURE_VENTE))
				.thenReturn(List.of(venteMovement));
		when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

		stockService.reverseMovementsForSource("invoice-1", StockSourceType.FACTURE_VENTE, "user-1");

		assertThat(product.getStockQuantity()).isEqualTo(6);
		verify(movementRepository, never()).findTopByProductIdOrderByCreatedDesc(any());
	}

	@Test
	void reverseMovementsForSource_achat_editedInvoice_onlyReversesTheCurrentlyLiveMovement() {
		// Simulates an achat invoice edited once before being deleted: the original ACHAT(+5) was
		// already superseded by its own ANNULATION_ACHAT during that edit, then a fresh ACHAT(+8) was
		// applied — all three share this invoice's id as sourceId. Only the live ACHAT(+8) should be
		// reversed; the older, already-settled pair must be left untouched (and must not trip the
		// "stock moved since" guard against each other).
		ProductDocument product = product(26, 629.15);
		StockMovementDocument liveAchat = StockMovementDocument.builder()
				.id("mv-newest").productId("prod-1").type(StockMovementType.ACHAT)
				.quantityDelta(8).unitCost(15.0)
				.quantityBefore(18).averageCostBefore(902.11).quantityAfter(26).averageCostAfter(629.15)
				.sourceType(StockSourceType.FACTURE_ACHAT).sourceId("invoice-1")
				.build();
		StockMovementDocument supersededCancellation = StockMovementDocument.builder()
				.id("mv-cancel").productId("prod-1").type(StockMovementType.ANNULATION_ACHAT)
				.quantityDelta(-5)
				.sourceType(StockSourceType.FACTURE_ACHAT).sourceId("invoice-1")
				.build();
		StockMovementDocument supersededOriginal = StockMovementDocument.builder()
				.id("mv-original").productId("prod-1").type(StockMovementType.ACHAT)
				.quantityDelta(5).unitCost(15.0)
				.sourceType(StockSourceType.FACTURE_ACHAT).sourceId("invoice-1")
				.build();
		// Repository returns newest-first, as the real query does.
		when(movementRepository.findBySourceIdAndSourceTypeOrderByCreatedDesc("invoice-1", StockSourceType.FACTURE_ACHAT))
				.thenReturn(List.of(liveAchat, supersededCancellation, supersededOriginal));
		when(movementRepository.findTopByProductIdOrderByCreatedDesc("prod-1")).thenReturn(Optional.of(liveAchat));
		when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

		stockService.reverseMovementsForSource("invoice-1", StockSourceType.FACTURE_ACHAT, "user-1");

		assertThat(product.getStockQuantity()).isEqualTo(18);
		assertThat(product.getAverageCost()).isEqualTo(902.11);
		verify(movementRepository, org.mockito.Mockito.times(1)).save(any());
	}

	@Test
	void report_usesTheLastMovementAtOrBeforeMonthEnd_forQuantityAndCMP() {
		ProductDocument product = ProductDocument.builder()
				.id("prod-1").name("Oud").defaultTaxId("tax-19").build();
		StockMovementDocument asOfEndOfMonth = StockMovementDocument.builder()
				.productId("prod-1").quantityAfter(12).averageCostAfter(20.0).build();
		when(productRepository.findAll()).thenReturn(List.of(product));
		when(movementRepository.existsByProductId("prod-1")).thenReturn(true);
		when(movementRepository.findTopByProductIdAndCreatedLessThanEqualOrderByCreatedDesc(eq("prod-1"), any(Instant.class)))
				.thenReturn(Optional.of(asOfEndOfMonth));
		when(taxRepository.findById("tax-19")).thenReturn(Optional.of(TaxDocument.builder().id("tax-19").name("TVA").taxvalue(19.0).build()));

		StockReportResponse report = stockService.report(2026, 10);

		assertThat(report.getYear()).isEqualTo(2026);
		assertThat(report.getMonth()).isEqualTo(10);
		assertThat(report.getItems()).hasSize(1);
		assertThat(report.getItems().get(0).getQuantite()).isEqualTo(12);
		assertThat(report.getItems().get(0).getPuHT()).isEqualTo(20.0);
		assertThat(report.getItems().get(0).getTotalHT()).isEqualTo(240.0);
		assertThat(report.getItems().get(0).getTotalTTC()).isEqualTo(285.6);
		assertThat(report.getTotalHT()).isEqualTo(240.0);
		assertThat(report.getTotalTTC()).isEqualTo(285.6);
		assertThat(report.getTotalTVA()).isEqualTo(45.6);
	}

	@Test
	void report_excludesArticlesWithNoMovementYetByThatMonth() {
		ProductDocument product = ProductDocument.builder().id("prod-1").name("Oud").build();
		when(productRepository.findAll()).thenReturn(List.of(product));
		when(movementRepository.existsByProductId("prod-1")).thenReturn(true);
		when(movementRepository.findTopByProductIdAndCreatedLessThanEqualOrderByCreatedDesc(eq("prod-1"), any(Instant.class)))
				.thenReturn(Optional.empty());

		StockReportResponse report = stockService.report(2026, 1);

		assertThat(report.getItems()).isEmpty();
		assertThat(report.getTotalHT()).isEqualTo(0.0);
		assertThat(report.getTotalTTC()).isEqualTo(0.0);
	}

	@Test
	void getStock_computesStockValue_fromQuantityTimesAverageCost() {
		when(productRepository.findById("prod-1")).thenReturn(Optional.of(product(10, 23.33)));

		StockResponse response = stockService.getStock("prod-1");

		assertThat(response.getQuantity()).isEqualTo(10);
		assertThat(response.getAverageCost()).isEqualTo(23.33);
		assertThat(response.getStockValue()).isEqualTo(233.3);
	}

	@Test
	void getStock_unknownProduct_throwsNotFound() {
		when(productRepository.findById("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> stockService.getStock("missing"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404");
	}
}
