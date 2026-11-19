package com.harmoniedev.api.achat.controller;

import com.harmoniedev.api.achat.domain.dto.request.AchatRequest;
import com.harmoniedev.api.achat.domain.dto.response.AchatResponse;
import com.harmoniedev.api.achat.service.AchatService;
import com.harmoniedev.api.common.dto.ApiResponse;
import com.harmoniedev.api.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/achats")
public class AchatController {
	private final AchatService service;

	public AchatController(AchatService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<AchatResponse>> create(
			@Valid @RequestBody AchatRequest request, Authentication authentication) {
		String actorId = ((AuthenticatedUser) authentication.getPrincipal()).getId();
		return ResponseEntity.ok(ApiResponse.success("Achat created", service.create(request, actorId)));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<AchatResponse>>> list() {
		return ResponseEntity.ok(ApiResponse.success("Achats", service.list()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<AchatResponse>> get(@PathVariable String id) {
		return ResponseEntity.ok(ApiResponse.success("Achat", service.get(id)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
		service.delete(id);
		return ResponseEntity.ok(ApiResponse.success("Achat deleted", null));
	}
}
