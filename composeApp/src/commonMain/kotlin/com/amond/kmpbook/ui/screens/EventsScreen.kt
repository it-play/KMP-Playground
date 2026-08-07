package com.amond.kmpbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.NewsRelevance
import com.amond.kmpbook.domain.model.ScheduleBasis
import com.amond.kmpbook.domain.model.ScheduledEventOccurrence
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.directionFor
import com.amond.kmpbook.domain.model.relevanceTo
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.format.formatDateTimeKst
import com.amond.kmpbook.ui.format.formatPercent
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketComponentSize
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketSpacing
import com.amond.kmpbook.ui.theme.MarketType
import kotlin.time.Instant

@Composable
internal fun EventsScreen(
    currentTime: Instant,
    events: List<GameEvent>,
    modifier: Modifier = Modifier,
    upcomingEvents: List<ScheduledEventOccurrence> = emptyList(),
    stocks: List<StockDefinition> = emptyList(),
    holdingIds: Set<String> = emptySet(),
    watchlistIds: Set<String> = emptySet(),
    onOpenStock: (String) -> Unit = {},
    filterState: EventNewsFilterState,
    onFilterStateChange: (EventNewsFilterState) -> Unit,
) {
    val audienceFilter = filterState.audience
    val directionFilter = filterState.direction
    val scopeFilter = filterState.scope
    val sectorFilter = filterState.sector
    val selectedId = filterState.selectedEventId

    val entries = remember(events, stocks, holdingIds, watchlistIds) {
        val stocksById = stocks.associateBy(StockDefinition::id)
        events.map { event ->
            val relevance = event.relevanceTo(stocks, holdingIds, watchlistIds)
            val personalDirections = (relevance.heldStockIds + relevance.watchedStockIds)
                .mapNotNull(stocksById::get)
                .map(event::directionFor)
                .toSet()
            EventFeedEntry(event, relevance, collapseDirections(personalDirections, event.impact.direction))
        }
    }
    val filtered = entries
        .filter { audienceFilter.matches(it.relevance) }
        .filter { directionFilter.matches(it.personalDirection) }
        .filter { scopeFilter.matches(it.event) }
        .filter { entry -> sectorFilter?.let { it in entry.relevance.relatedSectors } ?: true }
        .sortedByDescending { it.event.startsAt }
    val availableSectors = entries
        .flatMap { it.relevance.relatedSectors }
        .distinct()
        .sortedBy(Sector::displayName)
    val selected = filtered.firstOrNull { it.event.id == selectedId } ?: filtered.firstOrNull()
    val active = entries.count { it.event.isActiveAt(currentTime) }
    val personal = entries.count { it.relevance.isPersonal }
    val adverse = entries.count {
        it.relevance.isPersonal && it.personalDirection == ImpactDirection.NEGATIVE
    }

    Column(
        modifier.fillMaxSize().padding(MarketSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.md),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MarketSpacing.md)) {
            EventMetric("영향 중", "${active}건", "현재 가격·변동성에 반영", MarketColors.Rise, Modifier.weight(1f))
            EventMetric("내 뉴스", "${personal}건", "보유 또는 관심 종목과 연결", MarketColors.Celadon, Modifier.weight(1f))
            EventMetric("내 악재", "${adverse}건", "보유·관심 종목 우선 확인", MarketColors.Amber, Modifier.weight(1f))
        }
        UpcomingSchedule(upcomingEvents)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(MarketSpacing.md)) {
            LedgerPanel(Modifier.width(456.dp).fillMaxSize(), padding = 0.dp) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(MarketSpacing.md)) {
                        SectionHeading("뉴스", eyebrow = "보유·관심·분야에 맞춘 캠페인 피드")
                        Spacer(Modifier.height(MarketSpacing.sm))
                        FilterStrip("대상", AudienceFilter.entries, audienceFilter, { it.displayName }) {
                            onFilterStateChange(filterState.copy(audience = it))
                        }
                        Spacer(Modifier.height(MarketSpacing.xs))
                        FilterStrip("판정", DirectionFilter.entries, directionFilter, { it.displayName }) {
                            onFilterStateChange(filterState.copy(direction = it))
                        }
                        Spacer(Modifier.height(MarketSpacing.xs))
                        FilterStrip("범위", ScopeFilter.entries, scopeFilter, { it.displayName }) {
                            onFilterStateChange(filterState.copy(scope = it))
                        }
                        if (availableSectors.isNotEmpty()) {
                            Spacer(Modifier.height(MarketSpacing.xs))
                            SectorFilterStrip(availableSectors, sectorFilter) {
                                onFilterStateChange(filterState.copy(sector = it))
                            }
                        }
                    }
                    LedgerDivider()
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("선택한 조건에 맞는 뉴스가 없습니다.", style = MarketType.body, color = MarketColors.InkMuted)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(filtered, key = { it.event.id }) { entry ->
                                EventTimelineRow(
                                    entry = entry,
                                    active = entry.event.isActiveAt(currentTime),
                                    selected = entry.event.id == selected?.event?.id,
                                    onClick = {
                                        onFilterStateChange(filterState.copy(selectedEventId = entry.event.id))
                                    },
                                )
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().background(MarketColors.PaperMuted).padding(MarketSpacing.sm)) {
                        Text(
                            "${filtered.size}건 표시 · 호재/악재는 상품 구조에 따라 반대로 작동할 수 있음",
                            style = MarketType.caption,
                            color = MarketColors.InkMuted,
                        )
                    }
                }
            }
            LedgerPanel(Modifier.weight(1f).fillMaxSize(), padding = MarketSpacing.lg) {
                if (selected == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("왼쪽에서 뉴스를 선택하세요.", style = MarketType.body, color = MarketColors.InkMuted)
                    }
                } else {
                    EventDetail(
                        entry = selected,
                        currentTime = currentTime,
                        stocks = stocks,
                        holdingIds = holdingIds,
                        watchlistIds = watchlistIds,
                        onOpenStock = onOpenStock,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectorFilterStrip(
    sectors: List<Sector>,
    selected: Sector?,
    onSelect: (Sector?) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("분야", modifier = Modifier.width(28.dp), style = MarketType.caption, color = MarketColors.InkMuted)
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs),
        ) {
            item { EventFilter("전체", selected == null) { onSelect(null) } }
            items(sectors, key = Sector::name) { sector ->
                EventFilter(sector.displayName, selected == sector) { onSelect(sector) }
            }
        }
    }
}

