package com.amond.kmpbook.ui.screens.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.persistence.model.GameSaveEntry
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.components.MarketButtonVariant
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.format.formatDateTimeKst
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun GameLobbyScreen(
    saves: List<GameSaveEntry>,
    saveStatus: String,
    saveDirectory: String,
    onContinue: (GameSaveEntry) -> Unit,
    onLoad: (GameSaveEntry) -> Unit,
    onNewGame: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSaves by remember { mutableStateOf(false) }
    val latest = saves.firstOrNull()

    Row(modifier.fillMaxSize().background(MarketColors.Ledger)) {
        LobbyCover(
            latest = latest,
            saveCount = saves.size,
            modifier = Modifier.weight(0.9f).fillMaxHeight(),
        )
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .padding(horizontal = 64.dp, vertical = 48.dp),
        ) {
            Text("GAME LOBBY", style = MarketType.caption, color = MarketColors.PrimaryText)
            Spacer(Modifier.height(7.dp))
            Text("어느 장부를 펼칠까요?", style = MarketType.display.copy(fontSize = 34.sp), color = MarketColors.Ink)
            Spacer(Modifier.height(7.dp))
            Text(
                "최근 기록을 이어가거나 새로운 시장을 시작하세요.",
                style = MarketType.body,
                color = MarketColors.InkMuted,
            )
            Spacer(Modifier.height(28.dp))

            LobbyAction(
                title = "게임 이어하기",
                detail = latest?.let { "${it.name} · ${formatDateTimeKst(it.metadata.gameTime)}" }
                    ?: "이어갈 저장 장부가 없습니다.",
                index = "01",
                enabled = latest != null,
                primary = true,
                onClick = { latest?.let(onContinue) },
            )
            Spacer(Modifier.height(9.dp))
            LobbyAction(
                title = "게임 불러오기",
                detail = if (saves.isEmpty()) "저장 장부가 없습니다." else "최근 저장 순으로 ${saves.size}개 장부 보기",
                index = "02",
                enabled = saves.isNotEmpty(),
                onClick = { showSaves = !showSaves },
            )
            Spacer(Modifier.height(9.dp))
            LobbyAction(
                title = "새 게임",
                detail = "초기 투자금과 시장 환경을 새로 설정합니다.",
                index = "03",
                onClick = onNewGame,
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                LobbyAction(
                    title = "설정",
                    detail = "저장 위치와 조작 안내",
                    index = "04",
                    modifier = Modifier.weight(1f),
                    onClick = onSettings,
                )
                LobbyAction(
                    title = "종료하기",
                    detail = "Market Ledger 닫기",
                    index = "05",
                    modifier = Modifier.weight(1f),
                    onClick = onExit,
                )
            }

            if (showSaves) {
                Spacer(Modifier.height(18.dp))
                LedgerPanel(Modifier.fillMaxWidth().weight(1f), padding = 18.dp) {
                    Column(Modifier.fillMaxSize()) {
                        SectionHeading(
                            title = "저장 장부",
                            eyebrow = "LAST SAVED FIRST",
                            action = { StatusLabel("${saves.size}개", MarketColors.Positive) },
                        )
                        Spacer(Modifier.height(10.dp))
                        LedgerDivider()
                        Column(
                            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Spacer(Modifier.height(2.dp))
                            saves.forEachIndexed { index, save ->
                                SaveEntryRow(save = save, isLatest = index == 0, onClick = { onLoad(save) })
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
                Text(saveStatus, style = MarketType.label, color = MarketColors.InkMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    saveDirectory,
                    style = MarketType.caption,
                    color = MarketColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun LobbySettingsScreen(
    saveDirectory: String,
    saveCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MarketColors.Ledger), contentAlignment = Alignment.Center) {
        LedgerPanel(Modifier.width(720.dp), padding = 30.dp) {
            Column {
                SectionHeading(
                    title = "시작 화면 설정",
                    eyebrow = "MARKET LEDGER 2040",
                    action = { StatusLabel(".ml2 · ${saveCount}개", MarketColors.Positive) },
                )
                Spacer(Modifier.height(20.dp))
                Text("저장 장부 폴더", style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.small))
                        .padding(14.dp),
                ) {
                    Text(saveDirectory, style = MarketType.number, color = MarketColors.Ink)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "모든 저장 장부는 이 폴더에 .ml2 파일로 모입니다. 파일 이름은 저장할 때 직접 정할 수 있습니다.",
                    style = MarketType.body,
                    color = MarketColors.InkMuted,
                )
                Spacer(Modifier.height(22.dp))
                LedgerDivider()
                Spacer(Modifier.height(18.dp))
                Text("키보드 조작", style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
                Spacer(Modifier.height(7.dp))
                Text("게임 진행 중 ESC를 누르면 즉시 인게임 설정으로 이동합니다.", style = MarketType.body, color = MarketColors.InkMuted)
                Spacer(Modifier.height(28.dp))
                MarketButton("로비로 돌아가기", onBack, Modifier.fillMaxWidth(), variant = MarketButtonVariant.Weak)
            }
        }
    }
}

@Composable
private fun LobbyCover(
    latest: GameSaveEntry?,
    saveCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(MarketColors.Navy).padding(horizontal = 58.dp, vertical = 48.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(MarketColors.Primary, RoundedCornerShape(MarketRadii.small)),
                contentAlignment = Alignment.Center,
            ) {
                Text("40", style = MarketType.numberLarge.copy(fontSize = 18.sp), color = Color.White)
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text("Market Ledger 2040", style = MarketType.heading.copy(fontSize = 21.sp), color = Color.White)
                Text("PERSONAL MARKET ARCHIVE", style = MarketType.caption, color = Color.White.copy(alpha = 0.58f))
            }
        }
        Spacer(Modifier.weight(0.8f))
        Text("기록된 판단은\n다음 시장이 된다.", style = MarketType.display.copy(fontSize = 43.sp, lineHeight = 52.sp), color = Color.White)
        Spacer(Modifier.height(15.dp))
        Text(
            "한 시간씩 움직인 시장과 그 안에서 내린 모든 결정을 한 권의 장부로 보관합니다.",
            style = MarketType.body,
            color = Color.White.copy(alpha = 0.62f),
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(MarketRadii.medium))
                .background(MarketColors.NavyRaised, RoundedCornerShape(MarketRadii.medium))
                .padding(18.dp),
        ) {
            Column {
                Text("RECENT LEDGER", style = MarketType.caption, color = Color.White.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                Text(latest?.name ?: "아직 저장된 장부가 없습니다", style = MarketType.heading, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    latest?.let { "마지막 저장 ${formatDateTimeKst(it.metadata.savedAt)} · 턴 ${it.metadata.turn}" }
                        ?: "새 게임을 시작해 첫 장부를 만들어보세요.",
                    style = MarketType.label,
                    color = Color.White.copy(alpha = 0.62f),
                )
                Spacer(Modifier.height(13.dp))
                Text("ARCHIVE  $saveCount", style = MarketType.number, color = MarketColors.SignalLine)
            }
        }
    }
}

@Composable
private fun LobbyAction(
    title: String,
    detail: String,
    index: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val background = if (primary && enabled) MarketColors.Primary else MarketColors.Paper
    val titleColor = if (primary && enabled) Color.White else if (enabled) MarketColors.Ink else MarketColors.Grey400
    val detailColor = if (primary && enabled) Color.White.copy(alpha = 0.68f) else MarketColors.InkMuted
    Row(
        modifier
            .fillMaxWidth()
            .border(1.dp, if (primary && enabled) MarketColors.Primary else MarketColors.Line, RoundedCornerShape(MarketRadii.medium))
            .background(background, RoundedCornerShape(MarketRadii.medium))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(index, style = MarketType.number, color = if (primary && enabled) Color.White.copy(alpha = 0.55f) else MarketColors.PrimaryText)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MarketType.heading.copy(fontSize = 18.sp), color = titleColor)
            Text(detail, style = MarketType.caption, color = detailColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("→", style = MarketType.heading, color = titleColor)
    }
}

@Composable
private fun SaveEntryRow(save: GameSaveEntry, isLatest: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isLatest) MarketColors.PrimaryWeak else MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.small))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(save.name, style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
                if (isLatest) {
                    Spacer(Modifier.width(7.dp))
                    Text("최근", style = MarketType.caption, color = MarketColors.PrimaryText)
                }
            }
            Text(
                "저장 ${formatDateTimeKst(save.metadata.savedAt)} · 게임 ${formatDateTimeKst(save.metadata.gameTime)} · 턴 ${save.metadata.turn}",
                style = MarketType.caption,
                color = MarketColors.InkMuted,
            )
        }
        Text("불러오기", style = MarketType.label, color = MarketColors.PrimaryText)
    }
}
