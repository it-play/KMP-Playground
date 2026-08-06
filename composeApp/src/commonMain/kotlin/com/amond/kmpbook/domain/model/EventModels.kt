package com.amond.kmpbook.domain.model

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

enum class EventScope(val displayName: String) {
    GLOBAL("전 세계"),
    COUNTRY("국가"),
    MARKET("시장"),
    SECTOR("산업"),
    STOCK("개별 종목"),
}

enum class EventType(val displayName: String) {
    ECONOMIC_INDICATOR("경제지표"),
    CENTRAL_BANK("중앙은행·금리"),
    GEOPOLITICAL("지정학"),
    REGULATION_POLICY("규제·정책"),
    EARNINGS("실적 발표"),
    CORPORATE_ACTION("기업 행동"),
    PRODUCT_TECHNOLOGY("제품·기술"),
    INDUSTRY_SUPPLY_DEMAND("산업 수급"),
    CURRENCY("환율"),
    COMMODITY("원자재"),
    NATURAL_DISASTER("자연재해"),
    HEALTH_CRISIS("보건 위기"),
    MARKET_SENTIMENT("투자 심리"),
}

enum class EventSeverity(
    val displayName: String,
    val level: Int,
) {
    MINOR("경미", 1),
    MODERATE("보통", 2),
    MAJOR("중대", 3),
    CRITICAL("심각", 4),
}

enum class ImpactDirection(val displayName: String) {
    POSITIVE("호재"),
    NEGATIVE("악재"),
    MIXED("혼조"),
    NEUTRAL("중립"),
}

/**
 * 이벤트가 가격 과정에 주는 규칙 기반 효과. 비율은 0.05 = 5% 형식이다.
 * shockReturn은 발생 즉시, hourlyDrift는 유효 기간 매 시간 적용한다.
 */
data class GameEventImpact(
    val direction: ImpactDirection,
    val shockReturn: Double = 0.0,
    val hourlyDrift: Double = 0.0,
    val volatilityMultiplier: Double = 1.0,
    val volumeMultiplier: Double = 1.0,
    val liquidityMultiplier: Double = 1.0,
    val sentiment: Double = 0.0,
) {
    init {
        require(shockReturn > -1.0) { "즉시 가격 충격은 -100%보다 커야 합니다." }
        require(volatilityMultiplier >= 0.0) { "변동성 배수는 음수일 수 없습니다." }
        require(volumeMultiplier >= 0.0) { "거래량 배수는 음수일 수 없습니다." }
        require(liquidityMultiplier >= 0.0) { "유동성 배수는 음수일 수 없습니다." }
        require(sentiment in -1.0..1.0) { "심리 점수는 -1과 1 사이여야 합니다." }
    }
}

/** 뉴스 피드와 시세 엔진이 함께 소비하는 불변 이벤트. */
data class GameEvent(
    val id: String,
    val title: String,
    val description: String,
    val scope: EventScope,
    val type: EventType,
    val severity: EventSeverity,
    val impact: GameEventImpact,
    val startsAt: Instant,
    val durationHours: Int,
    val affectedMarkets: Set<Market> = emptySet(),
    val affectedSectors: Set<Sector> = emptySet(),
    val affectedStockIds: Set<String> = emptySet(),
    val sourceLabel: String = "게임 뉴스",
) {
    init {
        require(id.isNotBlank()) { "이벤트 ID는 비어 있을 수 없습니다." }
        require(title.isNotBlank() && description.isNotBlank()) { "이벤트 제목과 설명은 비어 있을 수 없습니다." }
        require(durationHours > 0) { "이벤트 기간은 1시간 이상이어야 합니다." }
        require(affectedStockIds.none(String::isBlank)) { "대상 종목 ID는 비어 있을 수 없습니다." }
        require(scope != EventScope.MARKET || affectedMarkets.isNotEmpty()) {
            "시장 이벤트에는 대상 시장이 필요합니다."
        }
        require(scope != EventScope.SECTOR || affectedSectors.isNotEmpty()) {
            "산업 이벤트에는 대상 산업이 필요합니다."
        }
        require(scope != EventScope.STOCK || affectedStockIds.isNotEmpty()) {
            "종목 이벤트에는 대상 종목이 필요합니다."
        }
    }

    val endsAt: Instant get() = startsAt + durationHours.hours

    fun isActiveAt(time: Instant): Boolean = time >= startsAt && time < endsAt

    fun affects(stock: StockDefinition): Boolean = when (scope) {
        EventScope.GLOBAL -> true
        EventScope.COUNTRY,
        EventScope.MARKET,
        -> stock.market in affectedMarkets
        EventScope.SECTOR -> stock.sector in affectedSectors
        EventScope.STOCK -> stock.id in affectedStockIds
    }
}
