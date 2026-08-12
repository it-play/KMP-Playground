package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.FundOfFundsUniverse
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Persisted reference portfolio of selected underlying funds. */
class FundOfFundsState(
    val benchmarkRef: BenchmarkRef,
    val universe: FundOfFundsUniverse,
    positions: List<FundOfFundsPosition>,
    val revision: Long,
    val bootstrapDate: LocalDate,
    val lastSelectionDate: LocalDate?,
    val nextSelectionDate: LocalDate,
    val lastReweightDate: LocalDate?,
    val nextReweightDate: LocalDate,
    val estimatedAnnualIncomeYield: Double,
    val eligibleCandidateCount: Int,
    val profileFingerprint: String,
    val universeFingerprint: String,
    val compositionHash: String,
    val asOf: Instant,
) {
    val positions: List<FundOfFundsPosition> = positions.toList()

    init {
        require(positions.isNotEmpty() && positions.size <= MAX_POSITIONS)
        require(positions == positions.sortedBy(FundOfFundsPosition::candidateFundId))
        require(positions.map(FundOfFundsPosition::candidateFundId).distinct().size == positions.size)
        require(abs(positions.sumOf(FundOfFundsPosition::currentWeight) - 1.0) <= WEIGHT_EPSILON)
        require(abs(positions.sumOf(FundOfFundsPosition::targetWeight) - 1.0) <= WEIGHT_EPSILON)
        require(positions.all { position -> position.asOf == asOf })
        require(revision >= 0L)
        require(lastSelectionDate == null || lastSelectionDate >= bootstrapDate)
        require(lastReweightDate == null || lastReweightDate >= bootstrapDate)
        require(nextSelectionDate > (lastSelectionDate ?: bootstrapDate))
        require(nextReweightDate > (lastReweightDate ?: bootstrapDate))
        require(estimatedAnnualIncomeYield.isFinite() && estimatedAnnualIncomeYield in 0.0..1.0)
        require(eligibleCandidateCount >= positions.size)
        require(HEX_16_PATTERN.matches(profileFingerprint))
        require(HEX_16_PATTERN.matches(universeFingerprint))
        require(HEX_16_PATTERN.matches(compositionHash))
    }

    fun copy(
        benchmarkRef: BenchmarkRef = this.benchmarkRef,
        universe: FundOfFundsUniverse = this.universe,
        positions: List<FundOfFundsPosition> = this.positions,
        revision: Long = this.revision,
        bootstrapDate: LocalDate = this.bootstrapDate,
        lastSelectionDate: LocalDate? = this.lastSelectionDate,
        nextSelectionDate: LocalDate = this.nextSelectionDate,
        lastReweightDate: LocalDate? = this.lastReweightDate,
        nextReweightDate: LocalDate = this.nextReweightDate,
        estimatedAnnualIncomeYield: Double = this.estimatedAnnualIncomeYield,
        eligibleCandidateCount: Int = this.eligibleCandidateCount,
        profileFingerprint: String = this.profileFingerprint,
        universeFingerprint: String = this.universeFingerprint,
        compositionHash: String = this.compositionHash,
        asOf: Instant = this.asOf,
    ): FundOfFundsState = FundOfFundsState(
        benchmarkRef = benchmarkRef,
        universe = universe,
        positions = positions,
        revision = revision,
        bootstrapDate = bootstrapDate,
        lastSelectionDate = lastSelectionDate,
        nextSelectionDate = nextSelectionDate,
        lastReweightDate = lastReweightDate,
        nextReweightDate = nextReweightDate,
        estimatedAnnualIncomeYield = estimatedAnnualIncomeYield,
        eligibleCandidateCount = eligibleCandidateCount,
        profileFingerprint = profileFingerprint,
        universeFingerprint = universeFingerprint,
        compositionHash = compositionHash,
        asOf = asOf,
    )

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FundOfFundsState &&
            benchmarkRef == other.benchmarkRef &&
            universe == other.universe &&
            positions == other.positions &&
            revision == other.revision &&
            bootstrapDate == other.bootstrapDate &&
            lastSelectionDate == other.lastSelectionDate &&
            nextSelectionDate == other.nextSelectionDate &&
            lastReweightDate == other.lastReweightDate &&
            nextReweightDate == other.nextReweightDate &&
            estimatedAnnualIncomeYield == other.estimatedAnnualIncomeYield &&
            eligibleCandidateCount == other.eligibleCandidateCount &&
            profileFingerprint == other.profileFingerprint &&
            universeFingerprint == other.universeFingerprint &&
            compositionHash == other.compositionHash &&
            asOf == other.asOf

    override fun hashCode(): Int {
        var result = benchmarkRef.hashCode()
        result = 31 * result + universe.hashCode()
        result = 31 * result + positions.hashCode()
        result = 31 * result + revision.hashCode()
        result = 31 * result + bootstrapDate.hashCode()
        result = 31 * result + (lastSelectionDate?.hashCode() ?: 0)
        result = 31 * result + nextSelectionDate.hashCode()
        result = 31 * result + (lastReweightDate?.hashCode() ?: 0)
        result = 31 * result + nextReweightDate.hashCode()
        result = 31 * result + estimatedAnnualIncomeYield.hashCode()
        result = 31 * result + eligibleCandidateCount
        result = 31 * result + profileFingerprint.hashCode()
        result = 31 * result + universeFingerprint.hashCode()
        result = 31 * result + compositionHash.hashCode()
        result = 31 * result + asOf.hashCode()
        return result
    }

    override fun toString(): String =
        "FundOfFundsState(benchmarkRef=$benchmarkRef, positions=${positions.size}, " +
            "revision=$revision, asOf=$asOf)"

    companion object {
        const val MAX_POSITIONS: Int = 256
        const val WEIGHT_EPSILON: Double = 1e-8
        private val HEX_16_PATTERN = Regex("[0-9a-f]{16}")
    }
}
