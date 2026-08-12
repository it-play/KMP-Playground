package com.amond.kmpbook.domain.simulation.fundproduct

import com.amond.kmpbook.domain.model.fundproduct.DailyResetAdvance
import com.amond.kmpbook.domain.model.fundproduct.DailyResetLifecycle
import com.amond.kmpbook.domain.model.fundproduct.DailyResetState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * 당일 기초지수 누적 단순수익률에 목표 배율을 한 번 적용한다.
 *
 * 시간을 여러 구간으로 나눠 호출해도 같은 종가 기초수익률이면 같은 NAV가 된다. 따라서
 * 변동성 누적손실은 임의의 시간별 drag가 아니라 거래일별 reset과 복리에서 발생한다.
 */
class DailyResetEngine {
    fun initialState(
        productId: String,
        referenceLevel: Double,
        nav: Double,
        tradingDate: kotlinx.datetime.LocalDate,
        at: kotlin.time.Instant,
        targetLeverage: Double,
    ): DailyResetState {
        require(referenceLevel.isFinite() && referenceLevel > 0.0)
        require(nav.isFinite() && nav > 0.0)
        require(targetLeverage.isFinite() && abs(targetLeverage) in 1.0..5.0)
        return DailyResetState(
            productId = productId,
            resetTradingDate = tradingDate,
            referenceLevelAtReset = referenceLevel,
            navAtReset = nav,
            currentReferenceLevel = referenceLevel,
            currentNav = nav,
            cumulativeCarryLogReturn = 0.0,
            exposureNotional = targetLeverage * nav,
            collateralBalance = nav,
            lifecycle = DailyResetLifecycle.ACTIVE,
            asOf = at,
            revision = 0L,
        )
    }

    /**
     * Re-denominates the listed product unit without changing its underlying index exposure or
     * aggregate economics. A corporate-action revision is owned by the runtime's corporate-action
     * ledger; [DailyResetState.revision] remains the reset-event lineage.
     */
    fun applyProductUnitAdjustment(
        state: DailyResetState,
        quantityMultiplier: Double,
        corporateActionAccountingSequence: Long,
        at: kotlin.time.Instant,
    ): DailyResetState {
        require(quantityMultiplier.isFinite() && quantityMultiplier > 0.0)
        require(corporateActionAccountingSequence > 0L)
        require(
            state.lastCorporateActionAccountingSequence == null ||
                corporateActionAccountingSequence > state.lastCorporateActionAccountingSequence,
        )
        require(at >= state.asOf)
        require(state.lifecycle == DailyResetLifecycle.ACTIVE) {
            "A value-exhausted daily-reset product cannot be re-denominated."
        }
        return state.copy(
            navAtReset = state.navAtReset / quantityMultiplier,
            currentNav = state.currentNav / quantityMultiplier,
            exposureNotional = state.exposureNotional / quantityMultiplier,
            collateralBalance = state.collateralBalance / quantityMultiplier,
            cumulativeUnitAdjustmentFactor =
                state.cumulativeUnitAdjustmentFactor * quantityMultiplier,
            lastCorporateActionAccountingSequence = corporateActionAccountingSequence,
            asOf = at,
        )
    }

    fun advance(input: DailyResetAdvanceInput): DailyResetAdvance {
        val previous = input.state
        if (previous.lifecycle == DailyResetLifecycle.VALUE_EXHAUSTED) {
            return DailyResetAdvance(
                state = previous.copy(asOf = input.to),
                productLogReturn = 0.0,
                resetApplied = false,
            )
        }

        val nextReferenceLevel = previous.currentReferenceLevel * exp(input.referenceLogReturn)
        val financingNotionalMultiplier = (abs(input.terms.targetLeverage) - 1.0).coerceAtLeast(0.0)
        val shortBorrowMultiplier = if (input.terms.targetLeverage < 0.0) {
            abs(input.terms.targetLeverage)
        } else {
            0.0
        }
        val annualNetCarry =
            input.cashRateAnnual * input.terms.modelParameters.collateralYieldParticipation -
                input.productExpenseRateAnnual -
                input.terms.modelParameters.annualFinancingSpread * financingNotionalMultiplier -
                input.shortBorrowRateAnnual * shortBorrowMultiplier
        val nextCarry = previous.cumulativeCarryLogReturn + annualNetCarry * input.elapsedYearFraction
        val cumulativeReferenceReturn = nextReferenceLevel / previous.referenceLevelAtReset - 1.0
        val leveragedFactor = 1.0 + input.terms.targetLeverage * cumulativeReferenceReturn
        val exhausted = leveragedFactor <= MIN_POSITIVE_FACTOR
        val nextNav = if (exhausted) {
            DailyResetState.MIN_NAV
        } else {
            (previous.navAtReset * leveragedFactor * exp(nextCarry))
                .coerceIn(DailyResetState.MIN_NAV, DailyResetState.MAX_NAV)
        }
        val intervalLogReturn = ln(nextNav / previous.currentNav)
        if (exhausted) {
            return DailyResetAdvance(
                state = previous.copy(
                    currentReferenceLevel = nextReferenceLevel,
                    currentNav = DailyResetState.MIN_NAV,
                    cumulativeCarryLogReturn = nextCarry,
                    exposureNotional = 0.0,
                    collateralBalance = DailyResetState.MIN_NAV,
                    lifecycle = DailyResetLifecycle.VALUE_EXHAUSTED,
                    asOf = input.to,
                    revision = previous.revision + 1L,
                ),
                productLogReturn = intervalLogReturn,
                resetApplied = false,
            )
        }

        val marked = previous.copy(
            currentReferenceLevel = nextReferenceLevel,
            currentNav = nextNav,
            cumulativeCarryLogReturn = nextCarry,
            exposureNotional = input.terms.targetLeverage * nextNav,
            collateralBalance = nextNav,
            asOf = input.to,
        )
        val nextState = if (input.resetAtEnd) {
            marked.copy(
                resetTradingDate = input.referenceTradingDate,
                referenceLevelAtReset = nextReferenceLevel,
                navAtReset = nextNav,
                cumulativeCarryLogReturn = 0.0,
                revision = marked.revision + 1L,
            )
        } else {
            marked
        }
        return DailyResetAdvance(
            state = nextState,
            productLogReturn = intervalLogReturn,
            resetApplied = input.resetAtEnd,
        )
    }

    companion object {
        private const val MIN_POSITIVE_FACTOR: Double = 1e-12
    }
}
