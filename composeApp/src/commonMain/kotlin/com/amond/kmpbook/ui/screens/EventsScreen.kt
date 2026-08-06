package com.amond.kmpbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.format.formatDateTimeKst
import com.amond.kmpbook.ui.format.formatPercent
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketType
import kotlin.time.Instant

@Composable
fun EventsScreen(
    currentTime: Instant,
    events: List<GameEvent>,
    modifier: Modifier = Modifier,
) {
    var scope by remember { mutableStateOf<EventScope?>(null) }
    var selectedId by remember(events) { mutableStateOf(events.lastOrNull()?.id) }
    val filtered = events.filter { scope == null || it.scope == scope }.sortedByDescending { it.startsAt }
    val selected = events.firstOrNull { it.id == selectedId } ?: filtered.firstOrNull()
    val active = events.count { it.isActiveAt(currentTime) }
    val critical = events.count { it.severity == EventSeverity.CRITICAL }

    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EventMetric("활성 이벤트", "${active}건", "가격 충격·드리프트 적용 중", MarketColors.Rise, Modifier.weight(1f))
            EventMetric("누적 뉴스", "${events.size}건", "동일 규칙·시드로 재현 가능", MarketColors.Celadon, Modifier.weight(1f))
            EventMetric("심각 단계", "${critical}건", "서킷브레이커 조건 포함", MarketColors.Amber, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LedgerPanel(Modifier.width(420.dp).fillMaxSize(), padding = 0.dp) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(12.dp)) {
                        SectionHeading("뉴스 타임라인", eyebrow = "RULE-BASED EVENTS")
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            EventFilter("전체", scope == null) { scope = null }
                            EventFilter("거시", scope == EventScope.GLOBAL) { scope = EventScope.GLOBAL }
                            EventFilter("시장", scope == EventScope.MARKET) { scope = EventScope.MARKET }
                            EventFilter("산업", scope == EventScope.SECTOR) { scope = EventScope.SECTOR }
                            EventFilter("기업", scope == EventScope.STOCK) { scope = EventScope.STOCK }
                        }
                    }
                    LedgerDivider()
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("이 범주의 이벤트가 아직 없습니다.", style = MarketType.body, color = MarketColors.InkMuted)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(filtered, key = { it.id }) { event ->
                                EventTimelineRow(
                                    event = event,
                                    active = event.isActiveAt(currentTime),
                                    selected = event.id == selected?.id,
                                    onClick = { selectedId = event.id },
                                )
                            }
                        }
                    }
                }
            }
            LedgerPanel(Modifier.weight(1f).fillMaxSize(), padding = 18.dp) {
                if (selected == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("왼쪽에서 뉴스를 선택하세요.", style = MarketType.body, color = MarketColors.InkMuted)
                    }
                } else {
                    EventDetail(selected, currentTime)
                }
            }
        }
    }
}

@Composable
private fun EventMetric(label: String, value: String, detail: String, color: Color, modifier: Modifier) {
    LedgerPanel(modifier.height(86.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(42.dp).background(color, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(11.dp))
            Column {
                Text(label, style = MarketType.label, color = MarketColors.InkMuted)
                Text(value, style = MarketType.numberLarge.copy(fontSize = 18.sp), color = MarketColors.Ink)
                Text(detail, style = MarketType.label.copy(fontSize = 9.sp), color = MarketColors.InkMuted)
            }
        }
    }
}

@Composable
private fun EventFilter(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) MarketColors.Navy else MarketColors.PaperMuted, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(text, style = MarketType.label.copy(fontSize = 9.sp), color = if (selected) Color.White else MarketColors.InkMuted)
    }
}

