package com.amond.kmpbook.ui.screens.market

import com.amond.kmpbook.domain.model.pricing.PriceBarInterval

internal enum class MarketCandleInterval(
    val displayName: String,
    val priceBarInterval: PriceBarInterval,
) {
    ONE_HOUR("1H", PriceBarInterval.ONE_HOUR),
    ONE_DAY("1D", PriceBarInterval.ONE_DAY),
    ONE_WEEK("1W", PriceBarInterval.ONE_WEEK),
    ONE_MONTH("1M", PriceBarInterval.ONE_MONTH),
    THREE_MONTHS("3M", PriceBarInterval.THREE_MONTHS),
}
