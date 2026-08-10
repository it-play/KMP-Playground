package com.amond.kmpbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun MarketSlider(
    title: String,
    detail: String,
    value: Double,
    valueText: String,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    secondaryValueText: String? = null,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(MarketRadii.medium)
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.62f)
            .border(1.dp, MarketColors.Line, shape)
            .background(if (enabled) MarketColors.Paper else MarketColors.Grey50, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.Ink,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                detail,
                modifier = Modifier.weight(1f),
                style = MarketType.caption,
                color = MarketColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            secondaryValueText?.let {
                Text(it, style = MarketType.caption, color = MarketColors.InkMuted)
                Spacer(Modifier.width(7.dp))
            }
            Box(
                Modifier
                    .background(
                        if (enabled) MarketColors.PrimaryWeak else MarketColors.Grey100,
                        RoundedCornerShape(MarketRadii.small),
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    valueText,
                    style = MarketType.number,
                    color = if (enabled) MarketColors.PrimaryText else MarketColors.InkMuted,
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            modifier = Modifier.fillMaxWidth().height(26.dp),
            enabled = enabled,
            valueRange = 0f..1f,
            steps = 99,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = MarketColors.Primary,
                activeTrackColor = MarketColors.Primary,
                inactiveTrackColor = MarketColors.Grey200,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = MarketColors.Grey400,
                disabledActiveTrackColor = MarketColors.Grey400,
                disabledInactiveTrackColor = MarketColors.Grey200,
                disabledActiveTickColor = Color.Transparent,
                disabledInactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun MarketCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(MarketRadii.medium)
    val selectedBackground = if (enabled) MarketColors.PrimaryWeak else MarketColors.Grey100
    val selectedBorder = if (enabled) MarketColors.Primary else MarketColors.Grey400
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.68f)
            .border(
                width = if (checked) 2.dp else 1.dp,
                color = if (checked) selectedBorder else MarketColors.Line,
                shape = shape,
            )
            .background(if (checked) selectedBackground else MarketColors.Paper, shape)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = if (checked) 0.dp else 1.dp,
                    color = if (checked) Color.Transparent else MarketColors.Grey400,
                    shape = RoundedCornerShape(MarketRadii.small),
                )
                .background(
                    color = if (checked) selectedBorder else MarketColors.Paper,
                    shape = RoundedCornerShape(MarketRadii.small),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text("✓", style = MarketType.label.copy(fontWeight = FontWeight.Bold), color = Color.White)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MarketType.body.copy(fontWeight = FontWeight.Medium), color = MarketColors.Ink)
            Text(
                detail,
                style = MarketType.label,
                color = MarketColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
