package com.harmoniedev.api.report.service;

import com.harmoniedev.api.achat.domain.dto.response.AchatResponse;
import com.harmoniedev.api.achat.service.AchatService;
import com.harmoniedev.api.common.dto.CurrencyAmount;
import com.harmoniedev.api.company.domain.dto.response.CompanyResponse;
import com.harmoniedev.api.company.service.CompanyService;
import com.harmoniedev.api.expense.domain.dto.response.DepenseResponse;
import com.harmoniedev.api.expense.service.DepenseService;
import com.harmoniedev.api.invoice.domain.dto.response.InvoiceItemResponse;
import com.harmoniedev.api.invoice.domain.dto.response.InvoiceResponse;
import com.harmoniedev.api.invoice.service.InvoiceService;
import com.harmoniedev.api.pdf.ReportPdfService;
import com.harmoniedev.api.report.domain.dto.response.FiscalReportResponse;
import com.harmoniedev.api.report.domain.dto.response.ReportOverviewResponse;
import com.harmoniedev.api.report.domain.dto.response.TopClientResponse;
import com.harmoniedev.api.report.domain.dto.response.TopServiceResponse;
import com.harmoniedev.api.stock.service.StockService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Invoices and expenses can each be recorded in different currencies, so a plain sum across all
 * of them is meaningless. totalRevenue/totalExpenses (and the top-clients/top-services rankings)
 * are computed for the platform's default currency (TND) only; revenueByCurrency/expensesByCurrency
 * carry the full per-currency breakdown for reporting.
 */
@Service
public class ReportService {
	private static final String DEFAULT_CURRENCY = "TND";

	/** Same "a sale actually happened" rule InvoiceService uses — a Devis is just a quote. */
	private static final Set<String> STOCK_MOVING_STATUSES = Set.of("Facture", "Bon de livraison");

	private final InvoiceService invoiceService;
	private final DepenseService depenseService;
	private final ReportPdfService reportPdfService;
	private final AchatService achatService;
	private final CompanyService companyService;
	private final StockService stockService;

	public ReportService(
			InvoiceService invoiceService,
			DepenseService depenseService,
			ReportPdfService reportPdfService,
			AchatService achatService,
			CompanyService companyService,
			StockService stockService) {
		this.invoiceService = invoiceService;
		this.depenseService = depenseService;
		this.reportPdfService = reportPdfService;
		this.achatService = achatService;
		this.companyService = companyService;
		this.stockService = stockService;
	}

	public byte[] exportPdf(String dateFrom, String dateTo, String currency) {
		return reportPdfService.generateReportPdf(
				overview(dateFrom, dateTo, currency),
				topClients(dateFrom, dateTo, 5, currency),
				topServices(dateFrom, dateTo, 5, currency),
				dateFrom, dateTo, resolveCurrency(currency));
	}

	public ReportOverviewResponse overview(String dateFrom, String dateTo, String currency) {
		String selected = resolveCurrency(currency);
		List<InvoiceResponse> invoices = standardInvoicesInRange(dateFrom, dateTo);
		List<DepenseResponse> depenses = depensesInRange(dateFrom, dateTo);
		double totalRevenue = invoices.stream()
				.filter(inv -> isCurrency(inv, selected))
				.mapToDouble(InvoiceResponse::getTotal)
				.sum();
		double totalExpenses = depenses.stream()
				.filter(d -> selected.equals(d.getCurrency()))
				.mapToDouble(DepenseResponse::getPrice)
				.sum();

		Map<String, Double> revenueByCurrency = invoices.stream()
				.collect(Collectors.groupingBy(
						inv -> inv.getCurrency() != null ? inv.getCurrency().getCode() : DEFAULT_CURRENCY,
						LinkedHashMap::new,
						Collectors.summingDouble(InvoiceResponse::getTotal)));
		Map<String, Double> expensesByCurrency = depenses.stream()
				.collect(Collectors.groupingBy(DepenseResponse::getCurrency, LinkedHashMap::new, Collectors.summingDouble(DepenseResponse::getPrice)));

		return ReportOverviewResponse.builder()
				.totalRevenue(round2(totalRevenue))
				.totalExpenses(round2(totalExpenses))
				.revenueByCurrency(toSortedCurrencyAmounts(revenueByCurrency, selected))
				.expensesByCurrency(toSortedCurrencyAmounts(expensesByCurrency, selected))
				.build();
	}

	public List<TopClientResponse> topClients(String dateFrom, String dateTo, int limit, String currency) {
		String selected = resolveCurrency(currency);
		List<InvoiceResponse> invoices = standardInvoicesInRange(dateFrom, dateTo).stream()
				.filter(inv -> isCurrency(inv, selected))
				.toList();
		Map<String, Double> totals = invoices.stream()
				.filter(inv -> inv.getClient() != null)
				.collect(Collectors.groupingBy(
						inv -> inv.getClient().getId(),
						Collectors.summingDouble(InvoiceResponse::getTotal)));
		Map<String, String> names = invoices.stream()
				.filter(inv -> inv.getClient() != null)
				.collect(Collectors.toMap(
						inv -> inv.getClient().getId(),
						inv -> clientDisplayName(inv),
						(a, b) -> a));
		return totals.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(limit)
				.map(e -> TopClientResponse.builder()
						.clientId(e.getKey())
						.nom(names.getOrDefault(e.getKey(), ""))
						.total(round2(e.getValue()))
						.build())
				.toList();
	}

