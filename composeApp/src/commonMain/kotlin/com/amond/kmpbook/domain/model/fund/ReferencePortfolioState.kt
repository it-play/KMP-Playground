package com.amond.kmpbook.domain.model.fund

import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Persisted composition and schedule of one shared benchmark/reference portfolio.
 *
 * This is not an ETF's legal holdings ledger. Products that track the same [benchmarkRef] bind to
 * this state and apply fees, FX, replication effects and tracking error in their product layer.
 */
data class ReferencePortfolioState(
    val portfolioId: String,
    val benchmarkRef: BenchmarkRef,
    val positions: List<ReferencePortfolioPosition>,
    val revision: Long,
    val lastReconstitutionDate: LocalDate,
    val lastRebalanceDate: LocalDate,
    val nextReconstitutionDate: LocalDate,
    val nextRebalanceDate: LocalDate,
    val pendingPlans: List<ReferencePortfolioPlan>,
    val lastTurnoverRate: Double,
    val estimatedAnnualIncomeYield: Double,
    val asOf: Instant,
    /** Last effective composition action, retained even when bootstrap deliberately has no ledger. */
    val lastAppliedActionKind: ReferencePortfolioActionKind =
        ReferencePortfolioActionKind.ANNUAL_RECONSTITUTION,
) {
    init {
        require(PORTFOLIO_ID.matches(portfolioId))
        require(portfolioId == portfolioIdFor(benchmarkRef)) {
            "Reference portfolio identity must be derived from its benchmark version."
        }
        require(positions.isNotEmpty() && positions.size <= EquityMethodologyProfile.MAX_CONSTITUENTS)
        require(positions.map(ReferencePortfolioPosition::assetId).distinct().size == positions.size)
        require(positions.map(ReferencePortfolioPosition::selectionRank).distinct().size == positions.size)
        require(positions == positions.sortedBy(ReferencePortfolioPosition::assetId)) {
            "Reference portfolio positions must be stored in stable assetId order."
        }
        require(abs(positions.sumOf(ReferencePortfolioPosition::currentWeight) - 1.0) <= WEIGHT_EPSILON)
        require(abs(positions.sumOf(ReferencePortfolioPosition::targetWeight) - 1.0) <= WEIGHT_EPSILON)
        require(revision >= 0L)
        require(lastReconstitutionDate <= lastRebalanceDate)
        require(positions.all { it.enteredOn <= lastRebalanceDate })
        require(lastReconstitutionDate < nextReconstitutionDate)
        require(lastRebalanceDate < nextRebalanceDate)
        require(pendingPlans.size <= MAX_PENDING_PLANS)
        require(pendingPlans == pendingPlans.sortedWith(PLAN_ORDER))
        require(pendingPlans.map(ReferencePortfolioPlan::id).distinct().size == pendingPlans.size)
        require(pendingPlans.map(ReferencePortfolioPlan::effectiveDate).distinct().size == pendingPlans.size)
        require(pendingPlans.all { plan ->
            plan.portfolioId == portfolioId && plan.benchmarkRef == benchmarkRef &&
                plan.effectiveDate > lastRebalanceDate
        })
        require(lastTurnoverRate.isFinite() && lastTurnoverRate in 0.0..1.0)
        require(estimatedAnnualIncomeYield.isFinite() && estimatedAnnualIncomeYield in 0.0..1.0)
    }

    companion object {
        fun portfolioIdFor(ref: BenchmarkRef): String =
            "reference:${ref.benchmarkId}:v${ref.version}"

        const val WEIGHT_EPSILON: Double = 1e-8
        const val MAX_PENDING_PLANS: Int = 8
        private val PORTFOLIO_ID = Regex("[a-z0-9][a-z0-9:._-]{2,199}")
        private val PLAN_ORDER = compareBy<ReferencePortfolioPlan>(ReferencePortfolioPlan::effectiveDate)
            .thenBy(ReferencePortfolioPlan::id)
    }
}
