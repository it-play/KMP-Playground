package com.amond.kmpbook.ui.screens.market

/** 사용자에게 노출하는 기간 프리셋. 봉 해상도는 기간에 맞춰 자동으로 선택한다. */
internal enum class MarketChartPeriod(
    val displayName: String,
    val preferredInterval: MarketCandleInterval?,
    val durationSeconds: Long?,
) {
    ONE_DAY("1D", MarketCandleInterval.ONE_HOUR, 24L * 60L * 60L),
    ONE_WEEK("1W", MarketCandleInterval.ONE_HOUR, 7L * 24L * 60L * 60L),
    ONE_MONTH("1M", MarketCandleInterval.ONE_DAY, 31L * 24L * 60L * 60L),
    THREE_MONTHS("3M", MarketCandleInterval.ONE_DAY, 93L * 24L * 60L * 60L),
    ONE_YEAR("1Y", MarketCandleInterval.ONE_WEEK, 366L * 24L * 60L * 60L),
    ALL("ALL", null, null),
}
