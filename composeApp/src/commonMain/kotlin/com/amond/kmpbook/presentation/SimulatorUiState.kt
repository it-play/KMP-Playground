package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GamePhase
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.Order
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PortfolioSnapshot
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.domain.model.Trade
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.simulation.EventEngineSnapshot
import com.amond.kmpbook.domain.simulation.MacroEnvironment
import com.amond.kmpbook.domain.simulation.OrderBookSnapshot
import com.amond.kmpbook.domain.tax.AnnualTaxLedger
import com.amond.kmpbook.domain.tax.FeeBreakdown
import com.amond.kmpbook.domain.tax.FifoCostBasisBook
import com.amond.kmpbook.domain.tax.TaxBreakdown
import com.amond.kmpbook.domain.tax.TaxLiabilityStatus
import com.amond.kmpbook.domain.tax.StockGainTaxTreatment
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round
import kotlin.time.Instant

data class NewGameOptions(
    val initialCapitalKrw: Double = 100_000_000.0,
    val seed: Long = DEFAULT_SEED,
    val usFractionalTrading: Boolean = false,
    val autoExchange: Boolean = true,
    val initialUsdKrw: Double = 1_350.0,
) {
    init {
        require(initialCapitalKrw > 0.0 && initialCapitalKrw.isFinite()) {
            "초기 자금은 0보다 커야 합니다."
        }
        require(initialUsdKrw > 0.0 && initialUsdKrw.isFinite()) {
            "초기 원·달러 환율은 0보다 커야 합니다."
        }
    }

    companion object {
        const val DEFAULT_SEED: Long = 20_260_807L
    }
}

data class OrderRequest(
    val stockId: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: Double,
    val limitPrice: Double? = null,
    val timeInForce: TimeInForce = TimeInForce.DAY,
)

data class ForeignExchangeRecord(
    val id: String,
    val executedAt: Instant,
    val fromCurrency: Currency,
    val toCurrency: Currency,
    val sourceAmount: Double,
    val receivedAmount: Double,
    /** 해당 거래에서 USD 1달러당 적용한 원화 가격. */
    val usdKrwRate: Double,
    val spreadCostKrw: Double,
    val automatic: Boolean,
) {
    init {
        require(fromCurrency != toCurrency) { "서로 다른 통화만 환전할 수 있습니다." }
        require(sourceAmount > 0.0 && receivedAmount > 0.0) { "환전 금액은 0보다 커야 합니다." }
        require(usdKrwRate > 0.0 && spreadCostKrw >= 0.0) { "환율과 스프레드 비용이 올바르지 않습니다." }
    }
}

data class TransactionCostRecord(
    val tradeId: String,
    val stockId: String,
    val market: Market,
    val paidAt: Instant,
    val currency: Currency,
    val commission: Double,
    val saleTax: Double,
    val exchangeRateToKrw: Double,
    val feeBreakdown: FeeBreakdown? = null,
    val taxBreakdown: TaxBreakdown? = null,
) {
    val commissionKrw: Double get() = commission * exchangeRateToKrw
    val saleTaxKrw: Double get() = saleTax * exchangeRateToKrw
}

data class RealizedGainRecord(
    val tradeId: String,
    val stockId: String,
    val market: Market,
    val soldAt: Instant,
    val settlementDate: LocalDate,
    val quantity: Double,
    val proceeds: Double,
    val costBasis: Double,
    val commission: Double,
    val saleTax: Double,
    val currency: Currency,
    val exchangeRateToKrw: Double,
    val taxTreatment: StockGainTaxTreatment = StockGainTaxTreatment.DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE,
    /** 외부계좌·관계인 정보가 없는 게임 계좌 기준 대주주 추정 설명. */
    val assessmentNotes: List<String> = emptyList(),
    /** 세법상 양도가액: 매도 결제 환율을 적용한 원화 금액. */
    val taxGrossProceedsKrw: Long = round(proceeds * exchangeRateToKrw).toLong(),
    /** FIFO 취득 lot의 취득 결제 환율·직접비용을 반영한 원화 취득가액. */
    val taxCostBasisKrw: Long = round(costBasis * exchangeRateToKrw).toLong(),
    val taxDirectSellingCostsKrw: Long = round((commission + saleTax) * exchangeRateToKrw).toLong(),
    val taxGainKrw: Long = taxGrossProceedsKrw - taxCostBasisKrw - taxDirectSellingCostsKrw,
) {
    val gain: Double get() = proceeds - costBasis - commission - saleTax
    val gainKrw: Double get() = gain * exchangeRateToKrw
}

