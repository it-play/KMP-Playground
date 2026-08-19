package com.amond.kmpbook.ui.screens.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.presentation.settings.AudioSettings
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.MarketCheckRow
import com.amond.kmpbook.ui.components.MarketSlider
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.theme.MarketColors
import kotlin.math.roundToInt

@Composable
fun AudioSettingsPanel(
    settings: AudioSettings,
    onSettingsChanged: (AudioSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    LedgerPanel(modifier) {
        AudioSettingsContent(settings, onSettingsChanged)
    }
}

@Composable
fun AudioSettingsContent(
    settings: AudioSettings,
    onSettingsChanged: (AudioSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SectionHeading(
            title = "사운드",
            action = {
                StatusLabel(
                    text = if (settings.muted) "음소거" else "${settings.masterVolume.asPercent()}%",
                    color = if (settings.muted) MarketColors.InkMuted else MarketColors.Primary,
                    strong = true,
                )
            },
        )
        Spacer(Modifier.height(1.dp))
        MarketCheckRow(
            checked = settings.muted,
            onCheckedChange = { onSettingsChanged(settings.copy(muted = it)) },
            title = "전체 음소거",
            detail = "모든 게임 소리를 끕니다.",
        )
        if (!settings.muted) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                MarketSlider(
                    title = "전체 음량",
                    detail = "모든 소리",
                    value = settings.masterVolume,
                    valueText = "${settings.masterVolume.asPercent()}%",
                    onValueChange = { onSettingsChanged(settings.copy(masterVolume = it)) },
                )
                MarketSlider(
                    title = "배경음악",
                    detail = "메뉴와 게임 음악",
                    value = settings.musicVolume,
                    valueText = "${settings.musicVolume.asPercent()}%",
                    onValueChange = { onSettingsChanged(settings.copy(musicVolume = it)) },
                )
                MarketSlider(
                    title = "효과음",
                    detail = "버튼과 게임 알림",
                    value = settings.effectsVolume,
                    valueText = "${settings.effectsVolume.asPercent()}%",
                    onValueChange = { onSettingsChanged(settings.copy(effectsVolume = it)) },
                )
            }
        }
    }
}

private fun Double.asPercent(): Int = (this * 100.0).roundToInt()
