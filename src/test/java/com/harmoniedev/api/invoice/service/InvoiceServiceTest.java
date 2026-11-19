package com.harmoniedev.api.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harmoniedev.api.client.domain.dto.response.ClientResponse;
import com.harmoniedev.api.client.repository.ClientRepository;
import com.harmoniedev.api.client.service.ClientService;
import com.harmoniedev.api.company.service.CompanyService;
import com.harmoniedev.api.currency.domain.dto.response.CurrencyResponse;
import com.harmoniedev.api.currency.service.CurrencyService;
import com.harmoniedev.api.invoice.domain.dto.request.InvoiceItemRequest;
import com.harmoniedev.api.invoice.domain.dto.request.InvoiceRequest;
import com.harmoniedev.api.invoice.domain.dto.response.InvoiceResponse;
import com.harmoniedev.api.invoice.domain.model.InvoiceDocument;
import com.harmoniedev.api.invoice.domain.model.InvoiceItemDocument;
import com.harmoniedev.api.invoice.repository.InvoiceRepository;
import com.harmoniedev.api.mail.MailService;
import com.harmoniedev.api.pdf.InvoicePdfService;
import com.harmoniedev.api.plan.service.PlanUsageService;
import com.harmoniedev.api.stock.domain.dto.response.StockResponse;
import com.harmoniedev.api.stock.domain.model.StockSourceType;
import com.harmoniedev.api.stock.service.StockService;
import com.harmoniedev.api.storage.CloudinaryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {
	@Mock
	private InvoiceRepository repository;
	@Mock
	private ClientService clientService;
	@Mock
	private ClientRepository clientRepository;
	@Mock
	private CurrencyService currencyService;
	@Mock
	private com.harmoniedev.api.tax.repository.TaxRepository taxRepository;
	@Mock
	private CompanyService companyService;
	@Mock
	private InvoicePdfService invoicePdfService;
	@Mock
	private MailService mailService;
	@Mock
	private PlanUsageService planUsageService;
	@Mock
	private CloudinaryService cloudinaryService;
	@Mock
	private StockService stockService;

	private InvoiceService invoiceService;

	@BeforeEach
	void setUp() {
		invoiceService = new InvoiceService(
				repository,
				clientService,
				clientRepository,
				currencyService,
				taxRepository,
				companyService,
				invoicePdfService,
				mailService,
				planUsageService,
				cloudinaryService,
				stockService);
	}

	private void stubClientAndCurrency() {
		when(clientService.get("client-1")).thenReturn(ClientResponse.builder().id("client-1").build());
		when(currencyService.get("tnd")).thenReturn(CurrencyResponse.builder().id("tnd").build());
	}

	private InvoiceRequest requestWithStatus(String status, String productId) {
		InvoiceItemRequest.InvoiceItemRequestBuilder item = InvoiceItemRequest.builder()
				.article("Oud du Cambodge")
				.quantity(2)
				.price(50);
		if (productId != null) item.productId(productId);
		return InvoiceRequest.builder()
				.clientId("client-1")
				.currencyId("tnd")
				.status(status)
				.date("2026-11-19")
				.expirationDate("2026-12-19")
				.items(List.of(item.build()))
				.build();
	}

	@Test
	void create_factureWithProductLine_movesStockOut_andFlagsInvoice() {
		stubClientAndCurrency();
		when(stockService.getStock("prod-1")).thenReturn(
				StockResponse.builder().productId("prod-1").quantity(10).averageCost(20).build());
		when(repository.findByTypeAndYear("Standard", 2026)).thenReturn(List.of());
		when(repository.save(any(InvoiceDocument.class))).thenAnswer(inv -> {
			InvoiceDocument doc = inv.getArgument(0);
			if (doc.getId() == null) doc.setId("invoice-1");
			return doc;
		});

		InvoiceResponse response = invoiceService.create(requestWithStatus("Facture", "prod-1"), "user-1");

		assertThat(response.getId()).isEqualTo("invoice-1");
		verify(stockService).recordVenteExit("prod-1", 2.0, "invoice-1", "user-1");
	}

	@Test
	void create_devis_doesNotMoveStock_evenWithAProductLine() {
		stubClientAndCurrency();
		when(repository.findByTypeAndYear("Standard", 2026)).thenReturn(List.of());
		when(repository.save(any(InvoiceDocument.class))).thenAnswer(inv -> {
			InvoiceDocument doc = inv.getArgument(0);
			doc.setId("invoice-2");
			return doc;
		});

		invoiceService.create(requestWithStatus("Devis", "prod-1"), "user-1");

		verify(stockService, never()).getStock(anyString());
		verify(stockService, never()).recordVenteExit(anyString(), org.mockito.ArgumentMatchers.anyDouble(), anyString(), anyString());
	}

	@Test
	void create_lineWithoutProductId_isIgnoredByStock() {
		stubClientAndCurrency();
		when(repository.findByTypeAndYear("Standard", 2026)).thenReturn(List.of());
		when(repository.save(any(InvoiceDocument.class))).thenAnswer(inv -> {
			InvoiceDocument doc = inv.getArgument(0);
			doc.setId("invoice-3");
			return doc;
		});

		invoiceService.create(requestWithStatus("Facture", null), "user-1");

		verify(stockService, never()).recordVenteExit(anyString(), org.mockito.ArgumentMatchers.anyDouble(), anyString(), anyString());
	}

	@Test
	void create_insufficientStock_throwsConflict_andNeverSavesTheInvoice() {
		stubClientAndCurrency();
		when(repository.findByTypeAndYear("Standard", 2026)).thenReturn(List.of());
		when(stockService.getStock("prod-1")).thenReturn(
				StockResponse.builder().productId("prod-1").quantity(1).averageCost(20).build());

		assertThatThrownBy(() -> invoiceService.create(requestWithStatus("Facture", "prod-1"), "user-1"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("409")
				.hasMessageContaining("Stock insuffisant");

		verify(repository, never()).save(any());
		verify(stockService, never()).recordVenteExit(anyString(), org.mockito.ArgumentMatchers.anyDouble(), anyString(), anyString());
	}

	@Test
	void delete_reversesStock_onlyWhenPreviouslyApplied() {
		InvoiceDocument applied = InvoiceDocument.builder().id("invoice-1").createdBy("user-1").stockApplied(true).build();
		when(repository.findById("invoice-1")).thenReturn(java.util.Optional.of(applied));

		invoiceService.delete("invoice-1");

		verify(stockService).reverseMovementsForSource("invoice-1", StockSourceType.FACTURE_VENTE, "user-1");
		verify(repository).deleteById("invoice-1");
	}

	@Test
	void delete_notPreviouslyApplied_skipsStockReversal() {
		InvoiceDocument devis = InvoiceDocument.builder().id("invoice-2").createdBy("user-1").stockApplied(false).build();
		when(repository.findById("invoice-2")).thenReturn(java.util.Optional.of(devis));

		invoiceService.delete("invoice-2");

		verify(stockService, never()).reverseMovementsForSource(anyString(), any(), anyString());
		verify(repository).deleteById("invoice-2");
	}
}
