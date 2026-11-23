package com.harmoniedev.api.sync.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * {@code InvoiceService.nextNumber()} picks "max existing + 1" against whichever Mongo the caller
 * is talking to — correct for a single database, but two invoices created independently (one on
 * the desktop while offline, one on the cloud/web in the meantime) can end up with the same
 * printed (type, year, number). Pushing the offline one up later would otherwise insert a second,
 * same-numbered invoice next to the one already on the cloud.
 *
 * <p>This only has anything to reconcile at the one point such a collision first becomes visible:
 * the cloud applying an incoming push. A pulled invoice (desktop applying the cloud's data) is
 * already using the cloud's authoritative number, so it never goes through this.
 */
@Service
@Slf4j
public class InvoiceNumberReconciler {
	private static final String INVOICES_COLLECTION = "invoices";

	private final MongoTemplate mongoTemplate;

	public InvoiceNumberReconciler(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	/** Mutates {@code payload}'s {@code number} in place, only if another invoice already owns it. */
	public void reconcile(Document payload) {
		String type = payload.getString("type");
		Integer year = payload.getInteger("year");
		Integer number = payload.getInteger("number");
		String incomingId = payload.getString("_id");
		if (type == null || year == null || number == null) {
			return;
		}

		MongoCollection<Document> invoices = mongoTemplate.getCollection(INVOICES_COLLECTION);
		Document clash = invoices.find(Filters.and(
						Filters.eq("type", type),
						Filters.eq("year", year),
						Filters.eq("number", number),
						Filters.ne("_id", incomingId)))
				.first();
		if (clash == null) {
			return;
		}

		int nextFree = highestNumber(invoices, type, year) + 1;
		log.info("Invoice number collision on sync: {} {} #{} already taken, renumbering incoming invoice {} to #{}",
				type, year, number, incomingId, nextFree);
		payload.put("number", nextFree);
	}

	private int highestNumber(MongoCollection<Document> invoices, String type, int year) {
		Document top = invoices.find(Filters.and(Filters.eq("type", type), Filters.eq("year", year)))
				.sort(Sorts.descending("number"))
				.first();
		return top == null ? 0 : top.getInteger("number", 0);
	}
}
