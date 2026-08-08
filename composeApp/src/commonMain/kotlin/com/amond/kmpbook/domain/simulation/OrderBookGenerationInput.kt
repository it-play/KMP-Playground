package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.MarketVenueProfiles
import com.amond.kmpbook.domain.model.OrderBook
import com.amond.kmpbook.domain.model.OrderBookLevel
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.StockDefinition
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Instant

data class OrderBookGenerationInput(
    val stock: StockDefinition,
    val timestamp: Instant,
    val lastPrice: Double,
    val dailyBasePrice: Double = lastPrice,
    val averageDailyVolume: Long = PriceGenerationInput.defaultAverageDailyVolume(stock),
    val session: MarketSession = MarketSession.REGULAR,
    /** Positive values produce stronger bid depth; range is deliberately bounded. */
    val buyPressure: Double = 0.0,
    /** 0 is calm and 1 is severe stress. Stress widens spreads and thins depth. */
    val marketStress: Double = 0.0,
) {
    init {
        require(lastPrice > 0.0 && lastPrice.isFinite())
        require(dailyBasePrice > 0.0 && dailyBasePrice.isFinite())
        require(averageDailyVolume >= 0L)
        require(buyPressure in -1.0..1.0)
        require(marketStress in 0.0..1.0)
    }
}
