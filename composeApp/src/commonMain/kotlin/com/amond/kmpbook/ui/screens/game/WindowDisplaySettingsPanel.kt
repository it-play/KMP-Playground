package com.amond.kmpbook.ui.screens.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.presentation.settings.WindowDisplayMode
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun WindowDisplaySettingsPanel(
    selectedMode: WindowDisplayMode,
    onModeSelected: (WindowDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    LedgerPanel(modifier, padding = 16.dp) {
        Column {
            SectionHeading(
                title = "화면 표시",
                action = {
                    StatusLabel(selectedMode.displayName, MarketColors.Primary, strong = true)
                },
            )
            Spacer(Modifier.size(10.dp))
            WindowDisplayMode.entries.forEachIndexed { index, mode ->
                WindowDisplayModeRow(
                    mode = mode,
                    selected = mode == selectedMode,
                    onClick = { onModeSelected(mode) },
                )
                if (index != WindowDisplayMode.entries.lastIndex) Spacer(Modifier.size(7.dp))
            }
        }
    }
}

@Composable
private fun WindowDisplayModeRow(
    mode: WindowDisplayMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(MarketRadii.medium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MarketColors.Primary else MarketColors.Line,
                shape = shape,
            )
            .background(if (selected) MarketColors.PrimaryWeak else MarketColors.Paper, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) "●" else "○",
            style = MarketType.body,
            color = if (selected) MarketColors.Primary else MarketColors.Grey400,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = mode.displayName,
                style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.Ink,
            )
            Text(
                text = mode.description,
                style = MarketType.caption,
                color = MarketColors.InkMuted,
            )
        }
    }
}
