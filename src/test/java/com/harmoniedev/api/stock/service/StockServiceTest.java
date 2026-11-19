package com.harmoniedev.api.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harmoniedev.api.catalog.domain.model.ProductDocument;
import com.harmoniedev.api.catalog.repository.ProductRepository;
import com.harmoniedev.api.stock.domain.dto.response.StockResponse;
import com.harmoniedev.api.stock.domain.model.StockMovementDocument;
import com.harmoniedev.api.stock.domain.model.StockMovementType;
import com.harmoniedev.api.stock.domain.model.StockSourceType;
import com.harmoniedev.api.stock.repository.StockMovementRepository;
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

	private StockService stockService;

	@BeforeEach
	void setUp() {
		stockService = new StockService(movementRepository, productRepository);
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

		stockService.recordAchatEntry("prod-1", 10, 20.0, "achat-1", "user-1");

		assertThat(product.getStockQuantity()).isEqualTo(10);
		assertThat(product.getAverageCost()).isEqualTo(20.0);
		verify(productRepository).save(product);

		ArgumentCaptor<StockMovementDocument> captor = ArgumentCaptor.forClass(StockMovementDocument.class);
		verify(movementRepository).save(captor.capture());
		StockMovementDocument movement = captor.getValue();
		assertThat(movement.getType()).isEqualTo(StockMovementType.ACHAT);
		assertThat(movement.getQuantityDelta()).isEqualTo(10);
		assertThat(movement.getUnitCost()).isEqualTo(20.0);
		assertThat(movement.getQuantityBefore()).isEqualTo(0);
		assertThat(movement.getAverageCostBefore()).isEqualTo(0);
		assertThat(movement.getQuantityAfter()).isEqualTo(10);
		assertThat(movement.getAverageCostAfter()).isEqualTo(20.0);
		assertThat(movement.getSourceType()).isEqualTo(StockSourceType.FACTURE_ACHAT);
		assertThat(movement.getSourceId()).isEqualTo("achat-1");
	}

	@Test
	void recordAchatEntry_onExistingStock_recomputesWeightedAverageCost() {
		// 10 units @ 20 TND already in stock -> buy 5 more @ 30 TND
		// CMP = (10*20 + 5*30) / 15 = (200+150)/15 = 23.33
		ProductDocument product = product(10, 20.0);
		when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

		stockService.recordAchatEntry("prod-1", 5, 30.0, "achat-2", "user-1");

		assertThat(product.getStockQuantity()).isEqualTo(15);
		assertThat(product.getAverageCost()).isEqualTo(23.33);
	}

	@Test
	void recordAchatEntry_rejectsNonPositiveQuantity() {
		assertThatThrownBy(() -> stockService.recordAchatEntry("prod-1", 0, 20.0, "achat-1", "user-1"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("400");
		verify(productRepository, never()).findById(any());
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
