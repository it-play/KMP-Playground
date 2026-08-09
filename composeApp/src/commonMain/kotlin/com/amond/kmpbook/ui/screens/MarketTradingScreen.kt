package com.amond.kmpbook.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.OrderBook
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.ReferenceCurrency
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.presentation.CorporateMetricsSnapshot
import com.amond.kmpbook.presentation.FundMetricsSnapshot
import com.amond.kmpbook.presentation.InstrumentMetricsSnapshot
import com.amond.kmpbook.presentation.NewsEffectState
import com.amond.kmpbook.presentation.NewsRelatedStockUi
import com.amond.kmpbook.presentation.NewsStockRelationKind
import com.amond.kmpbook.presentation.NewsStoryUi
import com.amond.kmpbook.presentation.ProtectionDetailUi
import com.amond.kmpbook.presentation.ProtectionStatusBadgeUi
import com.amond.kmpbook.ui.charts.CandlestickVolumeChart
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.components.MarketButtonTone
import com.amond.kmpbook.ui.components.MarketProtectionDetailSurface
import com.amond.kmpbook.ui.components.Metric
import com.amond.kmpbook.ui.components.ProtectionStatusBadge
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.components.deltaColor
import com.amond.kmpbook.ui.format.formatMoney
import com.amond.kmpbook.ui.format.formatPercent
import com.amond.kmpbook.ui.format.formatPrice
import com.amond.kmpbook.ui.format.formatQuantity
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketComponentSize
import com.amond.kmpbook.ui.theme.MarketLayout
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun MarketTradingScreen(
    stocks: List<StockDefinition>,
    quotes: Map<String, Quote>,
    priceHistory: Map<String, List<PriceBar>>,
    selectedStockId: String?,
    holding: Holding?,
    orderBook: OrderBook?,
    cashKrw: Double,
    cashUsd: Double,
    onSelectStock: (String) -> Unit,
    watchlistedStockIds: Set<String> = emptySet(),
    onToggleWatchlist: (String) -> Unit = {},
    onSubmitOrder: (
        side: OrderSide,
        type: OrderType,
        timeInForce: TimeInForce,
        quantity: Double,
        limitPrice: Double?,
    ) -> Unit,
    protectionBadges: Map<String, ProtectionStatusBadgeUi> = emptyMap(),
    selectedProtectionDetail: ProtectionDetailUi? = null,
    selectedMetrics: InstrumentMetricsSnapshot? = null,
    orderUnavailableReason: (stockId: String, orderType: OrderType) -> String? = { _, _ -> null },
    relatedNews: List<NewsStoryUi> = emptyList(),
    onOpenEvent: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val selectedStock = stocks.firstOrNull { it.id == selectedStockId } ?: stocks.firstOrNull()
    val quote = selectedStock?.let { quotes[it.id] }
    val bars = selectedStock?.let { priceHistory[it.id].orEmpty() }.orEmpty()
    var showSelectedProtectionDetail by remember(selectedStock?.id) { mutableStateOf(false) }
    var orderSide by remember(selectedStock?.id) { mutableStateOf(OrderSide.BUY) }
    var orderType by remember(selectedStock?.id) { mutableStateOf(OrderType.MARKET) }
    var selectedBookPrice by remember(selectedStock?.id) { mutableStateOf<Double?>(null) }
    var showOrderBook by remember(selectedStock?.id) { mutableStateOf(false) }

    LaunchedEffect(selectedStock?.id, orderType, orderBook != null) {
        showOrderBook = orderType == OrderType.LIMIT && orderBook != null
    }

    Row(
        modifier = modifier.fillMaxSize().padding(MarketLayout.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
    ) {
        WatchlistPanel(
            stocks = stocks,
            quotes = quotes,
            selectedStockId = selectedStock?.id,
            onSelectStock = onSelectStock,
            watchlistedStockIds = watchlistedStockIds,
            onToggleWatchlist = onToggleWatchlist,
            protectionBadges = protectionBadges,
            modifier = Modifier.width(MarketLayout.marketExplorerWidth).fillMaxHeight(),
        )
        if (selectedStock != null && quote != null) {
            val selectedNews = remember(relatedNews, selectedStock.id) {
                relatedNews.filter { story ->
                    story.relatedStocks.any { it.stockId == selectedStock.id }
                }
            }
            StockChartPanel(
                stock = selectedStock,
                quote = quote,
                bars = bars,
                holding = holding,
                relatedNews = selectedNews,
                onOpenEvent = onOpenEvent,
                watched = selectedStock.id in watchlistedStockIds,
                onToggleWatchlist = { onToggleWatchlist(selectedStock.id) },
                protectionBadge = protectionBadges[selectedStock.id],
                onOpenProtectionDetail = { showSelectedProtectionDetail = true },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            InstrumentSideRail(
                stock = selectedStock,
                quote = quote,
                metrics = selectedMetrics,
                orderBook = orderBook,
                side = orderSide,
                selectedPrice = selectedBookPrice,
                showOrderBook = showOrderBook,
                onShowMetrics = { showOrderBook = false },
                onShowOrderBook = { showOrderBook = true },
                onSelectPrice = { selectedBookPrice = it },
                modifier = Modifier.width(MarketLayout.marketOrderBookWidth).fillMaxHeight(),
            )
            OrderTicketPanel(
                stock = selectedStock,
                quote = quote,
                holding = holding,
                cashKrw = cashKrw,
                cashUsd = cashUsd,
                side = orderSide,
                onSideChange = { orderSide = it },
                type = orderType,
                onTypeChange = { nextType ->
                    orderType = nextType
                    showOrderBook = nextType == OrderType.LIMIT && orderBook != null
                    if (nextType == OrderType.MARKET) selectedBookPrice = null
                },
                selectedBookPrice = selectedBookPrice,
                onLimitPriceEdited = { selectedBookPrice = null },
                protectionDetail = selectedProtectionDetail,
                orderUnavailableReason = { type -> orderUnavailableReason(selectedStock.id, type) },
                onSubmitOrder = onSubmitOrder,
                modifier = Modifier.width(MarketLayout.marketOrderTicketWidth).fillMaxHeight(),
            )
        } else {
            LedgerPanel(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("표시할 종목이 없습니다.", style = MarketType.body, color = MarketColors.InkMuted)
                }
            }
        }
    }
    if (showSelectedProtectionDetail) {
        selectedProtectionDetail?.let { detail ->
            Dialog(onDismissRequest = { showSelectedProtectionDetail = false }) {
                MarketProtectionDetailSurface(
                    model = detail,
                    modifier = Modifier.width(720.dp),
                    contextTitle = selectedStock?.name,
                    onClose = { showSelectedProtectionDetail = false },
                )
            }
        }
    }
}

@Composable
private fun WatchlistPanel(
    stocks: List<StockDefinition>,
    quotes: Map<String, Quote>,
    selectedStockId: String?,
    onSelectStock: (String) -> Unit,
    watchlistedStockIds: Set<String>,
    onToggleWatchlist: (String) -> Unit,
    protectionBadges: Map<String, ProtectionStatusBadgeUi>,
    modifier: Modifier,
) {
    var query by remember { mutableStateOf("") }
    var venueFilter by remember { mutableStateOf(VenueFilter.ALL) }
    var instrumentFilter by remember { mutableStateOf(InstrumentFilter.ALL) }
    var watchlistOnly by remember { mutableStateOf(false) }
    var filtersExpanded by remember { mutableStateOf(false) }
    val activeFilterCount = listOf(
        watchlistOnly,
        instrumentFilter != InstrumentFilter.ALL,
        venueFilter != VenueFilter.ALL,
    ).count { it }
    val filterSummary = buildList {
        if (watchlistOnly) add("관심 종목")
        if (instrumentFilter != InstrumentFilter.ALL) add(instrumentFilter.label)
        if (venueFilter != VenueFilter.ALL) add(venueFilter.label)
    }.joinToString(" · ").ifEmpty { "전체 상품" }
    val filtered = stocks.filter { stock ->
        (!watchlistOnly || stock.id in watchlistedStockIds) &&
            instrumentFilter.matches(stock) &&
            venueFilter.matches(stock) && (
            query.isBlank() ||
                stock.symbol.contains(query, ignoreCase = true) ||
                stock.name.contains(query, ignoreCase = true) ||
                stock.englishName.contains(query, ignoreCase = true) ||
                stock.identityProfile?.let { identity ->
                    identity.legalName.contains(query, ignoreCase = true) ||
                        identity.issuerOrManager.contains(query, ignoreCase = true) ||
                        identity.aliases.any { it.contains(query, ignoreCase = true) } ||
                        identity.eventRiskTags.any { it.contains(query, ignoreCase = true) }
                } == true
            )
    }
    LedgerPanel(modifier, padding = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(12.dp)) {
                SectionHeading("종목 탐색", eyebrow = "UNIVERSE")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(30) },
                    modifier = Modifier.fillMaxWidth().height(MarketComponentSize.textFieldHeight),
                    placeholder = {
                        Text(
                            "종목명·티커 검색",
                            style = MarketType.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    textStyle = MarketType.body,
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        filterSummary,
                        modifier = Modifier.weight(1f),
                        style = MarketType.label,
                        color = if (activeFilterCount > 0) MarketColors.Primary else MarketColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FilterDisclosureButton(
                        expanded = filtersExpanded,
                        activeFilterCount = activeFilterCount,
                        onClick = { filtersExpanded = !filtersExpanded },
                    )
                }
                if (filtersExpanded) {
                    Spacer(Modifier.height(8.dp))
                    WatchlistFilterPanel(
                        watchlistOnly = watchlistOnly,
                        watchlistedCount = watchlistedStockIds.size,
                        onWatchlistOnlyChange = { watchlistOnly = it },
                        instrumentFilter = instrumentFilter,
                        onInstrumentFilterChange = { instrumentFilter = it },
                        venueFilter = venueFilter,
                        onVenueFilterChange = { venueFilter = it },
                        showReset = activeFilterCount > 0,
                        onReset = {
                            watchlistOnly = false
                            instrumentFilter = InstrumentFilter.ALL
                            venueFilter = VenueFilter.ALL
                        },
                    )
                }
            }
            LedgerDivider()
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("종목", modifier = Modifier.weight(1f), style = MarketType.label, color = MarketColors.InkMuted)
                Text("현재가", style = MarketType.label, color = MarketColors.InkMuted)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { stock ->
                    WatchlistRow(
                        stock = stock,
                        quote = quotes[stock.id],
                        selected = stock.id == selectedStockId,
                        watched = stock.id in watchlistedStockIds,
                        protectionBadge = protectionBadges[stock.id],
                        onClick = { onSelectStock(stock.id) },
                        onToggleWatchlist = { onToggleWatchlist(stock.id) },
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().background(MarketColors.PaperMuted).padding(9.dp),
            ) {
                Text(
                    "${filtered.size}개 종목",
                    style = MarketType.caption,
                    color = MarketColors.InkMuted,
                )
            }
        }
    }
}

