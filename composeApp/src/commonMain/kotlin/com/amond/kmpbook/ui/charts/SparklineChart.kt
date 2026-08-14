package com.amond.kmpbook.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.amond.kmpbook.ui.theme.MarketColors
import kotlin.math.abs
import kotlin.math.max

/** Non-interactive, line-only summary chart for lobby and dashboard surfaces. */
@Composable
fun SparklineChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MarketColors.Primary,
) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val rawLow = values.minOrNull() ?: return@Canvas
        val rawHigh = values.maxOrNull() ?: return@Canvas
        if (values.size == 1 || abs(rawHigh - rawLow) < 1e-12) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2.4f,
                cap = StrokeCap.Round,
            )
            return@Canvas
        }
        val rawSpan = max(rawHigh - rawLow, max(abs(rawHigh), 1.0) * 0.01)
        val padding = rawSpan * 0.08
        val low = rawLow - padding
        val span = rawSpan + padding * 2.0
        val verticalInset = 2f

        fun point(index: Int, value: Double): Offset = Offset(
            x = size.width * index / (values.size - 1),
            y = verticalInset +
                (size.height - verticalInset * 2f) * (1f - ((value - low) / span).toFloat()),
        )

        val path = Path().apply {
            val first = point(0, values.first())
            moveTo(first.x, first.y)
            values.drop(1).forEachIndexed { offset, value ->
                val next = point(offset + 1, value)
                lineTo(next.x, next.y)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.4f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
