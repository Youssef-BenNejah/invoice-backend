package com.harmoniedev.api.stock.controller;

import com.harmoniedev.api.common.dto.ApiResponse;
import com.harmoniedev.api.security.AuthenticatedUser;
import com.harmoniedev.api.stock.domain.dto.request.StockAdjustmentRequest;
import com.harmoniedev.api.stock.domain.dto.response.StockMovementResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockResponse;
import com.harmoniedev.api.stock.service.StockService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stock")
public class StockController {
	private final StockService service;

	public StockController(StockService service) {
		this.service = service;
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
