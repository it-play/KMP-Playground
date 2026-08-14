package com.amond.kmpbook.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.amond.kmpbook.ui.theme.MarketColors
import kotlin.math.max
import kotlin.math.min

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