data class DividendLedgerEntry(
    val id: String,
    val stockId: String,
    val paidAt: Instant,
    val currency: Currency,
    val grossAmount: Double,
    val withholdingTax: Double,
    val netAmount: Double,
    val exchangeRateToKrw: Double,
    val taxBreakdown: TaxBreakdown? = null,
) {
    val grossAmountKrw: Double get() = grossAmount * exchangeRateToKrw
    val withholdingTaxKrw: Double get() = withholdingTax * exchangeRateToKrw
    val netAmountKrw: Double get() = netAmount * exchangeRateToKrw
}

data class TaxPaymentNotice(
    val id: String,
    val taxYear: Int,
    val dueDate: LocalDate,
    val amountKrw: Long,
    val status: TaxLiabilityStatus,
    val message: String,
)

data class DailyPortfolioStat(
    val date: LocalDate,
    val totalAssetsKrw: Double,
    val cashValueKrw: Double,
    val stockValueKrw: Double,
    val dailyReturn: Double,
    val drawdown: Double,
    val benchmarkValue: Double,
    val usdKrw: Double,
)

data class BenchmarkPoint(
    val timestamp: Instant,
    val value: Double,
    val cumulativeReturn: Double,
)

/**
 * Compose가 직접 구독하는 완전한 불변 스냅샷이다. ViewModel은 내부 작업 상태를 한 시간씩
 * 처리한 뒤 컬렉션을 복사하여 이 객체를 한 번에 교체한다.
 */
