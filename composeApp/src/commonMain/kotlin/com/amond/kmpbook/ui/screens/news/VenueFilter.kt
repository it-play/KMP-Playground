package com.amond.kmpbook.ui.screens.news

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Market

internal enum class VenueFilter(val label: String) {
    ALL("전체"),
    KOSPI("코스피"),
    KOSDAQ("코스닥"),
    US_ALL("미국"),
    NASDAQ("Nasdaq"),
    NYSE("NYSE"),
    ARCA("Arca"),
    BZX("BZX"),
    AMERICAN("Amex"),
    ;

    fun matches(stock: StockDefinition): Boolean = when (this) {
        ALL -> true
        KOSPI -> stock.market == Market.KOSPI
        KOSDAQ -> stock.market == Market.KOSDAQ
        US_ALL -> stock.market.isUnitedStates
        NASDAQ -> stock.market == Market.NASDAQ
        NYSE -> stock.market == Market.NYSE
        ARCA -> stock.market == Market.NYSE_ARCA
        BZX -> stock.market == Market.CBOE_BZX
        AMERICAN -> stock.market == Market.NYSE_AMERICAN
    }
}
