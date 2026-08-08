package com.amond.kmpbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.EventRecordKind
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.IndustrySegment
import com.amond.kmpbook.domain.model.ScheduleBasis
import com.amond.kmpbook.domain.model.ScheduledEventOccurrence
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.presentation.NewsEffectState
import com.amond.kmpbook.presentation.NewsImpactPathUi
import com.amond.kmpbook.presentation.NewsInstrumentTerminationUi
import com.amond.kmpbook.presentation.NewsSectorGroupUi
import com.amond.kmpbook.presentation.NewsStockGroupUi
import com.amond.kmpbook.presentation.NewsStoryUi
import com.amond.kmpbook.presentation.NewsUiProjection
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.format.formatDateTimeKst
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketComponentSize
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketSpacing
import com.amond.kmpbook.ui.theme.MarketType

/**
 * 기사 자체와 규칙 엔진의 계수를 분리한 뉴스 브리핑 화면이다.
 * 핵심 작업은 사건을 종목·산업별 영향 경로로 읽는 것이며, 수치는 이미 발표된 사실만 보인다.
 */
@Composable
internal fun EventsScreen(
    projection: NewsUiProjection,
    modifier: Modifier = Modifier,
    upcomingEvents: List<ScheduledEventOccurrence> = emptyList(),
    onOpenStock: (String) -> Unit = {},
    onEventViewed: (String) -> Unit = {},
    filterState: EventNewsFilterState,
    onFilterStateChange: (EventNewsFilterState) -> Unit,
) {
    val groups = groupsFor(filterState.tab, projection)
    val activeGroup = filterState.groupKey
        ?.let { key -> groups.firstOrNull { it.key == key } }
        ?: groups.firstOrNull()
    val activeGroupKey = activeGroup?.key
    val visibleStories = projection.stories
        .asSequence()
        .filter { filterState.tab.matches(it) }
        .filter { story -> activeGroup?.matches?.invoke(story) ?: true }
        .toList()
    val selected = visibleStories.firstOrNull { it.event.id == filterState.selectedEventId }
        ?: visibleStories.firstOrNull()

    LaunchedEffect(selected?.event?.id) {
        selected?.event?.id?.let(onEventViewed)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(MarketSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.md),
    ) {
        NewsHeader(
            selectedTab = filterState.tab,
            marketEffectActiveCount = projection.marketEffectActiveCount,
            operationalActiveCount = projection.operationalActiveCount,
            personalCount = projection.personalCount,
            upcomingCount = upcomingEvents.size,
            onSelectTab = { tab ->
                onFilterStateChange(
                    filterState.copy(tab = tab, groupKey = null, selectedEventId = null),
                )
            },
        )
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val showRail = maxWidth >= 1_120.dp
            if (showRail) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(MarketSpacing.md),
                ) {
                    NewsGroupRail(
                        groups = groups,
                        selectedKey = activeGroupKey,
                        onSelect = { key ->
                            onFilterStateChange(filterState.copy(groupKey = key, selectedEventId = null))
                        },
                        modifier = Modifier.width(188.dp).fillMaxHeight(),
                    )
                    NewsListPanel(
                        stories = visibleStories,
                        selectedId = selected?.event?.id,
                        upcomingEvents = upcomingEvents,
                        showUpcoming = filterState.tab == NewsBrowseTab.SCHEDULES,
                        onSelect = { id -> onFilterStateChange(filterState.copy(selectedEventId = id)) },
                        modifier = Modifier.width(372.dp).fillMaxHeight(),
                    )
                    NewsDetailPanel(
                        story = selected,
                        onOpenStock = onOpenStock,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
                ) {
                    CompactGroupRail(
                        groups = groups,
                        selectedKey = activeGroupKey,
                        onSelect = { key ->
                            onFilterStateChange(filterState.copy(groupKey = key, selectedEventId = null))
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(MarketSpacing.md),
                    ) {
                        NewsListPanel(
                            stories = visibleStories,
                            selectedId = selected?.event?.id,
                            upcomingEvents = upcomingEvents,
                            showUpcoming = filterState.tab == NewsBrowseTab.SCHEDULES,
                            onSelect = { id -> onFilterStateChange(filterState.copy(selectedEventId = id)) },
                            modifier = Modifier.weight(0.78f).fillMaxHeight(),
                        )
                        NewsDetailPanel(
                            story = selected,
                            onOpenStock = onOpenStock,
                            modifier = Modifier.weight(1.22f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsHeader(
    selectedTab: NewsBrowseTab,
    marketEffectActiveCount: Int,
    operationalActiveCount: Int,
    personalCount: Int,
    upcomingCount: Int,
    onSelectTab: (NewsBrowseTab) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs)) {
                Text("뉴스 브리핑", style = MarketType.headingLarge, color = MarketColors.Ink)
                Text(
                    "시장 변화를 종목과 산업의 연결로 읽어요",
                    style = MarketType.body,
                    color = MarketColors.InkMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
                BriefingCount("시장 반영", marketEffectActiveCount, MarketColors.Amber)
                BriefingCount("조치·절차", operationalActiveCount, MarketColors.Primary)
                BriefingCount("내 종목", personalCount, MarketColors.Primary)
                BriefingCount("다음 일정", upcomingCount, MarketColors.Positive)
            }
        }
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs),
        ) {
            NewsBrowseTab.entries.forEach { tab ->
                NewsTab(
                    text = tab.displayName,
                    selected = tab == selectedTab,
                    onClick = { onSelectTab(tab) },
                )
            }
        }
    }
}

@Composable
private fun BriefingCount(label: String, count: Int, color: Color) {
    Row(
        Modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(MarketRadii.pill))
            .padding(horizontal = MarketSpacing.sm, vertical = MarketSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MarketType.caption, color = MarketColors.InkMuted)
        Text("${count}건", style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

@Composable
private fun NewsTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
            .background(
                if (selected) MarketColors.Navy else MarketColors.Paper,
                RoundedCornerShape(MarketRadii.medium),
            )
            .border(
                1.dp,
                if (selected) MarketColors.Navy else MarketColors.Line,
                RoundedCornerShape(MarketRadii.medium),
            )
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = MarketSpacing.md, vertical = MarketSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Color.White else MarketColors.InkMuted,
        )
    }
}

@Composable
private fun NewsGroupRail(
    groups: List<NewsGroupItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LedgerPanel(modifier, padding = MarketSpacing.sm) {
        Column(Modifier.fillMaxSize()) {
            Text("묶어보기", style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
            Spacer(Modifier.height(MarketSpacing.xs))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
            ) {
                items(groups, key = NewsGroupItem::key) { group ->
                    NewsGroupRow(group, selected = group.key == selectedKey) { onSelect(group.key) }
                }
            }
        }
    }
}

@Composable
private fun CompactGroupRail(
    groups: List<NewsGroupItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
        items(groups, key = NewsGroupItem::key) { group ->
            NewsTab(
                text = "${group.label} ${group.count}",
                selected = group.key == selectedKey,
                onClick = { onSelect(group.key) },
            )
        }
    }
}