	public List<TopServiceResponse> topServices(String dateFrom, String dateTo, int limit, String currency) {
		String selected = resolveCurrency(currency);
		List<InvoiceResponse> invoices = standardInvoicesInRange(dateFrom, dateTo).stream()
				.filter(inv -> isCurrency(inv, selected))
				.toList();
		Map<String, Double> totals = invoices.stream()
				.flatMap(inv -> inv.getItems().stream())
				.collect(Collectors.groupingBy(InvoiceItemResponse::getArticle, Collectors.summingDouble(InvoiceItemResponse::getTotal)));
		return totals.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(limit)
				.map(e -> TopServiceResponse.builder()
						.name(e.getKey())
						.totalSold(round2(e.getValue()))
						.build())
				.toList();
	}

	/**
	 * Indicative TVA/retenue-à-la-source/CMV figures for a period — a starting point for the
	 * monthly VAT declaration, not a substitute for a comptable's sign-off.
	 */
	public FiscalReportResponse fiscal(String dateFrom, String dateTo, String actorId) {
		CompanyResponse company = companyService.getOrCreate(actorId);
		boolean assujetti = company.isAssujettiTva();

		List<InvoiceResponse> sales = standardInvoicesInRange(dateFrom, dateTo).stream()
				.filter(inv -> STOCK_MOVING_STATUSES.contains(inv.getStatus()))
				.toList();
		double tvaCollectee = round2(sales.stream().mapToDouble(InvoiceResponse::getTaxAmount).sum());

		List<AchatResponse> achats = achatsInRange(dateFrom, dateTo);
		double achatTaxTotal = round2(achats.stream().mapToDouble(AchatResponse::getTaxAmount).sum());
		double tvaDeductible = assujetti ? achatTaxTotal : 0;
		double tvaNonRecuperable = assujetti ? 0 : achatTaxTotal;
		double retenueSourceTotal = round2(achats.stream().mapToDouble(AchatResponse::getRetenueSourceAmount).sum());

		return FiscalReportResponse.builder()
				.dateFrom(dateFrom)
				.dateTo(dateTo)
				.assujettiTva(assujetti)
				.regimeFiscal(company.getRegimeFiscal())
				.tvaCollectee(tvaCollectee)
				.tvaDeductible(tvaDeductible)
				.tvaNonRecuperable(tvaNonRecuperable)
				.soldeTva(round2(tvaCollectee - tvaDeductible))
				.retenueSourceTotal(retenueSourceTotal)
				.cmv(stockService.computeCmv(dateFrom, dateTo))
				.build();
	}

	private List<AchatResponse> achatsInRange(String dateFrom, String dateTo) {
		LocalDate from = parseOrNull(dateFrom);
		LocalDate to = parseOrNull(dateTo);
		return achatService.list().stream().filter(a -> inRange(a.getDate(), from, to)).toList();
	}

	private List<InvoiceResponse> standardInvoicesInRange(String dateFrom, String dateTo) {
		LocalDate from = parseOrNull(dateFrom);
		LocalDate to = parseOrNull(dateTo);
		return invoiceService.list().stream()
				.filter(inv -> "Standard".equals(inv.getType()))
				.filter(inv -> inRange(inv.getDate(), from, to))
				.toList();
	}

	private List<DepenseResponse> depensesInRange(String dateFrom, String dateTo) {
		LocalDate from = parseOrNull(dateFrom);
		LocalDate to = parseOrNull(dateTo);
		return depenseService.list().stream()
				.filter(d -> inRange(d.getCreated(), from, to))
				.toList();
	}

	private static boolean inRange(String isoDate, LocalDate from, LocalDate to) {
		try {
			LocalDate date = LocalDate.parse(isoDate);
			return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
		} catch (Exception ex) {
			return false;
		}
	}

	private static boolean inRange(Instant instant, LocalDate from, LocalDate to) {
		if (instant == null) return false;
		LocalDate date = instant.atZone(java.time.ZoneOffset.UTC).toLocalDate();
		return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
	}

	private static LocalDate parseOrNull(String date) {
		if (date == null || date.isBlank()) return null;
		try {
			return LocalDate.parse(date);
		} catch (Exception ex) {
			return null;
		}
	}

	private static String clientDisplayName(InvoiceResponse inv) {
		var client = inv.getClient();
		if (client.getPerson() != null) {
			return (client.getPerson().getPrenom() + " " + client.getPerson().getNom()).trim();
		}
		if (client.getEntreprise() != null) {
			return client.getEntreprise().getNom();
		}
		return "";
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private static boolean isCurrency(InvoiceResponse invoice, String currency) {
		return invoice.getCurrency() != null && currency.equals(invoice.getCurrency().getCode());
	}

	private static String resolveCurrency(String currency) {
		return (currency == null || currency.isBlank()) ? DEFAULT_CURRENCY : currency;
	}

	/** Selected currency first, then the rest alphabetically — so the UI can always show it up front. */
	private static List<CurrencyAmount> toSortedCurrencyAmounts(Map<String, Double> totals, String selected) {
		return totals.entrySet().stream()
				.map(e -> CurrencyAmount.builder().currency(e.getKey()).amount(round2(e.getValue())).build())
				.sorted(Comparator.comparing((CurrencyAmount c) -> !selected.equals(c.getCurrency()))
						.thenComparing(CurrencyAmount::getCurrency))
				.toList();
	}
}
