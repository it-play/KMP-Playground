package com.amond.kmpbook.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.format.formatDateTimeKst
import com.amond.kmpbook.ui.format.formatMoney
import com.amond.kmpbook.ui.format.formatPercent
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketType
import kotlin.time.Instant

data class SidebarSummary(
    val totalAssetsKrw: Double,
    val returnRate: Double,
    val unreadEvents: Int,
)

private data class NavigationItem(
    val screen: Screen,
    val shortLabel: String,
    val marker: String,
)

private val navigationItems = listOf(
    NavigationItem(Screen.HOME, "상황판", "01"),
    NavigationItem(Screen.MARKET, "시장·종목", "02"),
    NavigationItem(Screen.ORDER, "주문·체결", "03"),
    NavigationItem(Screen.PORTFOLIO, "포트폴리오", "04"),
    NavigationItem(Screen.EVENTS, "뉴스·이벤트", "05"),
    NavigationItem(Screen.ANALYTICS, "투자 분석", "06"),
    NavigationItem(Screen.TAX_REPORT, "세금 센터", "07"),
    NavigationItem(Screen.SETTINGS, "게임 설정", "08"),
)

@Composable
fun SimulatorSidebar(
    selected: Screen,
    summary: SidebarSummary,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(196.dp)
            .fillMaxHeight()
            .background(MarketColors.Navy)
            .padding(horizontal = 14.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MarketColors.Celadon, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "40",
                    style = MarketType.number.copy(fontSize = 15.sp, fontWeight = FontWeight.Black),
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Market Ledger",
                    style = MarketType.heading.copy(fontSize = 15.sp),
                    color = Color.White,
                )
                Text(
                    "STOCK SIM 2040",
                    style = MarketType.label.copy(fontSize = 8.sp, letterSpacing = 0.8.sp),
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "투자 계정",
            style = MarketType.label.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
            color = Color.White.copy(alpha = 0.45f),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            formatMoney(summary.totalAssetsKrw, Currency.KRW, compact = true),
            style = MarketType.numberLarge.copy(fontSize = 19.sp),
            color = Color.White,
        )
        Text(
            text = "${formatPercent(summary.returnRate)} 누적",
            style = MarketType.label,
            color = if (summary.returnRate >= 0.0) MarketColors.Rise else MarketColors.Fall,
        )

        Spacer(Modifier.height(24.dp))
        navigationItems.forEach { item ->
            SidebarItem(
                item = item,
                selected = item.screen == selected ||
                    (selected == Screen.STOCK_DETAIL && item.screen == Screen.MARKET),
                badge = if (item.screen == Screen.EVENTS) summary.unreadEvents else 0,
                onClick = { onSelect(item.screen) },
            )
            Spacer(Modifier.height(3.dp))
        }

        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(4.dp))
                .padding(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "2026 세법 동결 시나리오",
                    style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.82f),
                )
                Text(
                    "법률 기준일 2026.08.07\n미래 시세는 게임 데이터입니다.",
                    style = MarketType.label.copy(fontSize = 9.sp, lineHeight = 14.sp),
                    color = Color.White.copy(alpha = 0.48f),
                )
            }
        }
    }
}

@Composable
private fun SidebarItem(
    item: NavigationItem,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        if (selected) Color.White.copy(alpha = 0.11f) else Color.Transparent,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.marker,
            style = MarketType.number.copy(fontSize = 9.sp),
            color = if (selected) MarketColors.CeladonSoft else Color.White.copy(alpha = 0.28f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            item.shortLabel,
            modifier = Modifier.weight(1f),
            style = MarketType.body.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) Color.White else Color.White.copy(alpha = 0.67f),
            maxLines = 1,
        )
        if (badge > 0) {
            Text(
                badge.coerceAtMost(99).toString(),
                style = MarketType.number.copy(fontSize = 9.sp),
                color = Color.White,
                modifier = Modifier
                    .background(MarketColors.Rise, RoundedCornerShape(2.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun SimulationClockRail(
    currentTime: Instant,
    turn: Long,
    progress: Float,
    selectedStep: TurnStep,
    koreanSession: MarketSession,
    usSession: MarketSession,
    canAdvance: Boolean,
    onStepSelected: (TurnStep) -> Unit,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp)
            .background(MarketColors.Paper)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(modifier = Modifier.width(170.dp)) {
            Text(
                "시뮬레이션 시각 · KST",
                style = MarketType.label.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
                color = MarketColors.InkMuted,
            )
            Text(
                formatDateTimeKst(currentTime),
                style = MarketType.numberLarge.copy(fontSize = 18.sp),
                color = MarketColors.Ink,
                maxLines = 1,
            )
            Text(
                "TURN ${turn.toString().padStart(6, '0')}",
                style = MarketType.number.copy(fontSize = 9.sp),
                color = MarketColors.Celadon,
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("2026.08", style = MarketType.label, color = MarketColors.InkMuted)
                Spacer(Modifier.weight(1f))
                Text("2040.12", style = MarketType.label, color = MarketColors.InkMuted)
            }
            TimeProgressTrack(progress.coerceIn(0f, 1f), Modifier.fillMaxWidth().height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatusLabel(
                    text = "KRX ${koreanSession.displayName}",
                    color = if (koreanSession.isTradable) MarketColors.Rise else MarketColors.InkMuted,
                )
                StatusLabel(
                    text = "US ${usSession.displayName}",
                    color = if (usSession.isTradable) MarketColors.Celadon else MarketColors.InkMuted,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TurnStep.entries.forEach { step ->
                    StepButton(step, step == selectedStep) { onStepSelected(step) }
                }
            }
            Button(
                onClick = onAdvance,
                enabled = canAdvance,
                modifier = Modifier.width(256.dp).height(30.dp),
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MarketColors.Navy,
                    contentColor = Color.White,
                    disabledContainerColor = MarketColors.PaperMuted,
                    disabledContentColor = MarketColors.InkMuted,
                ),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(
                    "${selectedStep.displayName} 진행  →",
                    style = MarketType.label.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun StepButton(step: TurnStep, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(24.dp)
            .background(
                if (selected) MarketColors.Celadon else MarketColors.PaperMuted,
                RoundedCornerShape(2.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            step.displayName,
            style = MarketType.label.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
            color = if (selected) Color.White else MarketColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun TimeProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val centerY = size.height / 2f
        drawRect(
            color = MarketColors.PaperMuted,
            topLeft = Offset(0f, centerY - 2f),
            size = Size(size.width, 4f),
        )
        drawRect(
            color = MarketColors.Celadon,
            topLeft = Offset(0f, centerY - 2f),
            size = Size(size.width * progress, 4f),
        )
        repeat(15) { year ->
            val x = size.width * year / 14f
            drawLine(
                color = if (x <= size.width * progress) MarketColors.Celadon else MarketColors.Line,
                start = Offset(x, centerY - 4f),
                end = Offset(x, centerY + 4f),
                strokeWidth = if (year % 5 == 0) 2f else 1f,
            )
        }
        drawCircle(
            color = MarketColors.Rise,
            radius = 4.5f,
            center = Offset(size.width * progress, centerY),
        )
    }
}
