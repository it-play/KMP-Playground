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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.OrderBook
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.ui.charts.CandlestickVolumeChart
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
    onSubmitOrder: (
        side: OrderSide,
        type: OrderType,
        timeInForce: TimeInForce,
        quantity: Double,
        limitPrice: Double?,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedStock = stocks.firstOrNull { it.id == selectedStockId } ?: stocks.firstOrNull()
    val quote = selectedStock?.let { quotes[it.id] }
    val bars = selectedStock?.let { priceHistory[it.id].orEmpty() }.orEmpty()

    Row(
        modifier = modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WatchlistPanel(
            stocks = stocks,
            quotes = quotes,
            selectedStockId = selectedStock?.id,
            onSelectStock = onSelectStock,
            modifier = Modifier.width(252.dp).fillMaxHeight(),
        )
        if (selectedStock != null && quote != null) {
            StockChartPanel(
                stock = selectedStock,
                quote = quote,
                bars = bars,
                holding = holding,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Column(
                modifier = Modifier.width(304.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
}

@Composable
private fun WatchlistPanel(
    stocks: List<StockDefinition>,
    quotes: Map<String, Quote>,
    selectedStockId: String?,
    onSelectStock: (String) -> Unit,
    modifier: Modifier,
) {
    var query by remember { mutableStateOf("") }
    var market by remember { mutableStateOf<Market?>(null) }
    val filtered = stocks.filter { stock ->
        (market == null || (market == Market.NASDAQ && stock.market.isUnitedStates) || stock.market == market) && (
            query.isBlank() ||
                stock.symbol.contains(query, ignoreCase = true) ||
                stock.name.contains(query, ignoreCase = true)
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
                    FilterCell("전체", market == null) { market = null }
                    FilterCell("코스피", market == Market.KOSPI) { market = Market.KOSPI }
                    FilterCell("코스닥", market == Market.KOSDAQ) { market = Market.KOSDAQ }
                    FilterCell("미국", market == Market.NASDAQ) {
                        market = if (market == Market.NASDAQ) null else Market.NASDAQ
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
                        onClick = { onSelectStock(stock.id) },
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().background(MarketColors.PaperMuted).padding(9.dp),
            ) {
                Text(
                    "${filtered.size}종목 · 가격은 게임 시세",
                    style = MarketType.label.copy(fontSize = 9.sp),
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
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) MarketColors.Navy else MarketColors.PaperMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            style = MarketType.label.copy(fontSize = 9.sp),
            color = if (selected) Color.White else MarketColors.InkMuted,
        )
    }
}

@Composable
private fun WatchlistRow(
    stock: StockDefinition,
    quote: Quote?,
    selected: Boolean,
    onClick: () -> Unit,
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
                style = MarketType.label.copy(fontSize = 9.sp),
                color = MarketColors.InkMuted,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                quote?.let { formatPrice(it.price, stock.currency) } ?: "-",
                style = MarketType.number.copy(fontSize = 11.sp),
                color = quote?.let { deltaColor(it.change) } ?: MarketColors.InkMuted,
            )
            Text(
                quote?.let { formatPercent(it.changeRate) } ?: "-",
                style = MarketType.number.copy(fontSize = 9.sp),
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
    modifier: Modifier,
) {
    var range by remember { mutableStateOf("1개월") }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stock.name, style = MarketType.display.copy(fontSize = 24.sp), color = MarketColors.Ink)
                            Spacer(Modifier.width(8.dp))
                            StatusLabel(stock.market.displayName, MarketColors.Celadon)
                        }
                        Text(
                            "${stock.symbol}  ·  ${stock.englishName}  ·  ${stock.sector.displayName}",
                            style = MarketType.label,
                            color = MarketColors.InkMuted,
                        )
                    }
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
                Spacer(Modifier.height(13.dp))
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
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("캔들", style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
                Spacer(Modifier.width(10.dp))
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
            Row(
                modifier = Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("내 포지션", style = MarketType.label, color = MarketColors.InkMuted)
                    Text(
                        holding?.let { "${formatQuantity(it.quantity)}주 · 평균 ${formatPrice(it.averagePrice, stock.currency)}" }
                            ?: "보유 수량 없음",
                        style = MarketType.number,
                        color = MarketColors.Ink,
                    )
                }
                if (holding != null) {
                    Metric(
                        "평가손익",
                        formatMoney(holding.unrealizedProfit, stock.currency),
                        Modifier.width(130.dp),
                        deltaColor(holding.unrealizedProfit),
                        formatPercent(holding.returnRate),
                    )
                }
                Column(Modifier.width(170.dp)) {
                    Text("기업 개요", style = MarketType.label, color = MarketColors.InkMuted)
                    Text(
                        stock.description,
                        style = MarketType.label.copy(fontSize = 9.sp, lineHeight = 13.sp),
                        color = MarketColors.Ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).background(color, RoundedCornerShape(50)))
        Text(text, style = MarketType.label.copy(fontSize = 9.sp), color = MarketColors.InkMuted)
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
                            style = MarketType.number.copy(fontSize = 14.sp),
                            color = deltaColor(quote.change),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(formatPercent(quote.changeRate), style = MarketType.number.copy(fontSize = 10.sp), color = deltaColor(quote.change))
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
        Text(formatQuantity(quantity), Modifier.weight(1f), style = MarketType.number.copy(fontSize = 9.sp), color = MarketColors.InkMuted)
        Text(formatPrice(price, currency), Modifier.weight(1f), style = MarketType.number.copy(fontSize = 10.sp), color = color)
        Text(orders.toString(), Modifier.weight(0.5f), style = MarketType.number.copy(fontSize = 9.sp), color = MarketColors.InkMuted)
    }
}

@Composable
private fun OrderTicketPanel(
    stock: StockDefinition,
    quote: Quote,
    holding: Holding?,
    cashKrw: Double,
    cashUsd: Double,
    onSubmitOrder: (OrderSide, OrderType, TimeInForce, Double, Double?) -> Unit,
    modifier: Modifier,
) {
    var side by remember(stock.id) { mutableStateOf(OrderSide.BUY) }
    var type by remember(stock.id) { mutableStateOf(OrderType.MARKET) }
    var timeInForce by remember(stock.id) { mutableStateOf(TimeInForce.DAY) }
    var quantityText by remember(stock.id) { mutableStateOf("1") }
    var limitPriceText by remember(stock.id, quote.price) {
        mutableStateOf(formatPrice(quote.price, stock.currency).replace(",", ""))
    }
    val quantity = quantityText.toDoubleOrNull() ?: 0.0
    val limitPrice = limitPriceText.toDoubleOrNull()
    val expectedPrice = if (type == OrderType.LIMIT) limitPrice ?: quote.price else quote.price
    val expectedAmount = quantity * expectedPrice
    val availableCash = if (stock.currency == Currency.KRW) cashKrw else cashUsd

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
                suffix = "주",
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
                    else "${formatQuantity(holding?.quantity ?: 0.0)}주",
                    style = MarketType.number.copy(fontSize = 10.sp),
                    color = MarketColors.Ink,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (stock.currency == Currency.KRW) "매도 시 거래세 0.20% · 수수료 별도"
                else "양도세는 연간 손익으로 다음 해 5월 정산",
                style = MarketType.label.copy(fontSize = 9.sp),
                color = MarketColors.InkMuted,
            )
            Spacer(Modifier.height(7.dp))
            Button(
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
                    (type == OrderType.MARKET || (limitPrice ?: 0.0) > 0.0),
                modifier = Modifier.fillMaxWidth().height(38.dp),
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (side == OrderSide.BUY) MarketColors.Rise else MarketColors.Fall,
                    contentColor = Color.White,
                    disabledContainerColor = MarketColors.PaperMuted,
                    disabledContentColor = MarketColors.InkMuted,
                ),
            ) {
                Text("${stock.name} ${side.displayName} 주문", style = MarketType.label.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun SideTab(text: String, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(30.dp)
            .background(if (selected) color else MarketColors.PaperMuted, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
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
