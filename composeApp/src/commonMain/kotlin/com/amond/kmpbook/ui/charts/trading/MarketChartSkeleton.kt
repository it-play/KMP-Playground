package com.amond.kmpbook.ui.charts.trading

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.amond.kmpbook.ui.theme.MarketColors

/** 턴 계산 중 네이티브 차트를 완전히 대체하는 비인터랙티브 캔들 스켈레톤. */
@Composable
internal fun MarketChartSkeleton(modifier: Modifier = Modifier) {
    val candleLevels = listOf(0.66f, 0.58f, 0.62f, 0.48f, 0.52f, 0.39f, 0.46f, 0.34f, 0.42f, 0.30f)
    val candleMoves = listOf(-0.07f, 0.05f, -0.03f, 0.09f, -0.04f, 0.08f, -0.06f, 0.07f, -0.03f, 0.05f)
    val volumeLevels = listOf(0.34f, 0.52f, 0.28f, 0.64f, 0.42f, 0.72f, 0.38f, 0.58f, 0.46f, 0.66f)

    Canvas(
        modifier = modifier
            .background(MarketColors.Paper)
            .semantics { contentDescription = "턴 진행 중 차트 갱신 중" },
    ) {
        val priceAreaBottom = size.height * 0.72f
        val volumeAreaTop = size.height * 0.78f
        val plotLeft = size.width * 0.05f
        val plotRight = size.width * 0.93f
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val gridColor = MarketColors.Line.copy(alpha = 0.72f)
        val ghostColor = MarketColors.Grey400.copy(alpha = 0.46f)
        val bodyColor = MarketColors.Grey600.copy(alpha = 0.34f)

        repeat(4) { index ->
            val y = size.height * (0.16f + index * 0.16f)
            drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1f)
        }
        repeat(6) { index ->
            val x = plotLeft + plotWidth * index / 5f
            drawLine(gridColor, Offset(x, size.height * 0.10f), Offset(x, size.height * 0.94f), strokeWidth = 1f)
        }
        drawLine(gridColor, Offset(plotRight, size.height * 0.08f), Offset(plotRight, size.height * 0.96f), 1.2f)
        drawLine(gridColor, Offset(plotLeft, priceAreaBottom), Offset(plotRight, priceAreaBottom), 1.2f)

        candleLevels.forEachIndexed { index, level ->
            val x = plotLeft + plotWidth * (index + 0.5f) / candleLevels.size
            val centerY = size.height * level
            val move = candleMoves[index] * size.height
            val openY = centerY - move / 2f
            val closeY = centerY + move / 2f
            val wickTop = minOf(openY, closeY) - size.height * 0.045f
            val wickBottom = maxOf(openY, closeY) + size.height * 0.045f
            val bodyTop = minOf(openY, closeY)
            val bodyHeight = maxOf(kotlin.math.abs(closeY - openY), 3f)
            val bodyWidth = (plotWidth / candleLevels.size * 0.34f).coerceAtLeast(3f)

            drawLine(ghostColor, Offset(x, wickTop), Offset(x, wickBottom), strokeWidth = 2f)
            drawRect(
                color = bodyColor,
                topLeft = Offset(x - bodyWidth / 2f, bodyTop),
                size = Size(bodyWidth, bodyHeight),
            )
            val volumeHeight = (size.height * 0.14f * volumeLevels[index]).coerceAtLeast(2f)
            drawRect(
                color = ghostColor,
                topLeft = Offset(x - bodyWidth / 2f, size.height * 0.94f - volumeHeight),
                size = Size(bodyWidth, volumeHeight),
            )
        }

        drawRoundRect(
            color = MarketColors.Grey400.copy(alpha = 0.30f),
            topLeft = Offset(plotLeft, size.height * 0.035f),
            size = Size(size.width * 0.22f, size.height * 0.035f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
        )
        drawLine(
            color = MarketColors.Line.copy(alpha = 0.70f),
            start = Offset(plotLeft, volumeAreaTop),
            end = Offset(plotRight, volumeAreaTop),
            strokeWidth = 1f,
        )
    }
}