@Composable
private fun <T> FilterStrip(
    label: String,
    values: List<T>,
    selected: T,
    display: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
        Text(label, modifier = Modifier.width(28.dp), style = MarketType.caption, color = MarketColors.InkMuted)
        values.forEach { option ->
            EventFilter(display(option), selected == option) { onSelect(option) }
        }
    }
}

@Composable
private fun EventMetric(label: String, value: String, detail: String, color: Color, modifier: Modifier) {
    LedgerPanel(modifier.height(112.dp), padding = MarketSpacing.md) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(48.dp).background(color, RoundedCornerShape(MarketRadii.pill)))
            Spacer(Modifier.width(MarketSpacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs)) {
                Text(label, style = MarketType.label, color = MarketColors.InkMuted)
                Text(value, style = MarketType.numberLarge, color = MarketColors.Ink)
                Text(detail, style = MarketType.caption, color = MarketColors.InkMuted)
            }
        }
    }
}

@Composable
private fun UpcomingSchedule(events: List<ScheduledEventOccurrence>) {
    LedgerPanel(Modifier.fillMaxWidth().height(200.dp), padding = MarketSpacing.md) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading(
                title = "다음 발표 일정",
                eyebrow = "공식 일정과 장기 예상 일정 · 발표 수치는 게임 생성",
            )
            Spacer(Modifier.height(MarketSpacing.sm))
            if (events.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text("남은 정기 발표 일정이 없습니다.", style = MarketType.body, color = MarketColors.InkMuted)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MarketSpacing.sm)) {
                    items(events, key = { it.id }) { event -> UpcomingScheduleCard(event) }
                }
            }
        }
    }
}

