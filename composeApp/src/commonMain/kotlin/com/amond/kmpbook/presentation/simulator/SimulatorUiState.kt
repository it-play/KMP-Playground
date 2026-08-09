package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.corporateaction.CorporateActionRecord
import com.amond.kmpbook.domain.model.corporateaction.CorporateFundamentalState
import com.amond.kmpbook.domain.model.corporateaction.PendingCorporateAction
import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.game.GamePhase
import com.amond.kmpbook.domain.model.game.Screen
import com.amond.kmpbook.domain.model.game.TurnStep
import com.amond.kmpbook.domain.model.index.MarketIndexId
import com.amond.kmpbook.domain.model.index.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.instrument.FundFinancialState
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleState
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.portfolio.Holding
import com.amond.kmpbook.domain.model.portfolio.PortfolioSnapshot
import com.amond.kmpbook.domain.model.pricing.PriceBar
import com.amond.kmpbook.domain.model.pricing.PriceBarInterval
import com.amond.kmpbook.domain.model.pricing.Quote
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.schedule.ScheduledEventOccurrence
import com.amond.kmpbook.domain.model.trading.Order
import com.amond.kmpbook.domain.model.trading.Trade
import com.amond.kmpbook.domain.model.venue.MarketSession
import com.amond.kmpbook.domain.simulation.event.EventEngineSnapshot
import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
import com.amond.kmpbook.domain.simulation.market.MarketDynamicsSnapshot
import com.amond.kmpbook.domain.simulation.order.OrderBookSnapshot
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import com.amond.kmpbook.domain.simulation.schedule.ScheduledEventEngine
import com.amond.kmpbook.domain.tax.liability.AnnualTaxLedger
import com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus
import com.amond.kmpbook.domain.tax.lot.FifoCostBasisBook
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.presentation.portfolio.BenchmarkPoint
import com.amond.kmpbook.presentation.portfolio.DailyPortfolioStat
import com.amond.kmpbook.presentation.portfolio.DailyTradingSurveillancePoint
import com.amond.kmpbook.presentation.portfolio.DividendLedgerEntry
import com.amond.kmpbook.presentation.portfolio.ForeignExchangeRecord
import com.amond.kmpbook.presentation.portfolio.RealizedGainRecord
import com.amond.kmpbook.presentation.portfolio.TaxPaymentNotice
import com.amond.kmpbook.presentation.portfolio.TransactionCostRecord
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

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
    /** PER·PSR·ROE 계산의 원천이며, 파생 지표 스냅샷은 저장하지 않는다. */
    val corporateFundamentals: Map<String, CorporateFundamentalState>,
    /** AUM·괴리율 계산의 원천인 좌당 기준가와 존속 좌수 상태다. */
    val fundFinancialStates: Map<String, FundFinancialState>,
    /** 거래정지·폐장 중 소비되지 않은 ETF·ETN 설정·환매 충격이다. */
    val pendingFundFlowRates: Map<String, Double>,
    val selectedStockId: String?,
    val quotes: Map<String, Quote>,
    /** 최근 시간봉. 장중 시세 복원과 1일·1주 차트에 사용한다. */
    val priceHistory: Map<String, List<PriceBar>>,
    /** 엔진이 일·주·월·분기별로 직접 집계한 OHLCV. 현재 진행 중인 봉도 포함한다. */
    val chartPriceHistory: Map<String, Map<PriceBarInterval, List<PriceBar>>>,
    val cashByCurrency: Map<Currency, Double>,
    val holdings: Map<String, Holding>,
    val orders: List<Order>,
    val trades: List<Trade>,
    val selectedOrderBook: OrderBookSnapshot?,
    val marketSessions: Map<Market, MarketSession>,
    val macro: MacroEnvironment,
    /** 설정에서 조절한 목표와 시간에 따라 그 목표로 수렴하는 실제 동역학 상태. */
    val externalMarketForcesTarget: ExternalMarketForces,
    val marketDynamicsSnapshot: MarketDynamicsSnapshot,
    val activeEvents: List<GameEvent>,
    val newsEvents: List<GameEvent>,
    val readEventIds: Set<String>,
    /** 종목 화면에서 확인한 연관 뉴스를 종목별로 분리한 읽음 원장. */
    val readStockNewsEventIds: Map<String, Set<String>>,
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
    val selectedChartHistory: Map<PriceBarInterval, List<PriceBar>>
        get() = selectedStockId?.let(chartPriceHistory::get).orEmpty()
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
