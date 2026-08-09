package com.amond.kmpbook.ui.screens.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.presentation.market.MarketIndexDataSource
import com.amond.kmpbook.presentation.market.MarketIndexPoint
import com.amond.kmpbook.presentation.market.MarketIndexSeries
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.format.formatPercent
import com.amond.kmpbook.ui.format.formatPrice
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketType
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

private const val MARKET_CAROUSEL_INTERVAL_MILLIS: Long = 6_000L

@Composable
fun LobbyMarketCarousel(modifier: Modifier = Modifier) {
    val dataSource = remember { MarketIndexDataSource() }
    var series by remember { mutableStateOf(emptyList<MarketIndexSeries>()) }
    var activeIndex by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dataSource) {
        loading = true
        loadError = null
        runCatching { dataSource.loadThreeMonthSeries() }
            .onSuccess {
                series = it
                activeIndex = 0
            }
            .onFailure { loadError = it.message ?: "시세를 불러오지 못했습니다." }
        loading = false
    }
    LaunchedEffect(activeIndex, series.size) {
        if (series.size > 1) {
            delay(MARKET_CAROUSEL_INTERVAL_MILLIS)
            activeIndex = (activeIndex + 1) % series.size
        }
    }

    Box(modifier) {
        when {
            loading && series.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MarketColors.Primary)
            }
            loadError != null && series.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(loadError.orEmpty(), style = MarketType.body, color = MarketColors.RiseText)
            }
            series.isNotEmpty() -> {
                val index = activeIndex.coerceIn(0, series.lastIndex)
                IndexSeriesPanel(
                    series = series[index],
                    modifier = Modifier.fillMaxSize(),
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
            if (series.points.size >= 2) {
                IndexLineChart(
                    points = series.points,
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
                        series.errorMessage ?: "시세 데이터가 없습니다.",
                        style = MarketType.body,
                        color = MarketColors.RiseText,
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexLineChart(
    points: List<MarketIndexPoint>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val values = points.map(MarketIndexPoint::close)
        val minimum = values.minOrNull() ?: return@Canvas
        val maximum = values.maxOrNull() ?: return@Canvas
        val range = (maximum - minimum).takeIf { it > 0.0 } ?: 1.0
        val topPadding = size.height * 0.08f
        val bottom = size.height * 0.92f
        val chartHeight = bottom - topPadding

        repeat(5) { index ->
            val y = topPadding + chartHeight * index / 4f
            drawLine(MarketColors.Line.copy(alpha = 0.72f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        repeat(4) { index ->
            val x = size.width * index / 3f
            drawLine(MarketColors.Line.copy(alpha = 0.4f), Offset(x, topPadding), Offset(x, bottom), 1f)
        }

        val plotted = points.mapIndexed { index, point ->
            val x = if (points.size == 1) 0f else size.width * index / (points.size - 1).toFloat()
            val y = bottom - ((point.close - minimum) / range).toFloat() * chartHeight
            Offset(x, y)
        }
        val area = Path().apply {
            moveTo(plotted.first().x, bottom)
            lineTo(plotted.first().x, plotted.first().y)
            plotted.drop(1).forEach { lineTo(it.x, it.y) }
            lineTo(plotted.last().x, bottom)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.22f), Color.Transparent),
                startY = topPadding,
                endY = bottom,
            ),
        )
        val line = Path().apply {
            moveTo(plotted.first().x, plotted.first().y)
            plotted.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(line, color, style = Stroke(width = 3f))
        drawCircle(color, radius = 5f, center = plotted.last())
    }
}

private fun formatMarketDate(epochSeconds: Long): String {
    val date = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.UTC).date
    return "${date.year}.${date.month.number.toString().padStart(2, '0')}.${date.day.toString().padStart(2, '0')}"
}