@Composable
private fun EventTimelineRow(event: GameEvent, active: Boolean, selected: Boolean, onClick: () -> Unit) {
    val color = eventDirectionColor(event.impact.direction)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) color.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(7.dp).background(MarketColors.Line))
            Box(Modifier.width(8.dp).height(8.dp).background(color, RoundedCornerShape(50)))
            Box(Modifier.width(2.dp).height(31.dp).background(MarketColors.Line))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.title,
                    modifier = Modifier.weight(1f),
                    style = MarketType.body.copy(fontWeight = FontWeight.Medium),
                    color = MarketColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (active) StatusLabel("영향 중", color)
            }
            Text(
                "${formatDateTimeKst(event.startsAt)} · ${event.type.displayName} · ${event.scope.displayName}",
                style = MarketType.label.copy(fontSize = 9.sp),
                color = MarketColors.InkMuted,
            )
            Text(event.description, style = MarketType.label, color = MarketColors.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EventDetail(event: GameEvent, currentTime: Instant) {
    val color = eventDirectionColor(event.impact.direction)
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusLabel(event.impact.direction.displayName, color, strong = true)
            Spacer(Modifier.width(6.dp))
            StatusLabel(event.severity.displayName, if (event.severity >= EventSeverity.MAJOR) MarketColors.Amber else MarketColors.InkMuted)
            Spacer(Modifier.weight(1f))
            Text(event.sourceLabel, style = MarketType.label, color = MarketColors.InkMuted)
        }
        Spacer(Modifier.height(18.dp))
        Text(event.title, style = MarketType.display.copy(fontSize = 30.sp, lineHeight = 38.sp), color = MarketColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text(event.description, style = MarketType.body.copy(fontSize = 14.sp, lineHeight = 22.sp), color = MarketColors.InkMuted)
        Spacer(Modifier.height(20.dp))
        LedgerDivider()
        Spacer(Modifier.height(18.dp))
        Text("가격 엔진 영향", style = MarketType.heading, color = MarketColors.Ink)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            ImpactMetric("즉시 충격", formatPercent(event.impact.shockReturn), color, Modifier.weight(1f))
            ImpactMetric("시간 드리프트", formatPercent(event.impact.hourlyDrift), color, Modifier.weight(1f))
            ImpactMetric("변동성", "×${event.impact.volatilityMultiplier}", MarketColors.Amber, Modifier.weight(1f))
            ImpactMetric("거래량", "×${event.impact.volumeMultiplier}", MarketColors.Celadon, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Text("적용 범위", style = MarketType.heading, color = MarketColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append(event.scope.displayName)
                if (event.affectedMarkets.isNotEmpty()) append(" · ${event.affectedMarkets.joinToString { it.displayName }}")
                if (event.affectedSectors.isNotEmpty()) append(" · ${event.affectedSectors.joinToString { it.displayName }}")
                if (event.affectedStockIds.isNotEmpty()) append(" · ${event.affectedStockIds.size}개 종목")
            },
            style = MarketType.body,
            color = MarketColors.InkMuted,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "${formatDateTimeKst(event.startsAt)}부터 ${event.durationHours}시간 · " +
                if (event.isActiveAt(currentTime)) "현재 가격 모델에 반영 중" else "영향 종료",
            style = MarketType.number.copy(fontSize = 11.sp),
            color = if (event.isActiveAt(currentTime)) color else MarketColors.InkMuted,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth().background(MarketColors.PaperMuted, RoundedCornerShape(3.dp)).padding(12.dp),
        ) {
            Text(
                "이 뉴스는 규칙·조건·확률·쿨다운으로 생성된 게임 이벤트입니다. 실제 보도나 투자 정보가 아닙니다.",
                style = MarketType.label,
                color = MarketColors.InkMuted,
            )
        }
    }
}

@Composable
private fun ImpactMetric(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MarketType.label, color = MarketColors.InkMuted)
        Text(value, style = MarketType.number, color = color)
    }
}

private fun eventDirectionColor(direction: ImpactDirection): Color = when (direction) {
    ImpactDirection.POSITIVE -> MarketColors.Rise
    ImpactDirection.NEGATIVE -> MarketColors.Fall
    ImpactDirection.MIXED -> MarketColors.Amber
    ImpactDirection.NEUTRAL -> MarketColors.InkMuted
}
