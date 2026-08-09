package com.amond.kmpbook.ui.screens.market

import com.amond.kmpbook.domain.model.pricing.PriceBarInterval

internal enum class MarketCandleInterval(
    val displayName: String,
    val priceBarInterval: PriceBarInterval,
) {
    ONE_DAY("1일", PriceBarInterval.ONE_DAY),
    ONE_WEEK("1주", PriceBarInterval.ONE_WEEK),
    ONE_MONTH("1개월", PriceBarInterval.ONE_MONTH),
    THREE_MONTHS("3개월", PriceBarInterval.THREE_MONTHS),
}