@Composable
private fun NewsGroupRow(group: NewsGroupItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
            .background(
                if (selected) MarketColors.PrimaryWeak else Color.Transparent,
                RoundedCornerShape(MarketRadii.small),
            )
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = MarketSpacing.sm, vertical = MarketSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                group.label,
                style = MarketType.label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                color = if (selected) MarketColors.Primary else MarketColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            group.detail?.let {
                Text(it, style = MarketType.caption, color = MarketColors.InkMuted, maxLines = 1)
            }
        }
        Text(group.count.toString(), style = MarketType.number, color = MarketColors.InkMuted)
    }
}

@Composable
private fun NewsListPanel(
    stories: List<NewsStoryUi>,
    selectedId: String?,
    upcomingEvents: List<ScheduledEventOccurrence>,
    showUpcoming: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LedgerPanel(modifier, padding = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(MarketSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("브리핑", style = MarketType.heading, color = MarketColors.Ink)
                    Text("${stories.size}건을 연결 순서로 정리했어요", style = MarketType.caption, color = MarketColors.InkMuted)
                }
            }
            if (showUpcoming && upcomingEvents.isNotEmpty()) {
                UpcomingNewsStrip(upcomingEvents.take(4))
            }
            LedgerDivider()
            if (stories.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    EmptyNewsState(
                        if (showUpcoming) "아직 발표된 일정·공시가 없어요" else "이 묶음에 해당하는 뉴스가 없어요",
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(stories, key = { it.event.id }) { story ->
                        NewsStoryRow(
                            story = story,
                            selected = story.event.id == selectedId,
                            onClick = { onSelect(story.event.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingNewsStrip(events: List<ScheduledEventOccurrence>) {
    Column(
        Modifier.fillMaxWidth().background(MarketColors.Grey50).padding(MarketSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.xs),
    ) {
        Text("다음 발표", style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
            items(events, key = ScheduledEventOccurrence::id) { event ->
                Column(
                    Modifier
                        .width(210.dp)
                        .background(MarketColors.Paper, RoundedCornerShape(MarketRadii.small))
                        .border(1.dp, MarketColors.Line, RoundedCornerShape(MarketRadii.small))
                        .padding(MarketSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
                ) {
                    Text(
                        if (event.scheduleBasis == ScheduleBasis.OFFICIAL) "공식 일정" else "예상 일정",
                        style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (event.scheduleBasis == ScheduleBasis.OFFICIAL) MarketColors.Positive else MarketColors.Amber,
                    )
                    Text(event.title, style = MarketType.label, color = MarketColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatDateTimeKst(event.scheduledAt), style = MarketType.caption, color = MarketColors.InkMuted)
                }
            }
        }
    }
}

@Composable
private fun EmptyNewsState(message: String) {
    Box(Modifier.fillMaxSize().padding(MarketSpacing.xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
            Text("아직 조용해요", style = MarketType.heading, color = MarketColors.Ink)
            Text(message, style = MarketType.body, color = MarketColors.InkMuted)
        }
    }
}

@Composable
private fun NewsStoryRow(story: NewsStoryUi, selected: Boolean, onClick: () -> Unit) {
    val accent = statusColor(story.status.state)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) accent.copy(alpha = 0.07f) else Color.Transparent)
            .semantics {
                this.selected = selected
                stateDescription = buildString {
                    append(story.status.label)
                    story.secondaryStatus?.let { append(", ${it.label}") }
                    append(", ${directionLabel(story.personalDirection)}")
                }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MarketSpacing.md, vertical = MarketSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
            NewsEffectBadge(story.status.label, story.status.state)
            Text(story.event.type.displayName, style = MarketType.caption, color = MarketColors.InkMuted)
            Spacer(Modifier.weight(1f))
            Text(formatDateTimeKst(story.event.startsAt), style = MarketType.caption, color = MarketColors.InkMuted)
        }
        story.secondaryStatus?.let { secondary ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                NewsEffectBadge(secondary.label, secondary.state)
            }
        }
        Text(
            story.event.title,
            style = MarketType.body.copy(fontWeight = FontWeight.SemiBold),
            color = MarketColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            story.event.description,
            style = MarketType.label,
            color = MarketColors.InkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (story.impactPaths.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
                story.impactPaths.take(2).forEach { path ->
                    DirectionTag(path.label, path.direction)
                }
                if (story.impactPaths.size > 2) {
                    Text("외 ${story.impactPaths.size - 2}", style = MarketType.caption, color = MarketColors.InkMuted)
                }
            }
        }
        LedgerDivider()
    }
}

@Composable
private fun NewsDetailPanel(
    story: NewsStoryUi?,
    onOpenStock: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LedgerPanel(modifier, padding = 0.dp) {
        if (story == null) {
            EmptyNewsState("왼쪽에서 읽을 뉴스를 선택해 주세요")
            return@LedgerPanel
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MarketSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MarketSpacing.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
                    NewsEffectBadge(story.status.label, story.status.state, strong = true)
                    story.secondaryStatus?.let { secondary ->
                        NewsEffectBadge(secondary.label, secondary.state)
                    }
                    StatusLabel(story.event.type.displayName, MarketColors.InkMuted)
                    if (story.relevance.isHoldingRelated) StatusLabel("보유 종목 연결", MarketColors.Primary, strong = true)
                }
                Text(story.event.title, style = MarketType.headingLarge, color = MarketColors.Ink)
                Text(story.event.description, style = MarketType.body, color = MarketColors.InkMuted)
                Text(
                    "${formatDateTimeKst(story.event.startsAt)} · ${story.status.summary}",
                    style = MarketType.caption,
                    color = statusColor(story.status.state),
                )
                story.secondaryStatus?.let { secondary ->
                    Text(
                        secondary.summary,
                        style = MarketType.caption,
                        color = statusColor(secondary.state),
                    )
                }
            }

            if (story.event.reportedFacts.isNotEmpty()) {
                LedgerDivider()
                ReportedFactsSection(story)
            }

            story.instrumentTermination?.let { terms ->
                LedgerDivider()
                ProductTerminationTermsSection(terms)
            }

            LedgerDivider()
            Column(verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm)) {
                Text("이 뉴스가 연결되는 곳", style = MarketType.heading, color = MarketColors.Ink)
                Text(
                    "같은 사건도 비용 구조와 수익 모델에 따라 방향이 달라질 수 있어요.",
                    style = MarketType.label,
                    color = MarketColors.InkMuted,
                )
                ImpactAnalysisMap(story.event.title, story.impactPaths)
            }

            if (story.relatedStocks.isNotEmpty()) {
                LedgerDivider()
                Column(verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm)) {
                    Text("연결된 종목", style = MarketType.heading, color = MarketColors.Ink)
                    story.relatedStocks.take(RELATED_STOCK_DETAIL_LIMIT).forEach { stock ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MarketColors.Grey50, RoundedCornerShape(MarketRadii.medium))
                                .clickable(role = Role.Button) { onOpenStock(stock.stockId) }
                                .padding(MarketSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
                                    Text(stock.name, style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
                                    if (stock.held) Text("보유", style = MarketType.caption, color = MarketColors.Primary)
                                    if (stock.watched) Text("관심", style = MarketType.caption, color = MarketColors.Primary)
                                }
                                Text(
                                    "${stock.symbol} · ${stock.reason}",
                                    style = MarketType.caption,
                                    color = MarketColors.InkMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            DirectionBadge(stock.direction)
                        }
                    }
                    if (story.relatedStocks.size > RELATED_STOCK_DETAIL_LIMIT) {
                        Text(
                            "외 ${story.relatedStocks.size - RELATED_STOCK_DETAIL_LIMIT}개 종목은 종목 탭에서 이어서 볼 수 있어요.",
                            style = MarketType.caption,
                            color = MarketColors.InkMuted,
                        )
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MarketColors.Grey50, RoundedCornerShape(MarketRadii.medium))
                    .padding(MarketSpacing.md),
            ) {
                Text(
                    "영향 분석은 캠페인 규칙이 사건의 전달 경로를 설명한 것이며, 확정 수익률이나 목표가격을 예측하지 않아요.",
                    style = MarketType.label,
                    color = MarketColors.InkMuted,
                )
            }
        }
    }
}

@Composable
private fun ProductTerminationTermsSection(terms: NewsInstrumentTerminationUi) {
    Column(verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs)) {
                Text("상품 종료 조건", style = MarketType.heading, color = MarketColors.Ink)
                Text(
                    "공시에 고정된 일정과 평가 방식을 상장 원장 진행 상태와 함께 보여줘요.",
                    style = MarketType.label,
                    color = MarketColors.InkMuted,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
            ) {
                StatusLabel(terms.status.label, statusColor(terms.status.state), strong = true)
                StatusLabel(terms.kindLabel, MarketColors.NavyRaised)
            }
        }
        ProductTermRow(
            label = terms.scheduleLabel,
            value = terms.scheduleValue,
            description = "이 날짜 또는 시각보다 앞서 종료 효력이 발생하지 않아요.",
        )
        ProductTermRow(
            label = "평가 방식",
            value = terms.valuationLabel,
            description = terms.valuationDescription,
        )
        terms.settlementValue?.let { settlementDate ->
            ProductTermRow(
                label = "청산금 지급 예정일",
                value = settlementDate,
                description = "상장 원장에 확정된 지급 일정이에요.",
            )
        }
    }
}

