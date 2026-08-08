package com.amond.kmpbook.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.format.formatDateTimeEt
import com.amond.kmpbook.ui.format.formatDateTimeKst
import com.amond.kmpbook.ui.format.formatMoney
import com.amond.kmpbook.ui.format.formatPercent
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketComponentSize
import com.amond.kmpbook.ui.theme.MarketElevation
import com.amond.kmpbook.ui.theme.MarketLayout
import com.amond.kmpbook.ui.theme.MarketMotion
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketSpacing
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
    NavigationItem(Screen.HOME, "상황판", "홈"),
    NavigationItem(Screen.MARKET, "시장·종목", "시"),
    NavigationItem(Screen.ORDER, "주문·체결", "주"),
    NavigationItem(Screen.PORTFOLIO, "포트폴리오", "자"),
    NavigationItem(Screen.EVENTS, "뉴스·이벤트", "뉴"),
    NavigationItem(Screen.ANALYTICS, "투자 분석", "분"),
    NavigationItem(Screen.TAX_REPORT, "세금 센터", "세"),
    NavigationItem(Screen.SETTINGS, "게임 설정", "설"),
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
            .width(MarketLayout.sidebarWidth)
            .fillMaxHeight()
            .background(MarketColors.Navy)
            .padding(horizontal = MarketSpacing.sm, vertical = MarketSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MarketColors.Signal,
                        shape = RoundedCornerShape(MarketRadii.medium),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "M∴",
                    style = MarketType.label.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(MarketSpacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "Market Ledger",
                    style = MarketType.heading,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    "CAUSAL MARKET · 2040",
                    style = MarketType.caption.copy(letterSpacing = 0.35.sp),
                    color = MarketColors.Grey400,
                )
            }
        }

        Spacer(Modifier.height(MarketSpacing.lg))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MarketColors.NavyRaised,
            shape = RoundedCornerShape(MarketRadii.large),
        ) {
            Column(
                modifier = Modifier.padding(MarketSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
            ) {
                Text("투자 계정", style = MarketType.caption, color = MarketColors.Grey400)
                Text(
                    formatMoney(summary.totalAssetsKrw, Currency.KRW, compact = true),
                    style = MarketType.numberLarge.copy(fontSize = 21.sp, lineHeight = 29.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "누적 ${formatPercent(summary.returnRate)}",
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (summary.returnRate >= 0.0) MarketColors.Rise else MarketColors.Fall,
                )
            }
        }

        Spacer(Modifier.height(MarketSpacing.lg))
        Text("시뮬레이션", style = MarketType.caption, color = MarketColors.Grey400)
        Spacer(Modifier.height(MarketSpacing.xs))
        navigationItems.forEach { item ->
            SidebarItem(
                item = item,
                selected = item.screen == selected ||
                    (selected == Screen.STOCK_DETAIL && item.screen == Screen.MARKET),
                badge = if (item.screen == Screen.EVENTS) summary.unreadEvents else 0,
                onClick = { onSelect(item.screen) },
            )
            Spacer(Modifier.height(MarketSpacing.xxs))
        }

        Spacer(Modifier.weight(1f))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MarketColors.NavyRaised,
            shape = RoundedCornerShape(MarketRadii.medium),
        ) {
            Column(
                modifier = Modifier.padding(MarketSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
            ) {
                Text(
                    "2026 세법 동결 시나리오",
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    "법률 기준일 2026.08.07\n미래 시세는 게임 데이터입니다.",
                    style = MarketType.caption,
                    color = MarketColors.Grey400,
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
        targetValue = if (selected) MarketColors.Signal.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(MarketMotion.quick),
    )
    val markerBackground by animateColorAsState(
        targetValue = if (selected) MarketColors.Signal else MarketColors.NavyRaised,
        animationSpec = tween(MarketMotion.quick),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MarketComponentSize.minimumInteractiveTarget)
            .background(background, RoundedCornerShape(MarketRadii.medium))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MarketSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(markerBackground, RoundedCornerShape(MarketRadii.small)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                item.marker,
                style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) Color.White else MarketColors.Grey400,
            )
        }
        Spacer(Modifier.width(MarketSpacing.sm))
        Text(
            item.shortLabel,
            modifier = Modifier.weight(1f),
            style = MarketType.label.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) Color.White else MarketColors.Grey400,
            maxLines = 1,
        )
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .background(MarketColors.Rise, RoundedCornerShape(MarketRadii.pill))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badge.coerceAtMost(99).toString(),
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
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
    val finalUsSessionTail = GameCalendar.toGameLocalDateTime(currentTime).date >
        GameCalendar.CAMPAIGN_END_DATE
    Surface(
        modifier = modifier.fillMaxWidth().height(MarketLayout.marketPulseRailHeight),
        color = MarketColors.Paper,
        shadowElevation = MarketElevation.card,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MarketSpacing.md, vertical = MarketSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarketSpacing.md),
        ) {
            Column(
                modifier = Modifier.width(188.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(MarketColors.Signal, RoundedCornerShape(MarketRadii.pill)),
                    )
                    Text(
                        "MARKET PULSE · ${if (finalUsSessionTail) "ET" else "KST"}",
                        style = MarketType.caption.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp,
                        ),
                        color = MarketColors.InkMuted,
                    )
                }
                Text(
                    if (finalUsSessionTail) formatDateTimeEt(currentTime) else formatDateTimeKst(currentTime),
                    style = MarketType.numberLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
                    color = MarketColors.Ink,
                    maxLines = 1,
                )
                Text(
                    "TURN ${turn.toString().padStart(6, '0')}",
                    style = MarketType.caption.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MarketColors.PrimaryText,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MarketSpacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("시뮬레이션 여정", style = MarketType.caption, color = MarketColors.InkMuted)
                    Spacer(Modifier.weight(1f))
                    Text("2026.08", style = MarketType.caption, color = MarketColors.InkMuted)
                    Text("  —  ", style = MarketType.caption, color = MarketColors.Grey400)
                    Text("2040.12", style = MarketType.caption, color = MarketColors.InkMuted)
                }
                TimeProgressTrack(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
                    StatusLabel(
                        text = "KRX ${koreanSession.displayName}",
                        color = if (koreanSession.isTradable) MarketColors.Positive else MarketColors.InkMuted,
                    )
                    StatusLabel(
                        text = "US ${usSession.displayName}",
                        color = if (usSession.isTradable) MarketColors.Positive else MarketColors.InkMuted,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                TurnStep.entries.forEach { step ->
                    StepButton(step, step == selectedStep) { onStepSelected(step) }
                }
                MarketButton(
                    text = "${selectedStep.displayName} 진행  →",
                    onClick = onAdvance,
                    enabled = canAdvance,
                    modifier = Modifier.width(190.dp),
                )
            }
        }
    }
}

