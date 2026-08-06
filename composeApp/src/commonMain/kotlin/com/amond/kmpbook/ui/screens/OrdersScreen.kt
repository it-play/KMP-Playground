package com.amond.kmpbook.ui.screens

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
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Order
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.Trade
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.Metric
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.format.formatDateTimeKst
import com.amond.kmpbook.ui.format.formatMoney
import com.amond.kmpbook.ui.format.formatPrice
import com.amond.kmpbook.ui.format.formatQuantity
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun OrdersScreen(
    orders: List<Order>,
    trades: List<Trade>,
    stocks: List<StockDefinition>,
    onCancelOrder: (String) -> Unit,
    onOpenStock: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(0) }
    val stockById = stocks.associateBy { it.id }
    val openOrders = orders.count { it.isOpen }
    val grossTurnoverKrw = trades.sumOf { trade ->
        val stock = stockById[trade.stockId]
        trade.grossAmount * if (stock?.currency == Currency.USD) 1_350.0 else 1.0
    }
    val totalCostsKrw = trades.sumOf { trade ->
        (trade.commission + trade.tax) * if (trade.currency == Currency.USD) 1_350.0 else 1.0
    }

    Column(
        modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("미체결 주문", "${openOrders}건", "정규장 진입 시 체결 검사", Modifier.weight(1f))
            SummaryTile("누적 체결", "${trades.size}건", formatMoney(grossTurnoverKrw, Currency.KRW, true), Modifier.weight(1f))
            SummaryTile("거래 비용", formatMoney(totalCostsKrw, Currency.KRW), "수수료·거래세 합계", Modifier.weight(1f))
        }
        LedgerPanel(Modifier.fillMaxWidth().weight(1f), padding = 0.dp) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeading("주문·체결 원장", eyebrow = "ORDER LEDGER", modifier = Modifier.weight(1f))
                    TabCell("주문 ${orders.size}", tab == 0) { tab = 0 }
                    Spacer(Modifier.width(4.dp))
                    TabCell("체결 ${trades.size}", tab == 1) { tab = 1 }
                }
                LedgerDivider()
                if (tab == 0) {
                    OrderTable(orders, stockById, onCancelOrder, onOpenStock)
                } else {
                    TradeTable(trades, stockById, onOpenStock)
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, detail: String, modifier: Modifier) {
    LedgerPanel(modifier.height(88.dp)) {
        Metric(label, value, detail = detail)
    }
}

@Composable
private fun TabCell(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) MarketColors.Navy else MarketColors.PaperMuted, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MarketType.label, color = if (selected) Color.White else MarketColors.InkMuted)
    }
}

@Composable
private fun OrderTable(
    orders: List<Order>,
    stockById: Map<String, StockDefinition>,
    onCancelOrder: (String) -> Unit,
    onOpenStock: (String) -> Unit,
) {
    TableHeader(listOf("접수시각" to 1.2f, "종목" to 1.3f, "구분" to 0.6f, "방식" to 0.7f, "주문수량" to 0.8f, "주문가격" to 0.9f, "체결" to 0.7f, "상태" to 0.8f, "" to 0.5f))
    if (orders.isEmpty()) {
        EmptyLedger("접수된 주문이 없습니다.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(orders.reversed(), key = { it.id }) { order ->
            val stock = stockById[order.stockId] ?: return@items
            Row(
                Modifier.fillMaxWidth().clickable { onOpenStock(order.stockId) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(formatDateTimeKst(order.createdAt), 1.2f, small = true)
                Cell(stock.name, 1.3f)
                Cell(order.side.displayName, 0.6f, color = if (order.side == OrderSide.BUY) MarketColors.Rise else MarketColors.Fall)
                Cell(order.type.displayName, 0.7f)
                Cell(formatQuantity(order.quantity), 0.8f, number = true)
                Cell(order.limitPrice?.let { formatPrice(it, stock.currency) } ?: "시장가", 0.9f, number = true)
                Cell(formatQuantity(order.filledQuantity), 0.7f, number = true)
                Box(Modifier.weight(0.8f)) {
                    StatusLabel(order.status.displayName, if (order.status.isTerminal) MarketColors.InkMuted else MarketColors.Celadon)
                }
                Box(Modifier.weight(0.5f), contentAlignment = Alignment.CenterEnd) {
                    if (order.canCancel) {
                        Text(
                            "취소",
                            modifier = Modifier.clickable { onCancelOrder(order.id) },
                            style = MarketType.label.copy(fontWeight = FontWeight.Bold),
                            color = MarketColors.Rise,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeTable(
    trades: List<Trade>,
    stockById: Map<String, StockDefinition>,
    onOpenStock: (String) -> Unit,
) {
    TableHeader(listOf("체결시각" to 1.2f, "종목" to 1.3f, "구분" to 0.6f, "수량" to 0.8f, "체결가" to 0.9f, "약정금액" to 1f, "수수료" to 0.8f, "세금" to 0.8f))
    if (trades.isEmpty()) {
        EmptyLedger("체결 내역이 없습니다.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(trades.reversed(), key = { it.id }) { trade ->
            val stock = stockById[trade.stockId] ?: return@items
            Row(
                Modifier.fillMaxWidth().clickable { onOpenStock(trade.stockId) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(formatDateTimeKst(trade.executedAt), 1.2f, small = true)
                Cell(stock.name, 1.3f)
                Cell(trade.side.displayName, 0.6f, color = if (trade.side == OrderSide.BUY) MarketColors.Rise else MarketColors.Fall)
                Cell(formatQuantity(trade.quantity), 0.8f, number = true)
                Cell(formatPrice(trade.price, trade.currency), 0.9f, number = true)
                Cell(formatMoney(trade.grossAmount, trade.currency), 1f, number = true)
                Cell(formatMoney(trade.commission, trade.currency), 0.8f, number = true)
                Cell(formatMoney(trade.tax, trade.currency), 0.8f, number = true)
            }
        }
    }
}

@Composable
private fun TableHeader(columns: List<Pair<String, Float>>) {
    Row(Modifier.fillMaxWidth().background(MarketColors.PaperMuted).padding(horizontal = 14.dp, vertical = 7.dp)) {
        columns.forEach { (name, weight) ->
            Text(name, Modifier.weight(weight), style = MarketType.label, color = MarketColors.InkMuted)
        }
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    color: Color = MarketColors.Ink,
    number: Boolean = false,
    small: Boolean = false,
) {
    Text(
        text,
        Modifier.weight(weight),
        style = if (number) MarketType.number.copy(fontSize = if (small) 9.sp else 10.sp)
        else MarketType.body.copy(fontSize = if (small) 10.sp else 12.sp),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptyLedger(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MarketType.body, color = MarketColors.InkMuted)
    }
}
