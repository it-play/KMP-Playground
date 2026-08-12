package com.amond.kmpbook.domain.model.reference

import kotlin.time.Instant

/** Ordered curve for one sleeve, including enough deferred expiries to promote after a roll. */
data class FuturesCurveSnapshot(
    val sleeveId: String,
    val curveId: String,
    val currentSpotLevel: Double,
    val contracts: List<FuturesContractQuote>,
    val sourceId: String,
    val asOf: Instant,
) {
    init {
        require(ID_PATTERN.matches(sleeveId) && ID_PATTERN.matches(curveId))
        require(ID_PATTERN.matches(sourceId))
        require(currentSpotLevel.isFinite() && currentSpotLevel in MIN_SPOT..MAX_SPOT)
        require(contracts.size in 2..MAX_CONTRACTS)
        require(contracts == contracts.sortedWith(
            compareBy<FuturesContractQuote> { it.expiryDate }.thenBy { it.contractId },
        ))
        require(contracts.map(FuturesContractQuote::contractId).distinct().size == contracts.size)
        require(contracts.map(FuturesContractQuote::expiryDate).distinct().size == contracts.size)
    }

    companion object {
        private const val MIN_SPOT: Double = 1e-12
        private const val MAX_SPOT: Double = 1e24
        private const val MAX_CONTRACTS: Int = 36
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{2,199}")
    }
}