data class SimulatorUiState(
    val options: NewGameOptions,
    val phase: GamePhase,
    val screen: Screen,
    val currentTime: Instant,
    val turn: Long,
    val selectedTurnStep: TurnStep,
    val stocks: List<StockDefinition>,
    val selectedStockId: String?,
    val quotes: Map<String, Quote>,
    val priceHistory: Map<String, List<PriceBar>>,
    val cashByCurrency: Map<Currency, Double>,
    val holdings: Map<String, Holding>,
    val orders: List<Order>,
    val trades: List<Trade>,
    val selectedOrderBook: OrderBookSnapshot?,
    val marketSessions: Map<Market, MarketSession>,
    val macro: MacroEnvironment,
    val activeEvents: List<GameEvent>,
    val newsEvents: List<GameEvent>,
    val readEventIds: Set<String>,
    val portfolioSnapshots: List<PortfolioSnapshot>,
    val dailyStatistics: List<DailyPortfolioStat>,
    val benchmarkHistory: List<BenchmarkPoint>,
    val transactionCosts: List<TransactionCostRecord>,
    val realizedGains: List<RealizedGainRecord>,
    val fifoCostBasisBook: FifoCostBasisBook = FifoCostBasisBook(),
    val dividendLedger: List<DividendLedgerEntry>,
    val foreignExchangeLedger: List<ForeignExchangeRecord>,
    val annualTaxLedgers: Map<Int, AnnualTaxLedger>,
    val taxPaymentNotices: List<TaxPaymentNotice>,
    val peakAssetsKrw: Double,
    val maximumDrawdown: Double,
    /** 저장 게임에서 그대로 복원할 수 있는 기본 난수 스트림 상태. */
    val rngState: Long,
    /** 이벤트 발생 순서·쿨다운까지 복원하는 별도 스트림 상태. */
    val eventEngineSnapshot: EventEngineSnapshot,
    val nextSequence: Long,
    val isAdvancing: Boolean = false,
    val lastMessage: String? = null,
) {
    val seed: Long get() = options.seed
    val initialCapitalKrw: Double get() = options.initialCapitalKrw
    val usFractionalTrading: Boolean get() = options.usFractionalTrading
    val autoExchange: Boolean get() = options.autoExchange

    val selectedStock: StockDefinition? get() = stocks.firstOrNull { it.id == selectedStockId }
    val selectedQuote: Quote? get() = selectedStockId?.let(quotes::get)
    val selectedHolding: Holding? get() = selectedStockId?.let(holdings::get)
    val selectedHistory: List<PriceBar> get() = selectedStockId?.let(priceHistory::get).orEmpty()
    val orderBook: OrderBookSnapshot? get() = selectedOrderBook
    val sessions: Map<Market, MarketSession> get() = marketSessions
    val selectedSession: MarketSession? get() = selectedStock?.let { marketSessions[it.market] }

    val holdingList: List<Holding> get() = holdings.values.toList()
    val openOrders: List<Order> get() = orders.filter(Order::isOpen)
    val unreadEvents: Int get() = newsEvents.count { it.id !in readEventIds }
    val progress: Double get() = GameCalendar.progress(currentTime)
    val isAtEnd: Boolean get() = GameCalendar.isFinished(currentTime)

    val currentDate: LocalDate
        get() = currentTime.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE).date

    val cashValueKrw: Double
        get() = (cashByCurrency[Currency.KRW] ?: 0.0) +
            (cashByCurrency[Currency.USD] ?: 0.0) * macro.usdKrw

    val stockValueKrw: Double
        get() = holdings.values.sumOf { holding ->
            holding.marketValue * if (holding.currency == Currency.USD) macro.usdKrw else 1.0
        }

    val totalAssetsKrw: Double get() = cashValueKrw + stockValueKrw
    val totalAssets: Double get() = totalAssetsKrw

    val unrealizedProfitKrw: Double
        get() = holdings.values.sumOf { holding ->
            holding.unrealizedProfit * if (holding.currency == Currency.USD) macro.usdKrw else 1.0
        }

    val realizedProfitKrw: Double get() = realizedGains.sumOf(RealizedGainRecord::gainKrw)
    val totalCommissionKrw: Double get() = transactionCosts.sumOf(TransactionCostRecord::commissionKrw)
    val totalSaleTaxKrw: Double get() = transactionCosts.sumOf(TransactionCostRecord::saleTaxKrw)
    val totalDividendKrw: Double get() = dividendLedger.sumOf(DividendLedgerEntry::netAmountKrw)
    val paidAnnualTaxKrw: Double
        get() = taxPaymentNotices
            .filter { it.status == TaxLiabilityStatus.PAID }
            .sumOf { it.amountKrw.toDouble() }
    val totalReturnRate: Double
        get() = if (initialCapitalKrw == 0.0) 0.0 else totalAssetsKrw / initialCapitalKrw - 1.0

    val currentPortfolio: PortfolioSnapshot
        get() = PortfolioSnapshot(
            timestamp = currentTime,
            cashByCurrency = cashByCurrency,
            holdings = holdingList,
            exchangeRatesToKrw = mapOf(Currency.USD to macro.usdKrw),
            initialCapitalKrw = initialCapitalKrw,
            realizedProfitKrw = realizedProfitKrw,
            cumulativeCommissionKrw = totalCommissionKrw,
            cumulativeTaxKrw = totalSaleTaxKrw +
                dividendLedger.sumOf(DividendLedgerEntry::withholdingTaxKrw) +
                paidAnnualTaxKrw,
        )

    val annualTaxSummary: AnnualTaxLedger?
        get() = annualTaxLedgers[currentDate.year]

    val benchmarkReturn: Double get() = benchmarkHistory.lastOrNull()?.cumulativeReturn ?: 0.0
}
