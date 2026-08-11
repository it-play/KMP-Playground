package com.amond.kmpbook.ui.screens.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.modding.model.InstalledMod
import com.amond.kmpbook.modding.model.ModLoadIssue
import com.amond.kmpbook.persistence.model.GameSaveEntry
import com.amond.kmpbook.presentation.simulator.NewGameOptions
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.format.formatDateTimeKst
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun GameLobbyScreen(
    saves: List<GameSaveEntry>,
    saveStatus: String?,
    mods: List<InstalledMod>,
    modIssues: List<ModLoadIssue>,
    modStatusMessage: String?,
    isModCatalogBusy: Boolean,
    areModControlsBusy: Boolean,
    onContinue: (GameSaveEntry) -> Unit,
    onLoad: (GameSaveEntry) -> Unit,
    onStartNewGame: (NewGameOptions) -> Unit,
    onToggleMod: (InstalledMod, Boolean) -> Unit,
    onModSettingChanged: (InstalledMod, String, String) -> Unit,
    onRefreshMods: () -> Unit,
    onOpenModsDirectory: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = saves.firstOrNull()
    var selectedPanel by remember { mutableStateOf(LobbyPanel.MARKET) }

    Row(modifier.fillMaxSize().background(MarketColors.Ledger)) {
        Column(
            Modifier
                .width(610.dp)
                .fillMaxHeight()
                .background(MarketColors.Navy)
                .padding(horizontal = 72.dp, vertical = 58.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                "MARKET LEDGER\n2040",
                style = MarketType.display.copy(fontSize = 48.sp, lineHeight = 49.sp),
                color = Color.White,
            )
            Spacer(Modifier.height(34.dp))
            LobbyMenuItem(
                label = "게임 이어하기",
                value = latest?.name,
                enabled = latest != null,
                emphasized = true,
                onClick = {
                    latest?.let { save ->
                        selectedPanel = LobbyPanel.LOAD_GAME
                        onContinue(save)
                    }
                },
            )
            LobbyMenuItem(
                label = "게임 불러오기",
                value = if (saves.isEmpty()) null else "${saves.size}개",
                enabled = saves.isNotEmpty(),
                selected = selectedPanel == LobbyPanel.LOAD_GAME,
                onClick = {
                    selectedPanel = if (selectedPanel == LobbyPanel.LOAD_GAME) {
                        LobbyPanel.MARKET
                    } else {
                        LobbyPanel.LOAD_GAME
                    }
                },
            )
            LobbyMenuItem(
                label = "새 게임",
                selected = selectedPanel == LobbyPanel.NEW_GAME,
                onClick = {
                    selectedPanel = if (selectedPanel == LobbyPanel.NEW_GAME) {
                        LobbyPanel.MARKET
                    } else {
                        LobbyPanel.NEW_GAME
                    }
                },
            )
            LobbyMenuItem(
                label = "모드",
                selected = selectedPanel == LobbyPanel.MODS,
                onClick = {
                    selectedPanel = if (selectedPanel == LobbyPanel.MODS) {
                        LobbyPanel.MARKET
                    } else {
                        LobbyPanel.MODS
                    }
                },
            )
            LobbyMenuItem(label = "설정", onClick = onSettings)
            LobbyMenuItem(label = "종료하기", enabled = !areModControlsBusy, onClick = onExit)
            Spacer(Modifier.weight(1f))
        }

        Box(Modifier.weight(1f).fillMaxHeight().padding(58.dp)) {
            when (selectedPanel) {
                LobbyPanel.MARKET -> LobbyMarketCarousel(Modifier.fillMaxSize())
                LobbyPanel.LOAD_GAME -> SaveOverview(
                    title = "저장 파일",
                    saves = saves,
                    statusMessage = saveStatus,
                    onLoad = onLoad,
                    modifier = Modifier.fillMaxSize(),
                )
                LobbyPanel.NEW_GAME -> NewGameScreen(
                    onStart = { options ->
                        if (
                            isModCatalogBusy ||
                            mods.count(InstalledMod::enabled) > NewGameOptions.MAX_ACTIVE_MODS
                        ) {
                            selectedPanel = LobbyPanel.MODS
                        } else {
                            onStartNewGame(options)
                        }
                    },
                    onBack = { selectedPanel = LobbyPanel.MARKET },
                    modifier = Modifier.fillMaxSize(),
                    embedded = true,
                )
                LobbyPanel.MODS -> ModsScreen(
                    mods = mods,
                    issues = modIssues,
                    statusMessage = modStatusMessage,
                    isScanning = areModControlsBusy,
                    onToggleMod = onToggleMod,
                    onSettingChanged = onModSettingChanged,
                    onRefresh = onRefreshMods,
                    onOpenModsDirectory = onOpenModsDirectory,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LobbyMenuItem(
    label: String,
    onClick: () -> Unit,
    value: String? = null,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    selected: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        (hovered || selected) && enabled -> Color.White.copy(alpha = 0.08f)
        emphasized && enabled -> MarketColors.Primary
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> Color.White.copy(alpha = 0.2f)
        emphasized || hovered || selected -> Color.White
        else -> Color.White.copy(alpha = 0.68f)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(background, RoundedCornerShape(MarketRadii.small))
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MarketType.heading.copy(fontSize = 19.sp), color = contentColor)
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                style = MarketType.label,
                color = contentColor.copy(alpha = 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SaveOverview(
    title: String,
    saves: List<GameSaveEntry>,
    statusMessage: String?,
    onLoad: (GameSaveEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MarketType.display.copy(fontSize = 30.sp), color = MarketColors.Ink)
            Spacer(Modifier.weight(1f))
            StatusLabel("${saves.size}개", if (saves.isEmpty()) MarketColors.InkMuted else MarketColors.Positive)
        }
        Spacer(Modifier.height(18.dp))
        LedgerDivider()
        if (!statusMessage.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            LedgerPanel(
                modifier = Modifier.fillMaxWidth(),
                background = MarketColors.PrimaryWeak,
                padding = 12.dp,
            ) {
                Text(statusMessage, style = MarketType.body, color = MarketColors.PrimaryText)
            }
        }
        if (saves.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("저장된 게임이 없습니다.", style = MarketType.body, color = MarketColors.InkMuted)
            }
        } else {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Spacer(Modifier.height(18.dp))
                saves.forEachIndexed { index, save ->
                    SaveFileRow(save = save, isLatest = index == 0, onClick = { onLoad(save) })
                }
            }
        }
    }
}

@Composable
private fun SaveFileRow(save: GameSaveEntry, isLatest: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LedgerPanel(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        background = if (hovered) MarketColors.PrimaryWeak else MarketColors.Paper,
        padding = 18.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        save.name,
                        style = MarketType.heading.copy(fontSize = 18.sp),
                        color = MarketColors.Ink,
                    )
                    if (isLatest) {
                        Spacer(Modifier.width(9.dp))
                        StatusLabel("최근", MarketColors.Positive)
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    SaveValue("저장", formatDateTimeKst(save.metadata.savedAt))
                    SaveValue("게임 시각", formatDateTimeKst(save.metadata.gameTime))
                    SaveValue("턴", save.metadata.turn.toString())
                    SaveValue("크기", "${save.sizeBytes / 1024} KB")
                }
            }
            Spacer(Modifier.width(20.dp))
            Text("불러오기", style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.PrimaryText)
        }
    }
}

@Composable
private fun SaveValue(label: String, value: String) {
    Column {
        Text(label, style = MarketType.caption, color = MarketColors.InkMuted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MarketType.number, color = MarketColors.Ink)
    }
}
