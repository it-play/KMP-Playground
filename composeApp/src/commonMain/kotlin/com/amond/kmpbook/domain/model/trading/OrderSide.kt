package com.amond.kmpbook.domain.model.trading


enum class OrderSide(val displayName: String, val cashFlowSign: Int) {
    BUY("매수", -1),
    SELL("매도", 1),
}
