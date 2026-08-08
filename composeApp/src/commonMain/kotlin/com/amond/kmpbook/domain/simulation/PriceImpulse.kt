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

/** Price, volatility, and liquidity effect applied during this one-hour step. */
data class PriceImpulse(
    val returnRate: Double = 0.0,
    /** 종목 자체의 운용·발행사 사건. 인버스·레버리지 배율을 다시 곱하지 않는다. */
    val directProductReturnRate: Double = 0.0,
    val volatilityMultiplier: Double = 1.0,
    val volumeMultiplier: Double = 1.0,
) {
    init {
        require(returnRate > -1.0 && returnRate.isFinite()) {
            "Impulse return must be finite and greater than -100%"
        }
        require(directProductReturnRate > -1.0 && directProductReturnRate.isFinite()) {
            "Direct product impulse must be finite and greater than -100%"
        }
        require(volatilityMultiplier in 0.0..20.0) {
            "Volatility multiplier must be in [0, 20]"
        }
        require(volumeMultiplier in 0.0..100.0) { "Volume multiplier must be in [0, 100]" }
    }

    val referenceReturnRate: Double
        get() = ((1.0 + returnRate) / (1.0 + directProductReturnRate) - 1.0).coerceAtLeast(-0.95)
}
