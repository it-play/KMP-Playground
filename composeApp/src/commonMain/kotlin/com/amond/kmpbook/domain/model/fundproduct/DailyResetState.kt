package com.amond.kmpbook.domain.model.fundproduct

import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * 전일 종가 기준의 기초지수와 NAV를 고정해 당일 목표 단순수익률을 정확히 계산하는 상태다.
 */
data class DailyResetState(
    val productId: String,
    val resetTradingDate: LocalDate,
    val referenceLevelAtReset: Double,
    val navAtReset: Double,
    val currentReferenceLevel: Double,
    val currentNav: Double,
    val cumulativeCarryLogReturn: Double,
    val exposureNotional: Double,
    val collateralBalance: Double,
    val lifecycle: DailyResetLifecycle,
    val cumulativeUnitAdjustmentFactor: Double = 1.0,
    val lastCorporateActionAccountingSequence: Long? = null,
    val asOf: Instant,
    val revision: Long,
) {
    init {
        require(productId.isNotBlank())
        require(referenceLevelAtReset.isFinite() && referenceLevelAtReset > 0.0)
        require(navAtReset.isFinite() && navAtReset in MIN_NAV..MAX_NAV)
        require(currentReferenceLevel.isFinite() && currentReferenceLevel > 0.0)
        require(currentNav.isFinite() && currentNav in MIN_NAV..MAX_NAV)
        require(cumulativeCarryLogReturn.isFinite())
        require(exposureNotional.isFinite())
        require(collateralBalance.isFinite() && collateralBalance >= 0.0)
        require(
            abs(collateralBalance - currentNav) <=
                ACCOUNTING_EPSILON * maxOf(1.0, collateralBalance, currentNav),
        ) { "일일 reset 담보잔액은 현재 NAV와 일치해야 합니다." }
        require(revision >= 0L)
        require(cumulativeUnitAdjustmentFactor.isFinite() && cumulativeUnitAdjustmentFactor > 0.0)
        require(lastCorporateActionAccountingSequence == null || lastCorporateActionAccountingSequence > 0L)
        if (lastCorporateActionAccountingSequence == null) require(cumulativeUnitAdjustmentFactor == 1.0)
        if (lifecycle == DailyResetLifecycle.VALUE_EXHAUSTED) {
            require(currentNav == MIN_NAV)
            require(exposureNotional == 0.0)
        }
    }

    companion object {
        const val MIN_NAV: Double = 1e-9
        const val MAX_NAV: Double = 1e18
        private const val ACCOUNTING_EPSILON: Double = 1e-10
    }
}
