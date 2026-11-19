package com.harmoniedev.api.achat.repository;

import com.harmoniedev.api.achat.domain.model.AchatDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AchatRepository extends MongoRepository<AchatDocument, String> {
	List<AchatDocument> findByYear(int year);
}