@Composable
private fun ProductTermRow(
    label: String,
    value: String,
    description: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MarketColors.Grey50, RoundedCornerShape(MarketRadii.medium))
            .padding(MarketSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
    ) {
        Text(label, style = MarketType.caption, color = MarketColors.InkMuted)
        Text(value, style = MarketType.body.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
        Text(description, style = MarketType.label, color = MarketColors.InkMuted)
    }
}

@Composable
private fun ReportedFactsSection(story: NewsStoryUi) {
    Column(verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm)) {
        Text("발표된 수치", style = MarketType.heading, color = MarketColors.Ink)
        story.event.reportedFacts.chunked(2).forEach { rowFacts ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MarketSpacing.sm)) {
                rowFacts.forEach { fact ->
                    Column(
                        Modifier
                            .weight(1f)
                            .background(MarketColors.Grey50, RoundedCornerShape(MarketRadii.medium))
                            .padding(MarketSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
                    ) {
                        Text(fact.label, style = MarketType.label, color = MarketColors.InkMuted)
                        Text(fact.actual, style = MarketType.number, color = MarketColors.Ink)
                        fact.comparison?.let {
                            Text(it, style = MarketType.caption, color = MarketColors.Primary)
                        }
                    }
                }
                if (rowFacts.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ImpactAnalysisMap(headline: String, paths: List<NewsImpactPathUi>) {
    if (paths.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().background(MarketColors.Grey50, RoundedCornerShape(MarketRadii.medium)).padding(MarketSpacing.md),
        ) {
            Text("직접 연결된 종목·산업 분석이 없는 단순 시장 소식이에요.", style = MarketType.body, color = MarketColors.InkMuted)
        }
        return
    }

    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .width(154.dp)
                .background(MarketColors.PrimaryWeak, RoundedCornerShape(MarketRadii.medium))
                .padding(MarketSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
        ) {
            Text("핵심 변화", style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Primary)
            Text(
                headline,
                style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.Ink,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.width(MarketSpacing.lg).height(1.dp).background(MarketColors.Line))
        Box(Modifier.width(1.dp).fillMaxHeight().background(MarketColors.Line))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm)) {
            paths.take(6).forEach { path ->
                ImpactTargetNode(path)
            }
            if (paths.size > 6) {
                Text("연결 ${paths.size - 6}건 더 있음", style = MarketType.caption, color = MarketColors.InkMuted)
            }
        }
    }
}

