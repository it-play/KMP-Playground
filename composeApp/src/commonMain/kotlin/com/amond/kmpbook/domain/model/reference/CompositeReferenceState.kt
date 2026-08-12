package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Persisted reference allocation; it is shared by products and contains no product tracking overlay. */
class CompositeReferenceState(
    val benchmarkRef: BenchmarkRef,
    positions: List<CompositeReferenceSleevePosition>,
    val revision: Long,
    val lastSelectionDate: LocalDate?,
    val nextSelectionDate: LocalDate?,
    val lastReweightDate: LocalDate?,
    val nextReweightDate: LocalDate?,
    val estimatedAnnualIncomeYield: Double,
    val grossExposure: Double,
    val netExposure: Double,
    val effectiveDurationYears: Double,
    val lastMortgageRateAnnual: Double,
    val bootstrapCompositionHash: String,
    val profileFingerprint: String,
    val compositionHash: String,
    val asOf: Instant,
) {
    val positions: List<CompositeReferenceSleevePosition> = positions.toList()

    init {
        require(this.positions.isNotEmpty() && this.positions.size <= MAX_POSITIONS)
        require(this.positions == this.positions.sortedBy(CompositeReferenceSleevePosition::sleeveId))
        require(this.positions.map(CompositeReferenceSleevePosition::sleeveId).distinct().size == this.positions.size)
        require(revision >= 0L)
        if (revision == 0L) {
            require(lastSelectionDate == null && lastReweightDate == null)
        }
        require(nextSelectionDate == null || lastSelectionDate == null || nextSelectionDate > lastSelectionDate)
        require(nextReweightDate == null || lastReweightDate == null || nextReweightDate > lastReweightDate)
        require(estimatedAnnualIncomeYield.isFinite() && estimatedAnnualIncomeYield in 0.0..1.0)
        require(grossExposure.isFinite() && grossExposure in 0.0..MAX_EXPOSURE)
        require(netExposure.isFinite() && netExposure in -MAX_EXPOSURE..MAX_EXPOSURE)
        require(effectiveDurationYears.isFinite() && effectiveDurationYears in -50.0..50.0)
        require(lastMortgageRateAnnual.isFinite() && lastMortgageRateAnnual in 0.0..1.0)
        require(abs(grossExposure - this.positions.sumOf { it.currentWeightMagnitude }) <= EPSILON)
        require(abs(netExposure - this.positions.sumOf { it.signedCurrentWeight }) <= EPSILON)
        require(HEX_16_PATTERN.matches(bootstrapCompositionHash))
        require(HEX_16_PATTERN.matches(profileFingerprint))
        require(HEX_16_PATTERN.matches(compositionHash))
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is CompositeReferenceState &&
            benchmarkRef == other.benchmarkRef && positions == other.positions &&
            revision == other.revision && lastSelectionDate == other.lastSelectionDate &&
            nextSelectionDate == other.nextSelectionDate && lastReweightDate == other.lastReweightDate &&
            nextReweightDate == other.nextReweightDate &&
            estimatedAnnualIncomeYield == other.estimatedAnnualIncomeYield &&
            grossExposure == other.grossExposure && netExposure == other.netExposure &&
            effectiveDurationYears == other.effectiveDurationYears &&
            lastMortgageRateAnnual == other.lastMortgageRateAnnual &&
            bootstrapCompositionHash == other.bootstrapCompositionHash &&
            profileFingerprint == other.profileFingerprint && compositionHash == other.compositionHash &&
            asOf == other.asOf

    override fun hashCode(): Int {
        var result = benchmarkRef.hashCode()
        result = 31 * result + positions.hashCode()
        result = 31 * result + revision.hashCode()
        result = 31 * result + (lastSelectionDate?.hashCode() ?: 0)
        result = 31 * result + (nextSelectionDate?.hashCode() ?: 0)
        result = 31 * result + (lastReweightDate?.hashCode() ?: 0)
        result = 31 * result + (nextReweightDate?.hashCode() ?: 0)
        result = 31 * result + estimatedAnnualIncomeYield.hashCode()
        result = 31 * result + grossExposure.hashCode()
        result = 31 * result + netExposure.hashCode()
        result = 31 * result + effectiveDurationYears.hashCode()
        result = 31 * result + lastMortgageRateAnnual.hashCode()
        result = 31 * result + bootstrapCompositionHash.hashCode()
        result = 31 * result + profileFingerprint.hashCode()
        result = 31 * result + compositionHash.hashCode()
        result = 31 * result + asOf.hashCode()
        return result
    }

    companion object {
        const val MAX_POSITIONS: Int = 64
        private const val MAX_EXPOSURE: Double = 10.0
        private const val EPSILON: Double = 1e-8
        private val HEX_16_PATTERN = Regex("[0-9a-f]{16}")
    }
}
