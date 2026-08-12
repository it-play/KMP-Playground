package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.FundOfFundsCategory
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** One simulated, non-tradable underlying fund selected by a fund-of-funds methodology. */
data class FundOfFundsPosition(
    val candidateFundId: String,
    val category: FundOfFundsCategory,
    val underlyingBenchmarkRef: BenchmarkRef,
    val currentWeight: Double,
    val targetWeight: Double,
    val marketDiscountRate: Double,
    val indicatedAnnualDistributionYield: Double,
    val leverageRatio: Double,
    val annualExpenseRate: Double,
    val annualResidualVolatility: Double,
    val liquidityScore: Double,
    val selectionScore: Double,
    val enteredOn: LocalDate,
    val asOf: Instant,
) {
    init {
        require(CANDIDATE_ID_PATTERN.matches(candidateFundId))
        require(currentWeight.isFinite() && currentWeight in MIN_WEIGHT..1.0)
        require(targetWeight.isFinite() && targetWeight in MIN_WEIGHT..1.0)
        require(marketDiscountRate.isFinite() && marketDiscountRate in MIN_DISCOUNT..MAX_PREMIUM)
        require(
            indicatedAnnualDistributionYield.isFinite() &&
                indicatedAnnualDistributionYield in 0.0..MAX_DISTRIBUTION_YIELD,
        )
        require(leverageRatio.isFinite() && leverageRatio in 0.0..MAX_LEVERAGE)
        require(annualExpenseRate.isFinite() && annualExpenseRate in 0.0..MAX_EXPENSE_RATE)
        require(
            annualResidualVolatility.isFinite() &&
                annualResidualVolatility in 0.0..MAX_RESIDUAL_VOLATILITY,
        )
        require(liquidityScore.isFinite() && liquidityScore in 0.0..1.0)
        require(selectionScore.isFinite() && selectionScore in -100.0..100.0)
    }

    companion object {
        const val MIN_WEIGHT: Double = 1e-12
        const val MIN_DISCOUNT: Double = -0.95
        const val MAX_PREMIUM: Double = 2.0
        const val MAX_DISTRIBUTION_YIELD: Double = 1.0
        const val MAX_LEVERAGE: Double = 5.0
        const val MAX_EXPENSE_RATE: Double = 0.25
        const val MAX_RESIDUAL_VOLATILITY: Double = 3.0
        private val CANDIDATE_ID_PATTERN = Regex("sim-fof:[a-z0-9._-]+:[0-9]{3}")
    }
}
