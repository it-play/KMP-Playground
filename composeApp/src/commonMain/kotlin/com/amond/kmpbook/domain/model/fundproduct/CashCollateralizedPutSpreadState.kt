package com.amond.kmpbook.domain.model.fundproduct

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.time.Instant

/**
 * Fair-value state for a cash-collateralized put-spread product.
 *
 * The sole accounting identity is `cashBalance + netOptionMark = currentNav`. Premium receipts
 * and purchases have already changed [cashBalance], while [netOptionMark] is the still-open
 * package's fair value. Adding either flow counter to NAV would double count option economics.
 */
data class CashCollateralizedPutSpreadState(
    val productId: String,
    val cashBenchmarkRef: BenchmarkRef,
    val optionReference: DailyResetReference,
    val rollCalendar: OptionRollCalendar,
    val currentCashReferenceLevel: Double,
    val currentOptionReferenceLevel: Double,
    val currentNav: Double,
    val cashBalance: Double,
    val cycleOptionReferenceLevel: Double,
    val navAtRoll: Double,
    val optionNotionalAtRoll: Double,
    val maximumSettlementLossAtRoll: Double,
    val cycleStartedOn: LocalDate,
    val remainingTradingDays: Int,
    val remainingTimeYears: Double,
    val lastProcessedTradingDate: LocalDate?,
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
    val lifecycle: CashCollateralizedPutSpreadLifecycle,
    val cumulativeUnitAdjustmentFactor: Double = 1.0,
    val lastCorporateActionAccountingSequence: Long? = null,
    val asOf: Instant,
    val revision: Long,
) {
    init {
        require(ID_PATTERN.matches(productId))
        require(rollCalendar.isTradingDate(cycleStartedOn))
        requirePositiveReference(currentCashReferenceLevel)
        requirePositiveReference(currentOptionReferenceLevel)
        requirePositiveReference(cycleOptionReferenceLevel)
        require(currentNav.isFinite() && currentNav in MIN_NAV..MAX_NAV)
        requireFiniteNonNegative(cashBalance)
        require(navAtRoll.isFinite() && navAtRoll in 0.0..MAX_NAV)
        requireFiniteNonNegative(optionNotionalAtRoll)
        requireFiniteNonNegative(maximumSettlementLossAtRoll)
        require(remainingTradingDays in 0..MAX_TENOR_TRADING_DAYS)
        require(remainingTimeYears.isFinite() && remainingTimeYears in 0.0..MAX_TIME_YEARS)
        require(
            lastProcessedTradingDate == null ||
                lastProcessedTradingDate >= cycleStartedOn &&
                rollCalendar.isTradingDate(lastProcessedTradingDate),
        )
        requireFiniteNonNegative(longPutUnits)
        requireFiniteNonNegative(shortPutUnits)
        require(netOptionMark.isFinite() && abs(netOptionMark) <= MAX_AMOUNT)
        require(netOptionMark <= accountingTolerance(netOptionMark)) {
            "고행사가 매도 풋과 저행사가 매수 풋의 순 공정가치는 양수가 될 수 없습니다."
        }
        requireFiniteNonNegative(cycleGrossPremiumReceived)
        requireFiniteNonNegative(cycleGrossPremiumPaid)
        requireFiniteNonNegative(cycleImplementationCost)
        requireFiniteNonNegative(cumulativePremiumReceived)
        requireFiniteNonNegative(cumulativePremiumPaid)
        require(cumulativeSettlementCashFlow.isFinite() && abs(cumulativeSettlementCashFlow) <= MAX_AMOUNT)
        require(cumulativeSettlementCashFlow <= accountingTolerance(cumulativeSettlementCashFlow))
        requireFiniteNonNegative(cumulativeImplementationCost)
        require(cumulativePremiumReceived + accountingTolerance(cumulativePremiumReceived) >= cycleGrossPremiumReceived)
        require(cumulativePremiumPaid + accountingTolerance(cumulativePremiumPaid) >= cycleGrossPremiumPaid)
        require(
            cumulativeImplementationCost + accountingTolerance(cumulativeImplementationCost) >=
                cycleImplementationCost,
        )
        require(revision >= 0L)
        require(cumulativeUnitAdjustmentFactor.isFinite() && cumulativeUnitAdjustmentFactor > 0.0)
        require(lastCorporateActionAccountingSequence == null || lastCorporateActionAccountingSequence > 0L)
        if (lastCorporateActionAccountingSequence == null) require(cumulativeUnitAdjustmentFactor == 1.0)

        val accountedNav = cashBalance + netOptionMark
        require(accountedNav.isFinite())
        require(approximatelyEqual(accountedNav, currentNav)) {
            "현금담보와 순 옵션 공정가치의 합은 NAV와 일치해야 합니다."
        }

        when (lifecycle) {
            CashCollateralizedPutSpreadLifecycle.ACTIVE -> {
                require(currentNav > MIN_NAV)
                require(remainingTradingDays in 1..MAX_TENOR_TRADING_DAYS)
                require(longPutUnits > 0.0 && shortPutUnits > 0.0)
                require(approximatelyEqual(longPutUnits, shortPutUnits)) {
                    "풋스프레드의 매수·매도 계약 수는 같아야 합니다."
                }
                val requiredLongStrike = requireNotNull(longPutStrike)
                val requiredShortStrike = requireNotNull(shortPutStrike)
                require(requiredLongStrike.isFinite() && requiredLongStrike > 0.0)
                require(requiredShortStrike.isFinite() && requiredShortStrike > requiredLongStrike)
                require(
                    navAtRoll > MIN_NAV &&
                        optionNotionalAtRoll > 0.0 &&
                        maximumSettlementLossAtRoll > 0.0,
                )
                require(
                    approximatelyEqual(
                        optionNotionalAtRoll,
                        longPutUnits * cycleOptionReferenceLevel,
                    ),
                )
                require(
                    approximatelyEqual(
                        maximumSettlementLossAtRoll,
                        longPutUnits * (requiredShortStrike - requiredLongStrike),
                    ),
                )
            }
            CashCollateralizedPutSpreadLifecycle.AWAITING_PRODUCT_LIQUIDATION -> {
                require(currentNav > MIN_NAV && approximatelyEqual(currentNav, cashBalance))
                require(navAtRoll == 0.0 && optionNotionalAtRoll == 0.0)
                require(maximumSettlementLossAtRoll == 0.0)
                require(longPutUnits == 0.0 && shortPutUnits == 0.0)
                require(longPutStrike == null && shortPutStrike == null)
                require(netOptionMark == 0.0)
                require(remainingTradingDays == 0 && remainingTimeYears == 0.0)
                require(cycleGrossPremiumReceived == 0.0 && cycleGrossPremiumPaid == 0.0)
                require(cycleImplementationCost == 0.0)
            }
            CashCollateralizedPutSpreadLifecycle.VALUE_EXHAUSTED -> {
                require(currentNav == MIN_NAV && cashBalance == MIN_NAV)
                require(
                    navAtRoll == 0.0 &&
                        optionNotionalAtRoll == 0.0 &&
                        maximumSettlementLossAtRoll == 0.0,
                )
                require(longPutUnits == 0.0 && shortPutUnits == 0.0)
                require(longPutStrike == null && shortPutStrike == null)
                require(netOptionMark == 0.0)
                require(remainingTradingDays == 0 && remainingTimeYears == 0.0)
            }
        }
    }

    private fun requirePositiveReference(value: Double) {
        require(value.isFinite() && value in MIN_REFERENCE_LEVEL..MAX_REFERENCE_LEVEL)
    }

    private fun requireFiniteNonNegative(value: Double) {
        require(value.isFinite() && value in 0.0..MAX_AMOUNT)
    }

    private fun approximatelyEqual(
        left: Double,
        right: Double,
    ): Boolean = abs(left - right) <= ACCOUNTING_EPSILON * maxOf(1.0, abs(left), abs(right))

    private fun accountingTolerance(value: Double): Double = ACCOUNTING_EPSILON * maxOf(1.0, abs(value))

    companion object {
        const val MIN_NAV: Double = 1e-9
        const val MAX_NAV: Double = 1e18
        private const val MAX_AMOUNT: Double = 1e24
        const val MIN_REFERENCE_LEVEL: Double = 1e-12
        const val MAX_REFERENCE_LEVEL: Double = 1e18
        private const val MAX_TENOR_TRADING_DAYS: Int = 504
        private const val MAX_TIME_YEARS: Double = 2.0
        private const val ACCOUNTING_EPSILON: Double = 1e-9
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{2,199}")
    }
}
