package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.EquityReferenceRegion
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import kotlinx.datetime.LocalDate

/**
 * One non-tradable representative constituent anchor. [representedConstituentCount] makes explicit
 * when a bounded proxy cell stands in for more than one methodology constituent.
 */
data class EquityReferencePosition(
    val assetId: String,
    val region: EquityReferenceRegion,
    val countryCode: String,
    val sector: MethodologyEquitySector,
    /** Current NAV weight after deterministic market drift. */
    val weight: Double,
    /** Methodology weight set at the most recent reconstitution or reweight. */
    val targetWeight: Double,
    val representedConstituentCount: Int,
    val selectionScore: Double,
    val indicatedAnnualDividendYield: Double,
    val enteredOn: LocalDate,
) {
    init {
        require(ASSET_ID_PATTERN.matches(assetId))
        require(region != EquityReferenceRegion.GLOBAL)
        require(COUNTRY_CODE_PATTERN.matches(countryCode))
        require(weight.isFinite() && weight in MIN_POSITIVE_WEIGHT..1.0)
        require(targetWeight.isFinite() && targetWeight in MIN_POSITIVE_WEIGHT..1.0)
        require(representedConstituentCount in 1..MAX_REPRESENTED_CONSTITUENTS)
        require(selectionScore.isFinite() && selectionScore in -100.0..100.0)
        require(
            indicatedAnnualDividendYield.isFinite() &&
                indicatedAnnualDividendYield in 0.0..MAX_DIVIDEND_YIELD,
        )
    }

    companion object {
        private const val MIN_POSITIVE_WEIGHT: Double = 1e-12
        private const val MAX_REPRESENTED_CONSTITUENTS: Int = 10_000
        private const val MAX_DIVIDEND_YIELD: Double = 1.0
        private val ASSET_ID_PATTERN = Regex("[a-z0-9][a-z0-9:._-]{2,199}")
        private val COUNTRY_CODE_PATTERN = Regex("[A-Z]{2}")
    }
}
