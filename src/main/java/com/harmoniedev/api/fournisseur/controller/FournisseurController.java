package com.harmoniedev.api.fournisseur.controller;

import com.harmoniedev.api.common.dto.ApiResponse;
import com.harmoniedev.api.fournisseur.domain.dto.request.FournisseurRequest;
import com.harmoniedev.api.fournisseur.domain.dto.response.FournisseurResponse;
import com.harmoniedev.api.fournisseur.service.FournisseurService;
import com.harmoniedev.api.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fournisseurs")
public class FournisseurController {
	private final FournisseurService service;

	public FournisseurController(FournisseurService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<FournisseurResponse>> create(
			@Valid @RequestBody FournisseurRequest request, Authentication authentication) {
		String actorId = ((AuthenticatedUser) authentication.getPrincipal()).getId();
		return ResponseEntity.ok(ApiResponse.success("Fournisseur created", service.create(request, actorId)));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<FournisseurResponse>>> list() {
		return ResponseEntity.ok(ApiResponse.success("Fournisseurs", service.list()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<FournisseurResponse>> get(@PathVariable String id) {
		return ResponseEntity.ok(ApiResponse.success("Fournisseur", service.get(id)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<FournisseurResponse>> update(
			@PathVariable String id, @Valid @RequestBody FournisseurRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Fournisseur updated", service.update(id, request)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
		service.delete(id);
		return ResponseEntity.ok(ApiResponse.success("Fournisseur deleted", null));
	}
}
