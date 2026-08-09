package com.amond.kmpbook.domain.simulation.order

import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.venue.MarketSession
import com.amond.kmpbook.domain.simulation.price.PriceGenerationInput
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
