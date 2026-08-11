package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.market.Currency

/** 한 종목의 보유·평가 상태다. */
data class ModHoldingSnapshot(
    val instrumentId: String,
    val quantity: Double,
    val averagePrice: Double,
    val currentPrice: Double,
    val currency: Currency,
    val costBasis: Double,
    val marketValue: Double,
    val unrealizedProfit: Double,
    val realizedProfit: Double,
    val returnRate: Double,
)
