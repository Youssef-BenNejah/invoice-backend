package com.harmoniedev.api.achat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harmoniedev.api.achat.domain.dto.request.AchatItemRequest;
import com.harmoniedev.api.achat.domain.dto.request.AchatRequest;
import com.harmoniedev.api.achat.domain.dto.response.AchatResponse;
import com.harmoniedev.api.achat.domain.model.AchatDocument;
import com.harmoniedev.api.achat.repository.AchatRepository;
import com.harmoniedev.api.catalog.domain.model.ProductDocument;
import com.harmoniedev.api.catalog.repository.ProductRepository;
import com.harmoniedev.api.company.domain.dto.response.CompanyResponse;
import com.harmoniedev.api.company.service.CompanyService;
import com.harmoniedev.api.currency.domain.dto.response.CurrencyResponse;
import com.harmoniedev.api.currency.service.CurrencyService;
import com.harmoniedev.api.fournisseur.domain.dto.response.FournisseurResponse;
import com.harmoniedev.api.fournisseur.service.FournisseurService;
import com.harmoniedev.api.stock.domain.model.StockSourceType;
import com.harmoniedev.api.stock.service.StockService;
import com.harmoniedev.api.tax.repository.TaxRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AchatServiceTest {
	@Mock
	private AchatRepository repository;
	@Mock
	private FournisseurService fournisseurService;
	@Mock
	private CurrencyService currencyService;
	@Mock
	private TaxRepository taxRepository;
	@Mock
	private ProductRepository productRepository;
	@Mock
	private StockService stockService;
	@Mock
	private CompanyService companyService;

	private AchatService achatService;

	@BeforeEach
	void setUp() {
		achatService = new AchatService(
				repository, fournisseurService, currencyService, taxRepository, productRepository, stockService, companyService);
	}

	@Test
	void create_recordsAchatEntryPerLine_atUnitPriceHT_notTTC() {
		when(fournisseurService.get("supplier-1")).thenReturn(FournisseurResponse.builder().id("supplier-1").build());
		when(currencyService.get("tnd")).thenReturn(CurrencyResponse.builder().id("tnd").build());
		when(productRepository.findById("prod-1"))
				.thenReturn(Optional.of(ProductDocument.builder().id("prod-1").name("Oud du Cambodge").build()));
		when(repository.findByYear(2026)).thenReturn(List.of());
		when(companyService.getOrCreate("user-1")).thenReturn(CompanyResponse.builder().assujettiTva(true).build());
		when(repository.save(any(AchatDocument.class))).thenAnswer(inv -> {
			AchatDocument doc = inv.getArgument(0);
			doc.setId("achat-1");
			return doc;
		});

		AchatRequest request = AchatRequest.builder()
				.fournisseurId("supplier-1")
				.currencyId("tnd")
				.date("2026-11-19")
				.items(List.of(AchatItemRequest.builder().productId("prod-1").quantity(10).unitPriceHT(20.0).build()))
				.build();

		AchatResponse response = achatService.create(request, "user-1");

		assertThat(response.getId()).isEqualTo("achat-1");
		assertThat(response.getSubtotal()).isEqualTo(200.0);
		verify(stockService).recordAchatEntry("prod-1", 10.0, 20.0, "achat-1", "user-1");
	}

	@Test
	void create_nonAssujettiCompany_foldsNonRecoverableTvaIntoStockCost() {
		when(fournisseurService.get("supplier-1")).thenReturn(FournisseurResponse.builder().id("supplier-1").build());
		when(currencyService.get("tnd")).thenReturn(CurrencyResponse.builder().id("tnd").build());
		when(productRepository.findById("prod-1"))
				.thenReturn(Optional.of(ProductDocument.builder().id("prod-1").name("Oud du Cambodge").build()));
		when(repository.findByYear(2026)).thenReturn(List.of());
		when(repository.save(any(AchatDocument.class))).thenAnswer(inv -> {
			AchatDocument doc = inv.getArgument(0);
			doc.setId("achat-1");
			return doc;
		});
		// Non-assujetti : 19% de TVA sur 20.0/unité = 3.80/unité non récupérable -> coût stock = 23.80.
		when(companyService.getOrCreate("user-1")).thenReturn(CompanyResponse.builder().assujettiTva(false).build());
		com.harmoniedev.api.tax.domain.model.TaxDocument tva =
				com.harmoniedev.api.tax.domain.model.TaxDocument.builder().id("tva-19").name("TVA").taxvalue(19.0).build();
		when(taxRepository.findById("tva-19")).thenReturn(Optional.of(tva));

		AchatRequest request = AchatRequest.builder()
				.fournisseurId("supplier-1")
				.currencyId("tnd")
				.date("2026-11-19")
				.items(List.of(AchatItemRequest.builder().productId("prod-1").quantity(10).unitPriceHT(20.0).taxId("tva-19").build()))
				.build();

		achatService.create(request, "user-1");

		verify(stockService).recordAchatEntry("prod-1", 10.0, 23.8, "achat-1", "user-1");
	}

	@Test
	void create_retenueSourceRate_computesAmountAndNetPayable() {
		when(fournisseurService.get("supplier-1")).thenReturn(FournisseurResponse.builder().id("supplier-1").build());
		when(currencyService.get("tnd")).thenReturn(CurrencyResponse.builder().id("tnd").build());
		when(productRepository.findById("prod-1"))
				.thenReturn(Optional.of(ProductDocument.builder().id("prod-1").name("Oud du Cambodge").build()));
		when(repository.findByYear(2026)).thenReturn(List.of());
		when(repository.save(any(AchatDocument.class))).thenAnswer(inv -> {
			AchatDocument doc = inv.getArgument(0);
			doc.setId("achat-1");
			return doc;
		});
		when(companyService.getOrCreate("user-1")).thenReturn(CompanyResponse.builder().assujettiTva(true).build());

		AchatRequest request = AchatRequest.builder()
				.fournisseurId("supplier-1")
				.currencyId("tnd")
				.date("2026-11-19")
				.retenueSourceRate(1.5)
				.items(List.of(AchatItemRequest.builder().productId("prod-1").quantity(10).unitPriceHT(200.0).build()))
				.build();

		AchatResponse response = achatService.create(request, "user-1");

		assertThat(response.getTotal()).isEqualTo(2000.0);
		assertThat(response.getRetenueSourceAmount()).isEqualTo(30.0);
		assertThat(response.getMontantNetAPayer()).isEqualTo(1970.0);
	}

	@Test
	void create_invalidRetenueRate_isRejected() {
		when(fournisseurService.get("supplier-1")).thenReturn(FournisseurResponse.builder().id("supplier-1").build());
		when(currencyService.get("tnd")).thenReturn(CurrencyResponse.builder().id("tnd").build());

		AchatRequest request = AchatRequest.builder()
				.fournisseurId("supplier-1")
				.currencyId("tnd")
				.date("2026-11-19")
				.retenueSourceRate(2.0)
				.items(List.of(AchatItemRequest.builder().productId("prod-1").quantity(1).unitPriceHT(10).build()))
				.build();

		assertThatThrownBy(() -> achatService.create(request, "user-1"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("400");
		verify(repository, never()).save(any());
	}

	@Test
	void create_unknownProduct_throwsNotFound_andNeverTouchesStock() {
		when(fournisseurService.get("supplier-1")).thenReturn(FournisseurResponse.builder().id("supplier-1").build());
		when(currencyService.get("tnd")).thenReturn(CurrencyResponse.builder().id("tnd").build());
		when(productRepository.findById("missing")).thenReturn(Optional.empty());

		AchatRequest request = AchatRequest.builder()
				.fournisseurId("supplier-1")
				.currencyId("tnd")
				.date("2026-11-19")
				.items(List.of(AchatItemRequest.builder().productId("missing").quantity(1).unitPriceHT(10).build()))
				.build();

		assertThatThrownBy(() -> achatService.create(request, "user-1"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404");

		verify(repository, never()).save(any());
		verify(stockService, never()).recordAchatEntry(anyString(), anyDouble(), anyDouble(), anyString(), anyString());
	}

	@Test
	void delete_reversesStock_beforeDeletingTheDocument() {
		AchatDocument doc = AchatDocument.builder().id("achat-1").createdBy("user-1").items(List.of()).build();
		when(repository.findById("achat-1")).thenReturn(Optional.of(doc));

		achatService.delete("achat-1");

		verify(stockService).reverseMovementsForSource("achat-1", StockSourceType.FACTURE_ACHAT, "user-1");
		verify(repository).deleteById("achat-1");
	}

	@Test
	void delete_whenStockHasMovedSince_propagatesConflict_andDoesNotDeleteTheDocument() {
		AchatDocument doc = AchatDocument.builder().id("achat-1").createdBy("user-1").items(List.of()).build();
		when(repository.findById("achat-1")).thenReturn(Optional.of(doc));
		org.mockito.Mockito.doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "stock moved"))
				.when(stockService).reverseMovementsForSource("achat-1", StockSourceType.FACTURE_ACHAT, "user-1");

		assertThatThrownBy(() -> achatService.delete("achat-1"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("409");

		verify(repository, never()).deleteById(anyString());
	}
}