@Composable
private fun UpcomingScheduleCard(event: ScheduledEventOccurrence) {
    Column(
        Modifier
            .width(292.dp)
            .height(104.dp)
            .background(MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.medium))
            .padding(MarketSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
            StatusLabel(
                event.scheduleBasis.displayName,
                if (event.scheduleBasis == ScheduleBasis.OFFICIAL) MarketColors.Positive else MarketColors.Amber,
                strong = event.scheduleBasis == ScheduleBasis.OFFICIAL,
            )
            StatusLabel(event.valueBasis.displayName, MarketColors.Primary)
        }
        Text(
            event.title,
            style = MarketType.body.copy(fontWeight = FontWeight.SemiBold),
            color = MarketColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildString {
                append(formatDateTimeKst(event.scheduledAt))
                event.referencePeriod?.let { append(" · $it") }
            },
            style = MarketType.caption,
            color = MarketColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EventFilter(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) MarketColors.Navy else MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.pill))
            .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = MarketSpacing.sm, vertical = MarketSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (selected) "✓ $text" else text, style = MarketType.caption, color = if (selected) Color.White else MarketColors.InkMuted)
    }
}

@Composable
private fun EventTimelineRow(
    entry: EventFeedEntry,
    active: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val event = entry.event
    val color = eventDirectionColor(entry.personalDirection)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) color.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = MarketSpacing.md, vertical = MarketSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(MarketSpacing.xs).background(MarketColors.Line))
            Box(Modifier.width(8.dp).height(8.dp).background(color, RoundedCornerShape(MarketRadii.pill)))
            Box(Modifier.width(2.dp).height(MarketSpacing.xxl).background(MarketColors.Line))
        }
        Spacer(Modifier.width(MarketSpacing.sm))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.title,
                    modifier = Modifier.weight(1f),
                    style = MarketType.body.copy(fontWeight = FontWeight.Medium),
                    color = MarketColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (active) StatusLabel(if (ScheduledEventOccurrence.isScheduledId(event.id)) "정기" else "영향 중", color)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
                if (entry.relevance.isHoldingRelated) StatusLabel("보유", MarketColors.Primary, strong = true)
                if (entry.relevance.isWatchlistRelated) StatusLabel("관심", MarketColors.Celadon, strong = true)
                if (entry.relevance.isSectorRelated) StatusLabel("분야", MarketColors.InkMuted)
                StatusLabel(entry.personalDirection.displayName, color)
            }
            Text(
                "${formatDateTimeKst(event.startsAt)} · ${event.type.displayName} · ${event.scope.displayName}",
                style = MarketType.caption,
                color = MarketColors.InkMuted,
            )
            Text(event.description, style = MarketType.label, color = MarketColors.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EventDetail(
    entry: EventFeedEntry,
    currentTime: Instant,
    stocks: List<StockDefinition>,
    holdingIds: Set<String>,
    watchlistIds: Set<String>,
    onOpenStock: (String) -> Unit,
) {
    val event = entry.event
    val color = eventDirectionColor(entry.personalDirection)
    val stocksById = stocks.associateBy(StockDefinition::id)
    val targetIds = linkedSetOf<String>().apply {
        addAll(entry.relevance.heldStockIds)
        addAll(entry.relevance.watchedStockIds)
        addAll(event.affectedStockIds)
    }
    val targets = targetIds.mapNotNull(stocksById::get)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusLabel(entry.personalDirection.displayName, color, strong = true)
            if (entry.relevance.isPersonal && entry.personalDirection != event.impact.direction) {
                Spacer(Modifier.width(6.dp))
                StatusLabel("원 뉴스 ${event.impact.direction.displayName}", eventDirectionColor(event.impact.direction))
            }
            Spacer(Modifier.width(6.dp))
            StatusLabel(event.severity.displayName, if (event.severity >= EventSeverity.MAJOR) MarketColors.Amber else MarketColors.InkMuted)
            if (entry.relevance.isHoldingRelated) {
                Spacer(Modifier.width(6.dp))
                StatusLabel("내 보유 연관", MarketColors.Primary, strong = true)
            }
            if (entry.relevance.isWatchlistRelated) {
                Spacer(Modifier.width(6.dp))
                StatusLabel("관심 연관", MarketColors.Celadon, strong = true)
            }
            Spacer(Modifier.weight(1f))
            Text(event.sourceLabel, style = MarketType.label, color = MarketColors.InkMuted)
        }
        Spacer(Modifier.height(MarketSpacing.lg))
        Text(event.title, style = MarketType.headingLarge, color = MarketColors.Ink)
        Spacer(Modifier.height(MarketSpacing.xs))
        Text(event.description, style = MarketType.body, color = MarketColors.InkMuted)
        Spacer(Modifier.height(MarketSpacing.lg))
        LedgerDivider()
        Spacer(Modifier.height(MarketSpacing.lg))
        Text("가격 엔진 영향", style = MarketType.heading, color = MarketColors.Ink)
        Spacer(Modifier.height(MarketSpacing.sm))
        Row(Modifier.fillMaxWidth()) {
            ImpactMetric("원 이벤트 충격", formatPercent(event.impact.shockReturn), eventDirectionColor(event.impact.direction), Modifier.weight(1f))
            ImpactMetric("시간 드리프트", formatPercent(event.impact.hourlyDrift), color, Modifier.weight(1f))
            ImpactMetric("변동성", "×${event.impact.volatilityMultiplier}", MarketColors.Amber, Modifier.weight(1f))
            ImpactMetric("거래량", "×${event.impact.volumeMultiplier}", MarketColors.Celadon, Modifier.weight(1f))
        }
        Spacer(Modifier.height(MarketSpacing.lg))
        Text("적용 범위", style = MarketType.heading, color = MarketColors.Ink)
        Spacer(Modifier.height(MarketSpacing.xs))
        Text(
            buildString {
                append(event.scope.displayName)
                if (event.affectedMarkets.isNotEmpty()) append(" · ${event.affectedMarkets.joinToString { it.displayName }}")
                if (event.affectedSectors.isNotEmpty()) append(" · ${event.affectedSectors.joinToString { it.displayName }}")
                if (event.affectedStockIds.isNotEmpty()) append(" · ${event.affectedStockIds.size}개 직접 대상")
            },
            style = MarketType.body,
            color = MarketColors.InkMuted,
        )
        Spacer(Modifier.height(MarketSpacing.sm))
        Text(
            "발표 ${formatDateTimeKst(event.startsAt)} · 영향 창 ${event.durationHours}시간 · " +
                if (event.isActiveAt(currentTime)) "정규장 기준 반영 중 또는 대기" else "영향 종료",
            style = MarketType.caption,
            color = if (event.isActiveAt(currentTime)) color else MarketColors.InkMuted,
        )

        if (targets.isNotEmpty()) {
            Spacer(Modifier.height(MarketSpacing.lg))
            Text("내 종목에 미치는 방향", style = MarketType.heading, color = MarketColors.Ink)
            Spacer(Modifier.height(MarketSpacing.xs))
            Text(
                "인버스·옵션·채권형은 같은 뉴스도 기초자산과 다른 방향 또는 강도로 반응합니다.",
                style = MarketType.caption,
                color = MarketColors.InkMuted,
            )
            Spacer(Modifier.height(MarketSpacing.sm))
            targets.forEach { stock ->
                InstrumentImpactRow(
                    stock = stock,
                    direction = event.directionFor(stock),
                    held = stock.id in holdingIds,
                    watched = stock.id in watchlistIds,
                    onClick = { onOpenStock(stock.id) },
                )
                Spacer(Modifier.height(MarketSpacing.xs))
            }
        }

        Spacer(Modifier.height(MarketSpacing.lg))
        Box(
            Modifier
                .fillMaxWidth()
                .background(MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.medium))
                .padding(MarketSpacing.md),
        ) {
            Text(
                if (ScheduledEventOccurrence.isScheduledId(event.id)) {
                    "발표 일정과 실제·예상 수치는 분리됩니다. '게임 수치'는 occurrence id와 시드로 만든 가상 값이며 투자 정보가 아닙니다."
                } else {
                    "이 뉴스는 규칙·조건·확률·쿨다운으로 생성된 게임 이벤트입니다. 실제 보도나 투자 정보가 아닙니다."
                },
                style = MarketType.label,
                color = MarketColors.InkMuted,
            )
        }
    }
}

