package com.harmoniedev.api.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
class InvoiceNumberReconcilerTest {
	@Mock
	private MongoTemplate mongoTemplate;
	@Mock
	private MongoCollection<Document> invoices;

	private InvoiceNumberReconciler reconciler;

	@BeforeEach
	void setUp() {
		reconciler = new InvoiceNumberReconciler(mongoTemplate);
	}

	@Test
	void reconcile_withNoClash_leavesTheNumberUnchanged() {
		when(mongoTemplate.getCollection("invoices")).thenReturn(invoices);
		mockFindFirst(null);
		Document payload = new Document("_id", "inv-new").append("type", "Standard").append("year", 2026).append("number", 7);

		reconciler.reconcile(payload);

		assertThat(payload.getInteger("number")).isEqualTo(7);
	}

	@Test
	void reconcile_withAnotherInvoiceAlreadyOwningThatNumber_assignsTheNextFreeOne() {
		when(mongoTemplate.getCollection("invoices")).thenReturn(invoices);
		Document clashing = new Document("_id", "inv-existing").append("number", 7);
		Document highest = new Document("_id", "inv-highest").append("number", 9);
		mockFindFirst(clashing, highest);
		Document incoming = new Document("_id", "inv-new").append("type", "Standard").append("year", 2026).append("number", 7);

		reconciler.reconcile(incoming);

		assertThat(incoming.getInteger("number")).isEqualTo(10);
	}

	@Test
	void reconcile_withoutEnoughFieldsToIdentifyTheInvoice_doesNothing() {
		Document payload = new Document("_id", "inv-new").append("type", "Standard");

		reconciler.reconcile(payload);

		assertThat(payload.containsKey("number")).isFalse();
	}

	/** For the no-clash path: reconcile() never sorts, it returns right after the first find(). */
	@SuppressWarnings("unchecked")
	private void mockFindFirst(Document onlyResult) {
		FindIterable<Document> iterable = mock(FindIterable.class);
		when(invoices.find(any(Bson.class))).thenReturn(iterable);
		when(iterable.first()).thenReturn(onlyResult);
	}

	/** For the clash path: the first find().first() is the clash check, the second (after .sort()) is highestNumber(). */
	@SuppressWarnings("unchecked")
	private void mockFindFirst(Document clash, Document highest) {
		FindIterable<Document> iterable = mock(FindIterable.class);
		when(invoices.find(any(Bson.class))).thenReturn(iterable);
		when(iterable.sort(any(Bson.class))).thenReturn(iterable);
		when(iterable.first()).thenReturn(clash, highest);
	}
}
