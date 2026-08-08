package com.amond.kmpbook.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Holding
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
    orderUnavailableReason: (stockId: String, orderType: OrderType) -> String? = { _, _ -> null },
    modifier: Modifier = Modifier,
) {
    val selectedStock = stocks.firstOrNull { it.id == selectedStockId } ?: stocks.firstOrNull()
    val quote = selectedStock?.let { quotes[it.id] }
    val bars = selectedStock?.let { priceHistory[it.id].orEmpty() }.orEmpty()
    var showSelectedProtectionDetail by remember(selectedStock?.id) { mutableStateOf(false) }

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
            modifier = Modifier.width(252.dp).fillMaxHeight(),
        )
        if (selectedStock != null && quote != null) {
            StockChartPanel(
                stock = selectedStock,
                quote = quote,
                bars = bars,
                holding = holding,
                watched = selectedStock.id in watchlistedStockIds,
                onToggleWatchlist = { onToggleWatchlist(selectedStock.id) },
                protectionBadge = protectionBadges[selectedStock.id],
                onOpenProtectionDetail = { showSelectedProtectionDetail = true },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Column(
                modifier = Modifier.width(304.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
            ) {
                OrderBookPanel(
                    stock = selectedStock,
                    quote = quote,
                    orderBook = orderBook,
                    modifier = Modifier.fillMaxWidth().weight(0.95f),
                )
                OrderTicketPanel(
                    stock = selectedStock,
                    quote = quote,
                    holding = holding,
                    cashKrw = cashKrw,
                    cashUsd = cashUsd,
                    protectionDetail = selectedProtectionDetail,
                    orderUnavailableReason = { type -> orderUnavailableReason(selectedStock.id, type) },
                    onSubmitOrder = onSubmitOrder,
                    modifier = Modifier.fillMaxWidth().weight(1.05f),
                )
            }
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
                SectionHeading("종목 시세", eyebrow = "WATCHLIST")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(30) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    placeholder = { Text("종목명·티커 검색", style = MarketType.label) },
                    textStyle = MarketType.body,
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    FilterCell("전체 종목", !watchlistOnly) { watchlistOnly = false }
                    FilterCell("★ 관심 ${watchlistedStockIds.size}", watchlistOnly) { watchlistOnly = true }
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    InstrumentFilter.entries.forEach { filter ->
                        FilterCell(filter.label, instrumentFilter == filter) { instrumentFilter = filter }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    VenueFilter.entries.take(4).forEach { filter ->
                        FilterCell(filter.label, venueFilter == filter) { venueFilter = filter }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    VenueFilter.entries.drop(4).forEach { filter ->
                        FilterCell(filter.label, venueFilter == filter) { venueFilter = filter }
                    }
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
                    "${filtered.size}개 상품 · 가격은 게임 시세",
                    style = MarketType.caption,
                    color = MarketColors.InkMuted,
                )
            }
        }
    }
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
            .background(if (selected) MarketColors.CeladonSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 29.dp)
                .background(if (selected) MarketColors.Celadon else Color.Transparent),
        )
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(MarketRadii.pill))
                .clickable(onClick = onToggleWatchlist)
                .padding(horizontal = 3.dp, vertical = 4.dp),
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
    watched: Boolean,
    onToggleWatchlist: () -> Unit,
    protectionBadge: ProtectionStatusBadgeUi?,
    onOpenProtectionDetail: () -> Unit,
    modifier: Modifier,
) {
    var range by remember { mutableStateOf("1개월") }
    val uriHandler = LocalUriHandler.current
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
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stock.name,
                            style = MarketType.display.copy(fontSize = 24.sp),
                            color = MarketColors.Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${stock.symbol} · ${stock.englishName}",
                            style = MarketType.label,
                            color = MarketColors.InkMuted,
                            maxLines = 2,
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
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    StatusLabel(stock.instrumentType.displayName, MarketColors.Celadon)
                    StatusLabel(stock.market.displayName, MarketColors.InkMuted)
                    StatusLabel(stock.behavior.strategy.displayName, MarketColors.Primary)
                }
                if (protectionBadge != null) {
                    Spacer(Modifier.height(4.dp))
                    ProtectionStatusBadge(
                        model = protectionBadge,
                        onClick = onOpenProtectionDetail,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    fxExposureLabel(stock)?.let { label ->
                        StatusLabel(
                            text = label,
                            color = if ("헤지" in label) MarketColors.Celadon else MarketColors.Amber,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(MarketRadii.pill))
                            .background(if (watched) MarketColors.Amber.copy(alpha = 0.12f) else MarketColors.PaperMuted)
                            .clickable(onClick = onToggleWatchlist)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
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
                                .clickable { uriHandler.openUri(identity.officialSourceUrl) }
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        ) {
                            Text("공식 자료 ↗", style = MarketType.caption, color = MarketColors.Primary)
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    buildString {
                        append(stock.sector.displayName)
                        stock.etfProfile?.let { append(" · ${it.assetClass.displayName} · ${it.benchmark}") }
                    },
                    style = MarketType.label,
                    color = MarketColors.InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(13.dp))
                Row(Modifier.fillMaxWidth()) {
                    Metric("시가", formatPrice(quote.open, stock.currency), Modifier.weight(1f))
                    Metric("고가", formatPrice(quote.high, stock.currency), Modifier.weight(1f), MarketColors.Rise)
                    Metric("저가", formatPrice(quote.low, stock.currency), Modifier.weight(1f), MarketColors.Fall)
                    Metric("거래량", formatQuantity(quote.volume.toDouble()), Modifier.weight(1f))
                    stock.etfProfile?.let { profile ->
                        Metric(
                            "연 보수",
                            formatPercent(profile.annualExpenseRatio, withSign = false),
                            Modifier.weight(1f),
                            detail = "배율 ${profile.leverage}x",
                        )
                    } ?: Metric("연 변동성", formatPercent(stock.volatility, withSign = false), Modifier.weight(1f))
                }
            }
            LedgerDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("캔들", style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
                Spacer(Modifier.width(10.dp))
                LegendDot(MarketColors.Rise, "상승")
                Spacer(Modifier.width(8.dp))
                LegendDot(MarketColors.Fall, "하락")
                Spacer(Modifier.width(8.dp))
                LegendDot(MarketColors.Amber, "MA 5")
                Spacer(Modifier.width(8.dp))
                LegendDot(MarketColors.Celadon, "MA 20")
                Spacer(Modifier.weight(1f))
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
            ProductStructurePanel(
                stock = stock,
                holding = holding,
                modifier = Modifier.fillMaxWidth().height(190.dp),
            )
        }
    }
}

