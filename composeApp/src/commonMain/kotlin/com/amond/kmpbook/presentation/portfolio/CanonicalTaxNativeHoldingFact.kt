package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.market.Currency

/** Native-currency holding values reconstructed by canonical tax replay. */
data class CanonicalTaxNativeHoldingFact(
    val stockId: String,
    val quantity: Double,
    val averagePrice: Double,
    val currency: Currency,
    val realizedProfit: Double,
)
