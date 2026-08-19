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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.presentation.settings.AudioSettings
import com.amond.kmpbook.presentation.settings.WindowDisplayMode
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.components.MarketButtonVariant
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.VisibleVerticalScrollbar
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun LobbySettingsScreen(
    audioSettings: AudioSettings,
    onAudioSettingsChanged: (AudioSettings) -> Unit,
    windowDisplayMode: WindowDisplayMode,
    onWindowDisplayModeChanged: (WindowDisplayMode) -> Unit,
    maintenanceStatus: String?,
    isOpeningGameDirectory: Boolean,
    isCheckingResources: Boolean,
    onOpenGameDirectory: () -> Unit,
    onCheckResources: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                        action = {
                            TextButton(onClick = onBack) {
                                Text("닫기", style = MarketType.label, color = MarketColors.InkMuted)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                AudioSettingsPanel(
                    settings = audioSettings,
                    onSettingsChanged = onAudioSettingsChanged,
                    modifier = Modifier.fillMaxWidth(),
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
                        SectionHeading("파일과 리소스")
                        Spacer(Modifier.height(16.dp))
                        MarketButton(
                            text = if (isOpeningGameDirectory) "폴더 여는 중…" else "로컬 파일 열기",
                            onClick = onOpenGameDirectory,
                            enabled = !isOpeningGameDirectory && !isCheckingResources,
                            modifier = Modifier.fillMaxWidth(),
                            variant = MarketButtonVariant.Weak,
                        )
                        Spacer(Modifier.height(10.dp))
                        MarketButton(
                            text = if (isCheckingResources) "검사 중…" else "리소스 무결성 검사",
                            onClick = onCheckResources,
                            enabled = !isOpeningGameDirectory && !isCheckingResources,
                            modifier = Modifier.fillMaxWidth(),
                            variant = MarketButtonVariant.Weak,
                        )
                        maintenanceStatus?.let { status ->
                            Spacer(Modifier.height(14.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.small))
                                    .padding(14.dp),
                            ) {
                                Text(status, style = MarketType.body, color = MarketColors.Ink)
                            }
                        }
                    }
                }
            }
        }
    }
}
