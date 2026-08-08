package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.CorporateActionRecord
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GamePhase
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.domain.model.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.ListingLifecycleState
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.Order
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PortfolioSnapshot
import com.amond.kmpbook.domain.model.PendingCorporateAction
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.model.ScheduledEventOccurrence
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.domain.model.Trade
import com.amond.kmpbook.domain.model.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.simulation.EventEngineSnapshot
import com.amond.kmpbook.domain.simulation.MacroEnvironment
import com.amond.kmpbook.domain.simulation.OrderBookSnapshot
import com.amond.kmpbook.domain.simulation.DeterministicRandom
import com.amond.kmpbook.domain.simulation.ScheduledEventEngine
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
    /** 해외 기초시장이 상장시장과 어긋나는 ETF의 다음 개장 갭. */
    val pendingEtfReferenceReturns: Map<String, Double>,
    /** 상장시장 폐장 중 발생한 뉴스 충격의 다음 개장 갭. */
    val pendingClosedEventLogReturns: Map<String, Double>,
    /** 대표 미국 지수 현재값과 최근 시간봉. */
    val marketIndices: Map<MarketIndexId, MarketIndexSnapshot>,
    val marketIndexHistory: Map<MarketIndexId, List<MarketIndexSnapshot>>,
    /** 체결별 세무 원화환산율. 미결제 거래는 임시 체결환율, 결제 후에는 결제일 환율이다. */
    val taxExchangeRatesByTradeId: Map<String, Double>,
    /** 결제일 환율 확정을 기다리는 해외 거래 ID. */
    val pendingTaxSettlementTradeIds: Set<String>,
    /** 사용자가 별표로 지정한 종목. */
    val watchlistedStockIds: Set<String>,
    /** 공시되었지만 효력일이 도래하지 않은 분할·병합. */
    val pendingCorporateActions: List<PendingCorporateAction>,
    /** 이미 적용된 기업행동 원장. */
    val corporateActionLedger: List<CorporateActionRecord>,
    /** 거래소 상장 유지 심사·정리매매·상장폐지·청산 상태. */
    val listingLifecycleStates: Map<String, ListingLifecycleState>,
    val listingLifecycleLedger: List<ListingLifecycleLedgerEvent>,
    /** KRX/US market-wide and single-security protection state. */
    val tradingProtectionSnapshot: TradingProtectionSnapshot,
    /** 100-day alert/listing checks outlive the bounded intraday chart history. */
    val dailyTradingSurveillance: Map<String, List<DailyTradingSurveillancePoint>>,
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
    val watchlist: Set<String> get() = watchlistedStockIds
    /** Pure calendar projection: intentionally not a constructor field or save-schema member. */
    val upcomingScheduledEvents: List<ScheduledEventOccurrence>
        get() = ScheduledEventEngine(
            DeterministicRandom.mixSeed(seed, ScheduledEventEngine.STREAM_ID),
        ).upcoming(currentTime, stocks, UPCOMING_EVENT_LIMIT)
    val progress: Double get() = GameCalendar.progress(currentTime)
    val isAtEnd: Boolean get() = GameCalendar.isFinished(currentTime)

    val currentDate: LocalDate
        get() = GameCalendar.campaignDate(currentTime)

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
        get() {
            val historicalBasis = fifoCostBasisBook.lots.groupBy { it.stockId }
                .mapValues { (_, lots) -> lots.sumOf { it.remainingCostBasisKrw }.toDouble() }
            return holdings.values.sumOf { holding ->
                val rate = if (holding.currency == Currency.USD) macro.usdKrw else 1.0
                val currentValue = holding.marketValue * rate
                currentValue - (historicalBasis[holding.stockId] ?: holding.costBasis * rate)
            }
        }

    val realizedProfitKrw: Double get() = realizedGains.sumOf(RealizedGainRecord::gainKrw)
    val totalCommissionKrw: Double get() = transactionCosts.sumOf(TransactionCostRecord::commissionKrw)
    val totalSaleTaxKrw: Double get() = transactionCosts.sumOf(TransactionCostRecord::saleTaxKrw)
    val grossTradeTurnoverKrw: Double
        get() {
            val costsByTradeId = transactionCosts.associateBy(TransactionCostRecord::tradeId)
            return trades.sumOf { trade ->
                trade.grossAmount * costsByTradeId.getValue(trade.id).exchangeRateToKrw
            }
        }
    val totalTransactionCostKrw: Double
        get() = transactionCosts.sumOf { it.commissionKrw + it.saleTaxKrw }
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
            holdingCostBasisKrw = fifoCostBasisBook.lots.groupBy { it.stockId }
                .mapValues { (_, lots) -> lots.sumOf { it.remainingCostBasisKrw }.toDouble() },
        )

    val annualTaxSummary: AnnualTaxLedger?
        get() = annualTaxLedgers[currentDate.year]

    val benchmarkReturn: Double get() = benchmarkHistory.lastOrNull()?.cumulativeReturn ?: 0.0

    private companion object {
        const val UPCOMING_EVENT_LIMIT: Int = 12
    }
}