@Composable
private fun FilterDisclosureButton(
    expanded: Boolean,
    activeFilterCount: Int,
    onClick: () -> Unit,
) {
    val active = expanded || activeFilterCount > 0
    Box(
        Modifier
            .clip(RoundedCornerShape(MarketRadii.pill))
            .background(if (expanded) MarketColors.Navy else MarketColors.PaperMuted)
            .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = if (expanded) "종목 필터 접기" else "종목 필터 펼치기"
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            buildString {
                append("필터")
                if (activeFilterCount > 0) append(" $activeFilterCount")
                append(if (expanded) " ▴" else " ▾")
            },
            style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
            color = when {
                expanded -> Color.White
                active -> MarketColors.Primary
                else -> MarketColors.InkMuted
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun WatchlistFilterPanel(
    watchlistOnly: Boolean,
    watchlistedCount: Int,
    onWatchlistOnlyChange: (Boolean) -> Unit,
    instrumentFilter: InstrumentFilter,
    onInstrumentFilterChange: (InstrumentFilter) -> Unit,
    venueFilter: VenueFilter,
    onVenueFilterChange: (VenueFilter) -> Unit,
    showReset: Boolean,
    onReset: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.medium))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "필터 조건",
                modifier = Modifier.weight(1f),
                style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.InkMuted,
            )
            if (showReset) {
                Text(
                    "초기화",
                    modifier = Modifier
                        .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
                        .clickable(role = Role.Button, onClick = onReset)
                        .padding(horizontal = 6.dp, vertical = 12.dp),
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = MarketColors.Primary,
                    maxLines = 1,
                )
            }
        }
        FilterGroupLabel("목록")
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            FilterCell("전체 종목", !watchlistOnly) { onWatchlistOnlyChange(false) }
            FilterCell("★ 관심 $watchlistedCount", watchlistOnly) { onWatchlistOnlyChange(true) }
        }
        FilterGroupLabel("상품")
        InstrumentFilter.entries.chunked(2).forEach { filters ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                filters.forEach { filter ->
                    FilterCell(filter.label, instrumentFilter == filter) {
                        onInstrumentFilterChange(filter)
                    }
                }
            }
        }
        FilterGroupLabel("시장")
        VenueFilter.entries.chunked(3).forEach { filters ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                filters.forEach { filter ->
                    FilterCell(filter.label, venueFilter == filter) {
                        onVenueFilterChange(filter)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterGroupLabel(text: String) {
    Text(
        text,
        style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
        color = MarketColors.InkMuted,
        maxLines = 1,
    )
}

@Composable
private fun FilterCell(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(MarketRadii.pill))
            .background(if (selected) MarketColors.Navy else MarketColors.PaperMuted)
            .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (selected) "✓ $text" else text,
            style = MarketType.caption,
            color = if (selected) Color.White else MarketColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WatchlistRow(
    stock: StockDefinition,
    quote: Quote?,
    selected: Boolean,
    watched: Boolean,
    protectionBadge: ProtectionStatusBadgeUi?,
    onClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MarketColors.PrimaryWeak else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 29.dp)
                .background(if (selected) MarketColors.Primary else Color.Transparent),
        )
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(MarketRadii.pill))
                .size(MarketComponentSize.minimumInteractiveTarget)
                .clickable(role = Role.Button, onClick = onToggleWatchlist),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (watched) "★" else "☆",
                style = MarketType.body,
                color = if (watched) MarketColors.Amber else MarketColors.InkMuted,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stock.name,
                style = MarketType.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                color = MarketColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${stock.symbol} · ${stock.market.displayName}",
                style = MarketType.caption,
                color = MarketColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ProtectionStatusBadge(
                model = protectionBadge,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                quote?.let { formatPrice(it.price, stock.currency) } ?: "-",
                style = MarketType.number,
                color = quote?.let { deltaColor(it.change) } ?: MarketColors.InkMuted,
            )
            Text(
                quote?.let { formatPercent(it.changeRate) } ?: "-",
                style = MarketType.number,
                color = quote?.let { deltaColor(it.change) } ?: MarketColors.InkMuted,
            )
        }
    }
}

