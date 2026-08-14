package com.amond.kmpbook.domain.simulation.market

import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.market.Sector

/** Macroeconomic state and already-resolved hourly factor returns. */
data class MacroEnvironment(
    /** USD 정책금리 anchor. 기존 전역 금리 소비자와의 의미를 유지한다. */
    val policyRate: Double = 0.03,
    val policyRateChange: Double = 0.0,
    /** 한국은행 기준금리 anchor. KOFR·KRW 금리곡선은 미국 정책결정과 분리해 이 값을 쓴다. */
    val koreanPolicyRate: Double = policyRate,
    val koreanPolicyRateChange: Double = policyRateChange,
    val inflationRate: Double = 0.02,
    val inflationSurprise: Double = 0.0,
    val growthRate: Double = 0.02,
    val growthSurprise: Double = 0.0,
    val usdKrw: Double = 1_350.0,
    val previousUsdKrw: Double = usdKrw,
    /** 다중통화 ETF NAV용 원화 환율. 생략한 순수 엔진 입력은 USD 필드로 fallback한다. */
    val fxRatesToKrw: Map<ReferenceCurrency, Double>? = null,
    val previousFxRatesToKrw: Map<ReferenceCurrency, Double>? = null,
    val riskSentiment: Double = 0.0,
    val volatilityRegime: Double = 1.0,
    /** 동역학 엔진이 해석한 개인·기관 순수급. 외부 구매력 목표값 자체가 아니다. */
    val retailOrderFlow: Double = 0.0,
    val institutionalOrderFlow: Double = 0.0,
    /** 0은 풍부한 호가 깊이, 1은 심한 유동성 고갈을 뜻한다. */
    val liquidityStress: Double = 0.0,
    /** 확률 뉴스의 시간당 경쟁위험을 조절하는 bounded Hawkes 강도. */
    val newsIntensity: Double = 1.0,
    val marketHourlyReturns: Map<Market, Double> = emptyMap(),
    val sectorHourlyReturns: Map<Sector, Double> = emptyMap(),
    val regionalEtfHourlyReturns: Map<EtfExposureRegion, Double>? = null,
    val marketChangeFromPreviousClose: Map<Market, Double> = emptyMap(),
    /** S&P 500 프록시 기준으로 런타임이 이번 시간에 발동한 미국 공통 MWCB 레벨. */
    val usCircuitBreakerLevel: Int = 0,
) {
    init {
        require(
            policyRate.isFinite() && koreanPolicyRate.isFinite() &&
                inflationRate.isFinite() && growthRate.isFinite(),
        )
        require(
            policyRateChange.isFinite() && koreanPolicyRateChange.isFinite() &&
                inflationSurprise.isFinite() && growthSurprise.isFinite(),
        )
        require(usdKrw > 0.0 && usdKrw.isFinite()) { "USD/KRW must be positive and finite" }
        require(previousUsdKrw > 0.0 && previousUsdKrw.isFinite()) {
            "Previous USD/KRW must be positive and finite"
        }
        require(fxRatesToKrw.orEmpty().values.all { it > 0.0 && it.isFinite() })
        require(previousFxRatesToKrw.orEmpty().values.all { it > 0.0 && it.isFinite() })
        require(riskSentiment in -1.0..1.0) { "Risk sentiment must be in [-1, 1]" }
        require(volatilityRegime in 0.1..10.0) { "Volatility regime must be in [0.1, 10]" }
        require(retailOrderFlow.isFinite() && retailOrderFlow in -1.0..1.0)
        require(institutionalOrderFlow.isFinite() && institutionalOrderFlow in -1.0..1.0)
        require(liquidityStress.isFinite() && liquidityStress in 0.0..1.0)
        require(newsIntensity.isFinite() && newsIntensity in 0.25..3.5)
        require(marketHourlyReturns.values.all { it.isFinite() })
        require(sectorHourlyReturns.values.all { it.isFinite() })
        require(regionalEtfHourlyReturns.orEmpty().values.all { it.isFinite() })
        require(marketChangeFromPreviousClose.values.all { it.isFinite() })
        require(usCircuitBreakerLevel in 0..3)
    }

    fun rateToKrw(currency: ReferenceCurrency, previous: Boolean = false): Double {
        if (currency == ReferenceCurrency.KRW) return 1.0
        val rates = if (previous) previousFxRatesToKrw else fxRatesToKrw
        return rates?.get(currency) ?: if (currency == ReferenceCurrency.USD) {
            if (previous) previousUsdKrw else usdKrw
        } else {
            // A standalone engine input may omit a non-USD basket. Hold the missing cross-rate
            // flat instead of inventing currency P&L.
            1.0
        }
    }

    /** Gson처럼 생성자를 우회한 저장 입력도 모든 거시 불변조건에 다시 통과시킨다. */
    internal fun validatedCopy(): MacroEnvironment = copy(
        fxRatesToKrw = fxRatesToKrw?.toMap(),
        previousFxRatesToKrw = previousFxRatesToKrw?.toMap(),
        marketHourlyReturns = marketHourlyReturns.toMap(),
        sectorHourlyReturns = sectorHourlyReturns.toMap(),
        regionalEtfHourlyReturns = regionalEtfHourlyReturns?.toMap(),
        marketChangeFromPreviousClose = marketChangeFromPreviousClose.toMap(),
    )
}
