package com.harmoniedev.api.expense.service;

import com.harmoniedev.api.expense.domain.dto.request.DepenseNoteRequest;
import com.harmoniedev.api.expense.domain.dto.request.DepenseNoteStatusRequest;
import com.harmoniedev.api.expense.domain.dto.response.DepenseNoteResponse;
import com.harmoniedev.api.expense.domain.enums.NoteStatus;
import com.harmoniedev.api.expense.domain.model.DepenseNoteDocument;
import com.harmoniedev.api.expense.repository.DepenseNoteRepository;
import com.harmoniedev.api.plan.service.PlanUsageService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DepenseNoteService {
	private final DepenseNoteRepository repository;
	private final PlanUsageService planUsageService;

	public DepenseNoteService(DepenseNoteRepository repository, PlanUsageService planUsageService) {
		this.repository = repository;
		this.planUsageService = planUsageService;
	}

	public DepenseNoteResponse create(DepenseNoteRequest request, String actorId) {
		planUsageService.assertExpensesEnabled(actorId);
		DepenseNoteDocument doc = DepenseNoteDocument.builder()
				.fournisseurNom(request.getFournisseurNom())
				.telephone(request.getTelephone())
				.montant(request.getMontant())
				.date(request.getDate())
				.status(request.getStatus() != null ? request.getStatus() : NoteStatus.NON_PAYE)
				.description(request.getDescription())
				.createdBy(actorId)
				.build();
		repository.save(doc);
		return toResponse(doc);
	}

	public List<DepenseNoteResponse> list() {
		return repository.findAll().stream().map(this::toResponse).toList();
	}

	public DepenseNoteResponse get(String id) {
		return toResponse(findOrThrow(id));
	}

	public DepenseNoteResponse update(String id, DepenseNoteRequest request) {
		DepenseNoteDocument doc = findOrThrow(id);
		doc.setFournisseurNom(request.getFournisseurNom());
		doc.setTelephone(request.getTelephone());
		doc.setMontant(request.getMontant());
		doc.setDate(request.getDate());
		if (request.getStatus() != null) {
			doc.setStatus(request.getStatus());
		}
		doc.setDescription(request.getDescription());
		repository.save(doc);
		return toResponse(doc);
	}

	public DepenseNoteResponse updateStatus(String id, DepenseNoteStatusRequest request) {
		DepenseNoteDocument doc = findOrThrow(id);
		doc.setStatus(request.getStatus());
		repository.save(doc);
		return toResponse(doc);
	}

	public void delete(String id) {
		if (!repository.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found");
		}
		repository.deleteById(id);
	}

	private DepenseNoteDocument findOrThrow(String id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
	}

	private DepenseNoteResponse toResponse(DepenseNoteDocument doc) {
		return DepenseNoteResponse.builder()
				.id(doc.getId())
				.fournisseurNom(doc.getFournisseurNom())
				.telephone(doc.getTelephone())
				.montant(doc.getMontant())
				.date(doc.getDate())
				.status(doc.getStatus())
				.description(doc.getDescription())
				.createdBy(doc.getCreatedBy())
				.created(doc.getCreated())
				.build();
	}
}
