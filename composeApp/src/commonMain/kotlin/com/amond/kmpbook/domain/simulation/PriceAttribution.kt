package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.EtfFxProfile
import com.amond.kmpbook.domain.model.InstrumentStrategy
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.ReferenceCurrency
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TurnStep
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class PriceAttribution(
    val market: Double,
    val sector: Double,
    val ratesAndInflation: Double,
    val growthAndSentiment: Double,
    /** 개인·기관 수급의 종목별 상대 노출. 공통 시장 수급은 market 팩터에 포함된다. */
    val orderFlow: Double,
    val foreignExchange: Double,
    /** 기초자산 경로의 사건 수익. 가격과 펀드 기준가가 함께 소비한다. */
    val referenceEvent: Double,
    /** 운용·발행사 등 상품 자체 사건. 시장가격 괴리 계층에서만 소비한다. */
    val directProductEvent: Double,
    val fundCosts: Double,
    val carriedReference: Double,
    val carriedPriceDislocation: Double,
    /** 기초자산으로 설명되지 않는 잔차 공정가치. 가격과 펀드 기준가가 함께 소비한다. */
    val referenceResidual: Double,
    /** CEF 괴리·ETN 스프레드·미시구조처럼 시장가격에만 남는 잔차다. */
    val priceDislocation: Double,
) {
    /** 기초자산·거시·이벤트·비용으로 설명되는 한 시간의 공정가치 로그수익률이다. */
    val fairValueLogReturn: Double
        get() = market + sector + ratesAndInflation + growthAndSentiment +
            orderFlow + foreignExchange + referenceEvent + fundCosts + carriedReference + referenceResidual

    /** 시간의 제곱근에 비례하는 공통 확산 성분. */
    val systemicDiffusionLogReturn: Double get() = market + sector

    /** 거래시간에 선형 비례하는 조건부 평균·거시 전달 성분. */
    val systemicContinuousLogReturn: Double
        get() = ratesAndInflation + growthAndSentiment + orderFlow + foreignExchange

    /** 발생 시점에 이산적으로 반영되는 사건과 휴장 중 누적 사건 gap. */
    val systemicJumpLogReturn: Double
        get() = referenceEvent + directProductEvent + carriedPriceDislocation

    /** 공정가치로 설명되지 않는 종목 고유 가격 괴리다. 펀드 지표 엔진은 이를 NAV와 분리한다. */
    val priceDislocationLogReturn: Double
        get() = directProductEvent + carriedPriceDislocation + priceDislocation

    val totalBeforeStabilization: Double
        get() = fairValueLogReturn + priceDislocationLogReturn
}
