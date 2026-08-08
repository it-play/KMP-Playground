package com.amond.kmpbook.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.ui.theme.MarketColors
import kotlin.math.max
import kotlin.math.min

@Composable
fun CandlestickVolumeChart(
    bars: List<PriceBar>,
    modifier: Modifier = Modifier,
    showMovingAverages: Boolean = true,
) {
    // 기간 선택은 호출자가 소유한다. 차트가 다시 84개로 자르면 3개월 같은 긴 구간이
    // 조용히 한 달 남짓으로 축소되므로 전달받은 구간 전체를 그린다.
    val visibleBars = bars
    Box(modifier.background(MarketColors.Paper)) {
        Canvas(Modifier.fillMaxSize()) {
            if (visibleBars.isEmpty()) return@Canvas

            val chartBottom = size.height * 0.74f
            val volumeTop = size.height * 0.79f
            val rightPadding = 6f
            val candleAreaWidth = size.width - rightPadding
            val slot = candleAreaWidth / visibleBars.size
            val bodyWidth = max(0.75f, slot * 0.58f)
            val priceLow = visibleBars.minOf { it.low }
            val priceHigh = visibleBars.maxOf { it.high }
            val priceSpan = max(priceHigh - priceLow, priceHigh * 0.005)
            val maxVolume = max(1L, visibleBars.maxOf { it.volume })

            repeat(5) { line ->
                val y = chartBottom * line / 4f
                drawLine(
                    color = MarketColors.Line.copy(alpha = 0.65f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
            repeat(7) { line ->
                val x = size.width * line / 6f
                drawLine(
                    color = MarketColors.Line.copy(alpha = 0.35f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
            }

            fun priceY(value: Double): Float =
                (chartBottom - ((value - priceLow) / priceSpan * chartBottom)).toFloat()

            visibleBars.forEachIndexed { index, bar ->
                val x = slot * index + slot / 2f
                val rising = bar.close >= bar.open
                val color = if (rising) MarketColors.Rise else MarketColors.Fall
                val openY = priceY(bar.open)
                val closeY = priceY(bar.close)
                val highY = priceY(bar.high)
                val lowY = priceY(bar.low)

                drawLine(
                    color = color,
                    start = Offset(x, highY),
                    end = Offset(x, lowY),
                    strokeWidth = max(0.75f, bodyWidth * 0.18f),
                )
                val bodyTop = min(openY, closeY)
                val bodyHeight = max(1.5f, kotlin.math.abs(closeY - openY))
                if (rising) {
                    drawRect(
                        color = color,
                        topLeft = Offset(x - bodyWidth / 2f, bodyTop),
                        size = Size(bodyWidth, bodyHeight),
                        style = Stroke(width = max(0.75f, bodyWidth * 0.22f)),
                    )
                } else {
                    drawRect(
                        color = color,
                        topLeft = Offset(x - bodyWidth / 2f, bodyTop),
                        size = Size(bodyWidth, bodyHeight),
                    )
                }

                val volumeHeight = ((bar.volume.toDouble() / maxVolume) * (size.height - volumeTop)).toFloat()
                if (rising) {
                    drawRect(
                        color = color.copy(alpha = 0.55f),
                        topLeft = Offset(x - bodyWidth / 2f, size.height - volumeHeight),
                        size = Size(bodyWidth, volumeHeight),
                        style = Stroke(width = max(0.75f, bodyWidth * 0.18f)),
                    )
                } else {
                    drawRect(
                        color = color.copy(alpha = 0.32f),
                        topLeft = Offset(x - bodyWidth / 2f, size.height - volumeHeight),
                        size = Size(bodyWidth, volumeHeight),
                    )
                }
            }

            drawLine(
                color = MarketColors.Line,
                start = Offset(0f, volumeTop),
                end = Offset(size.width, volumeTop),
                strokeWidth = 1f,
            )

            if (showMovingAverages) {
                drawSeries(
                    values = movingAverage(visibleBars.map { it.close }, 5),
                    slot = slot,
                    y = ::priceY,
                    color = MarketColors.Amber,
                    strokeWidth = 1.6f,
                )
                drawSeries(
                    values = movingAverage(visibleBars.map { it.close }, 20),
                    slot = slot,
                    y = ::priceY,
                    color = MarketColors.Primary,
                    strokeWidth = 1.4f,
                )
            }
        }
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
    slot: Float,
    y: (Double) -> Float,
    color: Color,
    strokeWidth: Float,
) {
    val path = Path()
    var started = false
    values.forEachIndexed { index, value ->
        if (value != null) {
            val x = slot * index + slot / 2f
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
