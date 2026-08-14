package com.amond.kmpbook.domain.model.fund

/**
 * Typed factor/reference policy for equity benchmarks whose full constituent methodology is not yet implemented.
 *
 * Nullable counts and caps mean "not established", never zero. [provenance] and [confidence] describe the
 * individual policy mapping and do not change the enclosing benchmark's support level.
 */
class EquityReferenceProfile(
    val region: EquityReferenceRegion,
    countryCodes: Set<String>,
    val eligibleUniverse: EquityEligibleUniverse,
    val sectorPolicy: EquitySectorPolicy,
    includedSectors: Set<MethodologyEquitySector>,
    val themeId: String?,
    stylePolicies: Set<EquityStylePolicy>,
    val weightingModel: EquityReferenceWeightingModel,
    val targetConstituentCount: Int?,
    val individualWeightCap: Double?,
    val sectorWeightCap: Double?,
    val selectionCalendar: EquityRebalanceCalendar,
    selectionMonths: Set<Int>,
    val reweightCalendar: EquityRebalanceCalendar,
    reweightMonths: Set<Int>,
    val supportLevel: BenchmarkSupportLevel,
    val provenance: EquityReferenceProvenance,
    val confidence: EquityReferenceConfidence,
    officialSourceUrls: Set<String>,
    val assumptionId: String?,
) {
    val countryCodes: Set<String> = countryCodes.sorted().toCollection(linkedSetOf()).toSet()
    val includedSectors: Set<MethodologyEquitySector> = includedSectors
        .sortedBy(MethodologyEquitySector::ordinal)
        .toCollection(linkedSetOf())
        .toSet()
    val stylePolicies: Set<EquityStylePolicy> = stylePolicies
        .sortedBy(EquityStylePolicy::ordinal)
        .toCollection(linkedSetOf())
        .toSet()
    val selectionMonths: Set<Int> = selectionMonths.sorted().toCollection(linkedSetOf()).toSet()
    val reweightMonths: Set<Int> = reweightMonths.sorted().toCollection(linkedSetOf()).toSet()
    val officialSourceUrls: Set<String> = officialSourceUrls.sorted().toCollection(linkedSetOf()).toSet()

    init {
        require(this.countryCodes.size <= MAX_COUNTRY_CODES)
        require(this.countryCodes.all { it.matches(COUNTRY_CODE_PATTERN) }) {
            "Equity country codes must be canonical ISO alpha-2 strings."
        }
        require(this.stylePolicies.isNotEmpty() && this.stylePolicies.size <= MAX_STYLE_POLICIES)
        require(this.includedSectors.size <= MethodologyEquitySector.entries.size)
        require((sectorPolicy == EquitySectorPolicy.INCLUDED_ONLY) == this.includedSectors.isNotEmpty()) {
            "INCLUDED_ONLY sector policy and includedSectors must be present together."
        }
        if (eligibleUniverse == EquityEligibleUniverse.SECTOR_INDUSTRY) {
            require(sectorPolicy == EquitySectorPolicy.INCLUDED_ONLY)
        }
        if (eligibleUniverse == EquityEligibleUniverse.THEMATIC) {
            require(
                sectorPolicy in setOf(
                    EquitySectorPolicy.INCLUDED_ONLY,
                    EquitySectorPolicy.THEMATIC_CROSS_SECTOR,
                ),
            )
        }
        require((eligibleUniverse == EquityEligibleUniverse.THEMATIC) == (themeId != null)) {
            "Thematic equity universes require exactly one stable themeId."
        }
        themeId?.let {
            require(it.length <= MAX_THEME_ID_LENGTH && it.matches(THEME_ID_PATTERN))
        }
        require(
            targetConstituentCount == null ||
                targetConstituentCount in 1..ReferencePortfolioLimits.MAX_CONSTITUENTS,
        )
        require(individualWeightCap == null || individualWeightCap.isValidWeight())
        require(sectorWeightCap == null || sectorWeightCap.isValidWeight())
        if (individualWeightCap != null && sectorWeightCap != null) {
            require(sectorWeightCap >= individualWeightCap)
        }
        if (targetConstituentCount != null && individualWeightCap != null) {
            require(targetConstituentCount * individualWeightCap >= 1.0 - WEIGHT_EPSILON)
        }
        validateCalendar(selectionCalendar, this.selectionMonths, "selection")
        validateCalendar(reweightCalendar, this.reweightMonths, "reweight")
        require(supportLevel != BenchmarkSupportLevel.VERIFIED_RULES) {
            "Fully verified equity rules belong in equityMethodology."
        }
        require(this.officialSourceUrls.size <= MAX_OFFICIAL_SOURCE_URLS)
        require(this.officialSourceUrls.all(::isValidHttpsUrl))
        when (provenance) {
            EquityReferenceProvenance.VERIFIED_INDEX_METHODOLOGY,
            EquityReferenceProvenance.VERIFIED_PRODUCT_DISCLOSURE,
            -> {
                require(this.officialSourceUrls.isNotEmpty())
                require(assumptionId == null)
            }
            EquityReferenceProvenance.MODEL_ASSUMPTION -> {
                require(!assumptionId.isNullOrBlank())
                require(assumptionId.length <= MAX_ASSUMPTION_ID_LENGTH)
                require(assumptionId.none(Char::isISOControl))
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is EquityReferenceProfile &&
            region == other.region &&
            countryCodes == other.countryCodes &&
            eligibleUniverse == other.eligibleUniverse &&
            sectorPolicy == other.sectorPolicy &&
            includedSectors == other.includedSectors &&
            themeId == other.themeId &&
            stylePolicies == other.stylePolicies &&
            weightingModel == other.weightingModel &&
            targetConstituentCount == other.targetConstituentCount &&
            individualWeightCap == other.individualWeightCap &&
            sectorWeightCap == other.sectorWeightCap &&
            selectionCalendar == other.selectionCalendar &&
            selectionMonths == other.selectionMonths &&
            reweightCalendar == other.reweightCalendar &&
            reweightMonths == other.reweightMonths &&
            supportLevel == other.supportLevel &&
            provenance == other.provenance &&
            confidence == other.confidence &&
            officialSourceUrls == other.officialSourceUrls &&
            assumptionId == other.assumptionId

    override fun hashCode(): Int {
        var result = region.hashCode()
        result = 31 * result + countryCodes.hashCode()
        result = 31 * result + eligibleUniverse.hashCode()
        result = 31 * result + sectorPolicy.hashCode()
        result = 31 * result + includedSectors.hashCode()
        result = 31 * result + (themeId?.hashCode() ?: 0)
        result = 31 * result + stylePolicies.hashCode()
        result = 31 * result + weightingModel.hashCode()
        result = 31 * result + (targetConstituentCount ?: 0)
        result = 31 * result + (individualWeightCap?.hashCode() ?: 0)
        result = 31 * result + (sectorWeightCap?.hashCode() ?: 0)
        result = 31 * result + selectionCalendar.hashCode()
        result = 31 * result + selectionMonths.hashCode()
        result = 31 * result + reweightCalendar.hashCode()
        result = 31 * result + reweightMonths.hashCode()
        result = 31 * result + supportLevel.hashCode()
        result = 31 * result + provenance.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        result = 31 * result + (assumptionId?.hashCode() ?: 0)
        return result
    }

    private fun Double.isValidWeight(): Boolean = isFinite() && this in MIN_POSITIVE_WEIGHT..1.0

    private fun validateCalendar(
        calendar: EquityRebalanceCalendar,
        months: Set<Int>,
        label: String,
    ) {
        require(months.all { it in 1..12 }) { "$label months must be between 1 and 12." }
        when (calendar) {
            EquityRebalanceCalendar.CONTINUOUS_ACTIVE,
            EquityRebalanceCalendar.UNVERIFIED,
            -> require(months.isEmpty()) { "$label months must be empty for $calendar." }
            EquityRebalanceCalendar.MONTHLY -> require(months == (1..12).toSet())
            EquityRebalanceCalendar.QUARTERLY -> require(months.size == 4)
            EquityRebalanceCalendar.SEMI_ANNUAL -> require(months.size == 2)
            EquityRebalanceCalendar.ANNUAL -> require(months.size == 1)
        }
    }

    private fun isValidHttpsUrl(value: String): Boolean =
        value.length <= MAX_URL_LENGTH &&
            value.startsWith("https://") &&
            value.length > "https://".length &&
            value.none(Char::isISOControl)

    companion object {
        const val MAX_COUNTRY_CODES: Int = 64
        const val MAX_COUNTRY_CODE_LENGTH: Int = 2
        const val MAX_THEME_ID_LENGTH: Int = 120
        const val MAX_STYLE_POLICIES: Int = 8
        const val MAX_OFFICIAL_SOURCE_URLS: Int = 16
        const val MAX_ASSUMPTION_ID_LENGTH: Int = 160
        const val MAX_URL_LENGTH: Int = 2_048
        private const val MIN_POSITIVE_WEIGHT: Double = 1e-12
        private const val WEIGHT_EPSILON: Double = 1e-9
        private val COUNTRY_CODE_PATTERN: Regex = Regex("[A-Z]{2}")
        private val THEME_ID_PATTERN: Regex = Regex("[a-z0-9]+(?:[.-][a-z0-9]+)*")
    }
}
