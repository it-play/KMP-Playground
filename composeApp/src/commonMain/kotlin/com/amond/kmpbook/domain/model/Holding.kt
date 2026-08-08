package com.amond.kmpbook.domain.model

import kotlin.time.Instant

/** 한 종목의 현재 보유 상태. 수량은 향후 미국주식 소수점 거래를 위해 Double이다. */
data class Holding(
    val stockId: String,
    val quantity: Double,
    val averagePrice: Double,
    val currentPrice: Double,
    val currency: Currency,
    val realizedProfit: Double = 0.0,
) {
    init {
        require(stockId.isNotBlank()) { "종목 ID는 비어 있을 수 없습니다." }
        require(quantity > 0.0) { "보유 수량은 0보다 커야 합니다." }
        require(averagePrice >= 0.0 && currentPrice >= 0.0) { "가격은 음수일 수 없습니다." }
    }

    val costBasis: Double get() = quantity * averagePrice
    val marketValue: Double get() = quantity * currentPrice
    val unrealizedProfit: Double get() = marketValue - costBasis
    val returnRate: Double get() = if (costBasis == 0.0) 0.0 else unrealizedProfit / costBasis
}
