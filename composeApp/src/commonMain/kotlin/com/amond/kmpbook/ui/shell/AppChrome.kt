package com.amond.kmpbook.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.game.Screen
import com.amond.kmpbook.domain.model.game.TurnStep
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.venue.MarketSession
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.components.StatusLabel
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
import com.composables.icons.lucide.ChartCandlestick
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.ChartPie
import com.composables.icons.lucide.ClipboardList
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Newspaper
import com.composables.icons.lucide.ReceiptText
import com.composables.icons.lucide.SlidersHorizontal
import kotlin.time.Instant

private val navigationItems = listOf(
    NavigationItem(Screen.HOME, "상황판", Lucide.LayoutDashboard),
    NavigationItem(Screen.MARKET, "시장·종목", Lucide.ChartCandlestick),
    NavigationItem(Screen.ORDER, "주문·체결", Lucide.ClipboardList),
    NavigationItem(Screen.PORTFOLIO, "포트폴리오", Lucide.ChartPie),
    NavigationItem(Screen.EVENTS, "뉴스·이벤트", Lucide.Newspaper),
    NavigationItem(Screen.ANALYTICS, "투자 분석", Lucide.ChartNoAxesCombined),
    NavigationItem(Screen.TAX_REPORT, "세금 센터", Lucide.ReceiptText),
    NavigationItem(Screen.SETTINGS, "설정", Lucide.SlidersHorizontal),
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
            Text(
                "Market Ledger",
                style = MarketType.heading,
                color = Color.White,
                maxLines = 1,
            )
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
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) Color.White else MarketColors.Grey400,
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
                        "MARKET PULSE",
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
                    "TURN $turn",
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
                    Text("진행", style = MarketType.caption, color = MarketColors.InkMuted)
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
                        color = koreanSession.statusColor(),
                    )
                    StatusLabel(
                        text = "US ${usSession.displayName}",
                        color = usSession.statusColor(),
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
                    text = "턴 진행",
                    onClick = onAdvance,
                    enabled = canAdvance,
                    modifier = Modifier.width(148.dp),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StepButton(step: TurnStep, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) MarketColors.PrimaryWeak else MarketColors.Grey100,
        animationSpec = tween(MarketMotion.quick),
    )
    val level = TurnStep.entries.indexOf(step) + 1
    val tooltipText = "${level}단계 (${step.hours}배속) · ${step.displayName} 진행"
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(
                containerColor = MarketColors.NavyRaised,
                contentColor = Color.White,
            ) {
                Text(tooltipText, style = MarketType.caption)
            }
        },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(MarketComponentSize.minimumInteractiveTarget)
                .background(background, RoundedCornerShape(MarketRadii.small))
                .border(
                    width = 1.dp,
                    color = if (selected) MarketColors.Primary.copy(alpha = 0.34f) else Color.Transparent,
                    shape = RoundedCornerShape(MarketRadii.small),
                )
                .semantics {
                    contentDescription = "$tooltipText${if (selected) ", 선택됨" else ""}"
                }
                .selectable(selected = selected, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            TurnStepMarker(
                step = step,
                color = if (selected) MarketColors.Primary else MarketColors.InkMuted,
            )
        }
    }
}

private fun MarketSession.statusColor(): Color = when (this) {
    MarketSession.CLOSED -> MarketColors.InkMuted
    MarketSession.PRE_MARKET -> MarketColors.AmberText
    MarketSession.REGULAR -> MarketColors.Positive
    MarketSession.AFTER_HOURS -> MarketColors.PrimaryText
}

@Composable
private fun TurnStepMarker(step: TurnStep, color: Color) {
    val (arrowCount, hasBoundary) = when (step) {
        TurnStep.ONE_HOUR -> 1 to false
        TurnStep.FOUR_HOURS -> 2 to false
        TurnStep.TWELVE_HOURS -> 3 to false
        TurnStep.ONE_DAY -> 1 to true
        TurnStep.ONE_WEEK -> 2 to true
    }
    Canvas(Modifier.size(width = 28.dp, height = 18.dp)) {
        val arrowWidth = 5.dp.toPx()
        val gap = 2.5.dp.toPx()
        val stroke = 2.dp.toPx()
        val groupWidth = arrowCount * arrowWidth + (arrowCount - 1) * gap
        val boundaryGap = if (hasBoundary) 3.dp.toPx() else 0f
        val boundaryWidth = if (hasBoundary) stroke else 0f
        val startX = (size.width - groupWidth - boundaryGap - boundaryWidth) / 2f
        val top = 3.dp.toPx()
        val bottom = size.height - top
        val centerY = size.height / 2f

        repeat(arrowCount) { index ->
            val x = startX + index * (arrowWidth + gap)
            drawLine(
                color = color,
                start = Offset(x, top),
                end = Offset(x + arrowWidth, centerY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(x + arrowWidth, centerY),
                end = Offset(x, bottom),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        if (hasBoundary) {
            val boundaryX = startX + groupWidth + boundaryGap
            drawLine(
                color = color,
                start = Offset(boundaryX, top),
                end = Offset(boundaryX, bottom),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
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
