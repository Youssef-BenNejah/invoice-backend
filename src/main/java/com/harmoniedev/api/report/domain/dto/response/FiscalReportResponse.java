package com.harmoniedev.api.report.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Données indicatives pour la déclaration mensuelle de TVA et le suivi du coût des marchandises
 * vendues (CMV) — à vérifier avec votre comptable avant toute déclaration officielle.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FiscalReportResponse {
	private String dateFrom;
	private String dateTo;
	private boolean assujettiTva;
	private String regimeFiscal;

	/** TVA sur les ventes (Facture/Bon de livraison) de la période. */
	private double tvaCollectee;

	/** TVA sur les achats de la période — récupérable uniquement si l'entreprise est assujettie. */
	private double tvaDeductible;

	/** TVA payée sur les achats mais non récupérable (régime non assujetti) — entrée dans le coût du stock. */
	private double tvaNonRecuperable;

	/** tvaCollectee - tvaDeductible : montant dû (positif) ou crédit de TVA (négatif). */
	private double soldeTva;

	/** Retenue à la source totale sur les achats de la période. */
	private double retenueSourceTotal;

	/** Coût des marchandises vendues (CMV) sur la période, au CMP. */
	private double cmv;
}
