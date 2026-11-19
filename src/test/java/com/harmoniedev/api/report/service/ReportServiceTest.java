package com.harmoniedev.api.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.harmoniedev.api.achat.domain.dto.response.AchatResponse;
import com.harmoniedev.api.achat.service.AchatService;
import com.harmoniedev.api.company.domain.dto.response.CompanyResponse;
import com.harmoniedev.api.company.service.CompanyService;
import com.harmoniedev.api.currency.domain.dto.response.CurrencyResponse;
import com.harmoniedev.api.expense.service.DepenseService;
import com.harmoniedev.api.invoice.domain.dto.response.InvoiceResponse;
import com.harmoniedev.api.invoice.service.InvoiceService;
import com.harmoniedev.api.pdf.ReportPdfService;
import com.harmoniedev.api.report.domain.dto.response.FiscalReportResponse;
import com.harmoniedev.api.stock.service.StockService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
	@Mock
	private InvoiceService invoiceService;
	@Mock
	private DepenseService depenseService;
	@Mock
	private ReportPdfService reportPdfService;
	@Mock
	private AchatService achatService;
	@Mock
	private CompanyService companyService;
	@Mock
	private StockService stockService;

	private ReportService reportService;

	@BeforeEach
	void setUp() {
		reportService = new ReportService(invoiceService, depenseService, reportPdfService, achatService, companyService, stockService);
	}

	private InvoiceResponse sale(String status, double taxAmount, String date) {
		return InvoiceResponse.builder()
				.type("Standard")
				.status(status)
				.date(date)
				.taxAmount(taxAmount)
				.currency(CurrencyResponse.builder().code("TND").build())
				.build();
	}

	@Test
	void fiscal_assujettiCompany_countsAchatTaxAsDeductible() {
		when(companyService.getOrCreate("user-1")).thenReturn(
				CompanyResponse.builder().assujettiTva(true).regimeFiscal("Réel").build());
		when(invoiceService.list()).thenReturn(List.of(
				sale("Facture", 190, "2026-11-10"),
				sale("Devis", 999, "2026-11-10"))); // a Devis must never count toward TVA collectée
		when(achatService.list()).thenReturn(List.of(
				AchatResponse.builder().date("2026-11-05").taxAmount(95).retenueSourceAmount(15).build()));
		when(stockService.computeCmv("2026-11-01", "2026-11-30")).thenReturn(400.0);

		FiscalReportResponse response = reportService.fiscal("2026-11-01", "2026-11-30", "user-1");

		assertThat(response.getTvaCollectee()).isEqualTo(190);
		assertThat(response.getTvaDeductible()).isEqualTo(95);
		assertThat(response.getTvaNonRecuperable()).isEqualTo(0);
		assertThat(response.getSoldeTva()).isEqualTo(95);
		assertThat(response.getRetenueSourceTotal()).isEqualTo(15);
		assertThat(response.getCmv()).isEqualTo(400.0);
	}

	@Test
	void fiscal_nonAssujettiCompany_achatTaxIsNonRecoverable_notDeductible() {
		when(companyService.getOrCreate("user-1")).thenReturn(
				CompanyResponse.builder().assujettiTva(false).regimeFiscal("Forfaitaire").build());
		when(invoiceService.list()).thenReturn(List.of(sale("Facture", 190, "2026-11-10")));
		when(achatService.list()).thenReturn(List.of(
				AchatResponse.builder().date("2026-11-05").taxAmount(95).retenueSourceAmount(0).build()));
		when(stockService.computeCmv("2026-11-01", "2026-11-30")).thenReturn(0.0);

		FiscalReportResponse response = reportService.fiscal("2026-11-01", "2026-11-30", "user-1");

		assertThat(response.getTvaDeductible()).isEqualTo(0);
		assertThat(response.getTvaNonRecuperable()).isEqualTo(95);
		assertThat(response.getSoldeTva()).isEqualTo(190);
	}
}
