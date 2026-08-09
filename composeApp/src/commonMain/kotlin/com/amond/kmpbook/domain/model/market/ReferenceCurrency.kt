package com.amond.kmpbook.domain.model.market


/** ETF 기초자산 통화 바스켓에 사용하는 참조통화. 현금 결제통화와는 별도다. */
enum class ReferenceCurrency(val displayName: String) {
    KRW("원화"),
    USD("미국 달러"),
    EUR("유로"),
    JPY("일본 엔"),
    CNY("중국 위안"),
    HKD("홍콩 달러"),
    GBP("영국 파운드"),
    CAD("캐나다 달러"),
    CHF("스위스 프랑"),
    AUD("호주 달러"),
    SGD("싱가포르 달러"),
    TWD("대만 달러"),
    INR("인도 루피"),
    BRL("브라질 헤알"),
}
