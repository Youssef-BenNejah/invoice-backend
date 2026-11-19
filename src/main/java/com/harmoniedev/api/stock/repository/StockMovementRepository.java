package com.harmoniedev.api.stock.repository;

import com.harmoniedev.api.stock.domain.model.StockMovementDocument;
import com.harmoniedev.api.stock.domain.model.StockSourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StockMovementRepository extends MongoRepository<StockMovementDocument, String> {
	List<StockMovementDocument> findByProductIdOrderByCreatedDesc(String productId);

	Optional<StockMovementDocument> findTopByProductIdOrderByCreatedDesc(String productId);

	List<StockMovementDocument> findBySourceIdAndSourceTypeOrderByCreatedDesc(String sourceId, StockSourceType sourceType);
}
