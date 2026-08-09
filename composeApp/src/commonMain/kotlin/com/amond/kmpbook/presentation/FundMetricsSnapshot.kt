package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.Currency
import kotlin.time.Instant

data class FundMetricsSnapshot(
    override val stockId: String,
    override val asOf: Instant,
    override val currency: Currency,
    override val marketCapitalization: Double,
    val targetLeverage: Double,
    val annualExpenseRatio: Double,
    val unitsOrNotesOutstanding: Double,
    val navPerUnit: Double,
    val indicativeValuePerUnit: Double,
    val assetsUnderManagement: Double?,
    val outstandingNotional: Double?,
    val premiumDiscountRate: Double?,
    val lastNetFlow: Double,
) : InstrumentMetricsSnapshot {
    init {
        require(stockId.isNotBlank())
        require(marketCapitalization.isFinite() && marketCapitalization >= 0.0)
        require(targetLeverage.isFinite() && targetLeverage != 0.0)
        require(annualExpenseRatio.isFinite() && annualExpenseRatio >= 0.0)
        require(unitsOrNotesOutstanding.isFinite() && unitsOrNotesOutstanding > 0.0)
        require(navPerUnit.isFinite() && navPerUnit > 0.0)
        require(indicativeValuePerUnit.isFinite() && indicativeValuePerUnit > 0.0)
        require(assetsUnderManagement == null || assetsUnderManagement.isFinite() && assetsUnderManagement > 0.0)
        require(outstandingNotional == null || outstandingNotional.isFinite() && outstandingNotional > 0.0)
        require(premiumDiscountRate == null || premiumDiscountRate.isFinite())
        require(lastNetFlow.isFinite())
    }
}
