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

data class NewGameOptions(
    val initialCapitalKrw: Double = 100_000_000.0,
    val seed: Long = DEFAULT_SEED,
    val usFractionalTrading: Boolean = false,
    val autoExchange: Boolean = true,
    val initialUsdKrw: Double = 1_350.0,
) {
    init {
        require(initialCapitalKrw >= MIN_INITIAL_CAPITAL_KRW && initialCapitalKrw.isFinite()) {
            "초기 자금은 100만원 이상이어야 합니다."
        }
        require(initialUsdKrw > 0.0 && initialUsdKrw.isFinite()) {
            "초기 원·달러 환율은 0보다 커야 합니다."
        }
    }

    companion object {
        const val MIN_INITIAL_CAPITAL_KRW: Double = 1_000_000.0
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
    /** 국내상장 기타 ETF 매도 시 배당소득으로 원천징수된 과세표준. */
    val taxableFinancialIncomeKrw: Long = 0L,
) {
    val gain: Double get() = proceeds - costBasis - commission - saleTax
    /** 취득·매도 시점의 서로 다른 세법상 환율을 보존한 원화 실현손익. */
    val gainKrw: Double get() = taxGainKrw.toDouble()
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
    val taxableIncomeAmount: Double,
    /** 미국 펀드의 사후 원금환급 구조를 게임 시점에 분리한 금액. */
    val returnOfCapitalAmount: Double,
    /** ROC가 남은 원가를 초과해 국외주식 양도이득으로 전환된 원화 금액. */
    val excessReturnOfCapitalGainKrw: Long,
    /** 같은 시각의 체결·기업행동과 저장 전 순서를 보존하는 전역 회계 순번. */
    val accountingSequence: Long,
) {
    val grossAmountKrw: Double get() = grossAmount * exchangeRateToKrw
    val withholdingTaxKrw: Double get() = withholdingTax * exchangeRateToKrw
    val netAmountKrw: Double get() = netAmount * exchangeRateToKrw
    val financialIncomeAmount: Double get() = taxableIncomeAmount
    val financialIncomeAmountKrw: Double get() = financialIncomeAmount * exchangeRateToKrw
    val rocAmount: Double get() = returnOfCapitalAmount
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

/** Long-lived daily surveillance series used by KRX alert and listing-maintenance rules. */
data class DailyTradingSurveillancePoint(
    val date: LocalDate,
    val close: Double,
    val volume: Long,
    val turnoverRate: Double,
    /** 같은 거래일 KOSPI/KOSDAQ 유동시가총액 프록시. 미국 종목은 null이다. */
    val marketProxyClose: Double? = null,
    /** 같은 거래일 종가 기준 KOSPI·KOSDAQ 보통주 합산 시가총액 순위. */
    val krxMarketCapRank: Int? = null,
) {
    init {
        require(close >= 0.0 && close.isFinite())
        require(volume >= 0L)
        require(turnoverRate >= 0.0 && turnoverRate.isFinite())
        require(marketProxyClose == null || marketProxyClose > 0.0 && marketProxyClose.isFinite())
        require(krxMarketCapRank == null || krxMarketCapRank > 0)
    }
}

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
            DeterministicRandom.mixSeed(seed, SimulatorRuntime.SCHEDULED_EVENT_STREAM_ID),
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
