package com.harmoniedev.api.fournisseur.service;

import com.harmoniedev.api.fournisseur.domain.dto.request.FournisseurRequest;
import com.harmoniedev.api.fournisseur.domain.dto.response.FournisseurResponse;
import com.harmoniedev.api.fournisseur.domain.model.FournisseurDocument;
import com.harmoniedev.api.fournisseur.repository.FournisseurRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FournisseurService {
	private final FournisseurRepository repository;

	public FournisseurService(FournisseurRepository repository) {
		this.repository = repository;
	}

	public FournisseurResponse create(FournisseurRequest request, String actorId) {
		FournisseurDocument doc = FournisseurDocument.builder()
				.nom(request.getNom())
				.email(request.getEmail())
				.telephone(request.getTelephone())
				.fisc(request.getFisc())
				.adresse(request.getAdresse())
				.createdBy(actorId)
				.build();
		repository.save(doc);
		return toResponse(doc);
	}

	public List<FournisseurResponse> list() {
		return repository.findAll().stream().map(this::toResponse).toList();
	}

	public FournisseurResponse get(String id) {
		return toResponse(findOrThrow(id));
	}

	public FournisseurResponse update(String id, FournisseurRequest request) {
		FournisseurDocument doc = findOrThrow(id);
		doc.setNom(request.getNom());
		doc.setEmail(request.getEmail());
		doc.setTelephone(request.getTelephone());
		doc.setFisc(request.getFisc());
		doc.setAdresse(request.getAdresse());
		repository.save(doc);
		return toResponse(doc);
	}

	public void delete(String id) {
		if (!repository.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fournisseur not found");
		}
		repository.deleteById(id);
	}

	private FournisseurDocument findOrThrow(String id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fournisseur not found"));
	}

	private FournisseurResponse toResponse(FournisseurDocument doc) {
		return FournisseurResponse.builder()
				.id(doc.getId())
				.nom(doc.getNom())
				.email(doc.getEmail())
				.telephone(doc.getTelephone())
				.fisc(doc.getFisc())
				.adresse(doc.getAdresse())
				.createdBy(doc.getCreatedBy())
				.created(doc.getCreated())
				.build();
	}
}