@Composable
private fun ProductStructurePanel(
    stock: StockDefinition,
    holding: Holding?,
    modifier: Modifier,
) {
    val scrollState = remember(stock.id) { ScrollState(initial = 0) }
    val identity = stock.identityProfile
    val contractFacts = buildList {
        identity?.maturityDate?.let { add("만기 $it") }
        if (identity?.callable == true) add("발행사 조기상환 가능")
        identity?.adrUnderlyingShareRatio?.let { ratio ->
            add("1 ADR/ADS = 본주 ${formatQuantity(ratio)}")
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("내 포지션", style = MarketType.label, color = MarketColors.InkMuted)
                Text(
                    holding?.let {
                        "${formatQuantity(it.quantity, stock.quantityUnit)} · 평균 ${formatPrice(it.averagePrice, stock.currency)}"
                    } ?: "보유 수량 없음",
                    style = MarketType.number,
                    color = MarketColors.Ink,
                )
            }
            if (holding != null) {
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
        }
        LedgerDivider()
        Text(
            if (stock.isFundLike) "상품 구조와 위험" else "기업 구조와 위험",
            style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
            color = MarketColors.Ink,
        )
        Text(
            identity?.strategySummary ?: stock.description,
            style = MarketType.caption,
            color = MarketColors.Ink,
        )
        ProductInfoLine(
            label = "기본 구조",
            value = buildString {
                append(stock.instrumentType.displayName)
                append(" · ${stock.behavior.strategy.displayName}")
                append(" · ${stock.behavior.distributionFrequency.displayName}")
                stock.etfProfile?.let { append(" · 목표배율 ${it.leverage}x") }
            },
        )
        stock.etfProfile?.let { profile ->
            ProductInfoLine("기초자산", "${profile.assetClass.displayName} · ${profile.benchmark}")
            ProductInfoLine("세금 분류", profile.taxCategory.displayName)
        }
        identity?.let {
            ProductInfoLine("운용·발행", it.issuerOrManager)
            ProductInfoLine("분배", it.distributionNotes)
        }
        if (contractFacts.isNotEmpty()) {
            ProductInfoLine("계약", contractFacts.joinToString(" · "))
        }
        ProductInfoLine(
            "핵심 위험",
            "${stock.behavior.principalRisk.displayName} · ${stock.behavior.principalRisk.explanation}",
        )
    }
}

