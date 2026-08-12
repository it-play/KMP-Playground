package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.EquityReferenceRegion
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector

/** Stable identity of a non-tradable simulated constituent anchor. */
data class EquityReferenceAssetIdentity(
    val assetId: String,
    val region: EquityReferenceRegion,
    val countryCode: String,
    val sector: MethodologyEquitySector,
) {
    init {
        require(ASSET_ID_PATTERN.matches(assetId))
        require(COUNTRY_CODE_PATTERN.matches(countryCode))
        require(region != EquityReferenceRegion.GLOBAL)
    }

    companion object {
        private val ASSET_ID_PATTERN = Regex("[a-z0-9][a-z0-9:._-]{2,199}")
        private val COUNTRY_CODE_PATTERN = Regex("[A-Z]{2}")
    }
}
