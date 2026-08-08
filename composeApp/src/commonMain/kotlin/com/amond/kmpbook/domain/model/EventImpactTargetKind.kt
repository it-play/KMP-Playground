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
