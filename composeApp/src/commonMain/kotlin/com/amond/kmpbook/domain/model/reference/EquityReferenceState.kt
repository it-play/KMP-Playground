package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.EquityReferenceRegion
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Persisted representative reference basket. It is neither a forecast of future real holdings nor
 * a product portfolio; fees, tracking error, leverage and option overlays remain product state.
 *
 * Collection inputs are defensively copied so a caller cannot mutate persisted methodology state
 * after construction.
 */
class EquityReferenceState internal constructor(
    val benchmarkRef: BenchmarkRef,
    val region: EquityReferenceRegion,
    resolvedCountryCodes: Set<String>,
    val themeId: String?,
    positions: List<EquityReferencePosition>,
    val factorExposure: EquityReferenceFactorExposure,
    val revision: Long,
    val lastSelectionDate: LocalDate,
    val nextSelectionDate: LocalDate,
    val lastReweightDate: LocalDate,
    val nextReweightDate: LocalDate,
    val estimatedAnnualIncomeYield: Double,
    val declaredTargetConstituentCount: Int?,
    val eligibleCandidateCount: Int,
    val representativeBasketLimit: Int,
    val profileFingerprint: String,
    val universeModelVersion: String,
    val universeFingerprint: String,
    val compositionHash: String,
    val asOf: Instant,
    copyCollectionInputs: Boolean = true,
) {
    val resolvedCountryCodes: Set<String> = if (copyCollectionInputs) {
        resolvedCountryCodes.sorted().toCollection(linkedSetOf()).toSet()
    } else {
        resolvedCountryCodes
    }
    val positions: List<EquityReferencePosition> = if (copyCollectionInputs) {
        positions.toList()
    } else {
        positions
    }

    init {
        if (copyCollectionInputs) {
            require(this.resolvedCountryCodes.isNotEmpty())
            require(this.resolvedCountryCodes.all(COUNTRY_CODE_PATTERN::matches))
            require(this.positions.isNotEmpty() && this.positions.size <= representativeBasketLimit)
            require(this.positions == this.positions.sortedBy(EquityReferencePosition::assetId))
            require(this.positions.map(EquityReferencePosition::assetId).distinct().size == this.positions.size)
            require(abs(this.positions.sumOf(EquityReferencePosition::weight) - 1.0) <= WEIGHT_EPSILON)
            require(abs(this.positions.sumOf(EquityReferencePosition::targetWeight) - 1.0) <= WEIGHT_EPSILON)
            require(this.positions.all { it.countryCode in this.resolvedCountryCodes })
            require(
                factorExposure.countryWeights.keys ==
                    this.positions.mapTo(linkedSetOf()) { it.countryCode },
            )
            declaredTargetConstituentCount?.let { declared ->
                require(this.positions.sumOf(EquityReferencePosition::representedConstituentCount) == declared)
            }
            require(eligibleCandidateCount >= this.positions.size)
            require(themeId == null || THEME_ID_PATTERN.matches(themeId))
            require(revision >= 0L)
            require(nextSelectionDate > lastSelectionDate)
            require(nextReweightDate > lastReweightDate)
            require(
                estimatedAnnualIncomeYield.isFinite() &&
                    estimatedAnnualIncomeYield in 0.0..MAX_INCOME_YIELD,
            )
            require(declaredTargetConstituentCount == null || declaredTargetConstituentCount > 0)
            require(representativeBasketLimit in 1..MAX_REPRESENTATIVE_BASKET_SIZE)
            require(HEX_16_PATTERN.matches(profileFingerprint))
            require(VERSION_PATTERN.matches(universeModelVersion))
            require(HEX_16_PATTERN.matches(universeFingerprint))
            require(HEX_16_PATTERN.matches(compositionHash))
        }
    }

    val isRepresentativeBasket: Boolean get() = true

    /** Advances only the immutable timestamp while safely sharing already-owned collection values. */
    internal fun withAsOf(asOf: Instant): EquityReferenceState = EquityReferenceState(
        benchmarkRef = benchmarkRef,
        region = region,
        resolvedCountryCodes = resolvedCountryCodes,
        themeId = themeId,
        positions = positions,
        factorExposure = factorExposure,
        revision = revision,
        lastSelectionDate = lastSelectionDate,
        nextSelectionDate = nextSelectionDate,
        lastReweightDate = lastReweightDate,
        nextReweightDate = nextReweightDate,
        estimatedAnnualIncomeYield = estimatedAnnualIncomeYield,
        declaredTargetConstituentCount = declaredTargetConstituentCount,
        eligibleCandidateCount = eligibleCandidateCount,
        representativeBasketLimit = representativeBasketLimit,
        profileFingerprint = profileFingerprint,
        universeModelVersion = universeModelVersion,
        universeFingerprint = universeFingerprint,
        compositionHash = compositionHash,
        asOf = asOf,
        copyCollectionInputs = false,
    )

    fun copy(
        benchmarkRef: BenchmarkRef = this.benchmarkRef,
        region: EquityReferenceRegion = this.region,
        resolvedCountryCodes: Set<String> = this.resolvedCountryCodes,
        themeId: String? = this.themeId,
        positions: List<EquityReferencePosition> = this.positions,
        factorExposure: EquityReferenceFactorExposure = this.factorExposure,
        revision: Long = this.revision,
        lastSelectionDate: LocalDate = this.lastSelectionDate,
        nextSelectionDate: LocalDate = this.nextSelectionDate,
        lastReweightDate: LocalDate = this.lastReweightDate,
        nextReweightDate: LocalDate = this.nextReweightDate,
        estimatedAnnualIncomeYield: Double = this.estimatedAnnualIncomeYield,
        declaredTargetConstituentCount: Int? = this.declaredTargetConstituentCount,
        eligibleCandidateCount: Int = this.eligibleCandidateCount,
        representativeBasketLimit: Int = this.representativeBasketLimit,
        profileFingerprint: String = this.profileFingerprint,
        universeModelVersion: String = this.universeModelVersion,
        universeFingerprint: String = this.universeFingerprint,
        compositionHash: String = this.compositionHash,
        asOf: Instant = this.asOf,
    ): EquityReferenceState = EquityReferenceState(
        benchmarkRef = benchmarkRef,
        region = region,
        resolvedCountryCodes = resolvedCountryCodes,
        themeId = themeId,
        positions = positions,
        factorExposure = factorExposure,
        revision = revision,
        lastSelectionDate = lastSelectionDate,
        nextSelectionDate = nextSelectionDate,
        lastReweightDate = lastReweightDate,
        nextReweightDate = nextReweightDate,
        estimatedAnnualIncomeYield = estimatedAnnualIncomeYield,
        declaredTargetConstituentCount = declaredTargetConstituentCount,
        eligibleCandidateCount = eligibleCandidateCount,
        representativeBasketLimit = representativeBasketLimit,
        profileFingerprint = profileFingerprint,
        universeModelVersion = universeModelVersion,
        universeFingerprint = universeFingerprint,
        compositionHash = compositionHash,
        asOf = asOf,
    )

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is EquityReferenceState &&
            benchmarkRef == other.benchmarkRef &&
            region == other.region &&
            resolvedCountryCodes == other.resolvedCountryCodes &&
            themeId == other.themeId &&
            positions == other.positions &&
            factorExposure == other.factorExposure &&
            revision == other.revision &&
            lastSelectionDate == other.lastSelectionDate &&
            nextSelectionDate == other.nextSelectionDate &&
            lastReweightDate == other.lastReweightDate &&
            nextReweightDate == other.nextReweightDate &&
            estimatedAnnualIncomeYield == other.estimatedAnnualIncomeYield &&
            declaredTargetConstituentCount == other.declaredTargetConstituentCount &&
            eligibleCandidateCount == other.eligibleCandidateCount &&
            representativeBasketLimit == other.representativeBasketLimit &&
            profileFingerprint == other.profileFingerprint &&
            universeModelVersion == other.universeModelVersion &&
            universeFingerprint == other.universeFingerprint &&
            compositionHash == other.compositionHash &&
            asOf == other.asOf

    override fun hashCode(): Int {
        var result = benchmarkRef.hashCode()
        result = 31 * result + region.hashCode()
        result = 31 * result + resolvedCountryCodes.hashCode()
        result = 31 * result + (themeId?.hashCode() ?: 0)
        result = 31 * result + positions.hashCode()
        result = 31 * result + factorExposure.hashCode()
        result = 31 * result + revision.hashCode()
        result = 31 * result + lastSelectionDate.hashCode()
        result = 31 * result + nextSelectionDate.hashCode()
        result = 31 * result + lastReweightDate.hashCode()
        result = 31 * result + nextReweightDate.hashCode()
        result = 31 * result + estimatedAnnualIncomeYield.hashCode()
        result = 31 * result + (declaredTargetConstituentCount ?: 0)
        result = 31 * result + eligibleCandidateCount
        result = 31 * result + representativeBasketLimit
        result = 31 * result + profileFingerprint.hashCode()
        result = 31 * result + universeModelVersion.hashCode()
        result = 31 * result + universeFingerprint.hashCode()
        result = 31 * result + compositionHash.hashCode()
        result = 31 * result + asOf.hashCode()
        return result
    }

    override fun toString(): String =
        "EquityReferenceState(benchmarkRef=$benchmarkRef, positions=${positions.size}, " +
            "revision=$revision, asOf=$asOf)"

    companion object {
        const val MAX_REPRESENTATIVE_BASKET_SIZE: Int = 256
        private const val WEIGHT_EPSILON: Double = 1e-8
        private const val MAX_INCOME_YIELD: Double = 1.0
        private val COUNTRY_CODE_PATTERN = Regex("[A-Z]{2}")
        private val THEME_ID_PATTERN = Regex("[a-z0-9]+(?:[.-][a-z0-9]+)*")
        private val HEX_16_PATTERN = Regex("[0-9a-f]{16}")
        private val VERSION_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
