package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** One immutable representative-basket reconstitution/reweight ledger entry. */
class EquityReferenceRebalanceRecord(
    val id: String,
    val benchmarkRef: BenchmarkRef,
    val kind: EquityReferenceActionKind,
    val selectionDate: LocalDate,
    val effectiveAt: Instant,
    addedAssetIds: List<String>,
    removedAssetIds: List<String>,
    val compositionHashBefore: String,
    val compositionHashAfter: String,
    val turnoverRate: Double,
    val resultingPositionCount: Int,
    val representedConstituentCount: Int,
    val revision: Long,
) {
    val addedAssetIds: List<String> = addedAssetIds.toList()
    val removedAssetIds: List<String> = removedAssetIds.toList()

    init {
        require(id.isNotBlank() && id.length <= 512)
        require(this.addedAssetIds == this.addedAssetIds.sorted())
        require(this.addedAssetIds.distinct() == this.addedAssetIds)
        require(this.removedAssetIds == this.removedAssetIds.sorted())
        require(this.removedAssetIds.distinct() == this.removedAssetIds)
        require(this.addedAssetIds.toSet().intersect(this.removedAssetIds.toSet()).isEmpty())
        if (kind == EquityReferenceActionKind.REWEIGHT) {
            require(this.addedAssetIds.isEmpty() && this.removedAssetIds.isEmpty())
        }
        require(HEX_16_PATTERN.matches(compositionHashBefore))
        require(HEX_16_PATTERN.matches(compositionHashAfter))
        require(turnoverRate.isFinite() && turnoverRate in 0.0..1.0)
        require(resultingPositionCount in 1..EquityReferenceState.MAX_REPRESENTATIVE_BASKET_SIZE)
        require(representedConstituentCount >= resultingPositionCount)
        require(revision > 0L)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is EquityReferenceRebalanceRecord &&
            id == other.id &&
            benchmarkRef == other.benchmarkRef &&
            kind == other.kind &&
            selectionDate == other.selectionDate &&
            effectiveAt == other.effectiveAt &&
            addedAssetIds == other.addedAssetIds &&
            removedAssetIds == other.removedAssetIds &&
            compositionHashBefore == other.compositionHashBefore &&
            compositionHashAfter == other.compositionHashAfter &&
            turnoverRate == other.turnoverRate &&
            resultingPositionCount == other.resultingPositionCount &&
            representedConstituentCount == other.representedConstituentCount &&
            revision == other.revision

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + benchmarkRef.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + selectionDate.hashCode()
        result = 31 * result + effectiveAt.hashCode()
        result = 31 * result + addedAssetIds.hashCode()
        result = 31 * result + removedAssetIds.hashCode()
        result = 31 * result + compositionHashBefore.hashCode()
        result = 31 * result + compositionHashAfter.hashCode()
        result = 31 * result + turnoverRate.hashCode()
        result = 31 * result + resultingPositionCount
        result = 31 * result + representedConstituentCount
        result = 31 * result + revision.hashCode()
        return result
    }

    override fun toString(): String =
        "EquityReferenceRebalanceRecord(id=$id, kind=$kind, revision=$revision)"

    companion object {
        private val HEX_16_PATTERN = Regex("[0-9a-f]{16}")
    }
}
