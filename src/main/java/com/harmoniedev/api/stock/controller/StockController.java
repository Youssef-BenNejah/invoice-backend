package com.harmoniedev.api.stock.controller;

import com.harmoniedev.api.common.dto.ApiResponse;
import com.harmoniedev.api.pdf.StockPdfService;
import com.harmoniedev.api.security.AuthenticatedUser;
import com.harmoniedev.api.stock.domain.dto.request.StockAdjustmentRequest;
import com.harmoniedev.api.stock.domain.dto.response.StockDashboardResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockMovementResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockReportResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockResponse;
import com.harmoniedev.api.stock.service.StockService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/stock")
public class StockController {
	private final StockService service;
	private final StockPdfService pdfService;

	public StockController(StockService service, StockPdfService pdfService) {
		this.service = service;
		this.pdfService = pdfService;
	}

	@GetMapping("/dashboard")
	public ResponseEntity<ApiResponse<StockDashboardResponse>> dashboard() {
		return ResponseEntity.ok(ApiResponse.success("Stock dashboard", service.dashboard()));
	}

	@GetMapping("/dashboard/pdf")
	public ResponseEntity<byte[]> dashboardPdf() {
		byte[] pdf = pdfService.generateStockReportPdf(service.dashboard());
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rapport-stock.pdf\"")
				.body(pdf);
	}

	@GetMapping("/report")
	public ResponseEntity<ApiResponse<StockReportResponse>> report(
			@RequestParam int year, @RequestParam int month) {
		if (month < 1 || month > 12) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must be between 1 and 12");
		}
		return ResponseEntity.ok(ApiResponse.success("Stock report", service.report(year, month)));
	}

	@GetMapping("/products/{productId}")
	public ResponseEntity<ApiResponse<StockResponse>> getStock(@PathVariable String productId) {
		return ResponseEntity.ok(ApiResponse.success("Stock", service.getStock(productId)));
	}

	@GetMapping("/products/{productId}/movements")
	public ResponseEntity<ApiResponse<List<StockMovementResponse>>> movements(@PathVariable String productId) {
		return ResponseEntity.ok(ApiResponse.success("Stock movements", service.listMovements(productId)));
	}

	@PostMapping("/adjustments")
	public ResponseEntity<ApiResponse<StockResponse>> adjust(
			@Valid @RequestBody StockAdjustmentRequest request, Authentication authentication) {
		String actorId = ((AuthenticatedUser) authentication.getPrincipal()).getId();
		StockResponse response = service.adjust(request.getProductId(), request.getQuantity(), request.getReason(), actorId);
		return ResponseEntity.ok(ApiResponse.success("Stock adjusted", response));
	}
}
