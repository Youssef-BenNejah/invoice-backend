package com.harmoniedev.api.tax.service;

import com.harmoniedev.api.auth.domain.enums.Role;
import com.harmoniedev.api.auth.repository.UserRepository;
import com.harmoniedev.api.tax.domain.model.TaxDocument;
import com.harmoniedev.api.tax.repository.TaxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ensures a 19% TVA tax always exists and is marked default, so every invoice line and article
 * has a sensible tax to fall back to without anyone having to create one by hand first. No-ops
 * once any tax is already marked default; reuses an existing 19% tax instead of duplicating one.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaxSeeder {
	private static final double DEFAULT_RATE = 19;

	private final TaxRepository taxRepository;
	private final UserRepository userRepository;

	@EventListener(ApplicationReadyEvent.class)
	public void seedDefaultTax() {
		boolean hasDefault = taxRepository.findAll().stream().anyMatch(TaxDocument::isDefault);
		if (hasDefault) {
			return;
		}

		TaxDocument existing19 = taxRepository.findFirstByTaxvalue(DEFAULT_RATE).orElse(null);
		if (existing19 != null) {
			existing19.setDefault(true);
			taxRepository.save(existing19);
			log.info("Marked existing TVA {}% tax as default", DEFAULT_RATE);
			return;
		}

		String adminId = userRepository.findByRole(Role.ADMIN).stream().findFirst().map(u -> u.getId()).orElse(null);
		TaxDocument tva = TaxDocument.builder()
				.name("TVA")
				.taxvalue(DEFAULT_RATE)
				.isActive(true)
				.isDefault(true)
				.createdBy(adminId)
				.build();
		taxRepository.save(tva);
		log.info("Seeded default tax: TVA {}%", DEFAULT_RATE);
	}
}
