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
    FUND_OPERATION("펀드·ETN 운용"),
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
    /**
     * 가격 충격과 별개로 거래소 상장 감시가 소비하는 구조화된 신호다.
     * nullable인 이유는 이 필드가 없던 v1 저장 파일도 명시적으로 마이그레이션하기 위해서다.
     */
    val listingRiskTags: Set<ListingRiskTag>? = null,
    val listingRecoveryConditions: Set<ListingRecoveryCondition>? = null,
    val listingFinalDispositionHint: ListingFinalDispositionType? = null,
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

    /**
     * 가격 노출은 ETF의 기초자산까지 전파될 수 있지만, 상장 조치는 거래소가 직접 지정한
     * 종목에만 적용한다. 이 분리를 지키지 않으면 구성종목 파산이 ETF 자체 상폐가 된다.
     */
    fun directListingRiskTags(stockId: String): Set<ListingRiskTag> =
        if (stockId in affectedStockIds) listingRiskTags.orEmpty() else emptySet()

    fun directListingRecoveryConditions(stockId: String): Set<ListingRecoveryCondition> =
        if (stockId in affectedStockIds) listingRecoveryConditions.orEmpty() else emptySet()

    fun directListingFinalDispositionHint(stockId: String): ListingFinalDispositionType? =
        listingFinalDispositionHint.takeIf { stockId in affectedStockIds }

    fun affects(stock: StockDefinition): Boolean = when (scope) {
        EventScope.GLOBAL -> true
        EventScope.COUNTRY,
        EventScope.MARKET,
        -> stock.market in affectedMarkets || stock.etfProfile?.let { profile ->
            affectedMarkets.any(profile::isExposedTo)
        } == true
        EventScope.SECTOR -> {
            val explicitExposure = stock.identityProfile?.exposedSectors.orEmpty()
            when {
                explicitExposure.isNotEmpty() -> explicitExposure.any(affectedSectors::contains)
                !stock.isFundLike -> stock.sector in affectedSectors
                stock.etfProfile?.assetClass == EtfAssetClass.SECTOR_EQUITY -> stock.sector in affectedSectors
                else -> false
            }
        }
        EventScope.STOCK -> stock.id in affectedStockIds ||
            stock.identityProfile?.underlyingInstrumentIds?.any(affectedStockIds::contains) == true
    }
}
