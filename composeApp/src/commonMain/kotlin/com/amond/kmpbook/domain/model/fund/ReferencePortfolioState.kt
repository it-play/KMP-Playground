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
    val methodologyPathState: EquityMethodologyPathState,
    val revision: Long,
    val lastReconstitutionDate: LocalDate,
    val lastRebalanceDate: LocalDate,
    /** 정기 재구성이 없는 위원회형 지수는 최초 bootstrap 뒤 null이다. */
    val nextReconstitutionDate: LocalDate?,
    val nextRebalanceDate: LocalDate,
    val pendingSelectionDate: LocalDate? = null,
    val pendingSelectionIncumbentAssetIds: List<String>? = null,
    val pendingPlans: List<ReferencePortfolioPlan>,
    val lastTurnoverRate: Double,
    val estimatedAnnualIncomeYield: Double,
    val asOf: Instant,
    /** Last effective composition action, retained even when bootstrap deliberately has no ledger. */
    val lastAppliedActionKind: ReferencePortfolioActionKind =
        ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
) {
    init {
        require(PORTFOLIO_ID.matches(portfolioId))
        require(portfolioId == portfolioIdFor(benchmarkRef)) {
            "Reference portfolio identity must be derived from its benchmark version."
        }
        require(positions.isNotEmpty() && positions.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
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
        require(nextReconstitutionDate == null || lastReconstitutionDate < nextReconstitutionDate)
        require(lastRebalanceDate < nextRebalanceDate)
        require((pendingSelectionDate == null) == (pendingSelectionIncumbentAssetIds == null))
        pendingSelectionDate?.let { selectionDate ->
            requireNotNull(nextReconstitutionDate)
            require(selectionDate > lastReconstitutionDate && selectionDate < nextReconstitutionDate)
        }
        pendingSelectionIncumbentAssetIds?.let { incumbentAssetIds ->
            requireNotNull(nextReconstitutionDate)
            require(incumbentAssetIds.size in 1..ReferencePortfolioLimits.MAX_CONSTITUENTS)
            require(incumbentAssetIds == incumbentAssetIds.distinct().sorted())
            require(incumbentAssetIds.all(ASSET_ID::matches))
        }
        require(pendingPlans.size <= MAX_PENDING_PLANS)
        require(pendingPlans == pendingPlans.sortedWith(PLAN_ORDER))
        require(pendingPlans.map(ReferencePortfolioPlan::id).distinct().size == pendingPlans.size)
        require(pendingPlans.all { plan ->
            plan.portfolioId == portfolioId && plan.benchmarkRef == benchmarkRef &&
                plan.effectiveDate >= lastRebalanceDate
        })
        require(lastTurnoverRate.isFinite() && lastTurnoverRate in 0.0..1.0)
        require(estimatedAnnualIncomeYield.isFinite() && estimatedAnnualIncomeYield in 0.0..1.0)
    }

    companion object {
        fun portfolioIdFor(ref: BenchmarkRef): String =
            "reference:${ref.benchmarkId}:v${ref.version}"

        const val WEIGHT_EPSILON: Double = 1e-8
        const val MAX_PENDING_PLANS: Int = 32
        private val PORTFOLIO_ID = Regex("[a-z0-9][a-z0-9:._-]{2,199}")
        private val ASSET_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
        private val PLAN_ORDER = compareBy<ReferencePortfolioPlan>(ReferencePortfolioPlan::effectiveDate)
            .thenBy { plan -> plan.kind.executionPriority() }
            .thenBy(ReferencePortfolioPlan::id)

        private fun ReferencePortfolioActionKind.executionPriority(): Int = when (this) {
            ReferencePortfolioActionKind.CONSTITUENT_MERGER,
            ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION,
            ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
            ReferencePortfolioActionKind.TERMINAL_REMOVAL,
            -> 0
            ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL -> 1
            ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION -> 2
            ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION -> 3
            ReferencePortfolioActionKind.SCHEDULED_REWEIGHT -> 4
            ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT -> 5
            ReferencePortfolioActionKind.SPIN_OFF_ADDITION -> 6
        }
    }
}
