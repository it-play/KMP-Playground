package com.amond.kmpbook.ui.screens.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.presentation.settings.AudioSettings
import com.amond.kmpbook.presentation.settings.WindowDisplayMode
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.components.MarketButtonVariant
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.components.VisibleVerticalScrollbar
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun LobbySettingsScreen(
    saveDirectory: String,
    saveCount: Int,
    audioSettings: AudioSettings,
    onAudioSettingsChanged: (AudioSettings) -> Unit,
    windowDisplayMode: WindowDisplayMode,
    onWindowDisplayModeChanged: (WindowDisplayMode) -> Unit,
    onOpenSaveDirectory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Box(modifier.fillMaxSize().background(MarketColors.Ledger), contentAlignment = Alignment.Center) {
        VisibleVerticalScrollbar(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 820.dp)
                    .verticalScroll(scrollState)
                    .padding(vertical = 24.dp, horizontal = 13.dp),
            ) {
            LedgerPanel(Modifier.fillMaxWidth(), padding = 22.dp) {
                SectionHeading(
                    title = "설정",
                    action = { StatusLabel(".ml2 · ${saveCount}개", MarketColors.Positive) },
                )
            }
            Spacer(Modifier.height(16.dp))
            AudioSettingsPanel(
                settings = audioSettings,
                onSettingsChanged = onAudioSettingsChanged,
                modifier = Modifier.fillMaxWidth().height(410.dp),
            )
            Spacer(Modifier.height(16.dp))
            WindowDisplaySettingsPanel(
                selectedMode = windowDisplayMode,
                onModeSelected = onWindowDisplayModeChanged,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            LedgerPanel(Modifier.fillMaxWidth(), padding = 22.dp) {
                Column {
                    SectionHeading("저장과 조작")
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "저장 폴더",
                        style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                        color = MarketColors.Ink,
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.small))
                            .padding(14.dp),
                    ) {
                        Text(saveDirectory, style = MarketType.number, color = MarketColors.Ink)
                    }
                    Spacer(Modifier.height(10.dp))
                    MarketButton(
                        text = "탐색기로 보기",
                        onClick = onOpenSaveDirectory,
                        modifier = Modifier.fillMaxWidth(),
                        variant = MarketButtonVariant.Weak,
                    )
                    Spacer(Modifier.height(18.dp))
                    LedgerDivider()
                    Spacer(Modifier.height(14.dp))
                    Text("ESC", style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
                    Spacer(Modifier.height(5.dp))
                    Text("게임 중 설정 열기", style = MarketType.body, color = MarketColors.InkMuted)
                }
            }
            Spacer(Modifier.height(16.dp))
            MarketButton("로비로 돌아가기", onBack, Modifier.fillMaxWidth(), variant = MarketButtonVariant.Weak)
            }
        }
    }
}