@Composable
private fun StepButton(step: TurnStep, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) MarketColors.PrimaryWeak else MarketColors.Grey100,
        animationSpec = tween(MarketMotion.quick),
    )
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(MarketComponentSize.minimumInteractiveTarget)
            .background(background, RoundedCornerShape(MarketRadii.small))
            .selectable(selected = selected, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (selected) "✓${step.displayName}" else step.displayName,
            style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) MarketColors.Primary else MarketColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun TimeProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val centerY = size.height / 2f
        val trackHeight = 5.dp.toPx()
        val radius = trackHeight / 2f
        drawRoundRect(
            color = MarketColors.Grey100,
            topLeft = Offset(0f, centerY - radius),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawRoundRect(
            color = MarketColors.Signal,
            topLeft = Offset(0f, centerY - radius),
            size = Size(size.width * progress, trackHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
        repeat(15) { year ->
            val x = size.width * year / 14f
            val tickHalfHeight = if (year % 5 == 0) 5.dp.toPx() else 3.dp.toPx()
            drawLine(
                color = if (x <= size.width * progress) {
                    Color.White.copy(alpha = 0.72f)
                } else {
                    MarketColors.Grey400.copy(alpha = 0.55f)
                },
                start = Offset(x, centerY - tickHalfHeight),
                end = Offset(x, centerY + tickHalfHeight),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawCircle(
            color = MarketColors.Paper,
            radius = 6.dp.toPx(),
            center = Offset(size.width * progress, centerY),
        )
        drawCircle(
            color = MarketColors.Signal,
            radius = 4.dp.toPx(),
            center = Offset(size.width * progress, centerY),
        )
    }
}
