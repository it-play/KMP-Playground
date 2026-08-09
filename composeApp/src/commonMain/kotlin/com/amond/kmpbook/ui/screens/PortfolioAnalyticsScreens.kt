package com.amond.kmpbook.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.PortfolioSnapshot
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.Trade
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
import com.amond.kmpbook.ui.theme.MarketLayout
import com.amond.kmpbook.ui.theme.MarketType
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun PortfolioScreen(
    snapshot: PortfolioSnapshot,
    history: List<PortfolioSnapshot>,
    stocks: List<StockDefinition>,
    onOpenStock: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stockById = stocks.associateBy { it.id }
    val holdings = snapshot.holdings.sortedByDescending { it.marketValue * snapshot.rateToKrw(it.currency) }
    val allocationValues = holdings.groupBy { holding ->
        val stock = stockById[holding.stockId]
        stock?.etfProfile?.assetClass?.let { "ETF · ${it.displayName}" }
            ?: stock?.sector?.let { "개별주 · ${it.displayName}" }
            ?: "기타"
    }
        .mapValues { (_, group) -> group.sumOf { it.marketValue * snapshot.rateToKrw(it.currency) } }
        .toList().sortedByDescending { it.second }

    Column(
        modifier.fillMaxSize().padding(MarketLayout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MarketLayout.screenGap)) {
            PortfolioTile("총자산", formatMoney(snapshot.totalAssetValueKrw, Currency.KRW), formatPercent(snapshot.totalReturnRate), deltaColor(snapshot.totalProfitKrw), Modifier.weight(1f))
            PortfolioTile("투자상품 평가액", formatMoney(snapshot.stockValueKrw, Currency.KRW), "${holdings.size}개 상품", MarketColors.Ink, Modifier.weight(1f))
            PortfolioTile("현금", formatMoney(snapshot.cashValueKrw, Currency.KRW), "비중 ${formatPercent(snapshot.cashWeight, false)}", MarketColors.Primary, Modifier.weight(1f))
            PortfolioTile("미실현손익", formatMoney(snapshot.unrealizedProfitKrw, Currency.KRW), "현재가 기준", deltaColor(snapshot.unrealizedProfitKrw), Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
        ) {
            LedgerPanel(Modifier.weight(1.45f).fillMaxSize(), padding = 0.dp) {
                Column(Modifier.fillMaxSize()) {
                    SectionHeading("포지션 원장", eyebrow = "CURRENT HOLDINGS", modifier = Modifier.padding(12.dp))
                    LedgerDivider()
                    PortfolioHeader()
                    if (holdings.isEmpty()) {
                        PortfolioEmptyLedger("보유 중인 종목이 없습니다.")
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(holdings, key = { it.stockId }) { holding ->
                                val stock = stockById[holding.stockId] ?: return@items
                                PositionRow(holding, stock, snapshot, onOpenStock)
                            }
                        }
                    }
                    LedgerDivider()
                    Row(Modifier.padding(12.dp)) {
                        Text("합계", Modifier.weight(1.4f), style = MarketType.label.copy(fontWeight = FontWeight.Bold), color = MarketColors.Ink)
                        Text(formatMoney(snapshot.stockValueKrw, Currency.KRW), Modifier.weight(1f), style = MarketType.number, color = MarketColors.Ink)
                        Text(formatMoney(snapshot.unrealizedProfitKrw, Currency.KRW), Modifier.weight(0.9f), style = MarketType.number, color = deltaColor(snapshot.unrealizedProfitKrw))
                    }
                }
            }
            Column(
                Modifier.width(MarketLayout.detailRailWidth),
                verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
            ) {
                AssetAllocationPanel(allocationValues, snapshot.totalAssetValueKrw, Modifier.fillMaxWidth().weight(1f))
                CashPanel(snapshot, Modifier.fillMaxWidth().height(155.dp))
                LedgerPanel(Modifier.fillMaxWidth().height(150.dp)) {
                    Column(Modifier.fillMaxSize()) {
                        SectionHeading("자산 추이", eyebrow = "DAILY SNAPSHOTS")
                        Spacer(Modifier.height(8.dp))
                        LineAreaChart(
                            history.map { it.totalAssetValueKrw }.ifEmpty { listOf(snapshot.totalAssetValueKrw, snapshot.totalAssetValueKrw) },
                            Modifier.fillMaxWidth().weight(1f),
                            baseline = snapshot.initialCapitalKrw,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsScreen(
    snapshot: PortfolioSnapshot,
    history: List<PortfolioSnapshot>,
    trades: List<Trade>,
    stocks: List<StockDefinition>,
    grossTurnoverKrw: Double,
    totalCostsKrw: Double,
    maxDrawdown: Double,
    modifier: Modifier = Modifier,
) {
    val returns = history.zipWithNext { a, b ->
        if (a.totalAssetValueKrw == 0.0) 0.0 else b.totalAssetValueKrw / a.totalAssetValueKrw - 1.0
    }
    val average = returns.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    val variance = returns.takeIf { it.size > 1 }?.sumOf { (it - average).pow(2) }?.div(returns.size - 1) ?: 0.0
    val volatility = sqrt(variance) * sqrt(252.0)
    val sharpe = if (volatility == 0.0) 0.0 else (average * 252.0 - 0.03) / volatility
    val sellTrades = trades.filter { it.side == OrderSide.SELL }
    val marketValues = snapshot.holdings.mapNotNull { holding ->
        stocks.firstOrNull { it.id == holding.stockId }
            ?.let { stock -> stock to snapshot.holdingReturnRateKrw(holding) }
    }
    val best = marketValues.maxByOrNull { it.second }
    val worst = marketValues.minByOrNull { it.second }

    Column(
        modifier.fillMaxSize().padding(MarketLayout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MarketLayout.screenGap)) {
            AnalyticsTile("누적 수익률", formatPercent(snapshot.totalReturnRate), "초기자본 대비", deltaColor(snapshot.totalReturnRate), Modifier.weight(1f))
            AnalyticsTile("연환산 변동성", formatPercent(volatility, false), "일별 수익률 기준", MarketColors.Amber, Modifier.weight(1f))
            AnalyticsTile("샤프 지수", formatNumber(sharpe), "무위험수익률 3% 가정", if (sharpe >= 1.0) MarketColors.Primary else MarketColors.Ink, Modifier.weight(1f))
            AnalyticsTile("최대 낙폭", formatPercent(maxDrawdown, false), "고점 대비 저점", MarketColors.Fall, Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
        ) {
            Column(
                Modifier.weight(1.35f),
                verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
            ) {
                LedgerPanel(Modifier.fillMaxWidth().weight(1.1f)) {
                    Column(Modifier.fillMaxSize()) {
                        SectionHeading("수익률·낙폭", eyebrow = "PERFORMANCE PATH") {
                            StatusLabel("${history.size} 거래일", MarketColors.Primary)
                        }
                        Spacer(Modifier.height(12.dp))
                        LineAreaChart(
                            history.map { it.totalAssetValueKrw }.ifEmpty { listOf(snapshot.totalAssetValueKrw, snapshot.totalAssetValueKrw) },
                            Modifier.fillMaxWidth().weight(1f),
                            baseline = snapshot.initialCapitalKrw,
                        )
                    }
                }
                LedgerPanel(Modifier.fillMaxWidth().weight(0.9f)) {
                    Column(Modifier.fillMaxSize()) {
                        SectionHeading("일별 수익률 분포", eyebrow = "RETURN BARS")
                        Spacer(Modifier.height(10.dp))
                        ReturnBars(returns.takeLast(120), Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
            Column(
                Modifier.width(MarketLayout.detailRailWidth),
                verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
            ) {
                LedgerPanel(Modifier.fillMaxWidth().height(185.dp)) {
                    Column {
                        SectionHeading("운용 효율", eyebrow = "EFFICIENCY")
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Metric("총 회전금액", formatMoney(grossTurnoverKrw, Currency.KRW, true), Modifier.weight(1f))
                            Metric("총 거래비용", formatMoney(totalCostsKrw, Currency.KRW, true), Modifier.weight(1f), MarketColors.Amber)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Metric("체결 횟수", "${trades.size}회", Modifier.weight(1f))
                            Metric("매도 횟수", "${sellTrades.size}회", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(
                            "비용/회전율 ${formatPercent(if (grossTurnoverKrw == 0.0) 0.0 else totalCostsKrw / grossTurnoverKrw, false)}",
                            style = MarketType.label,
                            color = MarketColors.InkMuted,
                        )
                    }
                }
                HoldingExtreme("최고 포지션", best, MarketColors.Rise, Modifier.fillMaxWidth().height(104.dp))
                HoldingExtreme("최저 포지션", worst, MarketColors.Fall, Modifier.fillMaxWidth().height(104.dp))
                LedgerPanel(Modifier.fillMaxWidth().weight(1f), background = MarketColors.PaperMuted) {
                    Column {
                        SectionHeading("해석 메모", eyebrow = "MODEL NOTES")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "• 일별 스냅샷으로 성과를 계산합니다.\n" +
                                "• 배당·세금·수수료를 모두 순자산에 반영합니다.\n" +
                                "• 샤프 지수는 게임 내 3% 무위험수익률을 가정합니다.\n" +
                                "• 짧은 표본에서는 통계가 크게 흔들릴 수 있습니다.",
                            style = MarketType.label.copy(lineHeight = 17.sp),
                            color = MarketColors.InkMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioTile(label: String, value: String, detail: String, color: Color, modifier: Modifier) {
    LedgerPanel(modifier.height(108.dp)) { Metric(label, value, valueColor = color, detail = detail) }
}

@Composable
private fun AnalyticsTile(label: String, value: String, detail: String, color: Color, modifier: Modifier) {
    LedgerPanel(modifier.height(108.dp)) { Metric(label, value, valueColor = color, detail = detail) }
}

@Composable
private fun PortfolioHeader() {
    Row(Modifier.fillMaxWidth().background(MarketColors.PaperMuted).padding(horizontal = 12.dp, vertical = 7.dp)) {
        PositionHeader("종목", 1.4f)
        PositionHeader("수량", 0.7f)
        PositionHeader("평균단가", 0.9f)
        PositionHeader("현재가", 0.9f)
        PositionHeader("평가액", 1f)
        PositionHeader("평가손익", 1f)
        PositionHeader("수익률", 0.75f)
    }
}

@Composable
private fun PositionRow(
    holding: Holding,
    stock: StockDefinition,
    snapshot: PortfolioSnapshot,
    onOpenStock: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenStock(stock.id) }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1.4f)) {
            Text(stock.name, style = MarketType.body.copy(fontWeight = FontWeight.Medium), color = MarketColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${stock.symbol} · ${stock.market.displayName}", style = MarketType.caption, color = MarketColors.InkMuted)
        }
        PositionCell(formatQuantity(holding.quantity), 0.7f)
        PositionCell(formatPrice(holding.averagePrice, stock.currency), 0.9f)
        PositionCell(formatPrice(holding.currentPrice, stock.currency), 0.9f)
        PositionCell(formatMoney(holding.marketValue * snapshot.rateToKrw(holding.currency), Currency.KRW, true), 1f)
        val unrealizedKrw = snapshot.holdingUnrealizedProfitKrw(holding)
        PositionCell(formatMoney(unrealizedKrw, Currency.KRW, true), 1f, deltaColor(unrealizedKrw))
        PositionCell(formatPercent(snapshot.holdingReturnRateKrw(holding)), 0.75f, deltaColor(unrealizedKrw))
    }
}

@Composable
private fun RowScope.PositionHeader(text: String, weight: Float) {
    Text(text, Modifier.weight(weight), style = MarketType.label, color = MarketColors.InkMuted)
}

@Composable
private fun RowScope.PositionCell(text: String, weight: Float, color: Color = MarketColors.Ink) {
    Text(text, Modifier.weight(weight), style = MarketType.number, color = color, maxLines = 1)
}

@Composable
private fun AssetAllocationPanel(values: List<Pair<String, Double>>, total: Double, modifier: Modifier) {
    val palette = listOf(MarketColors.Primary, MarketColors.Rise, MarketColors.Fall, MarketColors.Amber, MarketColors.InkMuted)
    LedgerPanel(modifier) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading("자산군·섹터 배분", eyebrow = "ASSET EXPOSURE")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AllocationDonut(
                    values = values.mapIndexed { index, item -> item.second to palette[index % palette.size] },
                    modifier = Modifier.size(112.dp),
                )
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    values.take(7).forEachIndexed { index, (label, value) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(palette[index % palette.size], RoundedCornerShape(50)))
                            Spacer(Modifier.width(6.dp))
                            Text(label, Modifier.weight(1f), style = MarketType.label, color = MarketColors.InkMuted, maxLines = 1)
                            Text(formatPercent(if (total == 0.0) 0.0 else value / total, false), style = MarketType.number)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashPanel(snapshot: PortfolioSnapshot, modifier: Modifier) {
    LedgerPanel(modifier) {
        Column {
            SectionHeading("통화별 예수금", eyebrow = "CASH BALANCE")
            Spacer(Modifier.height(12.dp))
            Row {
                Metric("KRW", formatMoney(snapshot.cashByCurrency[Currency.KRW] ?: 0.0, Currency.KRW), Modifier.weight(1f))
                Metric("USD", formatMoney(snapshot.cashByCurrency[Currency.USD] ?: 0.0, Currency.USD), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text("USD 원화환산 ${formatMoney((snapshot.cashByCurrency[Currency.USD] ?: 0.0) * snapshot.rateToKrw(Currency.USD), Currency.KRW)}", style = MarketType.label, color = MarketColors.InkMuted)
        }
    }
}

@Composable
private fun HoldingExtreme(
    label: String,
    value: Pair<StockDefinition, Double>?,
    color: Color,
    modifier: Modifier,
) {
    LedgerPanel(modifier) {
        Column {
            Text(label, style = MarketType.label, color = MarketColors.InkMuted)
            Text(value?.first?.name ?: "포지션 없음", style = MarketType.heading, color = MarketColors.Ink, maxLines = 1)
            Text(value?.let { formatPercent(it.second) } ?: "-", style = MarketType.number, color = color)
        }
    }
}

@Composable
private fun ReturnBars(values: List<Double>, modifier: Modifier) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val maxAbs = (values.maxOfOrNull { kotlin.math.abs(it) } ?: 0.01).coerceAtLeast(0.001)
        val zero = size.height / 2f
        drawLine(MarketColors.Line, Offset(0f, zero), Offset(size.width, zero), 1f)
        val slot = size.width / values.size
        values.forEachIndexed { index, value ->
            val h = (kotlin.math.abs(value) / maxAbs * (size.height / 2f - 2f)).toFloat()
            drawRect(
                color = if (value >= 0.0) MarketColors.Rise.copy(alpha = 0.75f) else MarketColors.Fall.copy(alpha = 0.75f),
                topLeft = Offset(index * slot + slot * 0.14f, if (value >= 0.0) zero - h else zero),
                size = Size((slot * 0.72f).coerceAtLeast(1f), h),
            )
        }
    }
}

private fun formatNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return rounded.toString()
}

@Composable
private fun PortfolioEmptyLedger(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MarketType.body, color = MarketColors.InkMuted)
    }
}
