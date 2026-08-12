package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** One selection or weight-reset event in a shared composite reference. */
class CompositeReferenceRebalanceRecord(
    val id: String,
    val benchmarkRef: BenchmarkRef,
    val kind: CompositeReferenceActionKind,
    val effectiveDate: LocalDate,
    val effectiveAt: Instant,
    addedSleeveIds: List<String>,
    removedSleeveIds: List<String>,
    cashSubstitutedSleeveIds: List<String>,
    val compositionHashBefore: String,
    val compositionHashAfter: String,
    val turnoverRate: Double,
    val resultingGrossExposure: Double,
    val resultingNetExposure: Double,
    val resultingDurationYears: Double,
    val revision: Long,
) {
    val addedSleeveIds = addedSleeveIds.toList()
    val removedSleeveIds = removedSleeveIds.toList()
    val cashSubstitutedSleeveIds = cashSubstitutedSleeveIds.toList()

    init {
        require(id.isNotBlank() && id.length <= 512)
        require(this.addedSleeveIds == this.addedSleeveIds.sorted().distinct())
        require(this.removedSleeveIds == this.removedSleeveIds.sorted().distinct())
        require(this.cashSubstitutedSleeveIds == this.cashSubstitutedSleeveIds.sorted().distinct())
        require(this.addedSleeveIds.toSet().intersect(this.removedSleeveIds.toSet()).isEmpty())
        when (kind) {
            CompositeReferenceActionKind.SELECTION -> require(this.cashSubstitutedSleeveIds.isEmpty())
            CompositeReferenceActionKind.REWEIGHT -> require(
                this.addedSleeveIds.isEmpty() && this.removedSleeveIds.isEmpty() &&
                    this.cashSubstitutedSleeveIds.isEmpty(),
            )
            CompositeReferenceActionKind.EXTRAORDINARY_SOURCE_TO_CASH -> {
                require(this.addedSleeveIds.isEmpty() && this.removedSleeveIds.isEmpty())
                require(this.cashSubstitutedSleeveIds.isNotEmpty())
                require(turnoverRate == 0.0)
            }
        }
        require(HEX_16_PATTERN.matches(compositionHashBefore))
        require(HEX_16_PATTERN.matches(compositionHashAfter))
        require(turnoverRate.isFinite() && turnoverRate in 0.0..10.0)
        require(resultingGrossExposure.isFinite() && resultingGrossExposure in 0.0..10.0)
        require(resultingNetExposure.isFinite() && resultingNetExposure in -10.0..10.0)
        require(abs(resultingNetExposure) <= resultingGrossExposure + EXPOSURE_EPSILON)
        require(resultingDurationYears.isFinite() && resultingDurationYears in -50.0..50.0)
        require(revision > 0L)
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is CompositeReferenceRebalanceRecord &&
            id == other.id && benchmarkRef == other.benchmarkRef && kind == other.kind &&
            effectiveDate == other.effectiveDate && effectiveAt == other.effectiveAt &&
            addedSleeveIds == other.addedSleeveIds && removedSleeveIds == other.removedSleeveIds &&
            cashSubstitutedSleeveIds == other.cashSubstitutedSleeveIds &&
            compositionHashBefore == other.compositionHashBefore &&
            compositionHashAfter == other.compositionHashAfter && turnoverRate == other.turnoverRate &&
            resultingGrossExposure == other.resultingGrossExposure &&
            resultingNetExposure == other.resultingNetExposure &&
            resultingDurationYears == other.resultingDurationYears && revision == other.revision

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + benchmarkRef.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + effectiveDate.hashCode()
        result = 31 * result + effectiveAt.hashCode()
        result = 31 * result + addedSleeveIds.hashCode()
        result = 31 * result + removedSleeveIds.hashCode()
        result = 31 * result + cashSubstitutedSleeveIds.hashCode()
        result = 31 * result + compositionHashBefore.hashCode()
        result = 31 * result + compositionHashAfter.hashCode()
        result = 31 * result + turnoverRate.hashCode()
        result = 31 * result + resultingGrossExposure.hashCode()
        result = 31 * result + resultingNetExposure.hashCode()
        result = 31 * result + resultingDurationYears.hashCode()
        result = 31 * result + revision.hashCode()
        return result
    }

    companion object {
        private const val EXPOSURE_EPSILON: Double = 1e-8
        private val HEX_16_PATTERN = Regex("[0-9a-f]{16}")
    }
}
