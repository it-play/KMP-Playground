package com.amond.kmpbook.domain.model.event

import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.IndustrySegment
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Sector

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
