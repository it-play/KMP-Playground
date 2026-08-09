package com.amond.kmpbook.domain.model.market


/**
 * 거래소가 아니라 상장 시장 단위다. 같은 미국 정규장 규칙을 쓰더라도 NASDAQ과 NYSE를
 * 분리해 종목 검색, 지수, 이벤트 범위를 명확히 유지한다.
 */
enum class Market(
    val displayName: String,
    val countryName: String,
    val currency: Currency,
    val timeZoneId: String,
) {
    KOSPI("코스피", "대한민국", Currency.KRW, "Asia/Seoul"),
    KOSDAQ("코스닥", "대한민국", Currency.KRW, "Asia/Seoul"),
    NASDAQ("나스닥", "미국", Currency.USD, "America/New_York"),
    NYSE("뉴욕증권거래소", "미국", Currency.USD, "America/New_York"),
    NYSE_ARCA("NYSE Arca", "미국", Currency.USD, "America/New_York"),
    CBOE_BZX("Cboe BZX", "미국", Currency.USD, "America/New_York"),
    /** 소형주를 주로 취급하는 구 AMEX의 현재 상장 시장. NYSE/Arca와 상장처를 합치지 않는다. */
    NYSE_AMERICAN("NYSE American", "미국", Currency.USD, "America/New_York"),
    ;

    val isKorean: Boolean get() = this == KOSPI || this == KOSDAQ
    val isUnitedStates: Boolean
        get() = this == NASDAQ || this == NYSE || this == NYSE_ARCA || this == CBOE_BZX ||
            this == NYSE_AMERICAN
}
