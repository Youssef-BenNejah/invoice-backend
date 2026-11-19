package com.harmoniedev.api.achat.service;

import com.harmoniedev.api.achat.domain.dto.request.AchatItemRequest;
import com.harmoniedev.api.achat.domain.dto.request.AchatRequest;
import com.harmoniedev.api.achat.domain.dto.response.AchatItemResponse;
import com.harmoniedev.api.achat.domain.dto.response.AchatResponse;
import com.harmoniedev.api.achat.domain.model.AchatDocument;
import com.harmoniedev.api.achat.domain.model.AchatItemDocument;
import com.harmoniedev.api.achat.repository.AchatRepository;
import com.harmoniedev.api.catalog.domain.model.ProductDocument;
import com.harmoniedev.api.catalog.repository.ProductRepository;
import com.harmoniedev.api.currency.domain.dto.response.CurrencyResponse;
import com.harmoniedev.api.currency.service.CurrencyService;
import com.harmoniedev.api.fournisseur.domain.dto.response.FournisseurResponse;
import com.harmoniedev.api.fournisseur.service.FournisseurService;
import com.harmoniedev.api.stock.domain.model.StockSourceType;
import com.harmoniedev.api.stock.service.StockService;
import com.harmoniedev.api.tax.domain.model.TaxDocument;
import com.harmoniedev.api.tax.repository.TaxRepository;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AchatService {
	private final AchatRepository repository;
	private final FournisseurService fournisseurService;
	private final CurrencyService currencyService;
	private final TaxRepository taxRepository;
	private final ProductRepository productRepository;
	private final StockService stockService;

	public AchatService(
			AchatRepository repository,
			FournisseurService fournisseurService,
			CurrencyService currencyService,
			TaxRepository taxRepository,
			ProductRepository productRepository,
			StockService stockService) {
		this.repository = repository;
		this.fournisseurService = fournisseurService;
		this.currencyService = currencyService;
		this.taxRepository = taxRepository;
		this.productRepository = productRepository;
		this.stockService = stockService;
	}

	/** Creating an Achat is validating it: stock moves in immediately, there is no draft state. */
	public AchatResponse create(AchatRequest request, String actorId) {
		fournisseurService.get(request.getFournisseurId());
		currencyService.get(request.getCurrencyId());
		int year = Year.parse(request.getDate().substring(0, 4)).getValue();

		List<AchatItemDocument> items = buildItems(request.getItems());
		Totals totals = computeTotals(items);

		AchatDocument doc = AchatDocument.builder()
				.fournisseurId(request.getFournisseurId())
				.currencyId(request.getCurrencyId())
				.number(nextNumber(year))
				.year(year)
				.date(request.getDate())
				.note(request.getNote())
				.items(items)
				.subtotal(totals.subtotal())
				.taxAmount(totals.taxAmount())
				.total(totals.total())
				.createdBy(actorId)
				.build();
		repository.save(doc);

		for (AchatItemDocument item : items) {
			stockService.recordAchatEntry(item.getProductId(), item.getQuantity(), item.getUnitPriceHT(), doc.getId(), actorId);
		}
		return toResponse(doc);
	}

	public List<AchatResponse> list() {
		return repository.findAll().stream().map(this::toResponse).toList();
	}

	public AchatResponse get(String id) {
		return toResponse(findOrThrow(id));
	}

	/** Reverses the stock this achat brought in, then deletes it. Fails (409) if any line's stock has since moved on. */
	public void delete(String id) {
		AchatDocument doc = findOrThrow(id);
		stockService.reverseMovementsForSource(id, StockSourceType.FACTURE_ACHAT, doc.getCreatedBy());
		repository.deleteById(id);
	}

	private AchatDocument findOrThrow(String id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Achat not found"));
	}

	private int nextNumber(int year) {
		return repository.findByYear(year).stream()
				.mapToInt(AchatDocument::getNumber)
				.max()
				.orElse(0) + 1;
	}

	private List<AchatItemDocument> buildItems(List<AchatItemRequest> requests) {
		return requests.stream().map(item -> {
			ProductDocument product = productRepository.findById(item.getProductId())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + item.getProductId()));
			double base = item.getQuantity() * item.getUnitPriceHT();
			TaxDocument tax = (item.getTaxId() != null && !item.getTaxId().isBlank())
					? taxRepository.findById(item.getTaxId()).orElse(null) : null;
			double taxRate = tax != null ? tax.getTaxvalue() : 0;
			double taxAmount = round2(base * taxRate / 100);
			return AchatItemDocument.builder()
					.id(UUID.randomUUID().toString())
					.productId(product.getId())
					.article(product.getName())
					.quantity(item.getQuantity())
					.unitPriceHT(item.getUnitPriceHT())
					.taxId(tax != null ? tax.getId() : "")
					.taxRate(taxRate)
					.taxAmount(taxAmount)
					.total(round2(base + taxAmount))
					.build();
		}).toList();
	}

	private Totals computeTotals(List<AchatItemDocument> items) {
		double subtotal = round2(items.stream().mapToDouble(i -> i.getQuantity() * i.getUnitPriceHT()).sum());
		double taxAmount = round2(items.stream().mapToDouble(AchatItemDocument::getTaxAmount).sum());
		return new Totals(subtotal, taxAmount, round2(subtotal + taxAmount));
	}

	private record Totals(double subtotal, double taxAmount, double total) {
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private AchatResponse toResponse(AchatDocument doc) {
		FournisseurResponse fournisseur;
		CurrencyResponse currency;
		try {
			fournisseur = fournisseurService.get(doc.getFournisseurId());
		} catch (ResponseStatusException ex) {
			fournisseur = null;
		}
		try {
			currency = currencyService.get(doc.getCurrencyId());
		} catch (ResponseStatusException ex) {
			currency = null;
		}
		return AchatResponse.builder()
				.id(doc.getId())
				.fournisseur(fournisseur)
				.currency(currency)
				.number(doc.getNumber())
				.year(doc.getYear())
				.date(doc.getDate())
				.note(doc.getNote())
				.items(doc.getItems().stream()
						.map(it -> AchatItemResponse.builder()
								.id(it.getId())
								.productId(it.getProductId())
								.article(it.getArticle())
								.quantity(it.getQuantity())
								.unitPriceHT(it.getUnitPriceHT())
								.taxId(it.getTaxId())
								.taxRate(it.getTaxRate())
								.taxAmount(it.getTaxAmount())
								.total(it.getTotal())
								.build())
						.toList())
				.subtotal(doc.getSubtotal())
				.taxAmount(doc.getTaxAmount())
				.total(doc.getTotal())
				.createdBy(doc.getCreatedBy())
				.created(doc.getCreated())
				.build();
	}
}
