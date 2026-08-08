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
 * 뉴스 항목이 생성된 업무 흐름을 ID 규칙과 분리해 저장한다.
 *
 * [EventType]이 내용의 주제라면 이 값은 예정 발표·거래소 조치·기업 행위 같은
 * 기록의 출처다. UI와 저장 검증은 이벤트 ID 접두사를 해석하지 않는다.
 */
enum class EventRecordKind(val displayName: String) {
    NEWS("뉴스"),
    SCHEDULED_RELEASE("예정 발표"),
    MARKET_ACTION("거래소 조치"),
    CORPORATE_ACTION("기업 행위"),
    INSTRUMENT_LIFECYCLE("상품 생애주기"),
}

/** 뉴스가 거래소 규칙에 따라 직접 유발하는 종목 거래정지의 업무 종류다. */
enum class EventTradingHaltKind {
    MATERIAL_DISCLOSURE,
}

/**
 * 이벤트 ID를 해석하지 않고도 거래정지 규칙을 재현하기 위한 불변 지시자다.
 * 현재 중요정보 공시 정지는 KRX 상장 종목에 정확히 30분 동안만 적용한다.
 */
data class EventTradingHaltDirective(
    val kind: EventTradingHaltKind,
    val reason: TradingHaltReason,
    val eligibleMarkets: Set<Market>,
    val durationMinutes: Int,
    val detail: String,
) {
    init {
        val violation = semanticInvariantViolation()
        require(violation == null) { violation.orEmpty() }
    }

    fun semanticInvariantViolation(): String? = when {
        eligibleMarkets.isEmpty() -> "이벤트 거래정지에는 적용 시장이 필요합니다."
        durationMinutes <= 0 -> "이벤트 거래정지 기간은 양수여야 합니다."
        detail.isBlank() -> "이벤트 거래정지 안내는 비어 있을 수 없습니다."
        kind == EventTradingHaltKind.MATERIAL_DISCLOSURE &&
            reason != TradingHaltReason.MATERIAL_DISCLOSURE ->
            "중요정보 공시 거래정지는 중요정보 공시 사유를 사용해야 합니다."
        kind == EventTradingHaltKind.MATERIAL_DISCLOSURE &&
            eligibleMarkets != setOf(Market.KOSPI, Market.KOSDAQ) ->
            "중요정보 공시 거래정지는 KRX 시장에만 적용해야 합니다."
        kind == EventTradingHaltKind.MATERIAL_DISCLOSURE && durationMinutes != 30 ->
            "중요정보 공시 거래정지는 정확히 30분이어야 합니다."
        else -> null
    }
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
    /** 확률 엔진이 만든 뉴스라면 생성 규칙을 정확히 가리킨다. 표시용 ID를 역해석하지 않는다. */
    val generatorTemplateId: String? = null,
    val title: String,
    val description: String,
    val scope: EventScope,
    val type: EventType,
    val severity: EventSeverity,
    val impact: GameEventImpact,
    val startsAt: Instant,
    val durationHours: Int,
    val recordKind: EventRecordKind = EventRecordKind.NEWS,
    /** 정기 발표 카탈로그의 발생 ID와 종류를 ID 해석 없이 보존한다. */
    val scheduledEventReference: ScheduledEventReference? = null,
    /** 분할·병합 공시/적용 뉴스를 동일 기업행동 원장과 연결하는 불변 참조다. */
    val corporateActionReference: CorporateActionNewsReference? = null,
    val impactCoveragePolicy: EventImpactCoveragePolicy =
        EventImpactCoveragePolicy.SCOPE_FALLBACK_WITH_OVERRIDES,
    /** 뉴스 발표 시각과 실제 가격 반영 시각이 다른 정기 발표를 위한 정확한 효과 구간이다. */
    val effectStartsAt: Instant = startsAt,
    val effectDurationHours: Int = durationHours,
    val affectedMarkets: Set<Market> = emptySet(),
    val affectedSectors: Set<Sector> = emptySet(),
    val affectedStockIds: Set<String> = emptySet(),
    val sourceLabel: String = "게임 뉴스",
    /** 제목에서 파생한 예측치가 아니라 대상별 인과·방향을 담는 불변 분석 경로다. */
    val impactInsights: List<EventImpactInsight> = emptyList(),
    /** 경제 요인 그래프를 통해 명시 경로 밖의 산업·종목으로 전파되는 구조화된 시작 신호다. */
    val causalSignals: List<CausalSignalSeed> = emptyList(),
    /** 실적·경제지표처럼 실제로 발표된 값만 담는다. 가격 예상치는 저장하지 않는다. */
    val reportedFacts: List<ReportedFact> = emptyList(),
    /** 거래소·상장 조치 뉴스가 자신을 만든 원장 전이와 정확히 연결되는 불변 참조다. */
    val marketAction: MarketActionReference? = null,
    /** 상품 종료 공시의 일정·우선순위·평가 조건을 ID와 분리해 영구 보존한다. */
    val instrumentTermination: InstrumentTerminationTerms? = null,
    /** 중요 공시가 유발하는 거래정지 규칙을 이벤트 ID와 분리해 영구 보존한다. */
    val tradingHaltDirective: EventTradingHaltDirective? = null,
    /** 가격 충격과 별개로 거래소 상장 감시가 소비하는 구조화된 신호다. */
    val listingRiskTags: Set<ListingRiskTag> = emptySet(),
    val listingRecoveryConditions: Set<ListingRecoveryCondition> = emptySet(),
    val listingFinalDispositionHint: ListingFinalDispositionType? = null,
) {
    init {
        require(id.isNotBlank()) { "이벤트 ID는 비어 있을 수 없습니다." }
        require(generatorTemplateId?.isNotBlank() != false) { "이벤트 생성 템플릿 ID는 비어 있을 수 없습니다." }
        require(title.isNotBlank() && description.isNotBlank()) { "이벤트 제목과 설명은 비어 있을 수 없습니다." }
        require(durationHours > 0) { "이벤트 기간은 1시간 이상이어야 합니다." }
        require(effectDurationHours > 0) { "이벤트의 실제 반영 기간은 1시간 이상이어야 합니다." }
        require(effectStartsAt >= startsAt) { "이벤트의 실제 반영은 발표보다 먼저 시작할 수 없습니다." }
        require(effectStartsAt + effectDurationHours.hours <= startsAt + durationHours.hours) {
            "이벤트의 실제 반영 구간은 뉴스 보존 구간 안에 있어야 합니다."
        }
        require(affectedStockIds.none(String::isBlank)) { "대상 종목 ID는 비어 있을 수 없습니다." }
        require(scope !in setOf(EventScope.COUNTRY, EventScope.MARKET) || affectedMarkets.isNotEmpty()) {
            "국가·시장 이벤트에는 대상 시장이 필요합니다."
        }
        require(scope != EventScope.COUNTRY || affectedMarkets.map(Market::countryName).distinct().size == 1) {
            "국가 이벤트는 하나의 국가에 속한 시장만 대상으로 해야 합니다."
        }
        require(scope != EventScope.SECTOR || affectedSectors.isNotEmpty()) {
            "산업 이벤트에는 대상 산업이 필요합니다."
        }
        require(scope != EventScope.STOCK || affectedStockIds.isNotEmpty()) {
            "종목 이벤트에는 대상 종목이 필요합니다."
        }
        require(
            impactCoveragePolicy != EventImpactCoveragePolicy.EXPLICIT_PATHS_ONLY ||
                impactInsights.isNotEmpty() || causalSignals.isNotEmpty(),
        ) {
            "명시 경로 전용 이벤트에는 영향 경로 또는 인과 신호가 필요합니다."
        }
        require(causalSignals.map(CausalSignalSeed::factor).distinct().size == causalSignals.size) {
            "한 이벤트에는 같은 경제 요인의 인과 신호를 중복 선언할 수 없습니다."
        }
        require(
            scope !in setOf(EventScope.COUNTRY, EventScope.MARKET) ||
                causalSignals.map(CausalSignalSeed::transmissionProfile).distinct().size <= 1,
        ) {
            "국가·시장 이벤트 하나에는 하나의 시장 전염 프로필만 선언할 수 있습니다."
        }
        require((recordKind == EventRecordKind.MARKET_ACTION) == (marketAction != null)) {
            "거래소 조치 기록과 시장조치 참조는 항상 함께 존재해야 합니다."
        }
        require((recordKind == EventRecordKind.SCHEDULED_RELEASE) == (scheduledEventReference != null)) {
            "정기 발표 기록과 일정 발생 참조는 항상 함께 존재해야 합니다."
        }
        require((recordKind == EventRecordKind.CORPORATE_ACTION) == (corporateActionReference != null)) {
            "기업행동 기록과 기업행동 원장 참조는 항상 함께 존재해야 합니다."
        }
        scheduledEventReference?.let { reference ->
            require(reference.occurrenceId == id) {
                "정기 발표 뉴스 ID와 일정 발생 ID가 일치해야 합니다."
            }
            require(reference.kind.eventType == type) {
                "정기 발표 종류와 뉴스 주제가 일치해야 합니다."
            }
            require(scope == if (reference.kind == ScheduledEventKind.EARNINGS) EventScope.STOCK else EventScope.COUNTRY) {
                "정기 발표 종류와 뉴스 범위가 일치해야 합니다."
            }
        }
        corporateActionReference?.let { reference ->
            require(reference.semanticInvariantViolation() == null) {
                "기업행동 뉴스 참조가 유효하지 않습니다."
            }
            require(type == EventType.CORPORATE_ACTION && scope == EventScope.STOCK) {
                "기업행동 원장 참조는 개별 종목 기업행동 뉴스에만 붙일 수 있습니다."
            }
            require(affectedStockIds == setOf(reference.stockId)) {
                "기업행동 뉴스는 원장이 지정한 한 종목만 직접 대상으로 가져야 합니다."
            }
            val expectedStart = when (reference.transition) {
                CorporateActionNewsTransition.ANNOUNCED -> reference.announcedAt
                CorporateActionNewsTransition.APPLIED -> requireNotNull(reference.appliedAt)
                CorporateActionNewsTransition.CANCELLED -> requireNotNull(reference.cancelledAt)
            }
            require(startsAt == expectedStart) {
                "기업행동 뉴스 시각과 원장 전이 시각이 일치해야 합니다."
            }
        }
        require(generatorTemplateId == null || recordKind != EventRecordKind.MARKET_ACTION) {
            "런타임 시장조치 기록은 확률 이벤트 템플릿에서 생성할 수 없습니다."
        }
        marketAction?.let { action ->
            require(action.announcedAt == startsAt) { "시장조치 참조의 발표 시각은 뉴스 시각과 같아야 합니다." }
            require(action.stockId == null || action.stockId in affectedStockIds) {
                "시장조치 참조 종목은 뉴스의 직접 대상이어야 합니다."
            }
            require(action.markets.all(affectedMarkets::contains)) {
                "시장조치 참조 시장은 뉴스의 직접 대상이어야 합니다."
            }
        }
        instrumentTermination?.let { terms ->
            require(recordKind == EventRecordKind.INSTRUMENT_LIFECYCLE) {
                "상품 종료 조건은 상품 생애주기 기록에만 붙일 수 있습니다."
            }
            require(scope == EventScope.STOCK && affectedStockIds.size == 1) {
                "상품 종료 공시는 정확히 하나의 직접 대상 종목을 지정해야 합니다."
            }
            require(terms.effectiveNotBefore == null || terms.effectiveNotBefore >= startsAt) {
                "상품 종료 최소 효력 시각은 공시보다 빠를 수 없습니다."
            }
            require(listingFinalDispositionHint == null) {
                "구조화된 상품 종료 공시는 별도 최종 처분 힌트를 중복 저장하지 않습니다."
            }
            require(terms.listingRiskTag !in listingRiskTags) {
                "구조화된 상품 종료 공시는 종료 위험 태그를 중복 저장하지 않습니다."
            }
        }
        tradingHaltDirective?.let { directive ->
            require(scope == EventScope.STOCK && affectedStockIds.isNotEmpty()) {
                "이벤트 거래정지는 직접 대상 종목이 있는 종목 뉴스에만 붙일 수 있습니다."
            }
            require(directive.semanticInvariantViolation() == null) {
                "이벤트 거래정지 지시자가 유효하지 않습니다."
            }
        }
    }

    val endsAt: Instant get() = startsAt + durationHours.hours
    val effectEndsAt: Instant get() = effectStartsAt + effectDurationHours.hours

    fun isActiveAt(time: Instant): Boolean = time >= startsAt && time < endsAt

    /**
     * 가격 노출은 ETF의 기초자산까지 전파될 수 있지만, 상장 조치는 거래소가 직접 지정한
     * 종목에만 적용한다. 이 분리를 지키지 않으면 구성종목 파산이 ETF 자체 상폐가 된다.
     */
    fun directListingRiskTags(stockId: String): Set<ListingRiskTag> =
        if (stockId !in affectedStockIds) {
            emptySet()
        } else {
            buildSet {
                addAll(listingRiskTags)
                instrumentTermination?.listingRiskTag?.let(::add)
            }
        }

    fun directListingRecoveryConditions(stockId: String): Set<ListingRecoveryCondition> =
        if (stockId in affectedStockIds) listingRecoveryConditions else emptySet()

    fun directListingFinalDispositionHint(stockId: String): ListingFinalDispositionType? =
        if (stockId in affectedStockIds) {
            instrumentTermination?.finalDisposition ?: listingFinalDispositionHint
        } else {
            null
        }

    fun affects(stock: StockDefinition): Boolean = impactCoverageFor(stock).isAffected

    internal fun affectsByScope(stock: StockDefinition): Boolean = when (scope) {
        EventScope.GLOBAL -> true
        EventScope.COUNTRY,
        EventScope.MARKET,
        -> stock.market in affectedMarkets || stock.etfProfile?.let { profile ->
            affectedMarkets.any(profile::isExposedTo)
        } == true
        EventScope.SECTOR -> {
            affectedSectors.any(stock::isExposedToSector)
        }
        EventScope.STOCK -> stock.id in affectedStockIds ||
            stock.identityProfile?.underlyingInstrumentIds?.any(affectedStockIds::contains) == true
    }
}
