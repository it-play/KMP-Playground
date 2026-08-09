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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.domain.model.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.PortfolioSnapshot
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.presentation.NewsEffectState
import com.amond.kmpbook.presentation.NewsStoryUi
import com.amond.kmpbook.presentation.NewsUiProjection
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
import com.amond.kmpbook.ui.theme.MarketComponentSize
import com.amond.kmpbook.ui.theme.MarketLayout
import com.amond.kmpbook.ui.theme.MarketSpacing
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun HomeDashboardScreen(
    snapshot: PortfolioSnapshot,
    history: List<PortfolioSnapshot>,
    stocks: List<StockDefinition>,
    marketIndices: Map<MarketIndexId, MarketIndexSnapshot>,
    news: NewsUiProjection,
    estimatedTaxKrw: Double,
    usdKrw: Double,
    maxDrawdown: Double,
    onOpenStock: (String) -> Unit,
    onOpenEvents: (String?) -> Unit,
    onOpenTax: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stockById = stocks.associateBy { it.id }
    val totalAssets = snapshot.totalAssetValueKrw
    val holdings = snapshot.holdings.sortedByDescending { holding ->
        holding.marketValue * snapshot.rateToKrw(holding.currency)
    }
    Column(
        modifier = modifier.fillMaxSize().padding(MarketLayout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MarketLayout.screenGap)) {
            HeroAssetPanel(snapshot, estimatedTaxKrw, Modifier.weight(1.3f).height(148.dp))
            MarketPulsePanel(
                indices = marketIndices,
                usdKrw = usdKrw,
                modifier = Modifier.weight(1f).height(148.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
        ) {
            Column(
                Modifier.weight(1.42f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
            ) {
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
            Column(
                Modifier.width(MarketLayout.detailRailWidth).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
            ) {
                AllocationPanel(
                    holdings = holdings,
                    stockById = stockById,
                    snapshot = snapshot,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
                NewsPanel(
                    stories = news.homeStories,
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
                    style = MarketType.label.copy(letterSpacing = 0.2.sp),
                    color = Color.White.copy(alpha = 0.72f),
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
                        labelColor = Color.White.copy(alpha = 0.68f),
                    )
                    Metric(
                        "현금 비중",
                        formatPercent(snapshot.cashWeight, false),
                        Modifier.weight(1f),
                        Color.White,
                        labelColor = Color.White.copy(alpha = 0.68f),
                    )
                }
                Row {
                    Metric(
                        "누적 거래비용",
                        formatMoney(snapshot.cumulativeCommissionKrw, Currency.KRW),
                        Modifier.weight(1f),
                        Color.White.copy(alpha = 0.82f),
                        labelColor = Color.White.copy(alpha = 0.68f),
                    )
                    Metric(
                        "예상 납부세",
                        formatMoney(estimatedTaxKrw, Currency.KRW),
                        Modifier.weight(1f),
                        MarketColors.AmberSoft,
                        labelColor = Color.White.copy(alpha = 0.68f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketPulsePanel(
    indices: Map<MarketIndexId, MarketIndexSnapshot>,
    usdKrw: Double,
    modifier: Modifier,
) {
    LedgerPanel(modifier) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading("미국 대표 지수", eyebrow = "SIMULATION INDEX") {
                StatusLabel("게임 지수", MarketColors.Primary)
            }
            Spacer(Modifier.height(10.dp))
            val indexIds = listOf(
                MarketIndexId.SP_500,
                MarketIndexId.NASDAQ_COMPOSITE,
                MarketIndexId.DOW_JONES_INDUSTRIAL_AVERAGE,
                MarketIndexId.VIX,
            )
            Row(Modifier.fillMaxWidth()) {
                indexIds.forEach { id ->
                    val index = indices[id]
                    Column(Modifier.weight(1f)) {
                        Text(id.code, style = MarketType.label, color = MarketColors.InkMuted, maxLines = 1)
                        Text(
                            index?.let { "${formatPrice(it.value, Currency.USD, includeCurrency = false)} pt" } ?: "-",
                            style = MarketType.number,
                            color = MarketColors.Ink,
                            maxLines = 1,
                        )
                        Text(
                            index?.let { formatPercent(it.changeRate) } ?: "-",
                            style = MarketType.caption,
                            color = deltaColor(index?.changeRate ?: 0.0),
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
                Text("지수 산식·통화 바스켓 매시간 갱신", style = MarketType.caption, color = MarketColors.Primary)
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
                Text("게임 시작", style = MarketType.caption, color = MarketColors.InkMuted)
                Spacer(Modifier.weight(1f))
                Text("현재 · ${history.size}개 일별 기록", style = MarketType.caption, color = MarketColors.InkMuted)
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
                                Text(stock.symbol, style = MarketType.caption, color = MarketColors.InkMuted)
                            }
                            Text(formatQuantity(holding.quantity), Modifier.weight(0.7f), style = MarketType.number)
                            Text(formatPrice(holding.averagePrice, stock.currency), Modifier.weight(1f), style = MarketType.number)
                            Text(
                                formatMoney(holding.marketValue * snapshot.rateToKrw(holding.currency), Currency.KRW, compact = true),
                                Modifier.weight(1f),
                                style = MarketType.number,
                            )
                            Text(
                                formatPercent(snapshot.holdingReturnRateKrw(holding)),
                                Modifier.weight(0.8f),
                                style = MarketType.number,
                                color = deltaColor(snapshot.holdingUnrealizedProfitKrw(holding)),
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
        "미국" to byMarket.entries.filter { it.key?.isUnitedStates == true }.sumOf { it.value },
    )
    val colors = listOf(MarketColors.InkMuted, MarketColors.Primary, MarketColors.Rise, MarketColors.Fall)
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
                        Text("투자", style = MarketType.label, color = MarketColors.InkMuted)
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
                                style = MarketType.number,
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
private fun NewsPanel(stories: List<NewsStoryUi>, onOpenEvents: (String?) -> Unit, modifier: Modifier) {
    LedgerPanel(modifier, padding = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading(
                "시장 뉴스",
                eyebrow = "내 종목과 시장 연결",
                modifier = Modifier.padding(MarketSpacing.sm),
                action = {
                    Box(
                        Modifier
                            .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
                            .clickable(role = Role.Button) { onOpenEvents(null) }
                            .padding(horizontal = MarketSpacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("전체 보기 →", style = MarketType.label, color = MarketColors.Primary)
                    }
                },
            )
            LedgerDivider()
            if (stories.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("아직 발생한 이벤트가 없습니다.", style = MarketType.label, color = MarketColors.InkMuted)
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(stories, key = { it.event.id }) { story ->
                        EventFeedRow(story) { onOpenEvents(story.event.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventFeedRow(story: NewsStoryUi, onClick: () -> Unit) {
    val statusColor = when (story.status.state) {
        NewsEffectState.UPCOMING,
        NewsEffectState.WAITING_FOR_MARKET,
        NewsEffectState.PROCESS_ACTIVE,
        -> MarketColors.PrimaryText
        NewsEffectState.MARKET_ACTIVE -> MarketColors.AmberText
        NewsEffectState.RESTRICTION_ACTIVE -> MarketColors.NavyRaised
        NewsEffectState.INFORMATION -> MarketColors.InkMuted
        NewsEffectState.MARKET_ENDED,
        NewsEffectState.RESOLVED,
        -> MarketColors.InkMuted
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(statusColor, RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                story.event.title,
                style = MarketType.body.copy(fontWeight = FontWeight.Medium),
                color = MarketColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(story.status.label)
                        story.secondaryStatus?.let { append(" · ${it.label}") }
                    },
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColor,
                )
                Text(
                    " · ${story.event.type.displayName}",
                    modifier = Modifier.weight(1f),
                    style = MarketType.caption,
                    color = MarketColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val paths = story.impactPaths.take(2)
            if (paths.isEmpty()) {
                Text(
                    story.personalDirection.newsDirectionLabel,
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = story.personalDirection.newsDirectionColor,
                )
            } else {
                paths.forEach { path ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
                    ) {
                        Box(
                            Modifier.size(5.dp).background(
                                path.direction.newsDirectionColor,
                                RoundedCornerShape(50),
                            ),
                        )
                        Text(
                            path.label,
                            modifier = Modifier.weight(1f),
                            style = MarketType.caption,
                            color = MarketColors.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            path.direction.newsDirectionLabel,
                            style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = path.direction.newsDirectionColor,
                        )
                    }
                }
                if (story.impactPaths.size > paths.size) {
                    Text(
                        "외 ${story.impactPaths.size - paths.size}개 영향 경로",
                        style = MarketType.caption,
                        color = MarketColors.InkMuted,
                    )
                }
            }
        }
    }
}

private val ImpactDirection.newsDirectionLabel: String
    get() = when (this) {
        ImpactDirection.POSITIVE -> "긍정"
        ImpactDirection.NEGATIVE -> "부정"
        ImpactDirection.MIXED -> "엇갈림"
        ImpactDirection.NEUTRAL -> "중립"
    }

private val ImpactDirection.newsDirectionColor: Color
    get() = when (this) {
        ImpactDirection.POSITIVE -> MarketColors.RiseText
        ImpactDirection.NEGATIVE -> MarketColors.FallText
        ImpactDirection.MIXED -> MarketColors.AmberText
        ImpactDirection.NEUTRAL -> MarketColors.InkMuted
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
                Text("현재 연도 예상 납부액", style = MarketType.caption, color = MarketColors.InkMuted)
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
