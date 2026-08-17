package com.amond.kmpbook.ui.screens.game

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.presentation.market.MarketIndexDataSource
import com.amond.kmpbook.presentation.market.MarketIndexPoint
import com.amond.kmpbook.presentation.market.MarketIndexSeries
import com.amond.kmpbook.ui.charts.SparklineChart
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.MarketHistorySlideFrame
import com.amond.kmpbook.ui.format.formatPercent
import com.amond.kmpbook.ui.format.formatPrice
import com.amond.kmpbook.ui.screens.opening.OpeningSlide
import com.amond.kmpbook.ui.screens.opening.openingSlides
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketType
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

private const val MARKET_CAROUSEL_INTERVAL_MILLIS: Long = 6_000L
private const val MARKET_HISTORY_SLIDE_INTERVAL_MILLIS: Long = 6_500L
private const val MARKET_HISTORY_CROSSFADE_MILLIS: Int = 900
private const val MAX_MARKET_LOAD_ATTEMPTS: Int = 10
private const val INITIAL_MARKET_RETRY_DELAY_MILLIS: Long = 1_000L
private const val MAX_MARKET_RETRY_DELAY_MILLIS: Long = 30_000L

@Composable
internal fun LobbyMarketIndexLoader(state: LobbyMarketCarouselState) {
    val dataSource = remember { MarketIndexDataSource() }

    LaunchedEffect(dataSource, state) {
        if (!state.hasUnavailableSeries || state.isRetryExhausted) return@LaunchedEffect
        if (state.completedAttemptCount >= MAX_MARKET_LOAD_ATTEMPTS) {
            state.isRetryExhausted = true
            return@LaunchedEffect
        }

        state.isFetching = true
        try {
            while (
                state.hasUnavailableSeries &&
                state.completedAttemptCount < MAX_MARKET_LOAD_ATTEMPTS
            ) {
                if (state.completedAttemptCount > 0) {
                    delay(marketRetryDelayMillis(state.completedAttemptCount))
                }

                val requestedSymbols = state.unavailableSymbols
                state.completedAttemptCount += 1
                val loaded = try {
                    if (state.needsInitialLoad || requestedSymbols.isEmpty()) {
                        dataSource.loadThreeMonthSeries()
                    } else {
                        dataSource.loadThreeMonthSeries(requestedSymbols)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
                state.mergeLoadedSeries(loaded)
            }
            state.isRetryExhausted = state.hasUnavailableSeries
        } finally {
            state.isFetching = false
        }
    }
}

@Composable
internal fun LobbyMarketCarousel(
    state: LobbyMarketCarouselState,
    modifier: Modifier = Modifier,
) {
    var activeIndex by remember { mutableIntStateOf(0) }
    var historySlideIndex by remember { mutableIntStateOf(0) }
    val historySlides = remember(state) { openingSlides.shuffled() }

    LaunchedEffect(historySlides, state.hasUnavailableSeries, state.series.isEmpty()) {
        if (!state.hasUnavailableSeries || state.series.isNotEmpty()) return@LaunchedEffect
        while (true) {
            delay(MARKET_HISTORY_SLIDE_INTERVAL_MILLIS)
            historySlideIndex = (historySlideIndex + 1) % historySlides.size
        }
    }
    LaunchedEffect(activeIndex, state.series.size) {
        if (state.series.size > 1) {
            delay(MARKET_CAROUSEL_INTERVAL_MILLIS)
            val nextIndex = (activeIndex + 1) % state.series.size
            if (nextIndex == 0 && state.hasUnavailableSeries) {
                historySlideIndex = (historySlideIndex + 1) % historySlides.size
            }
            activeIndex = nextIndex
        }
    }

    val visibleSeries = state.series.getOrNull(
        activeIndex.coerceIn(0, state.series.lastIndex.coerceAtLeast(0)),
    )
    val accessibilityStatus = when {
        state.series.isEmpty() -> marketStatusDescription(state)
        visibleSeries?.isAvailable == false -> "${visibleSeries.name}. ${marketStatusDescription(state)}"
        else -> null
    }
    val carouselModifier = if (accessibilityStatus != null) {
        modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            stateDescription = accessibilityStatus
        }
    } else {
        modifier
    }

    Box(carouselModifier) {
        if (state.series.isEmpty()) {
            Crossfade(
                targetState = historySlideIndex,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(MARKET_HISTORY_CROSSFADE_MILLIS),
            ) { visibleSlideIndex ->
                MarketSeriesUnavailablePanel(
                    series = null,
                    historySlide = historySlides[visibleSlideIndex],
                    historySlideIndex = visibleSlideIndex,
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            val index = activeIndex.coerceIn(0, state.series.lastIndex)
            val currentSeries = state.series[index]
            val resolvedSlideIndex = if (currentSeries.isAvailable) {
                null
            } else {
                (historySlideIndex + index) % historySlides.size
            }
            Crossfade(
                targetState = currentSeries to resolvedSlideIndex,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(MARKET_HISTORY_CROSSFADE_MILLIS),
            ) { (visibleSeries, visibleSlideIndex) ->
                if (visibleSeries.isAvailable) {
                    IndexSeriesPanel(
                        series = visibleSeries,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val historyIndex = requireNotNull(visibleSlideIndex)
                    MarketSeriesUnavailablePanel(
                        series = visibleSeries,
                        historySlide = historySlides[historyIndex],
                        historySlideIndex = historyIndex,
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketSeriesUnavailablePanel(
    series: MarketIndexSeries?,
    historySlide: OpeningSlide,
    historySlideIndex: Int,
    state: LobbyMarketCarouselState,
    modifier: Modifier = Modifier,
) {
    val statusLabel = when {
        state.isRetryExhausted -> "재시도 중지"
        state.completedAttemptCount <= 1 -> "연결 중"
        else -> "재연결 중"
    }
    val statusDescription = marketStatusDescription(state)
    val headline = when {
        state.isRetryExhausted -> "실시간 시장 지수를 불러오지 못했습니다"
        state.completedAttemptCount <= 1 -> "실시간 시장 지수를 연결하고 있습니다"
        else -> "시장 연결을 다시 확인하고 있습니다"
    }
    val detail = if (state.isRetryExhausted) {
        "로비에 다시 접속하면 자동으로 다시 시도합니다."
    } else {
        "최대 ${MAX_MARKET_LOAD_ATTEMPTS}회까지 백그라운드에서 자동으로 확인합니다."
    }

    LedgerPanel(
        modifier = modifier,
        background = Color.Black,
        padding = 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            MarketHistorySlideFrame(
                image = historySlide.image,
                market = historySlide.market,
                year = historySlide.year,
                credit = historySlide.credit,
                factKey = "lobby-market-history:$historySlideIndex",
                statusLabel = statusLabel,
                statusDescription = statusDescription,
                showProgress = state.isFetching && !state.isRetryExhausted,
                statusSemanticsEnabled = false,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = series?.name ?: "시장 지수",
                    style = MarketType.display.copy(fontSize = 34.sp),
                    color = Color.White,
                )
                Text(
                    text = series?.symbol?.removePrefix("^") ?: "GLOBAL INDEX",
                    style = MarketType.number,
                    color = Color.White.copy(alpha = 0.68f),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = headline,
                    style = MarketType.heading.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    text = detail,
                    style = MarketType.body,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun IndexSeriesPanel(
    series: MarketIndexSeries,
    modifier: Modifier = Modifier,
) {
    val change = series.changeRate
    val trendColor = if ((change ?: 0.0) >= 0.0) MarketColors.Positive else MarketColors.Fall
    LedgerPanel(modifier = modifier, padding = 28.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(series.name, style = MarketType.display.copy(fontSize = 34.sp), color = MarketColors.Ink)
                    Spacer(Modifier.height(3.dp))
                    Text(series.symbol.removePrefix("^"), style = MarketType.number, color = MarketColors.InkMuted)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        series.currentValue?.let { formatPrice(it, Currency.USD, includeCurrency = false) } ?: "-",
                        style = MarketType.numberLarge.copy(fontSize = 34.sp),
                        color = MarketColors.Ink,
                    )
                    if (change != null) {
                        Text(formatPercent(change), style = MarketType.number, color = trendColor)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            if (series.isAvailable) {
                SparklineChart(
                    values = series.points.map(MarketIndexPoint::close),
                    color = trendColor,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(formatMarketDate(series.points.first().epochSeconds), style = MarketType.caption, color = MarketColors.InkMuted)
                    Spacer(Modifier.weight(1f))
                    Text(formatMarketDate(series.points.last().epochSeconds), style = MarketType.caption, color = MarketColors.InkMuted)
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "시장 지수를 준비하고 있습니다.",
                        style = MarketType.body,
                        color = MarketColors.InkMuted,
                    )
                }
            }
        }
    }
}

private fun marketStatusDescription(state: LobbyMarketCarouselState): String =
    if (state.isRetryExhausted) {
        "시장 지수 자동 재시도를 중지했습니다. 로비에 다시 접속하면 자동으로 다시 시도합니다."
    } else {
        "시장 지수 연결을 ${state.completedAttemptCount.coerceAtLeast(1)}회째 시도하고 있습니다."
    }

private fun marketRetryDelayMillis(completedAttemptCount: Int): Long {
    val exponent = (completedAttemptCount - 1).coerceIn(0, MAX_MARKET_LOAD_ATTEMPTS - 1)
    return (INITIAL_MARKET_RETRY_DELAY_MILLIS * (1L shl exponent))
        .coerceAtMost(MAX_MARKET_RETRY_DELAY_MILLIS)
}

private fun formatMarketDate(epochSeconds: Long): String {
    val date = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.UTC).date
    return "${date.year}.${date.month.number.toString().padStart(2, '0')}.${date.day.toString().padStart(2, '0')}"
}
