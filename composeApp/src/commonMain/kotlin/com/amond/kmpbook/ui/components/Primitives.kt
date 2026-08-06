package com.amond.kmpbook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketType

private val PanelShape = RoundedCornerShape(5.dp)

@Composable
fun LedgerPanel(
    modifier: Modifier = Modifier,
    background: Color = MarketColors.Paper,
    padding: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = background,
        shape = PanelShape,
        border = BorderStroke(1.dp, MarketColors.Line),
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.padding(padding)) { content() }
    }
}

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 29.dp)
                .background(MarketColors.Celadon, RoundedCornerShape(1.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MarketType.label.copy(fontSize = 9.sp, letterSpacing = 0.7.sp),
                    color = MarketColors.InkMuted,
                    maxLines = 1,
                )
            }
            Text(
                text = title,
                style = MarketType.heading,
                color = MarketColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (action != null) action()
    }
}

@Composable
fun StatusLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
) {
    Row(
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.09f), RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).background(color, RoundedCornerShape(50)))
        Text(
            text = text,
            style = MarketType.label.copy(
                fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp,
            ),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MarketColors.Ink,
    detail: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MarketType.label, color = MarketColors.InkMuted)
        Text(value, style = MarketType.number, color = valueColor, maxLines = 1)
        if (detail != null) {
            Text(
                detail,
                style = MarketType.label.copy(fontSize = 9.sp),
                color = MarketColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun LedgerDivider(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(1.dp).background(MarketColors.Line))
}

fun deltaColor(value: Double): Color = when {
    value > 0.0 -> MarketColors.Rise
    value < 0.0 -> MarketColors.Fall
    else -> MarketColors.InkMuted
}

