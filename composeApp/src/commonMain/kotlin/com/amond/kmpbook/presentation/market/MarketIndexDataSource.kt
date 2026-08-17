package com.amond.kmpbook.presentation.market

expect class MarketIndexDataSource() {
    suspend fun loadThreeMonthSeries(): List<MarketIndexSeries>

    suspend fun loadThreeMonthSeries(symbols: Set<String>): List<MarketIndexSeries>
}
