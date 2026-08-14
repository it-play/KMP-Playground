package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.ReferenceCurrency

/** 가격 엔진이 현재 사용 중인 주요 거시·수급 지표다. */
data class ModMacroSnapshot(
    val policyRate: Double,
    val policyRateChange: Double,
    val koreanPolicyRate: Double,
    val koreanPolicyRateChange: Double,
    val inflationRate: Double,
    val inflationSurprise: Double,
    val growthRate: Double,
    val growthSurprise: Double,
    val usdKrw: Double,
    val fxRatesToKrw: Map<ReferenceCurrency, Double>,
    val riskSentiment: Double,
    val volatilityRegime: Double,
    val retailOrderFlow: Double,
    val institutionalOrderFlow: Double,
    val liquidityStress: Double,
    val newsIntensity: Double,
    val marketHourlyReturns: Map<Market, Double>,
    val marketChangeFromPreviousClose: Map<Market, Double>,
    val usCircuitBreakerLevel: Int,
)
