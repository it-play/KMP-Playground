package com.amond.kmpbook.domain.model.market


/** 게임에서 취급하는 결제 통화. 금액 반올림은 거래/세금 계산 계층이 담당한다. */
enum class Currency(
    val displayName: String,
    val symbol: String,
    val decimalPlaces: Int,
) {
    KRW("대한민국 원", "₩", 0),
    USD("미국 달러", "$", 2),
}
