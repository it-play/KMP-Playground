package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.market.Currency

/** Canonical accounting values reconstructed for one persisted portfolio observation. */
data class CanonicalPortfolioSnapshotAccountingFact(
    val cashByCurrency: Map<Currency, Double>,
    val nativeHoldingsByStockId: Map<String, CanonicalTaxNativeHoldingFact>,
    val holdingCostBasisKrw: Map<String, Double>,
    val distributionReceivableByCurrency: Map<Currency, Double>,
    val realizedProfitKrw: Double,
    val cumulativeCommissionKrw: Double,
    val cumulativeTaxKrw: Double,
)
