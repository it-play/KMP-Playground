package com.amond.kmpbook.domain.model.game


/** Compose 내비게이션 구현과 분리된, 저장 가능한 화면 식별자. */
enum class Screen(val displayName: String) {
    HOME("홈"),
    MARKET("시장"),
    STOCK_DETAIL("종목 상세"),
    ORDER("주문"),
    PORTFOLIO("포트폴리오"),
    EVENTS("뉴스·이벤트"),
    ANALYTICS("투자 분석"),
    TAX_REPORT("세금 내역"),
    SETTINGS("설정"),
    ENDING("정산 결과"),
}
