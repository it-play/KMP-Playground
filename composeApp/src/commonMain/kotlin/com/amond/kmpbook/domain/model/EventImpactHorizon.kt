package com.amond.kmpbook.domain.model

import com.amond.kmpbook.domain.simulation.CausalMarketEngine
import com.amond.kmpbook.domain.simulation.MarketContagionEngine

/** 가격 충격의 지속 시간을 단정하지 않고 분석 관점의 시간축만 전달한다. */
enum class EventImpactHorizon(val displayName: String) {
    IMMEDIATE("즉시"),
    SHORT_TERM("단기"),
    MEDIUM_TERM("중기"),
    STRUCTURAL("구조적"),
}
