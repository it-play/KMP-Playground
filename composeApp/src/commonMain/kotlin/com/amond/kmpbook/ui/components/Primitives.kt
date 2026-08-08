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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketComponentSize
import com.amond.kmpbook.ui.theme.MarketElevation
import com.amond.kmpbook.ui.theme.MarketLayout
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketSpacing
import com.amond.kmpbook.ui.theme.MarketType
import com.amond.kmpbook.ui.theme.marketTrendVisual

private val PanelShape = RoundedCornerShape(MarketRadii.large)

/** A quiet grouped surface; cards are defined by space before decoration. */
@Composable
fun LedgerPanel(
    modifier: Modifier = Modifier,
    background: Color = MarketColors.Paper,
    padding: Dp = MarketLayout.panelPadding,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = background,
        shape = PanelShape,
        border = BorderStroke(MarketComponentSize.panelBorder, MarketColors.Line.copy(alpha = 0.72f)),
        shadowElevation = MarketElevation.card,
    ) {
        Box(modifier = Modifier.padding(padding)) { content() }
    }
}

@Composable
fun MarketButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: MarketButtonVariant = MarketButtonVariant.Fill,
    tone: MarketButtonTone = MarketButtonTone.Primary,
) {
    val isFill = variant == MarketButtonVariant.Fill
    val accent = when (tone) {
        MarketButtonTone.Primary -> MarketColors.Primary
        MarketButtonTone.Rise -> MarketColors.Rise
        MarketButtonTone.Fall -> MarketColors.Fall
        MarketButtonTone.Danger -> MarketColors.Rise
    }
    val weakBackground = when (tone) {
        MarketButtonTone.Primary -> MarketColors.PrimaryWeak
        MarketButtonTone.Rise,
        MarketButtonTone.Danger,
        -> MarketColors.RiseSoft
        MarketButtonTone.Fall -> MarketColors.FallSoft
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MarketComponentSize.primaryButtonHeight),
        shape = RoundedCornerShape(MarketRadii.medium),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFill) accent else weakBackground,
            contentColor = if (isFill) Color.White else accent,
            disabledContainerColor = MarketColors.Grey100,
            disabledContentColor = MarketColors.Grey400,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = MarketElevation.flat,
            pressedElevation = MarketElevation.flat,
            focusedElevation = MarketElevation.flat,
            hoveredElevation = MarketElevation.flat,
            disabledElevation = MarketElevation.flat,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(text, style = MarketType.label.copy(fontWeight = FontWeight.SemiBold))
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
        horizontalArrangement = Arrangement.spacedBy(MarketSpacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
        ) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow,
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = MarketColors.PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            .border(
                width = MarketComponentSize.panelBorder,
                color = color.copy(alpha = 0.13f),
                shape = RoundedCornerShape(MarketRadii.pill),
            )
            .background(color.copy(alpha = 0.09f), RoundedCornerShape(MarketRadii.pill))
            .padding(horizontal = MarketSpacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(MarketComponentSize.statusDot)
                .background(color, RoundedCornerShape(MarketRadii.pill)),
        )
        Text(
            text = text,
            style = MarketType.caption.copy(
                fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium,
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
    labelColor: Color = MarketColors.InkMuted,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
    ) {
        Text(label, style = MarketType.label, color = labelColor)
        Text(value, style = MarketType.number, color = valueColor, maxLines = 1)
        if (detail != null) {
            Text(
                detail,
                style = MarketType.caption,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun LedgerDivider(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(MarketComponentSize.divider).background(MarketColors.Line))
}

fun deltaColor(value: Double): Color = marketTrendVisual(value).color
