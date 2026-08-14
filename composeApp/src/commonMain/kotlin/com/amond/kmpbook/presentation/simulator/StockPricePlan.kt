package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.pricing.PriceBar
import com.amond.kmpbook.domain.model.pricing.Quote
import com.amond.kmpbook.domain.simulation.price.PriceGenerationInput

/**
 * Immutable input to parallel price calculation plus the state needed for ordered commit.
 * Product/reference state is resolved before this plan is created; workers only read [input].
 */
internal class StockPricePlan(
    val stock: StockDefinition,
    val previousQuote: Quote,
    val tracker: DailyPriceTracker?,
    val firstRegularBar: Boolean,
    val tradingFraction: Double,
    val stockSeedKey: Long,
    val input: PriceGenerationInput?,
    val flatBar: PriceBar?,
) {
    init {
        require((input == null) != (flatBar == null)) {
            "A stock price plan must contain exactly one calculation input or flat bar."
        }
        require(input == null || tracker != null) {
            "A calculated stock price plan requires a daily tracker."
        }
        require(previousQuote.stockId == stock.id)
        require(input == null || input.stock.id == stock.id)
        require(flatBar == null || flatBar.stockId == stock.id)
    }
}
