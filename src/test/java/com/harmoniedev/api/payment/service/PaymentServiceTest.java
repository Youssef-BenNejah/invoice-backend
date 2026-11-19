package com.harmoniedev.api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harmoniedev.api.invoice.domain.dto.response.InvoiceResponse;
import com.harmoniedev.api.invoice.service.InvoiceService;
import com.harmoniedev.api.payment.domain.model.PaymentDocument;
import com.harmoniedev.api.payment.repository.PaymentRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
	@Mock
	private PaymentRepository repository;
	@Mock
	private InvoiceService invoiceService;

	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		paymentService = new PaymentService(repository, invoiceService);
	}

	@Test
	void list_skipsPaymentWhoseInvoiceWasDeleted_insteadOfFailingTheWholeList() {
		PaymentDocument live = PaymentDocument.builder().id("pay-1").invoiceId("inv-live").amountPaid(100).build();
		PaymentDocument orphaned = PaymentDocument.builder().id("pay-2").invoiceId("inv-deleted").amountPaid(50).build();
		when(repository.findAll()).thenReturn(List.of(live, orphaned));
		when(invoiceService.get("inv-live")).thenReturn(
				InvoiceResponse.builder().id("inv-live").number(1).year(2026).type("Standard").total(100).build());
		when(invoiceService.get("inv-deleted")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

		List<com.harmoniedev.api.payment.domain.dto.response.PaymentResponse> result = paymentService.list();

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo("pay-1");
	}

	@Test
	void delete_withInvoiceStillPresent_reconcilesPaidAmount() {
		PaymentDocument doc = PaymentDocument.builder().id("pay-1").invoiceId("inv-1").amountPaid(100).build();
		when(repository.findById("pay-1")).thenReturn(java.util.Optional.of(doc));
		when(invoiceService.get("inv-1")).thenReturn(InvoiceResponse.builder().id("inv-1").paidAmount(150).total(150).build());

		paymentService.delete("pay-1");

		verify(repository).deleteById("pay-1");
		verify(invoiceService).applyPayment("inv-1", 50);
	}

	@Test
	void delete_whenInvoiceAlreadyDeleted_stillDeletesThePayment_withoutFailing() {
		PaymentDocument doc = PaymentDocument.builder().id("pay-2").invoiceId("inv-gone").amountPaid(50).build();
		when(repository.findById("pay-2")).thenReturn(java.util.Optional.of(doc));
		when(invoiceService.get("inv-gone")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

		paymentService.delete("pay-2");

		verify(repository).deleteById("pay-2");
		verify(invoiceService, never()).applyPayment(any(), anyDouble());
	}
}
