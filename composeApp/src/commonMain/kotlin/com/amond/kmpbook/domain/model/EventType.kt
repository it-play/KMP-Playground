package com.amond.kmpbook.domain.model

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

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
