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
    val foreignExchange: Double,
    val event: Double,
    val fundCosts: Double,
    val carriedReference: Double,
    val idiosyncratic: Double,
) {
    val totalBeforeStabilization: Double
        get() = market + sector + ratesAndInflation + growthAndSentiment +
            foreignExchange + event + fundCosts + carriedReference + idiosyncratic
}
