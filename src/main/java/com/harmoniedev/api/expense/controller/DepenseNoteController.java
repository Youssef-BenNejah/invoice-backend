package com.harmoniedev.api.expense.controller;

import com.harmoniedev.api.common.dto.ApiResponse;
import com.harmoniedev.api.expense.domain.dto.request.DepenseNoteRequest;
import com.harmoniedev.api.expense.domain.dto.request.DepenseNoteStatusRequest;
import com.harmoniedev.api.expense.domain.dto.response.DepenseNoteResponse;
import com.harmoniedev.api.expense.service.DepenseNoteService;
import com.harmoniedev.api.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/depense-notes")
public class DepenseNoteController {
	private final DepenseNoteService service;

	public DepenseNoteController(DepenseNoteService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<DepenseNoteResponse>> create(
			@RequestBody DepenseNoteRequest request, Authentication authentication) {
		String actorId = ((AuthenticatedUser) authentication.getPrincipal()).getId();
		return ResponseEntity.ok(ApiResponse.success("Note created", service.create(request, actorId)));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<DepenseNoteResponse>>> list() {
		return ResponseEntity.ok(ApiResponse.success("Notes", service.list()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<DepenseNoteResponse>> get(@PathVariable String id) {
		return ResponseEntity.ok(ApiResponse.success("Note", service.get(id)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<DepenseNoteResponse>> update(
			@PathVariable String id, @RequestBody DepenseNoteRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Note updated", service.update(id, request)));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<ApiResponse<DepenseNoteResponse>> updateStatus(
			@PathVariable String id, @Valid @RequestBody DepenseNoteStatusRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Note status updated", service.updateStatus(id, request)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
		service.delete(id);
		return ResponseEntity.ok(ApiResponse.success("Note deleted", null));
	}
}
