package com.amond.kmpbook.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.PriceBarInterval
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketType
import kotlinx.datetime.number
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun CandlestickVolumeChart(
    bars: List<PriceBar>,
    market: Market,
    priceFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    showMovingAverages: Boolean = true,
) {
    val visibleBars = remember(bars) { bars.sortedBy(PriceBar::startTime) }
    val currentPriceFormatter = rememberUpdatedState(priceFormatter)
    val textMeasurer = rememberTextMeasurer()
    val tickTextStyle = MarketType.caption.copy(color = MarketColors.InkMuted)
    val currentPriceTextStyle = MarketType.caption.copy(
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
    )

    Box(
        modifier = modifier.background(MarketColors.Paper),
        contentAlignment = Alignment.Center,
    ) {
        if (visibleBars.isEmpty()) {
            Text(
                text = "첫 거래 시간이 지나면 OHLCV 차트가 생성됩니다.",
                style = MarketType.label,
                color = MarketColors.InkMuted,
            )
            return@Box
        }

        Canvas(Modifier.fillMaxSize()) {
            val plotLeft = 8.dp.toPx()
            val priceAxisWidth = 78.dp.toPx()
            val plotRight = size.width - priceAxisWidth
            val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
            val priceTop = 10.dp.toPx()
            val timeAxisHeight = 23.dp.toPx()
            val volumeBottom = size.height - timeAxisHeight
            val volumeHeight = (size.height * 0.18f).coerceIn(34.dp.toPx(), 68.dp.toPx())
            val volumeTop = volumeBottom - volumeHeight
            val priceBottom = volumeTop - 13.dp.toPx()
            val priceHeight = (priceBottom - priceTop).coerceAtLeast(1f)
            val slot = plotWidth / visibleBars.size
            val maximumBodyWidth = min(48.dp.toPx(), slot * 0.72f)
            val minimumBodyWidth = min(2.dp.toPx(), maximumBodyWidth)
            val bodyWidth = (slot * 0.58f).coerceIn(minimumBodyWidth, maximumBodyWidth)
            val rawPriceLow = visibleBars.minOf { it.low }
            val rawPriceHigh = visibleBars.maxOf { it.high }
            val rawPriceSpan = max(rawPriceHigh - rawPriceLow, max(abs(rawPriceHigh), 1.0) * 0.006)
            val pricePadding = rawPriceSpan * 0.08
            val priceLow = (rawPriceLow - pricePadding).coerceAtLeast(0.0)
            val priceHigh = rawPriceHigh + pricePadding
            val priceSpan = (priceHigh - priceLow).coerceAtLeast(0.000001)
            val maxVolume = max(1L, visibleBars.maxOf { it.volume })
            val datesDiffer = GameCalendar.marketLocalDateTime(market, visibleBars.first().startTime).date !=
                GameCalendar.marketLocalDateTime(market, visibleBars.last().startTime).date

            fun candleX(index: Int): Float = plotLeft + slot * index + slot / 2f
            fun priceY(value: Double): Float =
                (priceBottom - ((value - priceLow) / priceSpan * priceHeight)).toFloat()
            val latestBar = visibleBars.last()
            val latestColor = when {
                latestBar.close > latestBar.open -> MarketColors.Rise
                latestBar.close < latestBar.open -> MarketColors.Fall
                else -> MarketColors.InkMuted
            }
            val latestY = priceY(latestBar.close).coerceIn(priceTop, priceBottom)

            repeat(5) { line ->
                val fraction = line / 4f
                val y = priceTop + priceHeight * fraction
                drawLine(
                    color = MarketColors.Line.copy(alpha = 0.62f),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1f,
                )
                val tickPrice = priceHigh - priceSpan * fraction
                val tickLayout = textMeasurer.measure(
                    text = currentPriceFormatter.value(tickPrice),
                    style = tickTextStyle,
                )
                if (abs(y - latestY) > 18.dp.toPx()) {
                    drawText(
                        textLayoutResult = tickLayout,
                        topLeft = Offset(
                            x = size.width - 5.dp.toPx() - tickLayout.size.width,
                            y = (y - tickLayout.size.height / 2f)
                                .coerceIn(0f, size.height - tickLayout.size.height),
                        ),
                    )
                }
            }

            val timeLabelIndices = listOf(0, visibleBars.lastIndex / 2, visibleBars.lastIndex).distinct()
            timeLabelIndices.forEach { index ->
                val x = candleX(index)
                drawLine(
                    color = MarketColors.Line.copy(alpha = 0.30f),
                    start = Offset(x, priceTop),
                    end = Offset(x, volumeBottom),
                    strokeWidth = 1f,
                )
                val timeLayout = textMeasurer.measure(
                    text = visibleBars[index].axisLabel(
                        market = market,
                        includeDate = datesDiffer,
                    ),
                    style = tickTextStyle,
                )
                drawText(
                    textLayoutResult = timeLayout,
                    topLeft = Offset(
                        x = (x - timeLayout.size.width / 2f)
                            .coerceIn(plotLeft, plotRight - timeLayout.size.width),
                        y = volumeBottom + 4.dp.toPx(),
                    ),
                )
            }

            visibleBars.forEachIndexed { index, bar ->
                val x = candleX(index)
                val color = when {
                    bar.close > bar.open -> MarketColors.Rise
                    bar.close < bar.open -> MarketColors.Fall
                    else -> MarketColors.InkMuted
                }
                val openY = priceY(bar.open)
                val closeY = priceY(bar.close)
                val highY = priceY(bar.high)
                val lowY = priceY(bar.low)

                drawLine(
                    color = color,
                    start = Offset(x, highY),
                    end = Offset(x, lowY),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Square,
                )
                val bodyTop = min(openY, closeY)
                val bodyHeight = abs(closeY - openY)
                if (bodyHeight < 1.4.dp.toPx()) {
                    drawLine(
                        color = color,
                        start = Offset(x - bodyWidth / 2f, (openY + closeY) / 2f),
                        end = Offset(x + bodyWidth / 2f, (openY + closeY) / 2f),
                        strokeWidth = 1.4.dp.toPx(),
                        cap = StrokeCap.Square,
                    )
                } else {
                    drawRect(
                        color = color,
                        topLeft = Offset(x - bodyWidth / 2f, bodyTop),
                        size = Size(bodyWidth, bodyHeight),
                    )
                }

                val barVolumeHeight = ((bar.volume.toDouble() / maxVolume) * volumeHeight).toFloat()
                drawRect(
                    color = color.copy(alpha = 0.34f),
                    topLeft = Offset(x - bodyWidth / 2f, volumeBottom - barVolumeHeight),
                    size = Size(bodyWidth, max(1.dp.toPx(), barVolumeHeight)),
                )
            }

            drawLine(
                color = MarketColors.Line,
                start = Offset(plotLeft, volumeTop),
                end = Offset(plotRight, volumeTop),
                strokeWidth = 1f,
            )

            if (showMovingAverages) {
                drawSeries(
                    values = movingAverage(visibleBars.map { it.close }, 5),
                    plotLeft = plotLeft,
                    slot = slot,
                    y = ::priceY,
                    color = MarketColors.Amber,
                    strokeWidth = 1.7.dp.toPx(),
                )
                drawSeries(
                    values = movingAverage(visibleBars.map { it.close }, 20),
                    plotLeft = plotLeft,
                    slot = slot,
                    y = ::priceY,
                    color = MarketColors.Primary,
                    strokeWidth = 1.5.dp.toPx(),
                )
            }

            drawLine(
                color = latestColor.copy(alpha = 0.72f),
                start = Offset(plotLeft, latestY),
                end = Offset(plotRight, latestY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                ),
            )
            val latestLayout = textMeasurer.measure(
                text = currentPriceFormatter.value(latestBar.close),
                style = currentPriceTextStyle,
            )
            val priceTagLeft = plotRight + 4.dp.toPx()
            val priceTagRight = size.width - 4.dp.toPx()
            val priceTagHeight = 21.dp.toPx()
            val priceTagTop = (latestY - priceTagHeight / 2f)
                .coerceIn(0f, size.height - priceTagHeight)
            drawRoundRect(
                color = latestColor,
                topLeft = Offset(priceTagLeft, priceTagTop),
                size = Size(priceTagRight - priceTagLeft, priceTagHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            )
            drawText(
                textLayoutResult = latestLayout,
                topLeft = Offset(
                    x = priceTagLeft + (priceTagRight - priceTagLeft - latestLayout.size.width) / 2f,
                    y = priceTagTop + (priceTagHeight - latestLayout.size.height) / 2f,
                ),
            )
        }
    }
}

private fun PriceBar.axisLabel(
    market: Market,
    includeDate: Boolean,
): String {
    val local = GameCalendar.marketLocalDateTime(market, startTime)
    val year = local.year.toString()
    val month = local.month.number.toString().padStart(2, '0')
    val day = local.day.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return when (step) {
        PriceBarInterval.ONE_HOUR -> if (includeDate) "$month.$day $hour:$minute" else "$hour:$minute"
        PriceBarInterval.ONE_DAY -> "$month.$day"
        PriceBarInterval.ONE_WEEK -> "$month.$day 주"
        PriceBarInterval.ONE_MONTH -> "$year.$month"
        PriceBarInterval.THREE_MONTHS -> "$year Q${local.month.ordinal / 3 + 1}"
    }
}

@Composable
fun LineAreaChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MarketColors.Primary,
    baseline: Double? = null,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val low = min(values.minOrNull() ?: 0.0, baseline ?: Double.POSITIVE_INFINITY)
            .takeIf { it.isFinite() } ?: (values.minOrNull() ?: 0.0)
        val high = max(values.maxOrNull() ?: 1.0, baseline ?: Double.NEGATIVE_INFINITY)
            .takeIf { it.isFinite() } ?: (values.maxOrNull() ?: 1.0)
        val span = max(high - low, max(kotlin.math.abs(high), 1.0) * 0.01)
        fun point(index: Int, value: Double): Offset = Offset(
            x = size.width * index / (values.size - 1),
            y = size.height - (((value - low) / span) * size.height).toFloat(),
        )

        repeat(4) { line ->
            val y = size.height * line / 3f
            drawLine(
                MarketColors.Line.copy(alpha = 0.5f),
                Offset(0f, y),
                Offset(size.width, y),
                1f,
            )
        }
        baseline?.let {
            val y = point(0, it).y
            drawLine(
                MarketColors.InkMuted.copy(alpha = 0.45f),
                Offset(0f, y),
                Offset(size.width, y),
                1f,
            )
        }

        val line = Path().apply {
            val first = point(0, values.first())
            moveTo(first.x, first.y)
            values.drop(1).forEachIndexed { offset, value ->
                val next = point(offset + 1, value)
                lineTo(next.x, next.y)
            }
        }
        val area = Path().apply {
            val first = point(0, values.first())
            moveTo(first.x, size.height)
            lineTo(first.x, first.y)
            values.drop(1).forEachIndexed { offset, value ->
                val next = point(offset + 1, value)
                lineTo(next.x, next.y)
            }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(area, color.copy(alpha = 0.10f))
        drawPath(line, color, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
    }
}

@Composable
fun AllocationDonut(
    values: List<Pair<Double, Color>>,
    modifier: Modifier = Modifier,
    trackColor: Color = MarketColors.PaperMuted,
) {
    Canvas(modifier) {
        val total = values.sumOf { max(0.0, it.first) }
        val stroke = min(size.width, size.height) * 0.16f
        val inset = stroke / 2f
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Butt),
        )
        if (total <= 0.0) return@Canvas
        var start = -90f
        values.filter { it.first > 0.0 }.forEach { (value, color) ->
            val sweep = (value / total * 360.0).toFloat()
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = max(0f, sweep - 1.5f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}

private fun movingAverage(values: List<Double>, period: Int): List<Double?> = values.indices.map { index ->
    if (index + 1 < period) null
    else values.subList(index + 1 - period, index + 1).average()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    values: List<Double?>,
    plotLeft: Float,
    slot: Float,
    y: (Double) -> Float,
    color: Color,
    strokeWidth: Float,
) {
    val path = Path()
    var started = false
    values.forEachIndexed { index, value ->
        if (value != null) {
            val x = plotLeft + slot * index + slot / 2f
            if (!started) {
                path.moveTo(x, y(value))
                started = true
            } else {
                path.lineTo(x, y(value))
            }
        }
    }
    if (started) drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}
