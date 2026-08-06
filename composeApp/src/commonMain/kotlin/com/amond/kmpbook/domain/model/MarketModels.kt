package com.amond.kmpbook.domain.model

import kotlin.time.Instant

/** 게임에서 취급하는 결제 통화. 금액 반올림은 거래/세금 계산 계층이 담당한다. */
enum class Currency(
    val displayName: String,
    val symbol: String,
    val decimalPlaces: Int,
) {
    KRW("대한민국 원", "₩", 0),
    USD("미국 달러", "$", 2),
}

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
    ;

    val isKorean: Boolean get() = this == KOSPI || this == KOSDAQ
    val isUnitedStates: Boolean get() = this == NASDAQ || this == NYSE
}

/** 현실 종목과 이벤트를 함께 분류하기 위한 넓은 산업군. */
enum class Sector(val displayName: String) {
    SEMICONDUCTOR("반도체"),
    INFORMATION_TECHNOLOGY("정보기술"),
    INTERNET_PLATFORM("인터넷·플랫폼"),
    COMMUNICATION_SERVICES("커뮤니케이션 서비스"),
    CONSUMER_DISCRETIONARY("경기소비재"),
    CONSUMER_STAPLES("필수소비재"),
    FINANCIALS("금융"),
    HEALTHCARE_BIO("헬스케어·바이오"),
    AUTOMOTIVE("자동차"),
    INDUSTRIALS("산업재"),
    AEROSPACE_DEFENSE("우주항공·방산"),
    ENERGY("에너지"),
    MATERIALS_CHEMICALS("소재·화학"),
    BATTERY("이차전지"),
    ROBOTICS("로봇"),
    ENTERTAINMENT("엔터테인먼트"),
    GAMING("게임"),
    RETAIL_ECOMMERCE("유통·전자상거래"),
    TRANSPORTATION_LOGISTICS("운송·물류"),
    UTILITIES("유틸리티"),
    REAL_ESTATE("부동산"),
    CONGLOMERATE("복합기업"),
    OTHER("기타"),
}

/** 시세창에 표시하는 거래 세션 상태. 정규장 체결 가능 여부는 [isTradable]로 판단한다. */
enum class MarketSession(
    val displayName: String,
    val isTradable: Boolean,
) {
    CLOSED("장 마감", false),
    PRE_MARKET("프리마켓", false),
    REGULAR("정규장", true),
    AFTER_HOURS("애프터마켓", false),
}

/** 코스피, 코스닥, 나스닥 종합지수 등 시장 지수의 한 시점 스냅샷. */
data class MarketIndex(
    val id: String,
    val name: String,
    val market: Market,
    val timestamp: Instant,
    val value: Double,
    val previousClose: Double,
    val open: Double = value,
    val high: Double = value,
    val low: Double = value,
) {
    init {
        require(id.isNotBlank()) { "지수 ID는 비어 있을 수 없습니다." }
        require(name.isNotBlank()) { "지수 이름은 비어 있을 수 없습니다." }
        require(value >= 0.0 && previousClose >= 0.0) { "지수 값은 음수일 수 없습니다." }
        require(high >= low) { "지수 고가는 저가 이상이어야 합니다." }
    }

    val change: Double get() = value - previousClose
    val changeRate: Double get() = if (previousClose == 0.0) 0.0 else change / previousClose
}
