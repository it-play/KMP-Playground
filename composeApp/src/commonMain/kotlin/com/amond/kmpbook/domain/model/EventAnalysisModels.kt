package com.amond.kmpbook.domain.model

import com.amond.kmpbook.domain.simulation.CausalMarketEngine
import com.amond.kmpbook.domain.simulation.MarketContagionEngine

/** 뉴스 분석에서 영향 경로가 가리키는 대상의 정밀도다. */
enum class EventImpactTargetKind(val displayName: String) {
    MARKET("시장"),
    INDUSTRY("산업군"),
    INDUSTRY_SEGMENT("세부 산업"),
    STOCK("종목"),
}

/** 가격 충격의 지속 시간을 단정하지 않고 분석 관점의 시간축만 전달한다. */
enum class EventImpactHorizon(val displayName: String) {
    IMMEDIATE("즉시"),
    SHORT_TERM("단기"),
    MEDIUM_TERM("중기"),
    STRUCTURAL("구조적"),
}

/**
 * 분석 경로가 없는 종목에 스코프의 기본 충격을 적용할지를 결정한다.
 *
 * [SCOPE_FALLBACK_WITH_OVERRIDES]는 기본 스코프를 보존하고 더 구체적인 분석 경로로 방향과
 * 민감도를 덮어쓴다. [EXPLICIT_PATHS_ONLY]는 명시적으로 일치하는 분석 경로만 영향받는다.
 */
enum class EventImpactCoveragePolicy {
    SCOPE_FALLBACK_WITH_OVERRIDES,
    EXPLICIT_PATHS_ONLY,
}

/**
 * 한 뉴스가 특정 시장·산업·종목에 전달되는 인과 경로다.
 *
 * [relativeSensitivity]는 화면에 예상 등락률로 노출하는 값이 아니라 이벤트 엔진이 같은
 * 사건 안에서 대상별 상대 민감도를 구분하기 위한 내부 배수다.
 */
data class EventImpactInsight(
    val targetKind: EventImpactTargetKind,
    val targetLabel: String,
    val direction: ImpactDirection,
    val rationale: String,
    val sector: Sector? = null,
    val industrySegment: IndustrySegment? = null,
    /** 비어 있으면 전 시장, 값이 있으면 해당 상장·기초시장에만 적용한다. */
    val markets: Set<Market> = emptySet(),
    val stockId: String? = null,
    val horizon: EventImpactHorizon = EventImpactHorizon.SHORT_TERM,
    val relativeSensitivity: Double = 1.0,
) {
    init {
        require(targetLabel.isNotBlank()) { "뉴스 영향 대상 이름은 비어 있을 수 없습니다." }
        require(rationale.isNotBlank()) { "뉴스 영향 분석 근거는 비어 있을 수 없습니다." }
        require(relativeSensitivity.isFinite() && relativeSensitivity > 0.0 && relativeSensitivity <= 3.0) {
            "뉴스 상대 민감도는 0보다 크고 3 이하여야 합니다."
        }
        require(stockId == null || stockId.isNotBlank()) { "뉴스 영향 종목 ID는 비어 있을 수 없습니다." }
        when (targetKind) {
            EventImpactTargetKind.MARKET -> require(markets.isNotEmpty() && sector == null && industrySegment == null && stockId == null) {
                "시장 영향에는 시장만 지정해야 합니다."
            }
            EventImpactTargetKind.INDUSTRY -> require(sector != null && industrySegment == null && stockId == null) {
                "산업 영향에는 산업을 지정하고 종목 ID를 비워야 합니다."
            }
            EventImpactTargetKind.INDUSTRY_SEGMENT -> require(
                sector != null && industrySegment?.parentSector == sector && stockId == null,
            ) {
                "세부 산업 영향에는 상위 산업을 지정하고 직접 종목 ID를 비워야 합니다."
            }
            EventImpactTargetKind.STOCK -> require(stockId != null && industrySegment == null) {
                "종목 영향에는 종목 ID가 필요합니다."
            }
        }
    }

    val specificity: Int
        get() = when (targetKind) {
            EventImpactTargetKind.MARKET -> 1
            EventImpactTargetKind.INDUSTRY -> 2
            EventImpactTargetKind.INDUSTRY_SEGMENT -> 3
            EventImpactTargetKind.STOCK -> 4
        }

    fun appliesTo(stock: StockDefinition): Boolean {
        val matchesMarket = markets.isEmpty() || markets.any { targetMarket ->
            stock.market == targetMarket || stock.etfProfile?.isExposedTo(targetMarket) == true
        }
        if (!matchesMarket) return false

        return when (targetKind) {
            EventImpactTargetKind.MARKET -> true
            EventImpactTargetKind.INDUSTRY -> stock.isExposedToSector(requireNotNull(sector))
            EventImpactTargetKind.INDUSTRY_SEGMENT -> requireNotNull(industrySegment) in stock.industrySegments
            EventImpactTargetKind.STOCK -> {
                val targetStockId = requireNotNull(stockId)
                stock.id == targetStockId ||
                    stock.identityProfile?.underlyingInstrumentIds?.contains(targetStockId) == true
            }
        }
    }
}

