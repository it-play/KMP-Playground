package com.amond.kmpbook.domain.model.reference

import kotlinx.datetime.LocalDate

/** One expiry on an injected futures curve; signed prices remain possible for oil-like contracts. */
data class FuturesContractQuote(
    val contractId: String,
    val expiryDate: LocalDate,
    val price: Double,
) {
    init {
        require(ID_PATTERN.matches(contractId))
        require(price.isFinite() && price in MIN_PRICE..MAX_PRICE)
    }

    companion object {
        private const val MIN_PRICE: Double = -1e12
        private const val MAX_PRICE: Double = 1e12
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{2,199}")
    }
}
