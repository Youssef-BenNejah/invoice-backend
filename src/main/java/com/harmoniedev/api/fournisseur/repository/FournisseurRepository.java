package com.harmoniedev.api.fournisseur.repository;

import com.harmoniedev.api.fournisseur.domain.model.FournisseurDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FournisseurRepository extends MongoRepository<FournisseurDocument, String> {
}