@Composable
private fun ProductInfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.width(72.dp),
            style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
            color = MarketColors.InkMuted,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MarketType.caption,
            color = MarketColors.Ink,
        )
    }
}

/**
 * ETF 상장통화와 기초자산 통화를 분리해 원화 평가 기준의 실질 환노출을 요약한다.
 */
private fun fxExposureLabel(stock: StockDefinition): String? {
    val profile = stock.etfProfile ?: return null
    val fxProfile = profile.fxProfile
        ?: return if (profile.legacyUsdKrwSensitivity != 0.0) "USD/KRW 환노출" else null
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
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).background(color, RoundedCornerShape(50)))
        Text(text, style = MarketType.caption, color = MarketColors.InkMuted)
    }
}

@Composable
private fun OrderBookPanel(
    stock: StockDefinition,
    quote: Quote,
    orderBook: OrderBook?,
    modifier: Modifier,
) {
    LedgerPanel(modifier, padding = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading("10단계 호가", eyebrow = "ORDER BOOK", modifier = Modifier.padding(12.dp))
            LedgerDivider()
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("잔량", Modifier.weight(1f), style = MarketType.label, color = MarketColors.InkMuted)
                Text("가격", Modifier.weight(1f), style = MarketType.label, color = MarketColors.InkMuted)
                Text("건수", Modifier.weight(0.5f), style = MarketType.label, color = MarketColors.InkMuted)
            }
            LazyColumn(Modifier.weight(1f)) {
                val asks = orderBook?.asks.orEmpty().take(10).reversed()
                items(asks) { level ->
                    OrderBookRow(
                        price = level.price,
                        quantity = level.quantity,
                        orders = level.orderCount,
                        currency = stock.currency,
                        color = MarketColors.Rise,
                    )
                }
                item {
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
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(formatPercent(quote.changeRate), style = MarketType.number, color = deltaColor(quote.change))
                    }
                }
                val bids = orderBook?.bids.orEmpty().take(10)
                items(bids) { level ->
                    OrderBookRow(
                        price = level.price,
                        quantity = level.quantity,
                        orders = level.orderCount,
                        currency = stock.currency,
                        color = MarketColors.Fall,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp)) {
                Text("매수 ${formatQuantity(orderBook?.totalBidQuantity ?: 0.0)}", style = MarketType.label, color = MarketColors.Fall)
                Spacer(Modifier.weight(1f))
                Text("매도 ${formatQuantity(orderBook?.totalAskQuantity ?: 0.0)}", style = MarketType.label, color = MarketColors.Rise)
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
) {
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.055f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(formatQuantity(quantity), Modifier.weight(1f), style = MarketType.number, color = MarketColors.InkMuted)
        Text(formatPrice(price, currency), Modifier.weight(1f), style = MarketType.number, color = color)
        Text(orders.toString(), Modifier.weight(0.5f), style = MarketType.number, color = MarketColors.InkMuted)
    }
}

