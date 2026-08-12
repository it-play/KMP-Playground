package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Immutable selection/reweight ledger entry for one fund-of-funds reference. */
class FundOfFundsRebalanceRecord(
    val id: String,
    val benchmarkRef: BenchmarkRef,
    val kind: FundOfFundsActionKind,
    val effectiveDate: LocalDate,
    val effectiveAt: Instant,
    addedCandidateFundIds: List<String>,
    removedCandidateFundIds: List<String>,
    val compositionHashBefore: String,
    val compositionHashAfter: String,
    val oneWayTurnoverRate: Double,
    val resultingFundCount: Int,
    val revision: Long,
) {
    val addedCandidateFundIds: List<String> = addedCandidateFundIds.toList()
    val removedCandidateFundIds: List<String> = removedCandidateFundIds.toList()

    init {
        require(id.isNotBlank() && id.length <= MAX_ID_LENGTH)
        require(addedCandidateFundIds == addedCandidateFundIds.sorted())
        require(removedCandidateFundIds == removedCandidateFundIds.sorted())
        require(addedCandidateFundIds.distinct() == addedCandidateFundIds)
        require(removedCandidateFundIds.distinct() == removedCandidateFundIds)
        require(addedCandidateFundIds.toSet().intersect(removedCandidateFundIds.toSet()).isEmpty())
        if (kind == FundOfFundsActionKind.REWEIGHT) {
            require(addedCandidateFundIds.isEmpty() && removedCandidateFundIds.isEmpty())
        }
        require(HEX_16_PATTERN.matches(compositionHashBefore))
        require(HEX_16_PATTERN.matches(compositionHashAfter))
        require(oneWayTurnoverRate.isFinite() && oneWayTurnoverRate in 0.0..1.0)
        require(resultingFundCount in 1..FundOfFundsState.MAX_POSITIONS)
        require(revision > 0L)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FundOfFundsRebalanceRecord &&
            id == other.id &&
            benchmarkRef == other.benchmarkRef &&
            kind == other.kind &&
            effectiveDate == other.effectiveDate &&
            effectiveAt == other.effectiveAt &&
            addedCandidateFundIds == other.addedCandidateFundIds &&
            removedCandidateFundIds == other.removedCandidateFundIds &&
            compositionHashBefore == other.compositionHashBefore &&
            compositionHashAfter == other.compositionHashAfter &&
            oneWayTurnoverRate == other.oneWayTurnoverRate &&
            resultingFundCount == other.resultingFundCount &&
            revision == other.revision

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + benchmarkRef.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + effectiveDate.hashCode()
        result = 31 * result + effectiveAt.hashCode()
        result = 31 * result + addedCandidateFundIds.hashCode()
        result = 31 * result + removedCandidateFundIds.hashCode()
        result = 31 * result + compositionHashBefore.hashCode()
        result = 31 * result + compositionHashAfter.hashCode()
        result = 31 * result + oneWayTurnoverRate.hashCode()
        result = 31 * result + resultingFundCount
        result = 31 * result + revision.hashCode()
        return result
    }

    companion object {
        const val MAX_ID_LENGTH: Int = 512
        private val HEX_16_PATTERN = Regex("[0-9a-f]{16}")
    }
}
