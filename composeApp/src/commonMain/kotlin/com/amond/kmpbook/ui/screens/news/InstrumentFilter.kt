package com.amond.kmpbook.ui.screens.news

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.amond.kmpbook.domain.model.instrument.StockDefinition

internal enum class InstrumentFilter(val label: String) {
    ALL("전체"),
    STOCK("기업"),
    KOREAN_ETF("국내상품"),
    US_ETF("미국상품"),
    ;

    fun matches(stock: StockDefinition): Boolean = when (this) {
        ALL -> true
        STOCK -> stock.hasCorporateEarnings
        KOREAN_ETF -> stock.isFundLike && stock.market.isKorean
        US_ETF -> stock.isFundLike && stock.market.isUnitedStates
    }
}
