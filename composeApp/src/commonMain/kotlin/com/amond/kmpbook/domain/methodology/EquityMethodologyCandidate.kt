package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector

/** Immutable identity, classification and versioned feature snapshot for one candidate. */
class EquityMethodologyCandidate(
    val assetId: String,
    val sector: MethodologyEquitySector,
    val signals: EquityMethodologySignals,
) {
    init {
        require(ASSET_ID.matches(assetId))
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is EquityMethodologyCandidate &&
            assetId == other.assetId && sector == other.sector && signals == other.signals

    override fun hashCode(): Int {
        var result = assetId.hashCode()
        result = 31 * result + sector.hashCode()
        result = 31 * result + signals.hashCode()
        return result
    }

    override fun toString(): String =
        "EquityMethodologyCandidate(assetId=$assetId, sector=$sector, signals=$signals)"

    companion object {
        private val ASSET_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
    }
}
