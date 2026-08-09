package com.amond.kmpbook.domain.model.trading


/** 매수 또는 매도 호가 한 단계. */
data class OrderBookLevel(
    val price: Double,
    val quantity: Double,
    val orderCount: Int,
) {
    init {
        require(price > 0.0) { "호가 가격은 0보다 커야 합니다." }
        require(quantity >= 0.0) { "호가 수량은 음수일 수 없습니다." }
        require(orderCount >= 0) { "주문 건수는 음수일 수 없습니다." }
    }
}
