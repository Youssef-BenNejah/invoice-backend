package com.harmoniedev.api.invoice.service;

import com.harmoniedev.api.client.domain.dto.response.ClientResponse;
import com.harmoniedev.api.client.domain.model.ClientDocument;
import com.harmoniedev.api.client.repository.ClientRepository;
import com.harmoniedev.api.client.service.ClientService;
import com.harmoniedev.api.company.domain.dto.response.CompanyResponse;
import com.harmoniedev.api.company.service.CompanyService;
import com.harmoniedev.api.currency.domain.dto.response.CurrencyResponse;
import com.harmoniedev.api.currency.service.CurrencyService;
import com.harmoniedev.api.fournisseur.domain.dto.response.FournisseurResponse;
import com.harmoniedev.api.fournisseur.service.FournisseurService;
import com.harmoniedev.api.invoice.domain.dto.request.InvoiceImportRequest;
import com.harmoniedev.api.invoice.domain.dto.request.InvoiceImportRowRequest;
import com.harmoniedev.api.invoice.domain.dto.request.InvoiceItemRequest;
import com.harmoniedev.api.invoice.domain.dto.request.InvoiceRequest;
import com.harmoniedev.api.invoice.domain.dto.response.DocumentUploadResponse;
import com.harmoniedev.api.invoice.domain.dto.response.InvoiceImportResponse;
import com.harmoniedev.api.invoice.domain.dto.response.InvoiceImportRowResult;
import com.harmoniedev.api.invoice.domain.dto.response.InvoiceItemResponse;
import com.harmoniedev.api.invoice.domain.dto.response.InvoiceResponse;
import com.harmoniedev.api.invoice.domain.model.InvoiceDocument;
import com.harmoniedev.api.invoice.domain.model.InvoiceItemDocument;
import com.harmoniedev.api.invoice.repository.InvoiceRepository;
import com.harmoniedev.api.pdf.InvoicePdfService;
import com.harmoniedev.api.plan.service.PlanUsageService;
import com.harmoniedev.api.stock.domain.dto.response.StockResponse;
import com.harmoniedev.api.stock.domain.model.StockSourceType;
import com.harmoniedev.api.stock.service.StockService;
import com.harmoniedev.api.storage.CloudinaryService;
import com.harmoniedev.api.tax.domain.model.TaxDocument;
import com.harmoniedev.api.tax.repository.TaxRepository;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InvoiceService {
	private static final Set<String> VALID_PAYMENT_STATUSES = Set.of("impayé", "Partiellement payé", "Payé");

	/** Only these document types represent goods actually leaving — a Devis is just a quote. */
	private static final Set<String> STOCK_MOVING_STATUSES = Set.of("Facture", "Bon de livraison");

	private final InvoiceRepository repository;
	private final ClientService clientService;
	private final ClientRepository clientRepository;
	private final FournisseurService fournisseurService;
	private final CurrencyService currencyService;
	private final TaxRepository taxRepository;
	private final CompanyService companyService;
	private final InvoicePdfService invoicePdfService;
	private final PlanUsageService planUsageService;
	private final CloudinaryService cloudinaryService;
	private final StockService stockService;

	public InvoiceService(
			InvoiceRepository repository,
			ClientService clientService,
			ClientRepository clientRepository,
			FournisseurService fournisseurService,
			CurrencyService currencyService,
			TaxRepository taxRepository,
			CompanyService companyService,
			InvoicePdfService invoicePdfService,
			PlanUsageService planUsageService,
			CloudinaryService cloudinaryService,
			StockService stockService) {
		this.repository = repository;
		this.clientService = clientService;
		this.clientRepository = clientRepository;
		this.fournisseurService = fournisseurService;
		this.currencyService = currencyService;
		this.taxRepository = taxRepository;
		this.companyService = companyService;
		this.invoicePdfService = invoicePdfService;
		this.planUsageService = planUsageService;
		this.cloudinaryService = cloudinaryService;
		this.stockService = stockService;
	}

	/** Uploads a scanned invoice document (image or PDF) and returns its hosted URL. */
	public DocumentUploadResponse uploadDocument(MultipartFile file, String actorId) {
		var result = cloudinaryService.uploadDocument(file, "invoices/" + actorId, UUID.randomUUID().toString());
		return DocumentUploadResponse.builder().url(result.url()).build();
	}

	public InvoiceResponse create(InvoiceRequest request, String actorId) {
		planUsageService.assertCanCreateInvoice(actorId);
		String type = request.getType() != null && !request.getType().isBlank() ? request.getType() : "Standard";
		int year = Year.parse(request.getDate().substring(0, 4)).getValue();

		assertParty(type, request.getClientId(), request.getFournisseurId());
		currencyService.get(request.getCurrencyId());

		List<InvoiceItemDocument> items = buildItems(request.getItems());
		Totals totals = computeTotals(items, request.getTimbre());

		InvoiceDocument doc = InvoiceDocument.builder()
				.clientId(isAchat(type) ? null : request.getClientId())
				.fournisseurId(isAchat(type) ? request.getFournisseurId() : null)
				.currencyId(request.getCurrencyId())
				.number(nextNumber(type, year))
				.year(year)
				.status(request.getStatus())
				.paymentStatus("impayé")
				.type(type)
				.isConverted(false)
				.date(request.getDate())
				.items(items)
				.timbre(request.getTimbre())
				.subtotal(totals.subtotal())
				.taxAmount(totals.taxAmount())
				.total(totals.total())
				.paidAmount(0)
				.createdBy(actorId)
				.factureImage(request.getFactureImage())
				.build();
		assertStockAvailable(doc);
		repository.save(doc);
		if (applyStockIfEligible(doc, actorId)) {
			repository.save(doc);
		}
		return toResponse(doc);
	}

	/**
	 * Re-enters historical invoices from a previous system (no line-item detail — a single total
	 * per invoice) for a chosen date range. Deliberately bypasses {@link PlanUsageService}: these
	 * are backdated records being migrated in, not new usage the current plan should be judged by.
	 */
	public InvoiceImportResponse bulkImport(InvoiceImportRequest request, String actorId) {
		LocalDate from = LocalDate.parse(request.getFromDate());
		LocalDate to = LocalDate.parse(request.getToDate());
		List<ClientDocument> tenantClients = clientRepository.findByCreatedBy(actorId);
		List<CurrencyResponse> tenantCurrencies = currencyService.list(actorId);

		List<InvoiceImportRowResult> results = new ArrayList<>();
		int imported = 0;
		for (InvoiceImportRowRequest row : request.getRows()) {
			try {
				results.add(importRow(row, from, to, tenantClients, tenantCurrencies, actorId));
				imported++;
			} catch (RowImportException ex) {
				results.add(InvoiceImportRowResult.builder().line(row.getLine()).success(false).message(ex.getMessage()).build());
			}
		}
		return InvoiceImportResponse.builder()
				.imported(imported)
				.failed(results.size() - imported)
				.results(results)
				.build();
	}

	private InvoiceImportRowResult importRow(
			InvoiceImportRowRequest row,
			LocalDate from,
			LocalDate to,
			List<ClientDocument> tenantClients,
			List<CurrencyResponse> tenantCurrencies,
			String actorId) {
		LocalDate date;
		try {
			date = LocalDate.parse(row.getDate());
		} catch (DateTimeParseException ex) {
			throw new RowImportException("Date invalide (attendu AAAA-MM-JJ)");
		}
		if (date.isBefore(from) || date.isAfter(to)) {
			throw new RowImportException("Date hors de la période sélectionnée");
		}

		ClientDocument client = tenantClients.stream()
				.filter(c -> clientDisplayName(c).equalsIgnoreCase(row.getClient().trim()))
				.findFirst()
				.orElseThrow(() -> new RowImportException("Client introuvable : " + row.getClient()));

		CurrencyResponse currency = tenantCurrencies.stream()
				.filter(c -> c.getCode().equalsIgnoreCase(row.getDevise().trim()))
				.findFirst()
				.orElseThrow(() -> new RowImportException("Devise introuvable : " + row.getDevise()));

		String type = row.getType() != null && !row.getType().isBlank() ? row.getType() : "Standard";
		if (!"Standard".equals(type) && !"Proforma".equals(type)) {
			throw new RowImportException("Type invalide : " + row.getType());
		}
		String paymentStatus = row.getStatut() != null && VALID_PAYMENT_STATUSES.contains(row.getStatut())
				? row.getStatut() : "impayé";
		double total = round2(row.getMontant());
		double paid = switch (paymentStatus) {
			case "Payé" -> total;
			case "Partiellement payé" -> row.getMontantPaye() != null ? round2(Math.min(row.getMontantPaye(), total)) : 0;
			default -> 0;
		};
		int year = date.getYear();
		int number = row.getNumero() != null ? row.getNumero() : nextNumber(type, year);

		InvoiceDocument doc = InvoiceDocument.builder()
				.clientId(client.getId())
				.currencyId(currency.getId())
				.number(number)
				.year(year)
				.status("Facture")
				.paymentStatus(paymentStatus)
				.type(type)
				.isConverted(false)
				.date(row.getDate())
				.items(List.of())
				.timbre(0)
				.subtotal(total)
				.taxAmount(0)
				.total(total)
				.paidAmount(paid)
				.createdBy(actorId)
				.factureImage(row.getFactureImage())
				.build();
		repository.save(doc);
		return InvoiceImportRowResult.builder().line(row.getLine()).success(true).invoiceId(doc.getId()).build();
	}

	private static String clientDisplayName(ClientDocument client) {
		if (client.getPerson() != null) {
			return (nullToEmpty(client.getPerson().getPrenom()) + " " + nullToEmpty(client.getPerson().getNom())).trim();
		}
		if (client.getEntreprise() != null) return nullToEmpty(client.getEntreprise().getNom());
		return "";
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static class RowImportException extends RuntimeException {
		RowImportException(String message) {
			super(message);
		}
	}

	public List<InvoiceResponse> list() {
		return repository.findAll().stream().map(this::toResponse).toList();
	}

	public InvoiceResponse get(String id) {
		return toResponse(findOrThrow(id));
	}

	public InvoiceResponse update(String id, InvoiceRequest request) {
		InvoiceDocument doc = findOrThrow(id);
		assertParty(doc.getType(), request.getClientId(), request.getFournisseurId());
		currencyService.get(request.getCurrencyId());

		List<InvoiceItemDocument> items = buildItems(request.getItems());
		Totals totals = computeTotals(items, request.getTimbre());

		// Reverse whatever stock this invoice previously moved before applying the edited version —
		// a VENTE reversal is always order-safe, so this is never blocked.
		reverseStockIfApplied(doc, doc.getCreatedBy());

		if (isAchat(doc.getType())) {
			doc.setFournisseurId(request.getFournisseurId());
		} else {
			doc.setClientId(request.getClientId());
		}
		doc.setCurrencyId(request.getCurrencyId());
		doc.setStatus(request.getStatus());
		doc.setDate(request.getDate());
		doc.setItems(items);
		doc.setTimbre(request.getTimbre());
		doc.setSubtotal(totals.subtotal());
		doc.setTaxAmount(totals.taxAmount());
		doc.setTotal(totals.total());
		doc.setFactureImage(request.getFactureImage());
		// type is immutable after creation — request.type is ignored here.
		assertStockAvailable(doc);
		repository.save(doc);
		if (applyStockIfEligible(doc, doc.getCreatedBy())) {
			repository.save(doc);
		}
		return toResponse(doc);
	}

	public void delete(String id) {
		InvoiceDocument doc = findOrThrow(id);
		reverseStockIfApplied(doc, doc.getCreatedBy());
		repository.deleteById(id);
	}

	public InvoiceResponse duplicate(String id) {
		InvoiceDocument source = findOrThrow(id);
		planUsageService.assertCanCreateInvoice(source.getCreatedBy());
		InvoiceDocument copy = InvoiceDocument.builder()
				.clientId(source.getClientId())
				.fournisseurId(source.getFournisseurId())
				.currencyId(source.getCurrencyId())
				.number(nextNumber(source.getType(), source.getYear()))
				.year(source.getYear())
				.status(source.getStatus())
				.paymentStatus("impayé")
				.type(source.getType())
				.isConverted(false)
				.date(source.getDate())
				.items(source.getItems().stream()
						.map(it -> InvoiceItemDocument.builder()
								.id(UUID.randomUUID().toString())
								.productId(it.getProductId())
								.ref(it.getRef())
								.article(it.getArticle())
								.description(it.getDescription())
								.quantity(it.getQuantity())
								.price(it.getPrice())
								.taxId(it.getTaxId())
								.taxRate(it.getTaxRate())
								.taxAmount(it.getTaxAmount())
								.taxName(it.getTaxName())
								.total(it.getTotal())
								.build())
						.toList())
				.timbre(source.getTimbre())
				.subtotal(source.getSubtotal())
				.taxAmount(source.getTaxAmount())
				.total(source.getTotal())
				.paidAmount(0)
				.createdBy(source.getCreatedBy())
				.factureImage(source.getFactureImage())
				.build();
		assertStockAvailable(copy);
		repository.save(copy);
		if (applyStockIfEligible(copy, source.getCreatedBy())) {
			repository.save(copy);
		}
		return toResponse(copy);
	}

	/** Only valid on a Proforma: marks it converted and creates a Standard duplicate. */
	public InvoiceResponse convert(String id) {
		InvoiceDocument source = findOrThrow(id);
		if (!"Proforma".equals(source.getType())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only a Proforma invoice can be converted");
		}
		planUsageService.assertCanCreateInvoice(source.getCreatedBy());
		source.setConverted(true);
		repository.save(source);

		InvoiceDocument standard = InvoiceDocument.builder()
				.clientId(source.getClientId())
				.fournisseurId(source.getFournisseurId())
				.currencyId(source.getCurrencyId())
				.number(nextNumber("Standard", source.getYear()))
				.year(source.getYear())
				.status(source.getStatus())
				.paymentStatus("impayé")
				.type("Standard")
				.isConverted(false)
				.date(source.getDate())
				.items(source.getItems().stream()
						.map(it -> InvoiceItemDocument.builder()
								.id(UUID.randomUUID().toString())
								.productId(it.getProductId())
								.ref(it.getRef())
								.article(it.getArticle())
								.description(it.getDescription())
								.quantity(it.getQuantity())
								.price(it.getPrice())
								.taxId(it.getTaxId())
								.taxRate(it.getTaxRate())
								.taxAmount(it.getTaxAmount())
								.taxName(it.getTaxName())
								.total(it.getTotal())
								.build())
						.toList())
				.timbre(source.getTimbre())
				.subtotal(source.getSubtotal())
				.taxAmount(source.getTaxAmount())
				.total(source.getTotal())
				.paidAmount(0)
				.createdBy(source.getCreatedBy())
				.factureImage(source.getFactureImage())
				.build();
		assertStockAvailable(standard);
		repository.save(standard);
		if (applyStockIfEligible(standard, source.getCreatedBy())) {
			repository.save(standard);
		}
		return toResponse(standard);
	}

	/** Called by PaymentService after recording/reversing a payment. */
	public void applyPayment(String invoiceId, double newPaidAmount) {
		InvoiceDocument doc = findOrThrow(invoiceId);
		doc.setPaidAmount(newPaidAmount);
		doc.setPaymentStatus(derivePaymentStatus(newPaidAmount, doc.getTotal()));
		repository.save(doc);
	}

	InvoiceDocument findOrThrow(String id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
	}

	public byte[] renderPdf(String id) {
		InvoiceResponse invoice = get(id);
		CompanyResponse company = companyService.getOrCreate(findOrThrow(id).getCreatedBy());
		return invoicePdfService.generateInvoicePdf(invoice, company);
	}

	public byte[] renderSummaryPdf(List<String> ids, String title) {
		List<InvoiceResponse> invoices = ids == null || ids.isEmpty()
				? list()
				: ids.stream().map(this::get).toList();
		return invoicePdfService.generateSummaryPdf(invoices, title);
	}

	public List<InvoiceResponse> resolveForExport(List<String> ids) {
		return ids == null || ids.isEmpty() ? list() : ids.stream().map(this::get).toList();
	}

	public byte[] renderZip(List<String> ids) {
		List<InvoiceResponse> invoices = resolveForExport(ids);
		var out = new java.io.ByteArrayOutputStream();
		try (var zip = new java.util.zip.ZipOutputStream(out)) {
			for (InvoiceResponse invoice : invoices) {
				CompanyResponse company = companyService.getOrCreate(findOrThrow(invoice.getId()).getCreatedBy());
				byte[] pdf = invoicePdfService.generateInvoicePdf(invoice, company);
				String label = invoice.getStatus() + " " + invoice.getNumber() + "/" + invoice.getYear();
				String who = invoice.getClient() != null ? clientName(invoice.getClient()) : fournisseurName(invoice.getFournisseur());
				String fileName = (label + (who.isBlank() ? "" : "-" + who) + ".pdf").replace(" ", "-").replace("/", "-");
				zip.putNextEntry(new java.util.zip.ZipEntry(fileName));
				zip.write(pdf);
				zip.closeEntry();
			}
		} catch (java.io.IOException ex) {
			throw new IllegalStateException("Failed to build ZIP export", ex);
		}
		return out.toByteArray();
	}

	private static String fournisseurName(FournisseurResponse fournisseur) {
		return fournisseur == null ? "" : fournisseur.getNom();
	}

	private static String clientName(ClientResponse client) {
		if (client == null) return "";
		if (client.getPerson() != null) {
			return (client.getPerson().getPrenom() + " " + client.getPerson().getNom()).trim();
		}
		if (client.getEntreprise() != null) return client.getEntreprise().getNom();
		return "";
	}

	private int nextNumber(String type, int year) {
		return repository.findByTypeAndYear(type, year).stream()
				.mapToInt(InvoiceDocument::getNumber)
				.max()
				.orElse(0) + 1;
	}

	private List<InvoiceItemDocument> buildItems(List<InvoiceItemRequest> requests) {
		return requests.stream().map(item -> {
			double base = item.getQuantity() * item.getPrice();
			TaxDocument tax = (item.getTaxId() != null && !item.getTaxId().isBlank())
					? taxRepository.findById(item.getTaxId()).orElse(null) : null;
			double taxRate = tax != null ? tax.getTaxvalue() : 0;
			double taxAmount = round2(base * taxRate / 100);
			return InvoiceItemDocument.builder()
					.id(UUID.randomUUID().toString())
					.productId(item.getProductId())
					.ref(item.getRef())
					.article(item.getArticle())
					.description(item.getDescription())
					.quantity(item.getQuantity())
					.price(item.getPrice())
					.taxId(tax != null ? tax.getId() : "")
					.taxRate(taxRate)
					.taxAmount(taxAmount)
					.taxName(tax != null ? tax.getName() : "")
					.total(round2(base + taxAmount))
					.build();
		}).toList();
	}

	private Totals computeTotals(List<InvoiceItemDocument> items, double timbre) {
		double itemsTotal = round2(items.stream().mapToDouble(InvoiceItemDocument::getTotal).sum());
		double taxAmount = round2(items.stream().mapToDouble(InvoiceItemDocument::getTaxAmount).sum());
		double subtotal = round2(itemsTotal + timbre);
		return new Totals(subtotal, taxAmount, subtotal);
	}

	private record Totals(double subtotal, double taxAmount, double total) {
	}

	private static boolean isAchat(String type) {
		return "Achat".equals(type);
	}

	private boolean isStockMoving(InvoiceDocument doc) {
		boolean direction = "Standard".equals(doc.getType()) || isAchat(doc.getType());
		return direction && STOCK_MOVING_STATUSES.contains(doc.getStatus());
	}

	/** Validates whichever party the invoice type requires, and only that one. */
	private void assertParty(String type, String clientId, String fournisseurId) {
		if (isAchat(type)) {
			if (fournisseurId == null || fournisseurId.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fournisseurId is required for a Facture Achat");
			}
			fournisseurService.get(fournisseurId);
		} else {
			if (clientId == null || clientId.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientId is required");
			}
			clientService.get(clientId);
		}
	}

	/** Pre-flight check so a sale with insufficient stock never gets saved in the first place — an achat can never be "insufficient". */
	private void assertStockAvailable(InvoiceDocument doc) {
		if (!isStockMoving(doc) || isAchat(doc.getType())) return;
		for (InvoiceItemDocument item : doc.getItems()) {
			if (item.getProductId() == null || item.getProductId().isBlank()) continue;
			StockResponse stock = stockService.getStock(item.getProductId());
			if (stock.getQuantity() - item.getQuantity() < -1e-6) {
				throw new ResponseStatusException(HttpStatus.CONFLICT,
						"Stock insuffisant pour \"" + item.getArticle() + "\" (disponible: " + stock.getQuantity()
								+ ", demandé: " + item.getQuantity() + ")");
			}
		}
	}

	/** Moves stock for every eligible line (direction depends on type) and flags the invoice. Returns whether anything changed. */
	private boolean applyStockIfEligible(InvoiceDocument doc, String actorId) {
		if (!isStockMoving(doc)) return false;
		boolean achat = isAchat(doc.getType());
		for (InvoiceItemDocument item : doc.getItems()) {
			if (item.getProductId() == null || item.getProductId().isBlank()) continue;
			if (achat) {
				stockService.recordAchatEntry(item.getProductId(), item.getQuantity(), item.getPrice(), doc.getId(), actorId);
			} else {
				stockService.recordVenteExit(item.getProductId(), item.getQuantity(), doc.getId(), actorId);
			}
		}
		doc.setStockApplied(true);
		return true;
	}

	/** Reverses any stock this invoice previously moved. Returns whether anything changed. */
	private boolean reverseStockIfApplied(InvoiceDocument doc, String actorId) {
		if (!doc.isStockApplied()) return false;
		StockSourceType sourceType = isAchat(doc.getType()) ? StockSourceType.FACTURE_ACHAT : StockSourceType.FACTURE_VENTE;
		stockService.reverseMovementsForSource(doc.getId(), sourceType, actorId);
		doc.setStockApplied(false);
		return true;
	}

	private static String derivePaymentStatus(double paid, double total) {
		if (paid >= total && total > 0) return "Payé";
		if (paid <= 0) return "impayé";
		return "Partiellement payé";
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private InvoiceResponse toResponse(InvoiceDocument doc) {
		ClientResponse client = null;
		FournisseurResponse fournisseur = null;
		CurrencyResponse currency;
		if (doc.getClientId() != null && !doc.getClientId().isBlank()) {
			try {
				client = clientService.get(doc.getClientId());
			} catch (ResponseStatusException ex) {
				client = null;
			}
		}
		if (doc.getFournisseurId() != null && !doc.getFournisseurId().isBlank()) {
			try {
				fournisseur = fournisseurService.get(doc.getFournisseurId());
			} catch (ResponseStatusException ex) {
				fournisseur = null;
			}
		}
		try {
			currency = currencyService.get(doc.getCurrencyId());
		} catch (ResponseStatusException ex) {
			currency = null;
		}
		return InvoiceResponse.builder()
				.id(doc.getId())
				.client(client)
				.fournisseur(fournisseur)
				.number(doc.getNumber())
				.year(doc.getYear())
				.currency(currency)
				.status(doc.getStatus())
				.paymentStatus(doc.getPaymentStatus())
				.type(doc.getType())
				.converted(doc.isConverted())
				.date(doc.getDate())
				.items(doc.getItems().stream()
						.map(it -> InvoiceItemResponse.builder()
								.id(it.getId())
								.productId(it.getProductId())
								.ref(it.getRef())
								.article(it.getArticle())
								.description(it.getDescription())
								.quantity(it.getQuantity())
								.price(it.getPrice())
								.taxId(it.getTaxId())
								.taxRate(it.getTaxRate())
								.taxAmount(it.getTaxAmount())
								.taxName(it.getTaxName())
								.total(it.getTotal())
								.build())
						.toList())
				.timbre(doc.getTimbre())
				.subtotal(doc.getSubtotal())
				.taxAmount(doc.getTaxAmount())
				.total(doc.getTotal())
				.paidAmount(doc.getPaidAmount())
				.createdBy(doc.getCreatedBy())
				.created(doc.getCreated())
				.factureImage(doc.getFactureImage())
				.build();
	}
}