@Composable
private fun OrderTicketPanel(
    stock: StockDefinition,
    quote: Quote,
    holding: Holding?,
    cashKrw: Double,
    cashUsd: Double,
    protectionDetail: ProtectionDetailUi?,
    orderUnavailableReason: (OrderType) -> String?,
    onSubmitOrder: (OrderSide, OrderType, TimeInForce, Double, Double?) -> Unit,
    modifier: Modifier,
) {
    var side by remember(stock.id) { mutableStateOf(OrderSide.BUY) }
    var type by remember(stock.id) { mutableStateOf(OrderType.MARKET) }
    var timeInForce by remember(stock.id) { mutableStateOf(TimeInForce.DAY) }
    var quantityText by remember(stock.id) { mutableStateOf("1") }
    var limitPriceText by remember(stock.id, quote.price) {
        mutableStateOf(formatPrice(quote.price, stock.currency, includeCurrency = false).replace(",", ""))
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
                SideTab("매수", side == OrderSide.BUY, MarketColors.Rise, Modifier.weight(1f)) { side = OrderSide.BUY }
                SideTab("매도", side == OrderSide.SELL, MarketColors.Fall, Modifier.weight(1f)) { side = OrderSide.SELL }
            }
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("주문 방식", style = MarketType.label, color = MarketColors.InkMuted)
                Spacer(Modifier.weight(1f))
                FilterCell("시장가", type == OrderType.MARKET) { type = OrderType.MARKET }
                Spacer(Modifier.width(4.dp))
                FilterCell("지정가", type == OrderType.LIMIT) { type = OrderType.LIMIT }
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
                    onValueChange = { limitPriceText = it.filter { c -> c.isDigit() || c == '.' } },
                )
                Spacer(Modifier.height(6.dp))
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
            Spacer(Modifier.weight(1f))
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

private enum class InstrumentFilter(val label: String) {
    ALL("전체"),
    STOCK("기업"),
    KOREAN_ETF("국내상품"),
    US_ETF("미국상품"),
    ;

    fun matches(stock: StockDefinition): Boolean = when (this) {
        ALL -> true
        STOCK -> stock.hasCorporateEarnings
        KOREAN_ETF -> stock.isFundLike && stock.market.isKorean
        US_ETF -> stock.isFundLike && stock.market.isUnitedStates
    }
}

private enum class VenueFilter(val label: String) {
    ALL("전체"),
    KOSPI("코스피"),
    KOSDAQ("코스닥"),
    US_ALL("미국"),
    NASDAQ("Nasdaq"),
    NYSE("NYSE"),
    ARCA("Arca"),
    BZX("BZX"),
    AMERICAN("Amex"),
    ;

    fun matches(stock: StockDefinition): Boolean = when (this) {
        ALL -> true
        KOSPI -> stock.market == Market.KOSPI
        KOSDAQ -> stock.market == Market.KOSDAQ
        US_ALL -> stock.market.isUnitedStates
        NASDAQ -> stock.market == Market.NASDAQ
        NYSE -> stock.market == Market.NYSE
        ARCA -> stock.market == Market.NYSE_ARCA
        BZX -> stock.market == Market.CBOE_BZX
        AMERICAN -> stock.market == Market.NYSE_AMERICAN
    }
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
        )
    }
}

@Composable
private fun TicketField(label: String, value: String, suffix: String, onValueChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(68.dp), style = MarketType.label, color = MarketColors.InkMuted)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).height(42.dp),
            textStyle = MarketType.number,
            singleLine = true,
            suffix = { Text(suffix, style = MarketType.label, color = MarketColors.InkMuted) },
        )
    }
}
