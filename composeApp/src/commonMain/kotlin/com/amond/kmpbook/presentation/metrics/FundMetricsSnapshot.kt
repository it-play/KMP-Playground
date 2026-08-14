package com.amond.kmpbook.presentation.metrics

import com.amond.kmpbook.domain.model.market.Currency
import kotlin.time.Instant

/**
 * 상품 구조별 원시 상태를 같은 화면 필드로 투영한 값이다.
 * 정산된 ETN은 발행잔량·지표가치·발행잔액이 정확히 0일 수 있다.
 */
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
        require(unitsOrNotesOutstanding.isFinite() && unitsOrNotesOutstanding >= 0.0)
        require(navPerUnit.isFinite() && navPerUnit >= 0.0)
        require(indicativeValuePerUnit.isFinite() && indicativeValuePerUnit >= 0.0)
        require(assetsUnderManagement == null || assetsUnderManagement.isFinite() && assetsUnderManagement >= 0.0)
        require(outstandingNotional == null || outstandingNotional.isFinite() && outstandingNotional >= 0.0)
        require(premiumDiscountRate == null || premiumDiscountRate.isFinite())
        require(lastNetFlow.isFinite())
    }
}