@Composable
private fun StockChartPanel(
    stock: StockDefinition,
    quote: Quote,
    bars: List<PriceBar>,
    holding: Holding?,
    relatedNews: List<NewsStoryUi>,
    onOpenEvent: (String) -> Unit,
    watched: Boolean,
    onToggleWatchlist: () -> Unit,
    protectionBadge: ProtectionStatusBadgeUi?,
    onOpenProtectionDetail: () -> Unit,
    modifier: Modifier,
) {
    var range by remember { mutableStateOf("1개월") }
    var selectedIntelligenceTab by remember(stock.id) { mutableStateOf(IntelligenceTab.IMPACT) }
    var isStructureExpanded by remember(stock.id) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val signals = remember(relatedNews, stock) {
        relatedNews.mapNotNull { story -> story.signalFor(stock) }
    }
    val tracedSignals = remember(signals) { signals.filter(StockNewsSignal::hasCausalTrace) }
    val availableIntelligenceTabs = remember(signals.isNotEmpty(), tracedSignals.isNotEmpty()) {
        buildList {
            if (tracedSignals.isNotEmpty()) add(IntelligenceTab.IMPACT)
            if (signals.isNotEmpty()) add(IntelligenceTab.NEWS)
            add(IntelligenceTab.STRUCTURE)
        }
    }
    val activeIntelligenceTab = selectedIntelligenceTab
        .takeIf(availableIntelligenceTabs::contains)
        ?: availableIntelligenceTabs.first()
    val structureVisibility = remember(stock.id) { MutableTransitionState(false) }
    structureVisibility.targetState =
        activeIntelligenceTab == IntelligenceTab.STRUCTURE && isStructureExpanded
    LaunchedEffect(stock.id, availableIntelligenceTabs) {
        if (selectedIntelligenceTab != activeIntelligenceTab) {
            selectedIntelligenceTab = activeIntelligenceTab
        }
    }
    val displayedBars = bars
        .asSequence()
        .filter { it.volume > 0L }
        .toList()
        .takeLast(
            when (range) {
                "1일" -> 8
                "1주" -> 40
                "3개월" -> 520
                else -> 180
            },
        )
    LedgerPanel(modifier, padding = 0.dp) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    "MARKET SIGNAL  /  시세",
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = MarketColors.Signal,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stock.name,
                            style = MarketType.display.copy(fontSize = 24.sp),
                            color = MarketColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${stock.symbol} · ${stock.englishName}",
                            style = MarketType.label,
                            color = MarketColors.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            formatMoney(quote.price, stock.currency),
                            style = MarketType.numberLarge,
                            color = deltaColor(quote.change),
                        )
                        Text(
                            "${if (quote.change >= 0.0) "+" else ""}${formatPrice(quote.change, stock.currency)}  ${formatPercent(quote.changeRate)}",
                            style = MarketType.number,
                            color = deltaColor(quote.change),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    StatusLabel(stock.instrumentType.displayName, MarketColors.Primary)
                    StatusLabel(stock.market.displayName, MarketColors.InkMuted)
                    StatusLabel(stock.behavior.strategy.displayName, MarketColors.Primary)
                }
                if (protectionBadge != null) {
                    Spacer(Modifier.height(2.dp))
                    ProtectionStatusBadge(
                        model = protectionBadge,
                        onClick = onOpenProtectionDetail,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    fxExposureLabel(stock)?.let { label ->
                        StatusLabel(
                            text = label,
                            color = if ("헤지" in label) MarketColors.Primary else MarketColors.Amber,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(MarketRadii.pill))
                            .background(if (watched) MarketColors.Amber.copy(alpha = 0.12f) else MarketColors.PaperMuted)
                            .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
                            .clickable(role = Role.Button, onClick = onToggleWatchlist)
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (watched) "★ 관심" else "☆ 관심 추가",
                            style = MarketType.caption,
                            color = if (watched) MarketColors.Amber else MarketColors.InkMuted,
                        )
                    }
                    stock.identityProfile?.let { identity ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(MarketRadii.pill))
                                .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
                                .clickable(role = Role.Button) { uriHandler.openUri(identity.officialSourceUrl) }
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("공식 자료 ↗", style = MarketType.caption, color = MarketColors.Primary)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append(stock.sector.displayName)
                        stock.etfProfile?.let { append(" · ${it.assetClass.displayName} · ${it.benchmark}") }
                    },
                    style = MarketType.label,
                    color = MarketColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Metric("시가", formatPrice(quote.open, stock.currency), Modifier.weight(1f))
                    Metric("고가", formatPrice(quote.high, stock.currency), Modifier.weight(1f), MarketColors.Rise)
                    Metric("저가", formatPrice(quote.low, stock.currency), Modifier.weight(1f), MarketColors.Fall)
                    Metric("거래량", formatQuantity(quote.volume.toDouble()), Modifier.weight(1f))
                    Metric("연 변동성", formatPercent(stock.volatility, withSign = false), Modifier.weight(1f))
                }
            }
            LedgerDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "가격·거래량",
                        style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                        color = MarketColors.Ink,
                    )
                    Text(
                        "상승 빨강 · 하락 파랑 · 이동평균 5/20",
                        style = MarketType.caption,
                        color = MarketColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                listOf("1일", "1주", "1개월", "3개월").forEach { item ->
                    FilterCell(item, item == range) { range = item }
                    Spacer(Modifier.width(3.dp))
                }
            }
            CandlestickVolumeChart(
                bars = displayedBars,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
            )
            LedgerDivider()
                MarketIntelligenceDeck(
                    stock = stock,
                    holding = holding,
                    signals = signals,
                    tracedSignals = tracedSignals,
                    activeTab = activeIntelligenceTab,
                    availableTabs = availableIntelligenceTabs,
                    structureVisibility = structureVisibility,
                    onTabSelected = { selectedIntelligenceTab = it },
                    onToggleStructure = { isStructureExpanded = !isStructureExpanded },
                    onOpenEvent = onOpenEvent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (
                activeIntelligenceTab == IntelligenceTab.STRUCTURE &&
                !structureVisibility.currentState &&
                !structureVisibility.targetState
            ) {
                StructureBookmarkHandle(
                    expanded = false,
                    onClick = { isStructureExpanded = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun MarketIntelligenceDeck(
    stock: StockDefinition,
    holding: Holding?,
    signals: List<StockNewsSignal>,
    tracedSignals: List<StockNewsSignal>,
    activeTab: IntelligenceTab,
    availableTabs: List<IntelligenceTab>,
    structureVisibility: MutableTransitionState<Boolean>,
    onTabSelected: (IntelligenceTab) -> Unit,
    onToggleStructure: () -> Unit,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier,
) {
    if (activeTab == IntelligenceTab.STRUCTURE) {
        AnimatedVisibility(
            visibleState = structureVisibility,
            modifier = modifier,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            Box(Modifier.fillMaxWidth().background(MarketColors.Grey50)) {
                Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
                    IntelligenceDeckHeader(
                        activeTab = activeTab,
                        availableTabs = availableTabs,
                        signalCount = signals.size,
                        onTabSelected = onTabSelected,
                    )
                    LedgerDivider()
                    ProductStructurePanel(stock, holding, Modifier.fillMaxWidth())
                }
                StructureBookmarkHandle(
                    expanded = true,
                    onClick = onToggleStructure,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    } else {
        Column(modifier.height(260.dp).background(MarketColors.Grey50)) {
            IntelligenceDeckHeader(
                activeTab = activeTab,
                availableTabs = availableTabs,
                signalCount = signals.size,
                onTabSelected = onTabSelected,
            )
            LedgerDivider()
            when (activeTab) {
                IntelligenceTab.IMPACT -> ImpactPathPanel(
                    stock = stock,
                    signals = tracedSignals,
                    onOpenEvent = onOpenEvent,
                    modifier = Modifier.fillMaxSize(),
                )
                IntelligenceTab.NEWS -> RelatedNewsPanel(
                    stock = stock,
                    signals = signals,
                    onOpenEvent = onOpenEvent,
                    modifier = Modifier.fillMaxSize(),
                )
                IntelligenceTab.STRUCTURE -> Unit
            }
        }
    }
}

@Composable
private fun IntelligenceDeckHeader(
    activeTab: IntelligenceTab,
    availableTabs: List<IntelligenceTab>,
    signalCount: Int,
    onTabSelected: (IntelligenceTab) -> Unit,
) {
    if (availableTabs.size == 1) {
        SectionHeading(
            title = activeTab.label,
            eyebrow = activeTab.eyebrow,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "WHY IT MOVES",
                style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.Signal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "왜 움직이는가",
                style = MarketType.heading,
                color = MarketColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        availableTabs.forEach { tab ->
            IntelligenceTabButton(
                text = if (tab == IntelligenceTab.NEWS && signalCount > 0) {
                    "${tab.label} $signalCount"
                } else {
                    tab.label
                },
                selected = activeTab == tab,
                onClick = { onTabSelected(tab) },
            )
        }
    }
}

@Composable
private fun IntelligenceTabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .width(104.dp)
            .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MarketType.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) MarketColors.Signal else MarketColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MarketColors.SignalLine),
            )
        }
    }
}

@Composable
private fun ImpactPathPanel(
    stock: StockDefinition,
    signals: List<StockNewsSignal>,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier,
) {
    val featured = signals.maxWithOrNull(
        compareBy<StockNewsSignal> { it.story.activityPriority }
            .thenBy { it.relation.relativeSensitivity }
            .thenBy { it.relation.confidence },
    )
    if (featured == null) {
        ConnectedNewsEmptyState(stock.name, modifier)
        return
    }
    val directionColor = impactDirectionColor(featured.relation.direction)
    Column(
        modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "가장 강한 연결 신호",
                modifier = Modifier.weight(1f),
                style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.InkMuted,
            )
            Text(
                "${featured.story.status.label} · ${impactDirectionLabel(featured.relation.direction)} · " +
                    relationTierLabel(featured.relation, featured.hasCausalTrace),
                style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = directionColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ImpactPathRibbon(featured, onOpenEvent)
        Text(
            "종목 영향 · ${featured.relation.reason}",
            style = MarketType.caption,
            color = MarketColors.InkMuted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ImpactPathRibbon(
    signal: StockNewsSignal,
    onOpenEvent: (String) -> Unit,
) {
    val pathParts = if (signal.pathNodes.size <= 3) {
        signal.pathNodes
    } else {
        listOf(
            signal.pathNodes.first(),
            signal.pathNodes.drop(1).dropLast(1).joinToString(" → "),
            signal.pathNodes.last(),
        )
    }
    val directionColor = impactDirectionColor(signal.relation.direction)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { contentDescription = "영향 경로 ${signal.causalPath}" }
            .border(1.dp, MarketColors.SignalLine, RoundedCornerShape(MarketRadii.medium))
            .background(MarketColors.SignalSoft, RoundedCornerShape(MarketRadii.medium))
            .clickable(role = Role.Button) { onOpenEvent(signal.story.event.id) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pathParts.take(3).forEachIndexed { index, part ->
            Column(Modifier.weight(1f)) {
                Text(
                    when (index) {
                        0 -> "요인"
                        pathParts.lastIndex -> "종목"
                        else -> "전달"
                    },
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (index == pathParts.lastIndex) directionColor else MarketColors.Signal,
                )
                Text(
                    part,
                    style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                    color = MarketColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index < pathParts.take(3).lastIndex) {
                Text(
                    "→",
                    modifier = Modifier.padding(horizontal = 6.dp),
                    style = MarketType.heading,
                    color = MarketColors.Signal,
                )
            }
        }
    }
}

@Composable
private fun RelatedNewsPanel(
    stock: StockDefinition,
    signals: List<StockNewsSignal>,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier,
) {
    if (signals.isEmpty()) {
        ConnectedNewsEmptyState(stock.name, modifier)
        return
    }
    LazyColumn(modifier) {
        items(signals, key = { it.story.event.id }) { signal ->
            RelatedNewsRow(signal) { onOpenEvent(signal.story.event.id) }
            LedgerDivider()
        }
    }
}

@Composable
private fun RelatedNewsRow(signal: StockNewsSignal, onClick: () -> Unit) {
    val directionColor = impactDirectionColor(signal.relation.direction)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                signal.story.status.label,
                style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = newsStatusColor(signal.story.status.state),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                impactDirectionLabel(signal.relation.direction),
                style = MarketType.caption.copy(fontWeight = FontWeight.Bold),
                color = directionColor,
            )
            Text(
                relationTierLabel(signal.relation, signal.hasCausalTrace),
                modifier = Modifier.weight(1f),
                style = MarketType.caption,
                color = MarketColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("뉴스 열기 →", style = MarketType.caption, color = MarketColors.Signal)
        }
        Text(
            signal.story.event.title,
            style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
            color = MarketColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "종목 영향 · ${signal.relation.reason}",
            style = MarketType.caption,
            color = MarketColors.InkMuted,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
        if (signal.hasCausalTrace) {
            Text(
                signal.causalPath,
                style = MarketType.caption.copy(fontWeight = FontWeight.Medium),
                color = MarketColors.Signal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConnectedNewsEmptyState(stockName: String, modifier: Modifier) {
    Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "연결된 뉴스가 아직 없습니다",
                style = MarketType.body.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${stockName}과 직접·산업·시장 범위가 겹치는 뉴스만 표시합니다.",
                style = MarketType.caption,
                color = MarketColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 새 인과 필드가 추가돼도 덱의 표시 구조와 분리해 이 변환만 확장한다. */
private fun NewsStoryUi.signalFor(stock: StockDefinition): StockNewsSignal? {
    val relation = relatedStocks.firstOrNull { it.stockId == stock.id } ?: return null
    val traceLabels = relation.causalTraceLabels
        .filter(String::isNotBlank)
        .fold(emptyList<String>()) { labels, label ->
            if (labels.lastOrNull() == label) labels else labels + label
        }
    return StockNewsSignal(
        story = this,
        relation = relation,
        pathNodes = traceLabels,
    )
}

private fun relationTierLabel(relation: NewsRelatedStockUi, hasCausalTrace: Boolean): String = when (relation.relationKind) {
    NewsStockRelationKind.DIRECT_TARGET -> "직접 관계"
    NewsStockRelationKind.UNDERLYING_EXPOSURE -> "직접 관계 · 기초자산"
    NewsStockRelationKind.CAUSAL_CHAIN -> when (relation.specificity) {
        4 -> "직접 관계"
        2, 3 -> "산업 관계"
        else -> "시장 관계"
    }.let { tier ->
        if (hasCausalTrace) "$tier · 인과 경로" else tier
    }
    NewsStockRelationKind.INDUSTRY_SEGMENT,
    NewsStockRelationKind.INDUSTRY,
    -> "산업 관계"
    NewsStockRelationKind.MARKET_CONTEXT -> "시장 관계"
}

private fun impactDirectionLabel(direction: ImpactDirection): String = when (direction) {
    ImpactDirection.POSITIVE -> "긍정"
    ImpactDirection.NEGATIVE -> "부정"
    ImpactDirection.MIXED -> "혼조"
    ImpactDirection.NEUTRAL -> "중립"
}

private fun impactDirectionColor(direction: ImpactDirection): Color = when (direction) {
    ImpactDirection.POSITIVE -> MarketColors.RiseText
    ImpactDirection.NEGATIVE -> MarketColors.FallText
    ImpactDirection.MIXED -> MarketColors.AmberText
    ImpactDirection.NEUTRAL -> MarketColors.InkMuted
}

private fun newsStatusColor(state: NewsEffectState): Color = when (state) {
    NewsEffectState.UPCOMING,
    NewsEffectState.WAITING_FOR_MARKET,
    NewsEffectState.PROCESS_ACTIVE,
    -> MarketColors.Signal
    NewsEffectState.MARKET_ACTIVE -> MarketColors.AmberText
    NewsEffectState.RESTRICTION_ACTIVE -> MarketColors.NavyRaised
    NewsEffectState.MARKET_ENDED,
    NewsEffectState.RESOLVED,
    NewsEffectState.INFORMATION,
    -> MarketColors.InkMuted
}

@Composable
private fun ProductStructurePanel(
    stock: StockDefinition,
    holding: Holding?,
    modifier: Modifier,
) {
    val identity = stock.identityProfile
    val contractFacts = buildList {
        identity?.maturityDate?.let { add("만기 $it") }
        if (identity?.callable == true) add("발행사 조기상환 가능")
        identity?.adrUnderlyingShareRatio?.let { ratio ->
            add("1 ADR/ADS = 본주 ${formatQuantity(ratio)}")
        }
    }
    val structureFacts = buildList {
        stock.etfProfile?.let { profile ->
            add("자산·지역" to "${profile.assetClass.displayName} · ${profile.exposureRegion.displayName}")
            add("운용 전략" to stock.behavior.strategy.displayName)
            if (profile.leverage != 1.0) add("일일 목표" to "${formatRatio(profile.leverage)}배")
            fxExposureLabel(stock)?.let { add("환 노출" to it) }
        } ?: run {
            add("산업·섹터" to stock.sector.displayName)
            add("사업 구조" to stock.behavior.strategy.displayName)
            if (stock.industrySegments.isNotEmpty()) {
                add("세부 노출" to stock.industrySegments.joinToString(" · ") { it.displayName })
            }
        }
        add("핵심 위험" to stock.behavior.principalRisk.displayName)
        identity?.issuerOrManager?.takeIf(String::isNotBlank)?.let { add("운용·발행" to it) }
        identity?.distributionNotes?.takeIf(String::isNotBlank)?.let { add("분배 정책" to it) }
        if (contractFacts.isNotEmpty()) add("계약" to contractFacts.joinToString(" · "))
        identity?.let { add("확인 기준" to "${it.legalName} · ${it.verifiedOn}") }
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ProductInfoLine(
            label = "분류",
            value = stock.structureClassification(),
            maxLines = 1,
        )
        if (holding != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "내 포지션",
                        style = MarketType.label,
                        color = MarketColors.InkMuted,
                        maxLines = 1,
                    )
                    Text(
                        "${formatQuantity(holding.quantity, stock.quantityUnit)} · " +
                            "평균 ${formatPrice(holding.averagePrice, stock.currency)}",
                        style = MarketType.number,
                        color = MarketColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Metric(
                    if (stock.currency == Currency.USD) "USD 평가손익" else "평가손익",
                    formatMoney(holding.unrealizedProfit, stock.currency),
                    Modifier.width(150.dp),
                    deltaColor(holding.unrealizedProfit),
                    if (stock.currency == Currency.USD) {
                        "${formatPercent(holding.returnRate)} · 원화손익 자산화면"
                    } else {
                        formatPercent(holding.returnRate)
                    },
                )
            }
            LedgerDivider()
        }
        Text(
            identity?.strategySummary ?: stock.description,
            style = MarketType.caption,
            color = MarketColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            structureFacts.chunked(2).forEach { facts ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    facts.forEach { (label, value) ->
                        ProductInfoLine(label, value, Modifier.weight(1f))
                    }
                    if (facts.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StructureBookmarkHandle(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = if (expanded) {
        RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
    } else {
        RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
    }
    Box(
        modifier = modifier
            .width(48.dp)
            .height(28.dp)
            .clip(shape)
            .background(MarketColors.Primary)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = if (expanded) "상품 구조 상세 접기" else "상품 구조 상세 펼치기"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (expanded) "↓" else "↑",
            style = MarketType.label.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}

private fun StockDefinition.structureClassification(): String = when (instrumentType) {
    InstrumentType.STOCK -> "사업회사"
    InstrumentType.ETF -> "ETF"
    InstrumentType.CLOSED_END_FUND -> "CEF"
    InstrumentType.ETN -> "ETN"
    InstrumentType.REIT -> "REIT"
    InstrumentType.ADR -> "ADR"
}

@Composable
private fun ProductInfoLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.width(72.dp),
            style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
            color = MarketColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MarketType.caption,
            color = MarketColors.Ink,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InstrumentSideRail(
    stock: StockDefinition,
    quote: Quote,
    metrics: InstrumentMetricsSnapshot?,
    orderBook: OrderBook?,
    side: OrderSide,
    selectedPrice: Double?,
    showOrderBook: Boolean,
    onShowMetrics: () -> Unit,
    onShowOrderBook: () -> Unit,
    onSelectPrice: (Double) -> Unit,
    modifier: Modifier,
) {
    val isShowingOrderBook = showOrderBook && orderBook != null
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SideRailTab(
                label = "지표",
                selected = !isShowingOrderBook,
                onClick = onShowMetrics,
                modifier = Modifier.weight(1f),
            )
            if (orderBook != null) {
                SideRailTab(
                    label = "호가",
                    selected = isShowingOrderBook,
                    onClick = onShowOrderBook,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (isShowingOrderBook) {
            OrderBookPanel(
                stock = stock,
                quote = quote,
                orderBook = requireNotNull(orderBook),
                side = side,
                selectedPrice = selectedPrice,
                onSelectPrice = onSelectPrice,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            InstrumentMetricsPanel(
                stock = stock,
                metrics = metrics,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun SideRailTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(MarketRadii.pill))
            .background(if (selected) MarketColors.Navy else MarketColors.PaperMuted)
            .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            style = MarketType.label,
            color = if (selected) Color.White else MarketColors.InkMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun InstrumentMetricsPanel(
    stock: StockDefinition,
    metrics: InstrumentMetricsSnapshot?,
    modifier: Modifier,
) {
    LedgerPanel(modifier, padding = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading(
                title = if (stock.isFundLike) "상품 핵심 지표" else "기업 핵심 지표",
                eyebrow = "KEY METRICS",
                modifier = Modifier.padding(12.dp),
            )
            LedgerDivider()
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "TTM/최근 산출",
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = MarketColors.InkMuted,
                )
                when (metrics) {
                    is CorporateMetricsSnapshot -> CorporateMetricsContent(stock, metrics)
                    is FundMetricsSnapshot -> FundMetricsContent(stock, metrics)
                    null -> MetricsUnavailableContent()
                }
            }
        }
    }
}

@Composable
private fun CorporateMetricsContent(
    stock: StockDefinition,
    metrics: CorporateMetricsSnapshot,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric(
            "시가총액",
            formatMoney(metrics.marketCapitalization, metrics.currency, compact = true),
            Modifier.weight(1f),
            detail = "현재가 × 발행주식",
        )
        Metric(
            "PER (TTM)",
            formatPriceEarnings(metrics),
            Modifier.weight(1f),
            detail = if (metrics.ttmNetIncome <= 0.0) "적자/이익 없음" else "최근 4분기",
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric(
            "PSR (TTM)",
            formatMultiple(metrics.priceSalesRatio),
            Modifier.weight(1f),
            detail = "최근 4분기",
        )
        Metric(
            "ROE (TTM)",
            formatNullablePercent(metrics.returnOnEquity),
            Modifier.weight(1f),
            detail = "평균 자기자본",
        )
    }
    Spacer(Modifier.height(12.dp))
    LedgerDivider()
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric("PBR", formatMultiple(metrics.priceToBookRatio), Modifier.weight(1f))
        Metric(
            "EPS (TTM)",
            formatNullableMoney(metrics.earningsPerShare, metrics.currency),
            Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric(
            "배당수익률",
            formatPercent(stock.dividendYield, withSign = false),
            Modifier.weight(1f),
            detail = "기준 배당 정책",
        )
        Metric(
            "발행주식",
            formatQuantity(metrics.sharesOutstanding, stock.quantityUnit),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun FundMetricsContent(
    stock: StockDefinition,
    metrics: FundMetricsSnapshot,
) {
    val isEtn = stock.instrumentType == InstrumentType.ETN
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric(
            "목표배율",
            formatRatio(metrics.targetLeverage, "x"),
            Modifier.weight(1f),
            detail = "계약값",
        )
        Metric(
            "연 운용보수",
            formatPercent(metrics.annualExpenseRatio, withSign = false),
            Modifier.weight(1f),
            detail = "계약값",
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric(
            if (isEtn) "발행잔액" else "운용자산 (AUM)",
            formatNullableCompactMoney(
                if (isEtn) metrics.outstandingNotional else metrics.assetsUnderManagement,
                metrics.currency,
            ),
            Modifier.weight(1f),
        )
        Metric(
            "시가총액",
            formatMoney(metrics.marketCapitalization, metrics.currency, compact = true),
            Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric(
            if (isEtn) "지표가치" else "NAV",
            formatPrice(
                if (isEtn) metrics.indicativeValuePerUnit else metrics.navPerUnit,
                metrics.currency,
            ),
            Modifier.weight(1f),
            detail = "1${stock.quantityUnit} 기준",
        )
        Metric(
            "괴리율",
            formatNullableSignedPercent(metrics.premiumDiscountRate),
            Modifier.weight(1f),
            detail = premiumDiscountLabel(metrics.premiumDiscountRate),
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric(
            "최근 순유입",
            formatNetFlow(metrics.lastNetFlow, metrics.currency),
            Modifier.weight(1f),
            valueColor = deltaColor(metrics.lastNetFlow),
            detail = "방향을 기호·문자로 병기",
        )
        Metric(
            if (isEtn) "발행증권" else "존속좌수",
            formatQuantity(metrics.unitsOrNotesOutstanding, stock.quantityUnit),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricsUnavailableContent() {
    Text(
        "지표 원시 상태를 산출하는 중이에요.",
        style = MarketType.body,
        color = MarketColors.Ink,
    )
    Text(
        "가격·실적·NAV가 준비되면 이 영역에서 같은 시점의 지표를 확인할 수 있어요.",
        style = MarketType.caption,
        color = MarketColors.InkMuted,
    )
}

private fun formatPriceEarnings(metrics: CorporateMetricsSnapshot): String =
    if (metrics.ttmNetIncome <= 0.0) "N/M" else formatMultiple(metrics.priceEarningsRatio)

private fun formatMultiple(value: Double?): String = value?.let { "${formatRatio(it)}배" } ?: "-"

private fun formatNullablePercent(value: Double?): String =
    value?.let { formatPercent(it, withSign = false) } ?: "-"

private fun formatNullableSignedPercent(value: Double?): String =
    value?.let { formatPercent(it, withSign = true) } ?: "-"

private fun formatNullableMoney(value: Double?, currency: Currency): String =
    value?.let { formatMoney(it, currency) } ?: "-"

private fun formatNullableCompactMoney(value: Double?, currency: Currency): String =
    value?.let { formatMoney(it, currency, compact = true) } ?: "-"

private fun premiumDiscountLabel(value: Double?): String = when {
    value == null -> "산출 불가"
    value > 0.0 -> "프리미엄 (+)"
    value < 0.0 -> "디스카운트 (-)"
    else -> "기준가와 일치 (=)"
}

private fun formatNetFlow(value: Double, currency: Currency): String = when {
    value > 0.0 -> "▲ 순유입 ${formatMoney(kotlin.math.abs(value), currency, compact = true)}"
    value < 0.0 -> "▼ 순유출 ${formatMoney(kotlin.math.abs(value), currency, compact = true)}"
    else -> "— 변동 없음"
}

private fun formatRatio(value: Double, suffix: String = ""): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return "$rounded$suffix"
}

/**
 * ETF 상장통화와 기초자산 통화를 분리해 원화 평가 기준의 실질 환노출을 요약한다.
 */
private fun fxExposureLabel(stock: StockDefinition): String? {
    val profile = stock.etfProfile ?: return null
    val fxProfile = profile.fxProfile
    val listingCurrency = if (stock.market.isUnitedStates) ReferenceCurrency.USD else ReferenceCurrency.KRW
    val foreignUnderlyingLegs = fxProfile.legs.filter {
        it.currency != listingCurrency && it.grossNotional >= 0.05
    }
    val unhedgedUnderlyingCurrencies = fxProfile.legs
        .filter { it.currency != listingCurrency && it.netNotional >= 0.05 }
        .map { it.currency }
    val isFullyHedgedToListing = foreignUnderlyingLegs.isNotEmpty() &&
        foreignUnderlyingLegs.all { it.hedgeRatioToListingCurrency >= 0.95 }

    if (stock.market.isUnitedStates && isFullyHedgedToListing) return "환헤지 · USD/KRW"
    if (stock.market.isKorean && isFullyHedgedToListing) return "환헤지(H)"
    if (unhedgedUnderlyingCurrencies.isEmpty()) {
        return if (stock.market.isUnitedStates) "USD/KRW 환노출" else "원화자산"
    }

    val currencyLabel = if (unhedgedUnderlyingCurrencies.size == 1) {
        unhedgedUnderlyingCurrencies.single().name
    } else {
        "다중통화"
    }
    return "$currencyLabel/KRW 환노출"
}

@Composable
private fun OrderBookPanel(
    stock: StockDefinition,
    quote: Quote,
    orderBook: OrderBook,
    side: OrderSide,
    selectedPrice: Double?,
    onSelectPrice: (Double) -> Unit,
    modifier: Modifier,
) {
    LedgerPanel(modifier, padding = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading(
                title = "${side.displayName} 지정가 선택",
                eyebrow = "10-LEVEL ORDER BOOK",
                modifier = Modifier.padding(12.dp),
            )
            LedgerDivider()
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("잔량", Modifier.weight(1f), style = MarketType.label, color = MarketColors.InkMuted)
                Text("가격", Modifier.weight(1f), style = MarketType.label, color = MarketColors.InkMuted)
                Text("건수", Modifier.weight(0.5f), style = MarketType.label, color = MarketColors.InkMuted)
            }
            orderBook.asks.take(10).reversed().forEach { level ->
                OrderBookRow(
                    price = level.price,
                    quantity = level.quantity,
                    orders = level.orderCount,
                    currency = stock.currency,
                    color = MarketColors.Rise,
                    selected = selectedPrice == level.price,
                    onClick = { onSelectPrice(level.price) },
                )
            }
            Row(
                Modifier.fillMaxWidth().background(MarketColors.PaperMuted).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("현재가", style = MarketType.label, color = MarketColors.InkMuted)
                Spacer(Modifier.weight(1f))
                Text(
                    formatPrice(quote.price, stock.currency),
                    style = MarketType.number,
                    color = deltaColor(quote.change),
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatPercent(quote.changeRate),
                    style = MarketType.number,
                    color = deltaColor(quote.change),
                    maxLines = 1,
                )
            }
            orderBook.bids.take(10).forEach { level ->
                OrderBookRow(
                    price = level.price,
                    quantity = level.quantity,
                    orders = level.orderCount,
                    currency = stock.currency,
                    color = MarketColors.Fall,
                    selected = selectedPrice == level.price,
                    onClick = { onSelectPrice(level.price) },
                )
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(8.dp)) {
                Text(
                    "매수 ${formatQuantity(orderBook.totalBidQuantity)}",
                    style = MarketType.label,
                    color = MarketColors.Fall,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "매도 ${formatQuantity(orderBook.totalAskQuantity)}",
                    style = MarketType.label,
                    color = MarketColors.Rise,
                )
            }
        }
    }
}

@Composable
private fun OrderBookRow(
    price: Double,
    quantity: Double,
    orders: Int,
    currency: Currency,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MarketColors.SignalSoft else color.copy(alpha = 0.055f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatQuantity(quantity),
            Modifier.weight(1f),
            style = MarketType.number,
            color = MarketColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatPrice(price, currency),
            Modifier.weight(1f),
            style = MarketType.number.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) MarketColors.Signal else color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            orders.toString(),
            Modifier.weight(0.5f),
            style = MarketType.number,
            color = MarketColors.InkMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun OrderTicketPanel(
    stock: StockDefinition,
    quote: Quote,
    holding: Holding?,
    cashKrw: Double,
    cashUsd: Double,
    side: OrderSide,
    onSideChange: (OrderSide) -> Unit,
    type: OrderType,
    onTypeChange: (OrderType) -> Unit,
    selectedBookPrice: Double?,
    onLimitPriceEdited: () -> Unit,
    protectionDetail: ProtectionDetailUi?,
    orderUnavailableReason: (OrderType) -> String?,
    onSubmitOrder: (OrderSide, OrderType, TimeInForce, Double, Double?) -> Unit,
    modifier: Modifier,
) {
    var timeInForce by remember(stock.id) { mutableStateOf(TimeInForce.DAY) }
    var quantityText by remember(stock.id) { mutableStateOf("1") }
    var limitPriceText by remember(stock.id) {
        mutableStateOf(formatPrice(quote.price, stock.currency, includeCurrency = false).replace(",", ""))
    }
    LaunchedEffect(stock.id, selectedBookPrice) {
        selectedBookPrice?.let { price ->
            limitPriceText = formatPrice(price, stock.currency, includeCurrency = false).replace(",", "")
        }
    }
    val quantity = quantityText.toDoubleOrNull() ?: 0.0
    val limitPrice = limitPriceText.toDoubleOrNull()
    val expectedPrice = if (type == OrderType.LIMIT) limitPrice ?: quote.price else quote.price
    val expectedAmount = quantity * expectedPrice
    val availableCash = if (stock.currency == Currency.KRW) cashKrw else cashUsd
    val protectionBlockReason = orderUnavailableReason(type)
    LedgerPanel(modifier, padding = 12.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SideTab("매수", side == OrderSide.BUY, MarketColors.Rise, Modifier.weight(1f)) {
                    onSideChange(OrderSide.BUY)
                }
                SideTab("매도", side == OrderSide.SELL, MarketColors.Fall, Modifier.weight(1f)) {
                    onSideChange(OrderSide.SELL)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("주문 방식", style = MarketType.label, color = MarketColors.InkMuted)
                Spacer(Modifier.weight(1f))
                FilterCell("시장가", type == OrderType.MARKET) { onTypeChange(OrderType.MARKET) }
                Spacer(Modifier.width(4.dp))
                FilterCell("지정가", type == OrderType.LIMIT) { onTypeChange(OrderType.LIMIT) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("유효 조건", style = MarketType.label, color = MarketColors.InkMuted)
                Spacer(Modifier.weight(1f))
                TimeInForce.entries.forEach { policy ->
                    FilterCell(
                        text = when (policy) {
                            TimeInForce.DAY -> "DAY"
                            TimeInForce.GOOD_TILL_CANCELLED -> "GTC"
                            TimeInForce.IMMEDIATE_OR_CANCEL -> "IOC"
                            TimeInForce.FILL_OR_KILL -> "FOK"
                        },
                        selected = policy == timeInForce,
                    ) { timeInForce = policy }
                    if (policy != TimeInForce.entries.last()) Spacer(Modifier.width(3.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (type == OrderType.LIMIT) {
                TicketField(
                    label = "주문 가격",
                    value = limitPriceText,
                    suffix = stock.currency.symbol,
                    onValueChange = {
                        limitPriceText = it.filter { c -> c.isDigit() || c == '.' }
                        onLimitPriceEdited()
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
            TicketField(
                label = "주문 수량",
                value = quantityText,
                suffix = stock.quantityUnit,
                onValueChange = { quantityText = it.filter { c -> c.isDigit() || c == '.' }.take(14) },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0.1, 0.25, 0.5, 1.0).forEach { ratio ->
                    val label = if (ratio == 1.0) "최대" else "${(ratio * 100).toInt()}%"
                    FilterCell(label, false) {
                        val maxQuantity = if (side == OrderSide.BUY) {
                            (availableCash / expectedPrice).coerceAtLeast(0.0)
                        } else {
                            holding?.quantity ?: 0.0
                        }
                        val stepped = kotlin.math.floor(maxQuantity * ratio / stock.quantityStep) * stock.quantityStep
                        quantityText = if (stock.supportsFractional) stepped.toString() else stepped.toLong().toString()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LedgerDivider()
            Spacer(Modifier.height(8.dp))
            Row {
                Text("예상 주문금액", style = MarketType.label, color = MarketColors.InkMuted)
                Spacer(Modifier.weight(1f))
                Text(formatMoney(expectedAmount, stock.currency), style = MarketType.number, color = MarketColors.Ink)
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    if (side == OrderSide.BUY) "주문 가능 현금" else "매도 가능 수량",
                    style = MarketType.label,
                    color = MarketColors.InkMuted,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (side == OrderSide.BUY) formatMoney(availableCash, stock.currency)
                    else formatQuantity(holding?.quantity ?: 0.0, stock.quantityUnit),
                    style = MarketType.number,
                    color = MarketColors.Ink,
                )
            }
            Spacer(Modifier.height(12.dp))
            protectionDetail?.let { detail ->
                Text(
                    text = protectionBlockReason ?: detail.primary.orderImpact,
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (protectionBlockReason == null) MarketColors.Primary else MarketColors.Rise,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
            }
            Text(
                when {
                    stock.etfProfile?.taxCategory == com.amond.kmpbook.domain.model.EtfTaxCategory.KOREAN_DOMESTIC_EQUITY ->
                        "ETF 거래세 면제 · 국내주식형 장내 매매차익 비과세"
                    stock.etfProfile?.taxCategory == com.amond.kmpbook.domain.model.EtfTaxCategory.KOREAN_OTHER ->
                        "ETF 거래세 면제 · 과표증분 한도 보유기간 과세 15.4%"
                    stock.isFundLike -> "국외 상장상품 양도손익은 연간 통산 후 다음 해 5월 신고"
                    stock.market == Market.KOSPI -> "매도 시 거래세 0.05% + 농특세 0.15% · 수수료 별도"
                    stock.market == Market.KOSDAQ -> "매도 시 거래세 0.20% · 수수료 별도"
                    else -> "양도세는 연간 손익으로 다음 해 5월 정산"
                },
                style = MarketType.caption,
                color = MarketColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            MarketButton(
                text = if (protectionBlockReason == null) {
                    "${stock.name} ${side.displayName} 주문"
                } else {
                    protectionDetail.disabledOrderCtaText()
                },
                onClick = {
                    if (quantity > 0.0) {
                        onSubmitOrder(
                            side,
                            type,
                            timeInForce,
                            quantity,
                            if (type == OrderType.LIMIT) limitPrice else null,
                        )
                    }
                },
                enabled = quantity > 0.0 && stock.acceptsQuantity(quantity) &&
                    (type == OrderType.MARKET || (limitPrice ?: 0.0) > 0.0) &&
                    protectionBlockReason == null,
                modifier = Modifier.fillMaxWidth(),
                tone = if (side == OrderSide.BUY) MarketButtonTone.Rise else MarketButtonTone.Fall,
            )
        }
    }
}

private fun ProtectionDetailUi?.disabledOrderCtaText(): String = when {
    this == null -> "지금은 주문할 수 없어요"
    primary.id.startsWith("market:") -> "시장 거래가 멈췄어요"
    primary.id.startsWith("stock:listing:") &&
        ("상장폐지" in primary.badgeLabel || "상품 종료" in primary.badgeLabel || "청산금" in primary.badgeLabel) ->
        "거래할 수 없는 종목이에요"
    else -> "거래가 멈췄어요"
}

@Composable
private fun SideTab(text: String, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(MarketComponentSize.minimumInteractiveTarget)
            .background(if (selected) color else MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.pill))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (selected) "✓ $text" else text,
            style = MarketType.label.copy(fontWeight = FontWeight.Bold),
            color = if (selected) Color.White else MarketColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TicketField(label: String, value: String, suffix: String, onValueChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(68.dp),
            style = MarketType.label,
            color = MarketColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).height(MarketComponentSize.textFieldHeight),
            textStyle = MarketType.number,
            singleLine = true,
            suffix = { Text(suffix, style = MarketType.label, color = MarketColors.InkMuted) },
        )
    }
}