/** [GameEvent.affects]와 가격 해석이 같은 커버리지 판정을 공유한다. */
internal data class EventImpactCoverageMatch(
    val applicableInsights: List<EventImpactInsight>,
    val causalImpact: CausalStockImpact?,
    val usesScopeFallback: Boolean,
) {
    val isAffected: Boolean
        get() = applicableInsights.isNotEmpty() || causalImpact != null || usesScopeFallback
}

internal fun GameEvent.impactCoverageFor(stock: StockDefinition): EventImpactCoverageMatch {
    val applicableInsights = impactInsights.filter { it.appliesTo(stock) }
    val causalImpact = causalSignals
        .takeIf { applicableInsights.isEmpty() && it.isNotEmpty() }
        ?.let { signals ->
            when (scope) {
                EventScope.GLOBAL,
                EventScope.SECTOR,
                -> CausalMarketEngine.impactFor(signals, stock)
                EventScope.COUNTRY,
                EventScope.MARKET,
                -> MarketContagionEngine.impactFor(
                    seeds = signals,
                    sourceMarkets = affectedMarkets,
                    stock = stock,
                )
                EventScope.STOCK -> signals
                    .takeIf { affectsByScope(stock) }
                    ?.let { directSignals -> CausalMarketEngine.impactFor(directSignals, stock) }
            }
        }
    val usesScopeFallback = applicableInsights.isEmpty() && causalImpact == null && causalSignals.isEmpty() &&
        impactCoveragePolicy == EventImpactCoveragePolicy.SCOPE_FALLBACK_WITH_OVERRIDES &&
        affectsByScope(stock)
    return EventImpactCoverageMatch(
        applicableInsights = applicableInsights,
        causalImpact = causalImpact,
        usesScopeFallback = usesScopeFallback,
    )
}

/** 실제로 발표·보도된 수치만 뉴스에 싣기 위한 표시용 사실 데이터다. */
data class ReportedFact(
    val label: String,
    val actual: String,
    val comparison: String? = null,
) {
    init {
        require(label.isNotBlank()) { "보도 수치 이름은 비어 있을 수 없습니다." }
        require(actual.isNotBlank()) { "보도 수치의 실제 값은 비어 있을 수 없습니다." }
        require(comparison == null || comparison.isNotBlank()) { "비교 값은 빈 문자열일 수 없습니다." }
    }
}

/** ETF의 명시적 산업 바스켓을 우선하며, 일반 종목에는 대표 산업을 사용한다. */
internal fun StockDefinition.isExposedToSector(target: Sector): Boolean {
    val explicitExposure = identityProfile?.exposedSectors.orEmpty()
    return when {
        explicitExposure.isNotEmpty() -> target in explicitExposure
        !isFundLike -> sector == target
        etfProfile?.assetClass == EtfAssetClass.SECTOR_EQUITY -> sector == target
        else -> false
    }
}
