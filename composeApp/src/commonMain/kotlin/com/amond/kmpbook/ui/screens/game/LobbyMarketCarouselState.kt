package com.amond.kmpbook.ui.screens.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.amond.kmpbook.presentation.market.MarketIndexSeries

@Stable
internal class LobbyMarketCarouselState {
    var series: List<MarketIndexSeries> by mutableStateOf(emptyList())
        internal set

    var completedAttemptCount: Int by mutableIntStateOf(0)
        internal set

    var isFetching: Boolean by mutableStateOf(false)
        internal set

    var isRetryExhausted: Boolean by mutableStateOf(false)
        internal set

    val unavailableSymbols: Set<String>
        get() = series.filterNot(MarketIndexSeries::isAvailable).mapTo(linkedSetOf()) { it.symbol }

    val needsInitialLoad: Boolean get() = series.isEmpty()

    val hasUnavailableSeries: Boolean get() = needsInitialLoad || unavailableSymbols.isNotEmpty()

    internal fun mergeLoadedSeries(loaded: List<MarketIndexSeries>) {
        if (loaded.isEmpty()) return
        val existingBySymbol = series.associateBy(MarketIndexSeries::symbol)
        val loadedBySymbol = loaded.associateBy(MarketIndexSeries::symbol)
        val orderedSymbols = buildList {
            addAll(series.map(MarketIndexSeries::symbol))
            loaded.mapTo(this) { it.symbol }
        }.distinct()

        series = orderedSymbols.mapNotNull { symbol ->
            val existing = existingBySymbol[symbol]
            val candidate = loadedBySymbol[symbol]
            when {
                existing?.isAvailable == true -> existing
                candidate != null -> candidate
                else -> existing
            }
        }
    }
}

@Composable
internal fun rememberLobbyMarketCarouselState(): LobbyMarketCarouselState =
    remember { LobbyMarketCarouselState() }
