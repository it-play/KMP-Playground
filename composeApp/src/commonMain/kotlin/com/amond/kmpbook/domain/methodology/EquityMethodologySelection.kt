package com.amond.kmpbook.domain.methodology

/** One selected candidate and its stable one-based methodology rank. */
data class EquityMethodologySelection(
    val assetId: String,
    val rank: Int,
) {
    init {
        require(assetId.isNotBlank())
        require(rank > 0)
    }
}
