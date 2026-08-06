package com.amond.kmpbook.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.PortfolioSnapshot
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.ui.charts.AllocationDonut
import com.amond.kmpbook.ui.charts.LineAreaChart
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.Metric
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.components.deltaColor
import com.amond.kmpbook.ui.format.formatMoney
import com.amond.kmpbook.ui.format.formatPercent
import com.amond.kmpbook.ui.format.formatPrice
import com.amond.kmpbook.ui.format.formatQuantity
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun HomeDashboardScreen(
    snapshot: PortfolioSnapshot,
    history: List<PortfolioSnapshot>,
    stocks: List<StockDefinition>,
    quotes: Map<String, Quote>,
    events: List<GameEvent>,
    estimatedTaxKrw: Double,
    usdKrw: Double,
    maxDrawdown: Double,
    onOpenStock: (String) -> Unit,
    onOpenEvents: () -> Unit,
    onOpenTax: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stockById = stocks.associateBy { it.id }
    val totalAssets = snapshot.totalAssetValueKrw
    val holdings = snapshot.holdings.sortedByDescending { holding ->
        holding.marketValue * snapshot.rateToKrw(holding.currency)
    }
    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroAssetPanel(snapshot, estimatedTaxKrw, Modifier.weight(1.3f).height(148.dp))
            MarketPulsePanel(stocks, quotes, usdKrw, Modifier.weight(1f).height(148.dp))
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1.42f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EquityCurvePanel(
                    history = history,
                    currentAssets = totalAssets,
                    maxDrawdown = maxDrawdown,
                    modifier = Modifier.fillMaxWidth().weight(1.1f),
                )
                HoldingsPanel(
                    holdings = holdings,
                    stockById = stockById,
                    snapshot = snapshot,
                    onOpenStock = onOpenStock,
                    modifier = Modifier.fillMaxWidth().weight(0.9f),
                )
            }
            Column(Modifier.width(332.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AllocationPanel(
                    holdings = holdings,
                    stockById = stockById,
                    snapshot = snapshot,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
                NewsPanel(
                    events = events.takeLast(8).reversed(),
                    onOpenEvents = onOpenEvents,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                TaxReminderPanel(
                    estimatedTaxKrw = estimatedTaxKrw,
                    onClick = onOpenTax,
                    modifier = Modifier.fillMaxWidth().height(82.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroAssetPanel(snapshot: PortfolioSnapshot, estimatedTaxKrw: Double, modifier: Modifier) {
    LedgerPanel(modifier, background = MarketColors.NavyRaised, padding = 18.dp) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "순자산",
                    style = MarketType.label.copy(letterSpacing = 0.7.sp),
                    color = Color.White.copy(alpha = 0.5f),
                )
                Text(
                    formatMoney(snapshot.totalAssetValueKrw, Currency.KRW),
                    style = MarketType.numberLarge.copy(fontSize = 28.sp),
                    color = Color.White,
                )
                Spacer(Modifier.height(5.dp))
                StatusLabel(
                    text = "${formatPercent(snapshot.totalReturnRate)} · 누적",
                    color = deltaColor(snapshot.totalProfitKrw),
                    strong = true,
                )
            }
            Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Metric(
                        "총 손익",
                        formatMoney(snapshot.totalProfitKrw, Currency.KRW),
                        Modifier.weight(1f),
                        deltaColor(snapshot.totalProfitKrw),
                    )
                    Metric(
                        "현금 비중",
                        formatPercent(snapshot.cashWeight, false),
                        Modifier.weight(1f),
                        Color.White,
                    )
                }
                Row {
                    Metric(
                        "누적 거래비용",
                        formatMoney(snapshot.cumulativeCommissionKrw, Currency.KRW),
                        Modifier.weight(1f),
                        Color.White.copy(alpha = 0.82f),
                    )
                    Metric(
                        "예상 납부세",
                        formatMoney(estimatedTaxKrw, Currency.KRW),
                        Modifier.weight(1f),
                        MarketColors.AmberSoft,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketPulsePanel(
    stocks: List<StockDefinition>,
    quotes: Map<String, Quote>,
    usdKrw: Double,
    modifier: Modifier,
) {
    LedgerPanel(modifier) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading("시장 온도", eyebrow = "MARKET PULSE")
            Spacer(Modifier.height(10.dp))
            val markets = listOf(Market.KOSPI, Market.KOSDAQ, Market.NASDAQ, Market.NYSE)
            Row(Modifier.fillMaxWidth()) {
                markets.forEach { market ->
                    val ids = stocks.filter { it.market == market }.map { it.id }.toSet()
                    val changes = quotes.values.filter { it.stockId in ids }.map { it.changeRate }
                    val average = changes.takeIf { it.isNotEmpty() }?.average() ?: 0.0
                    Column(Modifier.weight(1f)) {
                        Text(market.displayName, style = MarketType.label, color = MarketColors.InkMuted, maxLines = 1)
                        Text(formatPercent(average), style = MarketType.number, color = deltaColor(average))
                        Text(
                            "상승 ${changes.count { it > 0 }} · 하락 ${changes.count { it < 0 }}",
                            style = MarketType.label.copy(fontSize = 8.sp),
                            color = MarketColors.InkMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            LedgerDivider()
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("USD/KRW", style = MarketType.label, color = MarketColors.InkMuted)
                Spacer(Modifier.width(8.dp))
                Text(formatPrice(usdKrw, Currency.KRW), style = MarketType.number, color = MarketColors.Ink)
                Spacer(Modifier.weight(1f))
                Text("환율도 매 시간 변동", style = MarketType.label.copy(fontSize = 9.sp), color = MarketColors.Celadon)
            }
        }
    }
}

@Composable
private fun EquityCurvePanel(
    history: List<PortfolioSnapshot>,
    currentAssets: Double,
    maxDrawdown: Double,
    modifier: Modifier,
) {
    LedgerPanel(modifier) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading("자산 곡선", eyebrow = "EQUITY CURVE") {
                StatusLabel("최대낙폭 ${formatPercent(maxDrawdown, false)}", MarketColors.Amber)
            }
            Spacer(Modifier.height(12.dp))
            LineAreaChart(
                values = history.map { it.totalAssetValueKrw }.ifEmpty { listOf(currentAssets, currentAssets) },
                baseline = history.firstOrNull()?.initialCapitalKrw,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Text("게임 시작", style = MarketType.label.copy(fontSize = 9.sp), color = MarketColors.InkMuted)
                Spacer(Modifier.weight(1f))
                Text("현재 · ${history.size}개 일별 기록", style = MarketType.label.copy(fontSize = 9.sp), color = MarketColors.InkMuted)
            }
        }
    }
}

@Composable
private fun HoldingsPanel(
    holdings: List<Holding>,
    stockById: Map<String, StockDefinition>,
    snapshot: PortfolioSnapshot,
    onOpenStock: (String) -> Unit,
    modifier: Modifier,
) {
    LedgerPanel(modifier, padding = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading("보유 종목", eyebrow = "POSITIONS", modifier = Modifier.padding(12.dp))
            LedgerDivider()
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                TableLabel("종목", Modifier.weight(1.5f))
                TableLabel("수량", Modifier.weight(0.7f))
                TableLabel("평균단가", Modifier.weight(1f))
                TableLabel("평가액", Modifier.weight(1f))
                TableLabel("수익률", Modifier.weight(0.8f), Alignment.End)
            }
            if (holdings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("시장 화면에서 첫 종목을 매수해 보세요.", style = MarketType.body, color = MarketColors.InkMuted)
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(holdings, key = { it.stockId }) { holding ->
                        val stock = stockById[holding.stockId] ?: return@items
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenStock(stock.id) }.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1.5f)) {
                                Text(stock.name, style = MarketType.body, color = MarketColors.Ink, maxLines = 1)
                                Text(stock.symbol, style = MarketType.label.copy(fontSize = 9.sp), color = MarketColors.InkMuted)
                            }
                            Text(formatQuantity(holding.quantity), Modifier.weight(0.7f), style = MarketType.number.copy(fontSize = 10.sp))
                            Text(formatPrice(holding.averagePrice, stock.currency), Modifier.weight(1f), style = MarketType.number.copy(fontSize = 10.sp))
                            Text(
                                formatMoney(holding.marketValue * snapshot.rateToKrw(holding.currency), Currency.KRW, compact = true),
                                Modifier.weight(1f),
                                style = MarketType.number.copy(fontSize = 10.sp),
                            )
                            Text(
                                formatPercent(holding.returnRate),
                                Modifier.weight(0.8f),
                                style = MarketType.number.copy(fontSize = 10.sp),
                                color = deltaColor(holding.unrealizedProfit),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllocationPanel(
    holdings: List<Holding>,
    stockById: Map<String, StockDefinition>,
    snapshot: PortfolioSnapshot,
    modifier: Modifier,
) {
    val byMarket = holdings.groupBy { stockById[it.stockId]?.market }.mapValues { (_, group) ->
        group.sumOf { it.marketValue * snapshot.rateToKrw(it.currency) }
    }
    val slices = listOf(
        "현금" to snapshot.cashValueKrw,
        "코스피" to (byMarket[Market.KOSPI] ?: 0.0),
        "코스닥" to (byMarket[Market.KOSDAQ] ?: 0.0),
        "미국" to ((byMarket[Market.NASDAQ] ?: 0.0) + (byMarket[Market.NYSE] ?: 0.0)),
    )
    val colors = listOf(MarketColors.InkMuted, MarketColors.Celadon, MarketColors.Rise, MarketColors.Fall)
    LedgerPanel(modifier) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading("자산 배분", eyebrow = "ALLOCATION")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(114.dp), contentAlignment = Alignment.Center) {
                    AllocationDonut(
                        values = slices.mapIndexed { index, item -> item.second to colors[index] },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("주식", style = MarketType.label, color = MarketColors.InkMuted)
                        Text(formatPercent(snapshot.stockWeight, false), style = MarketType.number, color = MarketColors.Ink)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    slices.forEachIndexed { index, (label, value) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(colors[index], RoundedCornerShape(50)))
                            Spacer(Modifier.width(6.dp))
                            Text(label, Modifier.weight(1f), style = MarketType.label, color = MarketColors.InkMuted)
                            Text(
                                formatPercent(if (snapshot.totalAssetValueKrw == 0.0) 0.0 else value / snapshot.totalAssetValueKrw, false),
                                style = MarketType.number.copy(fontSize = 10.sp),
                                color = MarketColors.Ink,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsPanel(events: List<GameEvent>, onOpenEvents: () -> Unit, modifier: Modifier) {
    LedgerPanel(modifier, padding = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading(
                "시장 뉴스",
                eyebrow = "EVENT FEED",
                modifier = Modifier.padding(12.dp),
                action = {
                    Text(
                        "전체 보기 →",
                        modifier = Modifier.clickable(onClick = onOpenEvents),
                        style = MarketType.label,
                        color = MarketColors.Celadon,
                    )
                },
            )
            LedgerDivider()
            if (events.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("아직 발생한 이벤트가 없습니다.", style = MarketType.label, color = MarketColors.InkMuted)
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(events, key = { it.id }) { event -> EventFeedRow(event) }
                }
            }
        }
    }
}

@Composable
private fun EventFeedRow(event: GameEvent) {
    val color = when (event.impact.direction) {
        ImpactDirection.POSITIVE -> MarketColors.Rise
        ImpactDirection.NEGATIVE -> MarketColors.Fall
        ImpactDirection.MIXED -> MarketColors.Amber
        ImpactDirection.NEUTRAL -> MarketColors.InkMuted
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (event.severity >= EventSeverity.MAJOR) color else color.copy(alpha = 0.45f),
                    RoundedCornerShape(50),
                ),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MarketType.body.copy(fontWeight = FontWeight.Medium), color = MarketColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${event.type.displayName} · ${event.severity.displayName} · ${event.durationHours}시간",
                style = MarketType.label.copy(fontSize = 9.sp),
                color = MarketColors.InkMuted,
            )
        }
    }
}

@Composable
private fun TaxReminderPanel(estimatedTaxKrw: Double, onClick: () -> Unit, modifier: Modifier) {
    LedgerPanel(
        modifier.clickable(onClick = onClick),
        background = MarketColors.AmberSoft,
        padding = 12.dp,
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("세금 센터", style = MarketType.label.copy(fontWeight = FontWeight.Bold), color = MarketColors.Amber)
                Text(formatMoney(estimatedTaxKrw, Currency.KRW), style = MarketType.number, color = MarketColors.Ink)
                Text("현재 연도 예상 납부액", style = MarketType.label.copy(fontSize = 9.sp), color = MarketColors.InkMuted)
            }
            Text("→", style = MarketType.heading, color = MarketColors.Amber)
        }
    }
}

@Composable
private fun TableLabel(text: String, modifier: Modifier, alignment: Alignment.Horizontal = Alignment.Start) {
    Box(modifier, contentAlignment = when (alignment) {
        Alignment.End -> Alignment.CenterEnd
        Alignment.CenterHorizontally -> Alignment.Center
        else -> Alignment.CenterStart
    }) {
        Text(text, style = MarketType.label, color = MarketColors.InkMuted)
    }
}