@Composable
private fun ImpactTargetNode(path: NewsImpactPathUi) {
    val color = directionColor(path.direction)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(22.dp).height(1.dp).background(MarketColors.Line))
        Column(
            Modifier
                .weight(1f)
                .background(color.copy(alpha = 0.075f), RoundedCornerShape(MarketRadii.medium))
                .border(1.dp, color.copy(alpha = 0.12f), RoundedCornerShape(MarketRadii.medium))
                .padding(MarketSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MarketSpacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xs)) {
                Column(Modifier.weight(1f)) {
                    Text(path.categoryLabel, style = MarketType.caption, color = MarketColors.InkMuted)
                    Text(path.label, style = MarketType.body.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
                }
                DirectionBadge(path.direction)
            }
            Text(path.reason, style = MarketType.label, color = MarketColors.InkMuted)
            if (path.horizonLabel.isNotBlank()) {
                Text(path.horizonLabel, style = MarketType.caption, color = color)
            }
        }
    }
}

@Composable
private fun NewsEffectBadge(label: String, state: NewsEffectState, strong: Boolean = false) {
    StatusLabel(label, statusColor(state), strong = strong)
}

@Composable
private fun DirectionBadge(direction: ImpactDirection) {
    StatusLabel(directionLabel(direction), directionColor(direction), strong = true)
}

