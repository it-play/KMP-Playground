package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.corporateaction.CorporateActionRecord
import com.amond.kmpbook.domain.model.corporateaction.CorporateFundamentalState
import com.amond.kmpbook.domain.model.corporateaction.PendingCorporateAction
import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioRecord
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioState
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadState
import com.amond.kmpbook.domain.model.fundproduct.DailyResetState
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyState
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundLedgerEntry
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundState
import com.amond.kmpbook.domain.model.fundstructure.EtnLedgerEntry
import com.amond.kmpbook.domain.model.fundstructure.EtnState
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferenceState
import com.amond.kmpbook.domain.model.reference.FixedIncomeRollRecord
import com.amond.kmpbook.domain.model.reference.KofrIndexState
import com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceState
import com.amond.kmpbook.domain.model.reference.EquityReferenceRebalanceRecord
import com.amond.kmpbook.domain.model.reference.EquityReferenceState
import com.amond.kmpbook.domain.model.reference.FuturesAllocationRecord
import com.amond.kmpbook.domain.model.reference.FuturesReferenceState
import com.amond.kmpbook.domain.model.reference.FuturesRollRecord
import com.amond.kmpbook.domain.model.reference.FundOfFundsRebalanceRecord
import com.amond.kmpbook.domain.model.reference.FundOfFundsState
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaRebalanceRecord
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaState
import com.amond.kmpbook.domain.model.reference.CompositeReferenceRebalanceRecord
import com.amond.kmpbook.domain.model.reference.CompositeReferenceState
import com.amond.kmpbook.domain.model.game.GamePhase
import com.amond.kmpbook.domain.model.game.Screen
import com.amond.kmpbook.domain.model.game.TurnStep
import com.amond.kmpbook.domain.model.history.HistoricalScenarioReference
import com.amond.kmpbook.domain.model.index.MarketIndexId
import com.amond.kmpbook.domain.model.index.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.instrument.FundFinancialState
import com.amond.kmpbook.domain.model.instrument.PendingDistributionEntitlement
import com.amond.kmpbook.domain.model.instrument.DistributionEntitlementOrigin
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.instrument.distributionReceivableByCurrency
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
import com.amond.kmpbook.domain.data.InstrumentCatalogReference
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
import com.amond.kmpbook.presentation.portfolio.CashAdjustmentRecord
import com.amond.kmpbook.presentation.portfolio.CanonicalPortfolioAccountingTotals
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
    /** 번들 및 활성 모드 종목팩의 순서와 SHA-256을 캠페인에 고정한다. */
    val catalogReference: InstrumentCatalogReference,
    /** 캠페인이 사용하는 역사 시나리오의 ID·버전·콘텐츠 해시를 저장 게임에 고정한다. */
    val historicalScenarioReference: HistoricalScenarioReference?,
    val phase: GamePhase,
    val screen: Screen,
    val currentTime: Instant,
    val turn: Long,
    val selectedTurnStep: TurnStep,
    val stocks: List<StockDefinition>,
    /** PER·PSR·ROE 계산의 원천이며, 파생 지표 스냅샷은 저장하지 않는다. */
    val corporateFundamentals: Map<String, CorporateFundamentalState>,
    /** 개방형 ETF의 AUM·괴리율 계산 원천인 좌당 NAV와 설정·환매 좌수 상태다. */
    val fundFinancialStates: Map<String, FundFinancialState>,
    /** 여러 상품이 공유하는 지수/기준 포트폴리오의 현재 구성·비중·대기 일정이다. */
    val referencePortfolioStates: Map<String, ReferencePortfolioState>,
    /** 기준 포트폴리오의 편입·편출·회전율을 재현하는 결정적 재조정 원장이다. */
    val referencePortfolioLedger: List<ReferencePortfolioRecord>,
    /** 일일 레버리지·인버스 상품의 전일 종가 기준점과 당일 누적 NAV 상태다. */
    val dailyResetStates: Map<String, DailyResetState>,
    /** 옵션 운용 상품의 기초 보유량·현금담보·옵션 레그와 roll 주기 상태다. */
    val optionStrategyStates: Map<String, OptionStrategyState>,
    /** 현금 기준수익과 주가지수 풋스프레드를 분리 계상하는 담보형 옵션 상태다. */
    val cashCollateralizedPutSpreadStates: Map<String, CashCollateralizedPutSpreadState>,
    /** ETN의 계약상 지표가치·발행수량·미지급 쿠폰·발행사 신용 상태다. */
    val etnStates: Map<String, EtnState>,
    /** ETN 쿠폰·발행/상환·계약 종료의 배치 원장이다. */
    val etnLedger: List<EtnLedgerEntry>,
    /** CEF의 총자산·고정 주식수·부채·우선주·UNII·시장 할인 상태다. */
    val closedEndFundStates: Map<String, ClosedEndFundState>,
    /** CEF 분배·자본행동·차입/상환의 배치 원장이다. */
    val closedEndFundLedger: List<ClosedEndFundLedgerEntry>,
    /** 채권·현금 benchmark의 금리곡선, 신용스프레드, 만기 ladder 상태다. */
    val fixedIncomeReferenceStates: Map<String, FixedIncomeReferenceState>,
    /** 전일 fixing, 다음 공표 대기값과 ACT/365 일복리 KOFR 지수 상태다. */
    val kofrIndexStates: Map<BenchmarkRef, KofrIndexState>,
    /** 만기 도래 sleeve의 편출·신규 만기군 편입 원장이다. */
    val fixedIncomeRollLedger: List<FixedIncomeRollRecord>,
    /** 실물·현물형 원자재 benchmark의 현물·담보·순 carry 상태다. */
    val commoditySpotReferenceStates: Map<BenchmarkRef, CommoditySpotReferenceState>,
    /** 선물형 benchmark의 만기곡선·계약비중·동적 sleeve 배분 상태다. */
    val futuresReferenceStates: Map<BenchmarkRef, FuturesReferenceState>,
    /** front/deferred 계약 교체를 재현하는 선물 roll 원장이다. */
    val futuresRollLedger: List<FuturesRollRecord>,
    /** 전술적 multi-sleeve benchmark의 목표 비중 변경 원장이다. */
    val futuresAllocationLedger: List<FuturesAllocationRecord>,
    /** 상세 holdings 규칙 전 주식 benchmark의 대표 구성·현재/목표 비중·요인 상태다. */
    val equityReferenceStates: Map<BenchmarkRef, EquityReferenceState>,
    /** 주식 대표 basket의 selection과 weight-only reweight를 구분하는 원장이다. */
    val equityReferenceLedger: List<EquityReferenceRebalanceRecord>,
    /** PCEF/YYY/YMAX 계열이 선택한 비거래 기초 펀드 basket과 현재/목표 비중이다. */
    val fundOfFundsStates: Map<BenchmarkRef, FundOfFundsState>,
    /** 펀드오브펀드의 구성 변경과 weight-only reweight를 구분하는 원장이다. */
    val fundOfFundsRebalanceLedger: List<FundOfFundsRebalanceRecord>,
    /** 시장중립·상대가치·추세 driver의 서명 노출과 bounded 신호 상태다. */
    val alternativeRiskPremiaStates: Map<BenchmarkRef, AlternativeRiskPremiaState>,
    /** 대체위험 프리미엄의 목표 노출 변경 원장이다. */
    val alternativeRiskPremiaRebalanceLedger: List<AlternativeRiskPremiaRebalanceRecord>,
    /** 실제 기업 및 typed benchmark sleeve를 합성한 동적 기준 상태다. */
    val compositeReferenceStates: Map<BenchmarkRef, CompositeReferenceState>,
    /** 복합 기준의 선정·재가중·회전율과 구성 해시 원장이다. */
    val compositeReferenceRebalanceLedger: List<CompositeReferenceRebalanceRecord>,
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
    /** 날짜별 이력 사이에서 진행 중인 장중 누적 벤치마크 값이다. */
    val currentBenchmarkValue: Double,
    val benchmarkHistory: List<BenchmarkPoint>,
    val transactionCosts: List<TransactionCostRecord>,
    val realizedGains: List<RealizedGainRecord>,
    val fifoCostBasisBook: FifoCostBasisBook = FifoCostBasisBook(),
    /** 보유·지급액과 무관하게 상품 분배 주기를 마지막으로 평가한 종목별 날짜다. */
    val lastEvaluatedDistributionDateByStock: Map<String, LocalDate>,
    /** 분배락일 보유량을 동결했지만 지급일이 아직 오지 않은 ETF 현금 권리다. */
    val pendingDistributionEntitlements: List<PendingDistributionEntitlement>,
    /** 분배락 당시 보유권·금액 source를 지급 뒤에도 보존하는 영구 계보 원장이다. */
    val distributionEntitlementOrigins: List<DistributionEntitlementOrigin>,
    val dividendLedger: List<DividendLedgerEntry>,
    val foreignExchangeLedger: List<ForeignExchangeRecord>,
    /** 명시적 디버그 현금 설정만 보존하는 관측 사실 원장이다. */
    val cashAdjustmentLedger: List<CashAdjustmentRecord>,
    val annualTaxLedgers: Map<Int, AnnualTaxLedger>,
    val taxPaymentNotices: List<TaxPaymentNotice>,
    /** 초기자본·일별 마감 스냅샷·현재 평가값에서 파생한 최고자산이다. */
    val peakAssetsKrw: Double,
    /** 일별 마감 및 현재 평가 시계열의 running peak 대비 최대 낙폭이다. */
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

    val distributionReceivableByCurrency: Map<Currency, Double>
        get() = pendingDistributionEntitlements.distributionReceivableByCurrency()

    val distributionReceivableValueKrw: Double
        get() = distributionReceivableByCurrency.entries.sumOf { (currency, amount) ->
            amount * if (currency == Currency.USD) macro.usdKrw else 1.0
        }

    val totalAssetsKrw: Double
        get() = cashValueKrw + stockValueKrw + distributionReceivableValueKrw
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

    val realizedProfitKrw: Double
        get() = CanonicalPortfolioAccountingTotals.realizedProfitKrw(
            realizedGains.asSequence().map(RealizedGainRecord::taxGainKrw),
        )
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
            accountingSequenceExclusiveUpperBound = nextSequence,
            cashByCurrency = cashByCurrency,
            holdings = holdingList,
            distributionReceivableByCurrency = distributionReceivableByCurrency,
            exchangeRatesToKrw = mapOf(Currency.USD to macro.usdKrw),
            initialCapitalKrw = initialCapitalKrw,
            realizedProfitKrw = realizedProfitKrw,
            cumulativeCommissionKrw = totalCommissionKrw,
            cumulativeTaxKrw = CanonicalPortfolioAccountingTotals.cumulativeTaxKrw(
                saleTaxesKrw = transactionCosts.asSequence()
                    .map(TransactionCostRecord::saleTaxKrw),
                dividendWithholdingTaxesKrw = dividendLedger.asSequence()
                    .map(DividendLedgerEntry::withholdingTaxKrw),
                paidAnnualTaxesKrw = taxPaymentNotices.asSequence()
                    .filter { notice -> notice.status == TaxLiabilityStatus.PAID }
                    .map { notice -> notice.amountKrw.toDouble() },
            ),
            holdingCostBasisKrw = fifoCostBasisBook.lots.groupBy { it.stockId }
                .mapValues { (_, lots) -> lots.sumOf { it.remainingCostBasisKrw }.toDouble() },
        )

    val annualTaxSummary: AnnualTaxLedger?
        get() = annualTaxLedgers[currentDate.year]

    val benchmarkReturn: Double get() = currentBenchmarkValue / BENCHMARK_START_VALUE - 1.0

    private companion object {
        const val UPCOMING_EVENT_LIMIT: Int = 12
        const val BENCHMARK_START_VALUE: Double = 100.0
    }
}
