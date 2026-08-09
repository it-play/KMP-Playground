package com.amond.kmpbook.domain.model.instrument


/**
 * 종목명이 아니라 실제 수익 구조를 가격 엔진과 캠페인 이벤트에 전달하는 분류다.
 * 예를 들어 커버드콜은 상승 참여율이 낮고, 일일 레버리지는 변동성 누적 손실을 갖는다.
 */
enum class InstrumentStrategy(val displayName: String) {
    OPERATING_COMPANY("사업회사"),
    ADR_EQUITY("해외주식 ADR"),
    REAL_ESTATE_INCOME("부동산 인컴"),
    BROAD_EQUITY("광범위 주식"),
    DIVIDEND_EQUITY("배당주"),
    SECTOR_EQUITY("섹터·테마 주식"),
    COVERED_CALL("커버드콜 인컴"),
    BUFFER_INCOME("버퍼·배리어 인컴"),
    DAILY_LEVERAGED("일일 레버리지"),
    DAILY_INVERSE("일일 인버스"),
    MONEY_MARKET("머니마켓·초단기"),
    TREASURY("국채"),
    INFLATION_LINKED_BOND("물가연동채"),
    FLOATING_RATE("변동금리채"),
    INVESTMENT_GRADE_BOND("투자적격채권"),
    HIGH_YIELD_BOND("하이일드 채권"),
    CLO("대출채권담보부증권"),
    MULTI_ASSET("혼합자산"),
    COMMODITY_FUTURES("원자재 선물"),
    CRYPTO_FUTURES("가상자산 선물"),
    CLOSED_END_INCOME("폐쇄형 인컴펀드"),
    ETN_LINKED("발행사 신용연계 ETN"),
    ALTERNATIVE("대체전략"),
}