@Composable
private fun DirectionTag(label: String, direction: ImpactDirection) {
    val color = directionColor(direction)
    Text(
        "$label · ${directionLabel(direction)}",
        modifier = Modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(MarketRadii.pill))
            .padding(horizontal = MarketSpacing.xs, vertical = MarketSpacing.xxs),
        style = MarketType.caption.copy(fontWeight = FontWeight.Medium),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun statusColor(state: NewsEffectState): Color = when (state) {
    NewsEffectState.UPCOMING,
    NewsEffectState.WAITING_FOR_MARKET,
    NewsEffectState.PROCESS_ACTIVE,
    -> MarketColors.PrimaryText
    NewsEffectState.MARKET_ACTIVE -> MarketColors.AmberText
    NewsEffectState.RESTRICTION_ACTIVE -> MarketColors.NavyRaised
    NewsEffectState.MARKET_ENDED,
    NewsEffectState.RESOLVED,
    -> MarketColors.InkMuted
    NewsEffectState.INFORMATION -> MarketColors.InkMuted
}

private fun directionColor(direction: ImpactDirection): Color = when (direction) {
    ImpactDirection.POSITIVE -> MarketColors.RiseText
    ImpactDirection.NEGATIVE -> MarketColors.FallText
    ImpactDirection.MIXED -> MarketColors.AmberText
    ImpactDirection.NEUTRAL -> MarketColors.InkMuted
}

private fun directionLabel(direction: ImpactDirection): String = when (direction) {
    ImpactDirection.POSITIVE -> "긍정"
    ImpactDirection.NEGATIVE -> "부정"
    ImpactDirection.MIXED -> "엇갈림"
    ImpactDirection.NEUTRAL -> "중립"
}

private fun groupsFor(tab: NewsBrowseTab, projection: NewsUiProjection): List<NewsGroupItem> = when (tab) {
    NewsBrowseTab.BRIEFING -> listOf(
        NewsGroupItem("all", "모아보기", "중요 연결 우선", projection.stories.size),
        NewsGroupItem(
            "active",
            "시장 반영 중",
            "가격 과정에 반영",
            projection.marketEffectActiveCount,
            matches = { story -> story.marketEffectStatus?.state == NewsEffectState.MARKET_ACTIVE },
        ),
        NewsGroupItem(
            "process",
            "조치·절차 진행",
            "거래 제한과 운영 절차",
            projection.operationalActiveCount,
            matches = { story ->
                story.operationalStatus?.state in setOf(
                    NewsEffectState.RESTRICTION_ACTIVE,
                    NewsEffectState.PROCESS_ACTIVE,
                )
            },
        ),
        NewsGroupItem(
            "waiting",
            "장 반영 대기",
            "발표 후 개장 대기",
            projection.stories.count {
                it.marketEffectStatus?.state == NewsEffectState.WAITING_FOR_MARKET
            },
            matches = { story ->
                story.marketEffectStatus?.state == NewsEffectState.WAITING_FOR_MARKET
            },
        ),
        NewsGroupItem(
            "ended",
            "시장 영향 종료",
            "가격 반영 구간 종료",
            projection.stories.count {
                it.marketEffectStatus?.state == NewsEffectState.MARKET_ENDED
            },
            matches = { story -> story.marketEffectStatus?.state == NewsEffectState.MARKET_ENDED },
        ),
        NewsGroupItem(
            "resolved",
            "조치·절차 완료",
            "해제·적용·정산 완료",
            projection.stories.count {
                it.operationalStatus?.state == NewsEffectState.RESOLVED
            },
            matches = { story -> story.operationalStatus?.state == NewsEffectState.RESOLVED },
        ),
        NewsGroupItem(
            "protection",
            "시장조치",
            "거래 제한과 해제",
            projection.stories.count(NewsStoryUi::isMarketAction),
            matches = NewsStoryUi::isMarketAction,
        ),
    )
    NewsBrowseTab.STOCKS -> buildList {
        add(
            NewsGroupItem(
                "all",
                "모든 관련 종목",
                "보유·관심·실제 영향 대상",
                projection.stories.count { it.relatedStocks.isNotEmpty() },
                matches = { story -> story.relatedStocks.isNotEmpty() },
            ),
        )
        projection.stockGroups.forEach { add(it.toGroupItem()) }
    }
    NewsBrowseTab.INDUSTRIES -> buildList {
        add(
            NewsGroupItem(
                "all",
                "모든 산업",
                "산업 연결 전체",
                projection.stories.count(NewsStoryUi::hasSectorTargets),
                matches = NewsStoryUi::hasSectorTargets,
            ),
        )
        projection.sectorGroups.forEach { add(it.toGroupItem()) }
    }
    NewsBrowseTab.SCHEDULES -> listOf(
        NewsGroupItem(
            "all",
            "일정·공시 전체",
            "발표와 운영 기록",
            projection.stories.count { tab.matches(it) },
            matches = tab::matches,
        ),
        NewsGroupItem(
            "scheduled",
            "정기 발표",
            "경제지표와 실적",
            projection.stories.count(NewsStoryUi::isScheduled),
            matches = NewsStoryUi::isScheduled,
        ),
        NewsGroupItem(
            "corporate",
            "기업행동",
            "배당·증자·구조 변경",
            projection.stories.count { it.event.type == EventType.CORPORATE_ACTION },
            matches = { story -> story.event.type == EventType.CORPORATE_ACTION },
        ),
        NewsGroupItem(
            "lifecycle",
            "상품 일정",
            "만기·상환·청산",
            projection.stories.count { it.event.recordKind == EventRecordKind.INSTRUMENT_LIFECYCLE },
            matches = { story ->
                story.event.recordKind == EventRecordKind.INSTRUMENT_LIFECYCLE
            },
        ),
        NewsGroupItem(
            "protection",
            "거래소 조치",
            "정지·재개·상장 절차",
            projection.stories.count(NewsStoryUi::isMarketAction),
            matches = NewsStoryUi::isMarketAction,
        ),
    )
}

private val NewsStoryUi.hasSectorTargets: Boolean
    get() = impactPaths.any { it.sector != null }

private fun NewsStoryUi.targetsIndustry(
    sector: Sector,
    industrySegment: IndustrySegment?,
): Boolean = impactPaths.any { path ->
    if (industrySegment == null) path.sector == sector else path.industrySegment == industrySegment
}

private fun NewsStockGroupUi.toGroupItem(): NewsGroupItem = NewsGroupItem(
    key = key,
    label = label,
    detail = detail,
    count = count,
    matches = { story -> story.relatedStocks.any { it.stockId == stockId } },
)

private fun NewsSectorGroupUi.toGroupItem(): NewsGroupItem = NewsGroupItem(
    key = key,
    label = label,
    detail = buildString {
        if (industrySegment != null) append("${sector.displayName} · ")
        append(if (personalCount > 0) "내 종목 연결 ${personalCount}건" else "산업 뉴스")
    },
    count = count,
    matches = { story -> story.targetsIndustry(sector, industrySegment) },
)

private data class NewsGroupItem(
    val key: String,
    val label: String,
    val detail: String?,
    val count: Int,
    val matches: (NewsStoryUi) -> Boolean = { true },
)

internal data class EventNewsFilterState(
    val tab: NewsBrowseTab = NewsBrowseTab.BRIEFING,
    val groupKey: String? = null,
    val selectedEventId: String? = null,
)

internal enum class NewsBrowseTab(val displayName: String) {
    BRIEFING("브리핑"),
    STOCKS("종목"),
    INDUSTRIES("산업"),
    SCHEDULES("일정·공시"),
    ;

    fun matches(story: NewsStoryUi): Boolean = when (this) {
        BRIEFING -> true
        STOCKS -> story.relatedStocks.isNotEmpty()
        INDUSTRIES -> story.hasSectorTargets
        SCHEDULES -> story.isScheduled || story.isOperational || story.event.type == EventType.CORPORATE_ACTION
    }
}

private const val RELATED_STOCK_DETAIL_LIMIT = 12
