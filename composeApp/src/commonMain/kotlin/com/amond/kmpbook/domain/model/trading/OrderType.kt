package com.amond.kmpbook.domain.model.trading

enum class OrderType(val displayName: String) {
    MARKET("시장가"),
    LIMIT("지정가"),
}