@Composable
private fun InstrumentImpactRow(
    stock: StockDefinition,
    direction: ImpactDirection,
    held: Boolean,
    watched: Boolean,
    onClick: () -> Unit,
) {
    val color = eventDirectionColor(direction)
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.06f), RoundedCornerShape(MarketRadii.medium))
            .clickable(onClick = onClick)
            .padding(MarketSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
                Text(stock.name, style = MarketType.body.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
                if (held) StatusLabel("보유", MarketColors.Primary, strong = true)
                if (watched) StatusLabel("관심", MarketColors.Celadon, strong = true)
            }
            Text(
                "${stock.symbol} · ${stock.instrumentType.displayName} · ${stock.behavior.strategy.displayName} · ${stock.behavior.principalRisk.displayName}",
                style = MarketType.caption,
                color = MarketColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusLabel(direction.displayName, color, strong = true)
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

private data class EventFeedEntry(
    val event: GameEvent,
    val relevance: NewsRelevance,
    val personalDirection: ImpactDirection,
)

private fun collapseDirections(
    directions: Set<ImpactDirection>,
    fallback: ImpactDirection,
): ImpactDirection = when {
    directions.isEmpty() -> fallback
    directions.size == 1 -> directions.single()
    directions.all { it == ImpactDirection.NEUTRAL } -> ImpactDirection.NEUTRAL
    else -> ImpactDirection.MIXED
}

internal data class EventNewsFilterState(
    val audience: AudienceFilter = AudienceFilter.ALL,
    val direction: DirectionFilter = DirectionFilter.ALL,
    val scope: ScopeFilter = ScopeFilter.ALL,
    val sector: Sector? = null,
    val selectedEventId: String? = null,
)

internal enum class AudienceFilter(val displayName: String) {
    ALL("전체"),
    PERSONAL("내 뉴스"),
    HOLDING("보유"),
    WATCHLIST("관심"),
    SECTOR("분야"),
    ;

    fun matches(relevance: NewsRelevance): Boolean = when (this) {
        ALL -> true
        PERSONAL -> relevance.isPersonal
        HOLDING -> relevance.isHoldingRelated
        WATCHLIST -> relevance.isWatchlistRelated
        SECTOR -> relevance.isSectorRelated
    }
}

internal enum class DirectionFilter(val displayName: String) {
    ALL("전체"),
    POSITIVE("호재"),
    NEGATIVE("악재"),
    UPDATE("소식"),
    ;

    fun matches(direction: ImpactDirection): Boolean = when (this) {
        ALL -> true
        POSITIVE -> direction == ImpactDirection.POSITIVE
        NEGATIVE -> direction == ImpactDirection.NEGATIVE
        UPDATE -> direction in setOf(ImpactDirection.MIXED, ImpactDirection.NEUTRAL)
    }
}

internal enum class ScopeFilter(val displayName: String) {
    ALL("전체"),
    MACRO("거시"),
    MARKET("시장"),
    SECTOR("산업"),
    INSTRUMENT("종목"),
    ;

    fun matches(event: GameEvent): Boolean = when (this) {
        ALL -> true
        MACRO -> event.scope == EventScope.GLOBAL || event.scope == EventScope.COUNTRY
        MARKET -> event.scope == EventScope.MARKET
        SECTOR -> event.scope == EventScope.SECTOR
        INSTRUMENT -> event.scope == EventScope.STOCK
    }
}
