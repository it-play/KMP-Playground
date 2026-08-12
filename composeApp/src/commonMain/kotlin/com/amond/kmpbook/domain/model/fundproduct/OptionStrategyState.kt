package com.amond.kmpbook.domain.model.fundproduct

import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Mark-to-market state for one option-overlay product.
 *
 * The accounting identity is deliberately explicit: reference units plus cash plus the net fair
 * value of all option legs equals NAV. Premium cash and the opening option value therefore enter
 * together and cannot both be counted as return. Flow counters record only events observed after
 * campaign bootstrap; the already-running initial package is represented by residual cash at fair
 * value and starts those counters at zero.
 */
data class OptionStrategyState(
    val productId: String,
    val strategyKind: OptionStrategyKind,
    val rollCalendar: OptionRollCalendar,
    val currentReferenceLevel: Double,
    val currentNav: Double,
    val underlyingUnits: Double,
    val cashBalance: Double,
    val cycleReferenceLevel: Double,
    val optionNotionalAtRoll: Double,
    val cycleStartedOn: LocalDate,
    val remainingTradingDays: Int,
    val remainingTimeYears: Double,
    val lastProcessedTradingDate: LocalDate?,
    val longCallUnits: Double,
    val longCallStrike: Double?,
    val shortCallUnits: Double,
    val shortCallStrike: Double?,
    val longPutUnits: Double,
    val longPutStrike: Double?,
    val shortPutUnits: Double,
    val shortPutStrike: Double?,
    val netOptionMark: Double,
    val cycleGrossPremiumReceived: Double,
    val cycleGrossPremiumPaid: Double,
    val cycleImplementationCost: Double,
    val cumulativePremiumReceived: Double,
    val cumulativePremiumPaid: Double,
    val cumulativeSettlementCashFlow: Double,
    val cumulativeImplementationCost: Double,
    val lifecycle: OptionStrategyLifecycle,
    val cumulativeUnitAdjustmentFactor: Double = 1.0,
    val lastCorporateActionAccountingSequence: Long? = null,
    val asOf: Instant,
    val revision: Long,
) {
    init {
        require(ID_PATTERN.matches(productId))
        require(rollCalendar.isTradingDate(cycleStartedOn))
        require(currentReferenceLevel.isFinite() && currentReferenceLevel > 0.0)
        require(currentNav.isFinite() && currentNav in MIN_NAV..MAX_NAV)
        requireFiniteNonNegative(underlyingUnits)
        require(cashBalance.isFinite() && abs(cashBalance) <= MAX_NAV)
        require(cycleReferenceLevel.isFinite() && cycleReferenceLevel > 0.0)
        requireFiniteNonNegative(optionNotionalAtRoll)
        require(remainingTradingDays in 0..MAX_TENOR_TRADING_DAYS)
        require(remainingTimeYears.isFinite() && remainingTimeYears in 0.0..MAX_TIME_YEARS)
        require(
            lastProcessedTradingDate == null ||
                lastProcessedTradingDate >= cycleStartedOn &&
                rollCalendar.isTradingDate(lastProcessedTradingDate),
        )
        requireLeg(longCallUnits, longCallStrike)
        requireLeg(shortCallUnits, shortCallStrike)
        requireLeg(longPutUnits, longPutStrike)
        requireLeg(shortPutUnits, shortPutStrike)
        require(netOptionMark.isFinite() && abs(netOptionMark) <= MAX_NAV)
        requireFiniteNonNegative(cycleGrossPremiumReceived)
        requireFiniteNonNegative(cycleGrossPremiumPaid)
        requireFiniteNonNegative(cycleImplementationCost)
        requireFiniteNonNegative(cumulativePremiumReceived)
        requireFiniteNonNegative(cumulativePremiumPaid)
        require(cumulativeSettlementCashFlow.isFinite() && abs(cumulativeSettlementCashFlow) <= MAX_NAV)
        requireFiniteNonNegative(cumulativeImplementationCost)
        require(cumulativePremiumReceived + ACCOUNTING_EPSILON >= cycleGrossPremiumReceived)
        require(cumulativePremiumPaid + ACCOUNTING_EPSILON >= cycleGrossPremiumPaid)
        require(cumulativeImplementationCost + ACCOUNTING_EPSILON >= cycleImplementationCost)
        require(revision >= 0L)
        require(cumulativeUnitAdjustmentFactor.isFinite() && cumulativeUnitAdjustmentFactor > 0.0)
        require(lastCorporateActionAccountingSequence == null || lastCorporateActionAccountingSequence > 0L)
        if (lastCorporateActionAccountingSequence == null) require(cumulativeUnitAdjustmentFactor == 1.0)

        val accountedNav = underlyingUnits * currentReferenceLevel + cashBalance + netOptionMark
        require(accountedNav.isFinite())
        require(
            abs(accountedNav - currentNav) <=
                ACCOUNTING_EPSILON * maxOf(1.0, abs(accountedNav), currentNav),
        ) { "옵션 상품의 기초자산·현금·옵션 공정가치 합은 NAV와 일치해야 합니다." }

        when (lifecycle) {
            OptionStrategyLifecycle.ACTIVE -> Unit
            OptionStrategyLifecycle.AWAITING_PRODUCT_LIQUIDATION -> {
                require(optionNotionalAtRoll == 0.0 && netOptionMark == 0.0)
                require(longCallUnits == 0.0 && shortCallUnits == 0.0)
                require(longPutUnits == 0.0 && shortPutUnits == 0.0)
                require(remainingTradingDays == 0 && remainingTimeYears == 0.0)
                require(cycleGrossPremiumReceived == 0.0 && cycleGrossPremiumPaid == 0.0)
                require(cycleImplementationCost == 0.0)
            }
            OptionStrategyLifecycle.VALUE_EXHAUSTED -> {
                require(currentNav == MIN_NAV)
                require(underlyingUnits == 0.0 && cashBalance == MIN_NAV)
                require(optionNotionalAtRoll == 0.0 && netOptionMark == 0.0)
                require(longCallUnits == 0.0 && shortCallUnits == 0.0)
                require(longPutUnits == 0.0 && shortPutUnits == 0.0)
                require(remainingTradingDays == 0 && remainingTimeYears == 0.0)
            }
        }
    }

    private fun requireLeg(units: Double, strike: Double?) {
        requireFiniteNonNegative(units)
        if (units == 0.0) {
            require(strike == null)
        } else {
            requireNotNull(strike)
            require(strike.isFinite() && strike > 0.0)
        }
    }

    private fun requireFiniteNonNegative(value: Double) {
        require(value.isFinite() && value in 0.0..MAX_NAV)
    }

    companion object {
        const val MIN_NAV: Double = 1e-9
        const val MAX_NAV: Double = 1e18
        private const val MAX_TENOR_TRADING_DAYS: Int = 504
        private const val MAX_TIME_YEARS: Double = 2.0
        private const val ACCOUNTING_EPSILON: Double = 1e-9
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{2,199}")
    }
}
