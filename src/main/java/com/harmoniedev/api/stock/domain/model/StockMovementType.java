package com.harmoniedev.api.stock.domain.model;

public enum StockMovementType {
	/** Entrée depuis une facture d'achat fournisseur. */
	ACHAT,
	/** Sortie depuis une facture de vente (Facture / Bon de livraison). */
	VENTE,
	/** Correction manuelle (inventaire physique), avec motif obligatoire. */
	AJUSTEMENT,
	/** Compensation d'un ACHAT annulé (achat supprimé). */
	ANNULATION_ACHAT,
	/** Compensation d'une VENTE annulée (facture supprimée/éditée). */
	ANNULATION_VENTE
}
