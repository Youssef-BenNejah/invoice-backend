package com.harmoniedev.api.expense.repository;

import com.harmoniedev.api.expense.domain.model.DepenseNoteDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DepenseNoteRepository extends MongoRepository<DepenseNoteDocument, String> {
}
