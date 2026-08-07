package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.data.StockCatalog
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.CorporateActionKind
import com.amond.kmpbook.domain.model.CorporateActionMath
import com.amond.kmpbook.domain.model.CorporateActionRecord
import com.amond.kmpbook.domain.model.CorporateActionSource
import com.amond.kmpbook.domain.model.EtfTaxCategory
import com.amond.kmpbook.domain.model.DistributionFrequency
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.GameEndReason
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GamePhase
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.domain.model.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.Order
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderStatus
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PortfolioSnapshot
import com.amond.kmpbook.domain.model.PendingCorporateAction
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.ReferenceCurrency
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.model.ScheduledEventEmission
import com.amond.kmpbook.domain.model.ScheduledEventKind
import com.amond.kmpbook.domain.model.ScheduledEventOccurrence
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.domain.model.Trade
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.simulation.DeterministicRandom
import com.amond.kmpbook.domain.simulation.EventEngine
import com.amond.kmpbook.domain.simulation.EventGenerationContext
import com.amond.kmpbook.domain.simulation.EventShockCalculator
import com.amond.kmpbook.domain.simulation.MacroEnvironment
import com.amond.kmpbook.domain.simulation.MarketMicrostructure
import com.amond.kmpbook.domain.simulation.MarketIndexCalculationInput
import com.amond.kmpbook.domain.simulation.MarketIndexEngine
import com.amond.kmpbook.domain.simulation.OrderBookEngine
import com.amond.kmpbook.domain.simulation.OrderBookGenerationInput
import com.amond.kmpbook.domain.simulation.OrderBookSnapshot
import com.amond.kmpbook.domain.simulation.PriceEngine
import com.amond.kmpbook.domain.simulation.PriceGenerationInput
import com.amond.kmpbook.domain.simulation.ScheduledEventEngine
import com.amond.kmpbook.domain.tax.AnnualStockTaxCalculator
import com.amond.kmpbook.domain.tax.AnnualStockTaxRequest
import com.amond.kmpbook.domain.tax.BrokerFeeCalculator
import com.amond.kmpbook.domain.tax.BrokerFeeRequest
import com.amond.kmpbook.domain.tax.BrokerFeeSchedule
import com.amond.kmpbook.domain.tax.DividendTaxCalculator
import com.amond.kmpbook.domain.tax.DividendTaxClass
import com.amond.kmpbook.domain.tax.DividendTaxRequest
import com.amond.kmpbook.domain.tax.DomesticSaleTaxCalculator
import com.amond.kmpbook.domain.tax.DomesticSaleTaxRequest
import com.amond.kmpbook.domain.tax.DomesticEtfSaleTaxCalculator
import com.amond.kmpbook.domain.tax.DomesticEtfSaleTaxRequest
import com.amond.kmpbook.domain.tax.ForeignInstrumentTaxClass
import com.amond.kmpbook.domain.tax.FifoCostBasisBook
import com.amond.kmpbook.domain.tax.MoneyAmount
import com.amond.kmpbook.domain.tax.MoneyRoundingPolicy
import com.amond.kmpbook.domain.tax.MajorShareholderAssessmentRequest
import com.amond.kmpbook.domain.tax.MajorShareholderCalculator
import com.amond.kmpbook.domain.tax.RealizedStockGain
import com.amond.kmpbook.domain.tax.ShareholderHoldingSnapshot
import com.amond.kmpbook.domain.tax.ShareholderRelation
import com.amond.kmpbook.domain.tax.StockGainTaxTreatment
import com.amond.kmpbook.domain.tax.TaxRate
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.round
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

internal class SimulatorRuntime(
    initialOptions: NewGameOptions,
    startInSetup: Boolean = false,
) {
    var options: NewGameOptions = initialOptions
        private set
    var phase: GamePhase = if (startInSetup) GamePhase.SETUP else GamePhase.PLAYING
        private set
    var screen: Screen = Screen.HOME
        private set
    var currentTime: Instant = GameCalendar.startInstant
        private set
    var turn: Long = 0L
        private set
    var selectedTurnStep: TurnStep = TurnStep.ONE_HOUR
        private set
    var selectedStockId: String? = null
        private set
    var isAdvancing: Boolean = false
        private set
    var lastMessage: String? = "새 게임을 시작했습니다."
        private set

    private val baseStockDefinitions: List<StockDefinition> = if (options.usFractionalTrading) {
        StockCatalog.withUsFractionalTrading()
    } else {
        StockCatalog.all
    }
    private val baseStockById = baseStockDefinitions.associateBy(StockDefinition::id)
    private val mutableStocks = baseStockDefinitions.toMutableList()
    val stocks: List<StockDefinition> get() = mutableStocks
    private val stockById = mutableStocks.associateByTo(linkedMapOf(), StockDefinition::id)
    private val quotes = linkedMapOf<String, Quote>()
    private val history = linkedMapOf<String, ArrayDeque<PriceBar>>()
    private val pendingEtfReferenceReturns = mutableMapOf<String, Double>()
    private val marketIndices = linkedMapOf<MarketIndexId, MarketIndexSnapshot>()
    private val marketIndexHistory = linkedMapOf<MarketIndexId, ArrayDeque<MarketIndexSnapshot>>()
    private val dailyTrackers = mutableMapOf<String, DailyPriceTracker>()
    private val cash = mutableMapOf(Currency.KRW to options.initialCapitalKrw, Currency.USD to 0.0)
    private val holdings = linkedMapOf<String, Holding>()
    private val orders = mutableListOf<Order>()
    private val trades = mutableListOf<Trade>()
    private val transactionCosts = mutableListOf<TransactionCostRecord>()
    private val realizedGains = mutableListOf<RealizedGainRecord>()
    private var fifoCostBasisBook = FifoCostBasisBook()
    private val taxExchangeRatesByTradeId = linkedMapOf<String, Double>()
    private val pendingTaxSettlementTradeIds = linkedSetOf<String>()
    private val dividends = mutableListOf<DividendLedgerEntry>()
    private val foreignExchanges = mutableListOf<ForeignExchangeRecord>()
    private val activeEvents = mutableListOf<GameEvent>()
    private val newsEvents = mutableListOf<GameEvent>()
    private val readEventIds = mutableSetOf<String>()
    private val watchlistedStockIds = linkedSetOf<String>()
    private val pendingCorporateActions = mutableListOf<PendingCorporateAction>()
    private val corporateActionLedger = mutableListOf<CorporateActionRecord>()
    private val terminatedInstrumentIds = linkedSetOf<String>()
    private val portfolioSnapshots = mutableListOf<PortfolioSnapshot>()
    private val dailyStatistics = mutableListOf<DailyPortfolioStat>()
    private val benchmarkHistory = mutableListOf<BenchmarkPoint>()
    private val annualTaxLedgers = linkedMapOf<Int, com.amond.kmpbook.domain.tax.AnnualTaxLedger>()
    private val taxPaymentNotices = mutableListOf<TaxPaymentNotice>()

    private var macro = MacroEnvironment(
        usdKrw = options.initialUsdKrw,
        fxRatesToKrw = initialFxRates(options.initialUsdKrw),
        previousFxRatesToKrw = initialFxRates(options.initialUsdKrw),
    )
    private var macroDate = gameDate(currentTime)
    private var benchmarkValue = BENCHMARK_START
    private var peakAssetsKrw = options.initialCapitalKrw
    private var maximumDrawdown = 0.0
    private var nextSequence = 1L
    private var usCircuitBreakerState = UsCircuitBreakerState()

    private val random = DeterministicRandom(
        DeterministicRandom.mixSeed(options.seed, MACRO_STREAM_ID),
    )
    private val priceEngine = PriceEngine(DeterministicRandom.mixSeed(options.seed, PRICE_STREAM_ID))
    private val orderBookEngine = OrderBookEngine(DeterministicRandom.mixSeed(options.seed, BOOK_STREAM_ID))
    private val marketIndexEngine = MarketIndexEngine()
    private val eventEngine = EventEngine(DeterministicRandom.mixSeed(options.seed, EVENT_STREAM_ID))
    private val scheduledEventEngine = ScheduledEventEngine(
        DeterministicRandom.mixSeed(options.seed, SCHEDULED_EVENT_STREAM_ID),
    )
    private val domesticSaleTaxCalculator = DomesticSaleTaxCalculator()
    private val domesticEtfSaleTaxCalculator = DomesticEtfSaleTaxCalculator()
    private val majorShareholderCalculator = MajorShareholderCalculator()
    private val annualStockTaxCalculator = AnnualStockTaxCalculator()
    private val dividendTaxCalculator = DividendTaxCalculator()
    private val brokerFeeCalculator = BrokerFeeCalculator(
        BrokerFeeSchedule(
            id = "simulator-general-account-2026",
            brokerName = "시뮬레이션 일반계좌",
            domesticCommissionRate = TaxRate(150L), // 0.015%
            usCommissionRate = TaxRate(700L), // 0.070%
            fxSpreadRate = TaxRate(1_000L), // 0.10%
        ),
    )

    init {
        require(stocks.size >= 24) { "기본 종목 카탈로그가 충분하지 않습니다." }
        selectedStockId = stocks.firstOrNull()?.id
        initializeMarketData()
        initializeMarketIndices(currentTime)
        recalculateAnnualTax(gameDate(currentTime).year)
        recordDailySnapshot(gameDate(currentTime), currentTime)
    }

    fun selectScreen(value: Screen) {
        screen = value
        lastMessage = null
    }

    fun selectStock(stockId: String): Boolean {
        if (stockId !in stockById) return fail("존재하지 않는 종목입니다.")
        selectedStockId = stockId
        lastMessage = null
        return true
    }

    fun selectTurnStep(value: TurnStep) {
        selectedTurnStep = value
        lastMessage = null
    }

    fun setAutoExchange(enabled: Boolean) {
        options = options.copy(autoExchange = enabled)
        lastMessage = if (enabled) "자동 환전을 켰습니다." else "자동 환전을 껐습니다."
    }

    fun clearMessage() {
        lastMessage = null
    }

    fun markEventRead(eventId: String) {
        if (newsEvents.any { it.id == eventId }) readEventIds += eventId
    }

    fun markAllEventsRead() {
        readEventIds += newsEvents.map(GameEvent::id)
    }

    fun toggleWatchlist(stockId: String): Boolean {
        if (stockId !in stockById) return fail("존재하지 않는 종목입니다.")
        val added = if (stockId in watchlistedStockIds) {
            watchlistedStockIds.remove(stockId)
            false
        } else {
            watchlistedStockIds.add(stockId)
            true
        }
        lastMessage = if (added) "관심 종목에 추가했습니다." else "관심 종목에서 해제했습니다."
        return added
    }

    fun pause() {
        if (phase == GamePhase.PLAYING) phase = GamePhase.PAUSED
    }

    fun resume() {
        if (phase == GamePhase.PAUSED) phase = GamePhase.PLAYING
    }

    fun finishSettlement() {
        if (phase == GamePhase.SETTLEMENT) {
            phase = GamePhase.FINISHED
            lastMessage = "최종 정산을 완료했습니다."
        }
    }

    fun advance(step: TurnStep) {
        if (phase != GamePhase.PLAYING) {
            fail(if (phase == GamePhase.SETTLEMENT || phase == GamePhase.FINISHED) "종료된 게임은 진행할 수 없습니다." else "게임이 일시 정지되어 있습니다.")
            return
        }
        isAdvancing = true
        lastMessage = null
        var advanced = 0
        repeat(step.hours) {
            if (GameCalendar.isFinished(currentTime)) return@repeat
            advanceOneHour()
            advanced += 1
        }
        if (GameCalendar.isFinished(currentTime)) enterSettlement()
        isAdvancing = false
        if (phase == GamePhase.PLAYING) lastMessage = "${advanced}시간 진행했습니다."
    }

    fun placeOrder(request: OrderRequest): Boolean {
        if (phase != GamePhase.PLAYING) return fail("진행 중인 게임에서만 주문할 수 있습니다.")
        val stock = stockById[request.stockId] ?: return fail("존재하지 않는 종목입니다.")
        if (isInstrumentMatured(stock, currentTime)) {
            return fail("만기상환이 끝난 상품은 더 이상 주문할 수 없습니다.")
        }
        val isFullCorporateActionRemainder = request.side == OrderSide.SELL &&
            holdings[stock.id]?.let { abs(it.quantity - request.quantity) < QUANTITY_EPSILON } == true
        if (!request.quantity.isFinite() ||
            (!stock.acceptsQuantity(request.quantity) && !isFullCorporateActionRemainder)
        ) {
            return fail("주문 수량은 ${stock.quantityStep} 단위의 양수여야 합니다.")
        }
        if (request.type == OrderType.LIMIT && (request.limitPrice == null || request.limitPrice <= 0.0)) {
            return fail("지정가 주문에는 0보다 큰 가격이 필요합니다.")
        }
        if (request.limitPrice != null) {
            val rounded = MarketMicrostructure.roundNearest(stock.market, request.limitPrice)
            if (abs(rounded - request.limitPrice) > PRICE_EPSILON) {
                return fail("지정가가 ${stock.market.displayName} 호가 단위에 맞지 않습니다.")
            }
        }
        if (!validateOrderResources(stock, request)) return false

        val order = Order(
            id = nextId("order"),
            stockId = stock.id,
            side = request.side,
            type = request.type,
            quantity = request.quantity,
            createdAt = currentTime,
            limitPrice = request.limitPrice,
            status = OrderStatus.ACCEPTED,
            timeInForce = request.timeInForce,
        )
        orders += order
        val index = orders.lastIndex
        tryImmediateExecution(index, stock)
        if (orders[index].status == OrderStatus.ACCEPTED) {
            when (request.timeInForce) {
                TimeInForce.IMMEDIATE_OR_CANCEL -> orders[index] = orders[index].copy(
                    status = OrderStatus.CANCELLED,
                    updatedAt = currentTime,
                    rejectionReason = "즉시 체결되지 않은 잔량을 취소했습니다.",
                )
                TimeInForce.FILL_OR_KILL -> orders[index] = orders[index].copy(
                    status = OrderStatus.REJECTED,
                    updatedAt = currentTime,
                    rejectionReason = "즉시 전량 체결 조건을 충족하지 못했습니다.",
                )
                TimeInForce.DAY,
                TimeInForce.GOOD_TILL_CANCELLED,
                -> Unit
            }
        }
        if (orders[index].status == OrderStatus.REJECTED) return false
        lastMessage = when (orders[index].status) {
            OrderStatus.FILLED -> "주문이 체결되었습니다."
            OrderStatus.CANCELLED -> "즉시 체결되지 않아 주문을 취소했습니다."
            else -> "주문이 접수되었습니다."
        }
        return true
    }

    fun cancelOrder(orderId: String): Boolean {
        val index = orders.indexOfFirst { it.id == orderId }
        if (index < 0) return fail("주문을 찾을 수 없습니다.")
        val order = orders[index]
        if (!order.canCancel) return fail("현재 상태에서는 주문을 취소할 수 없습니다.")
        orders[index] = order.copy(status = OrderStatus.CANCELLED, updatedAt = currentTime)
        lastMessage = "주문을 취소했습니다."
        return true
    }

    fun exchange(from: Currency, to: Currency, amount: Double): Boolean {
        if (phase != GamePhase.PLAYING && phase != GamePhase.PAUSED) return fail("현재는 환전할 수 없습니다.")
        if (from == to) return fail("서로 다른 통화를 선택해 주세요.")
        if (!amount.isFinite() || amount <= 0.0) return fail("환전 금액은 0보다 커야 합니다.")
        val result = exchangeInternal(from, to, amount, automatic = false)
        if (result) lastMessage = "환전이 완료되었습니다."
        return result
    }

    fun snapshot(): SimulatorUiState {
        val sessions = Market.entries.associateWith(::marketSessionAtCurrentTime)
        val scheduledActiveEvents = scheduledEventEngine.activeImpactEventsAt(
            currentTime,
            stocks.filterNot { isInstrumentMatured(it, currentTime) },
        )
        val stateQuotes = quotes.mapValues { (stockId, quote) ->
            val stock = stockById.getValue(stockId)
            quote.copy(session = sessions.getValue(stock.market))
        }.toMutableMap()
        val selectedBook = selectedStockId?.takeIf { id ->
            !isInstrumentMatured(stockById.getValue(id), currentTime)
        }?.let { id ->
            val stock = stockById.getValue(id)
            val quote = stateQuotes.getValue(id)
            orderBook(stock, quote, sessions.getValue(stock.market)).also { book ->
                stateQuotes[id] = book.applyTopOfBook(quote)
            }
        }
        return SimulatorUiState(
            options = options,
            phase = phase,
            screen = screen,
            currentTime = currentTime,
            turn = turn,
            selectedTurnStep = selectedTurnStep,
            stocks = stocks.toList(),
            selectedStockId = selectedStockId,
            quotes = stateQuotes.toMap(),
            priceHistory = history.mapValues { (_, bars) -> bars.toList() },
            cashByCurrency = cash.toMap(),
            holdings = holdings.toMap(),
            orders = orders.toList(),
            trades = trades.toList(),
            selectedOrderBook = selectedBook,
            marketSessions = sessions,
            macro = macro,
            activeEvents = (activeEvents.filter { it.isActiveAt(currentTime) } + scheduledActiveEvents)
                .distinctBy(GameEvent::id),
            newsEvents = newsEvents.sortedByDescending(GameEvent::startsAt),
            readEventIds = readEventIds.toSet(),
            portfolioSnapshots = portfolioSnapshots.toList(),
            dailyStatistics = dailyStatistics.toList(),
            benchmarkHistory = benchmarkHistory.toList(),
            transactionCosts = transactionCosts.toList(),
            realizedGains = realizedGains.toList(),
            fifoCostBasisBook = fifoCostBasisBook,
            dividendLedger = dividends.toList(),
            foreignExchangeLedger = foreignExchanges.toList(),
            annualTaxLedgers = annualTaxLedgers.toMap(),
            taxPaymentNotices = taxPaymentNotices.toList(),
            peakAssetsKrw = peakAssetsKrw,
            maximumDrawdown = maximumDrawdown,
            rngState = random.snapshot(),
            eventEngineSnapshot = eventEngine.snapshot(),
            nextSequence = nextSequence,
            isAdvancing = isAdvancing,
            lastMessage = lastMessage,
            pendingEtfReferenceReturns = pendingEtfReferenceReturns.toMap(),
            marketIndices = marketIndices.toMap(),
            marketIndexHistory = marketIndexHistory.mapValues { (_, values) -> values.toList() },
            usCircuitBreakerState = usCircuitBreakerState,
            taxExchangeRatesByTradeId = taxExchangeRatesByTradeId.toMap(),
            pendingTaxSettlementTradeIds = pendingTaxSettlementTradeIds.toSet(),
            watchlistedStockIds = watchlistedStockIds.toSet(),
            pendingCorporateActions = pendingCorporateActions.toList(),
            corporateActionLedger = corporateActionLedger.toList(),
            terminatedInstrumentIds = terminatedInstrumentIds.toSet(),
        )
    }

    private fun restoreFrom(state: SimulatorUiState) {
        require(GameCalendar.isWithinGameRange(state.currentTime)) { "저장 시각이 게임 범위를 벗어났습니다." }
        require(state.turn == GameCalendar.turnAt(state.currentTime)) { "저장 턴과 시각이 일치하지 않습니다." }
        val ids = stockById.keys
        val savedIds = state.stocks.map(StockDefinition::id)
        require(savedIds.distinct().size == savedIds.size && savedIds.all(ids::contains)) {
            "저장 종목 카탈로그에 현재 버전이 알 수 없는 상품이 있습니다."
        }
        require(state.quotes.keys == savedIds.toSet() && state.priceHistory.keys == savedIds.toSet()) {
            "저장된 모든 상품의 시세와 차트 기록이 필요합니다."
        }
        require(state.cashByCurrency.keys.containsAll(Currency.entries)) { "통화별 현금 잔액이 누락되었습니다." }
        require(state.cashByCurrency.values.all { it >= 0.0 && it.isFinite() }) { "현금 잔액이 올바르지 않습니다." }
        require(state.holdings.keys.all(ids::contains)) { "알 수 없는 보유 종목이 있습니다." }
        require(state.orders.all { it.stockId in ids } && state.trades.all { it.stockId in ids }) {
            "주문·체결 원장에 알 수 없는 종목이 있습니다."
        }
        require(state.pendingEtfReferenceReturns.orEmpty().all { (stockId, value) ->
            stockById[stockId]?.isFundLike == true && value.isFinite()
        }) { "ETF 개장 갭 상태가 올바르지 않습니다." }
        require(state.watchlistedStockIds.orEmpty().all(ids::contains)) {
            "관심 종목에 현재 카탈로그가 알 수 없는 ID가 있습니다."
        }
        require(state.pendingCorporateActions.orEmpty().all { it.stockId in ids }) {
            "예정 기업행동에 알 수 없는 종목이 있습니다."
        }
        require(state.corporateActionLedger.orEmpty().all { it.stockId in ids }) {
            "기업행동 원장에 알 수 없는 종목이 있습니다."
        }
        validateCorporateActionState(
            pending = state.pendingCorporateActions.orEmpty(),
            applied = state.corporateActionLedger.orEmpty(),
            validStockIds = ids,
        )
        require(state.terminatedInstrumentIds.orEmpty().all(ids::contains)) {
            "거래종료 원장에 현재 카탈로그가 알 수 없는 ID가 있습니다."
        }
        val savedIndices = state.marketIndices.orEmpty()
        require(savedIndices.isEmpty() || savedIndices.keys == MarketIndexId.entries.toSet()) {
            "대표 지수 현재값 4종이 모두 필요합니다."
        }
        require(state.marketIndexHistory.orEmpty().all { (id, values) ->
            id in MarketIndexId.entries && values.all { it.id == id } &&
                values.zipWithNext().all { (left, right) -> left.timestamp <= right.timestamp }
        }) { "대표 지수 이력이 올바르지 않습니다." }

        options = state.options
        phase = state.phase
        screen = state.screen
        currentTime = state.currentTime
        turn = state.turn
        selectedTurnStep = state.selectedTurnStep
        selectedStockId = state.selectedStockId?.also { require(it in ids) }
        isAdvancing = false
        lastMessage = "저장 게임을 불러왔습니다."
        macro = normalizeFxState(state.macro)
        macroDate = gameDate(currentTime)
        benchmarkValue = state.benchmarkHistory.lastOrNull()?.value ?: BENCHMARK_START
        peakAssetsKrw = state.peakAssetsKrw
        maximumDrawdown = state.maximumDrawdown
        nextSequence = state.nextSequence
        require(nextSequence > 0L) { "저장 시퀀스가 올바르지 않습니다." }
        val accountingSequences = buildList {
            state.trades.mapNotNullTo(this) { it.accountingSequence }
            state.dividendLedger.mapNotNullTo(this) { it.accountingSequence }
            state.corporateActionLedger.orEmpty().mapNotNullTo(this) { it.accountingSequence }
        }
        require(accountingSequences.all { it in 1 until nextSequence } &&
            accountingSequences.distinct().size == accountingSequences.size
        ) { "체결·분배·기업행동의 전역 회계 순번이 올바르지 않습니다." }
        require(state.dividendLedger.map(DividendLedgerEntry::id).distinct().size == state.dividendLedger.size &&
            state.dividendLedger.all { it.stockId in ids && (it.accountingSequence == null || it.accountingSequence > 0L) }
        ) { "분배 원장 ID·종목·회계 순번이 올바르지 않습니다." }

        random.restore(state.rngState)
        eventEngine.restore(state.eventEngineSnapshot)
        quotes.clear()
        quotes.putAll(state.quotes)
        history.clear()
        state.priceHistory.forEach { (stockId, bars) ->
            require(bars.isNotEmpty()) { "차트 기록이 비어 있습니다." }
            history[stockId] = ArrayDeque(bars.takeLast(MAX_RECENT_BARS))
        }
        for (stock in stocks) {
            if (stock.id !in quotes) initializeInstrumentMarketData(stock, currentTime)
        }
        pendingEtfReferenceReturns.clear()
        pendingEtfReferenceReturns.putAll(state.pendingEtfReferenceReturns.orEmpty())
        marketIndices.clear()
        marketIndexHistory.clear()
        if (savedIndices.isEmpty()) {
            initializeMarketIndices(currentTime)
        } else {
            marketIndices.putAll(savedIndices)
            for (id in MarketIndexId.entries) {
                val values = state.marketIndexHistory.orEmpty()[id].orEmpty()
                    .takeLast(MAX_INDEX_BARS)
                marketIndexHistory[id] = ArrayDeque<MarketIndexSnapshot>().apply {
                    if (values.isEmpty()) addLast(savedIndices.getValue(id)) else addAll(values)
                }
            }
        }
        usCircuitBreakerState = state.usCircuitBreakerState ?: UsCircuitBreakerState()
        cash.clear()
        cash.putAll(state.cashByCurrency)
        holdings.clear()
        holdings.putAll(state.holdings)
        orders.clear()
        orders += state.orders
        trades.clear()
        trades += state.trades
        transactionCosts.clear()
        transactionCosts += state.transactionCosts
        realizedGains.clear()
        realizedGains += state.realizedGains
        dividends.clear()
        dividends += state.dividendLedger
        corporateActionLedger.clear()
        corporateActionLedger += state.corporateActionLedger.orEmpty()
        rebuildDynamicStockDefinitions(corporateActionLedger)
        val savedStocksById = state.stocks.associateBy(StockDefinition::id)
        require(state.stocks.all { saved ->
            stockById[saved.id]?.sharesOutstanding == saved.sharesOutstanding
        }) { "저장된 유통주식수가 기업행동 원장과 일치하지 않습니다." }
        terminatedInstrumentIds.clear()
        terminatedInstrumentIds += state.terminatedInstrumentIds.orEmpty()
        // 구형 저장은 거래종료 집합이 없으므로 만기일과 기존 만기 뉴스를 함께 복원 근거로 쓴다.
        if (state.terminatedInstrumentIds == null) {
            stocks.filter { stock ->
                instrumentMaturityDate(stock)?.let { marketDate(stock.market, currentTime) >= it } == true &&
                    state.newsEvents.any { it.id.startsWith("instrument-maturity-redemption:${stock.id}:") }
            }.mapTo(terminatedInstrumentIds, StockDefinition::id)
        }
        restoreTaxExchangeRateLedger(state)
        val replayedTaxYears = replayTaxAccountingLedger()
        require(fifoCostBasisBook.lots.all { it.stockId in ids } && fifoBookMatchesHoldings()) {
            "FIFO 세무원장과 보유 수량이 일치하지 않습니다."
        }
        foreignExchanges.clear()
        foreignExchanges += state.foreignExchangeLedger
        activeEvents.clear()
        activeEvents += state.eventEngineSnapshot.activeEvents
        newsEvents.clear()
        newsEvents += state.newsEvents.sortedBy(GameEvent::startsAt)
        readEventIds.clear()
        readEventIds += state.readEventIds
        watchlistedStockIds.clear()
        watchlistedStockIds += state.watchlistedStockIds.orEmpty()
        pendingCorporateActions.clear()
        pendingCorporateActions += state.pendingCorporateActions.orEmpty()
        portfolioSnapshots.clear()
        portfolioSnapshots += state.portfolioSnapshots
        dailyStatistics.clear()
        dailyStatistics += state.dailyStatistics
        benchmarkHistory.clear()
        benchmarkHistory += state.benchmarkHistory
        annualTaxLedgers.clear()
        annualTaxLedgers.putAll(state.annualTaxLedgers)
        taxPaymentNotices.clear()
        taxPaymentNotices += state.taxPaymentNotices
        replayedTaxYears.forEach(::recalculateAnnualTax)

        dailyTrackers.clear()
        for (stock in stocks) {
            val quote = quotes.getValue(stock.id)
            val trackerDate = marketDate(stock.market, currentTime)
            dailyTrackers[stock.id] = DailyPriceTracker(
                date = trackerDate,
                basePrice = quote.previousClose,
                open = quote.open,
                high = quote.high,
                low = quote.low,
                hasRegularTrading = history.getValue(stock.id).any { bar ->
                    bar.volume > 0L && marketDate(stock.market, bar.startTime) == trackerDate
                },
            )
        }
    }

    internal fun setTimeForTesting(time: Instant) {
        currentTime = GameCalendar.clamp(time)
        turn = GameCalendar.turnAt(currentTime)
        phase = if (GameCalendar.isFinished(currentTime)) GamePhase.SETTLEMENT else GamePhase.PLAYING
        screen = if (phase == GamePhase.SETTLEMENT) Screen.ENDING else screen
        lastMessage = null
    }

    private fun initializeMarketData() {
        for (stock in stocks) {
            initializeInstrumentMarketData(stock, currentTime)
        }
    }

    private fun initializeMarketIndices(at: Instant) {
        val initial = marketIndexEngine.initialSnapshots(at)
        marketIndices.putAll(initial)
        for ((id, snapshot) in initial) {
            marketIndexHistory[id] = ArrayDeque<MarketIndexSnapshot>().apply { addLast(snapshot) }
        }
    }

    private fun initializeInstrumentMarketData(stock: StockDefinition, at: Instant) {
        val session = marketSession(stock.market, at)
        quotes[stock.id] = Quote(
            stockId = stock.id,
            timestamp = at,
            price = stock.initialPrice,
            previousClose = stock.initialPrice,
            session = session,
        )
        history[stock.id] = ArrayDeque<PriceBar>().apply {
            addLast(
                PriceBar(
                    stockId = stock.id,
                    startTime = at - 1.hours,
                    endTime = at,
                    step = TurnStep.ONE_HOUR,
                    open = stock.initialPrice,
                    high = stock.initialPrice,
                    low = stock.initialPrice,
                    close = stock.initialPrice,
                    volume = 0L,
                ),
            )
        }
        dailyTrackers[stock.id] = DailyPriceTracker(
            date = marketDate(stock.market, at),
            basePrice = stock.initialPrice,
            open = stock.initialPrice,
            high = stock.initialPrice,
            low = stock.initialPrice,
            hasRegularTrading = false,
        )
    }

    private fun advanceOneHour() {
        val from = currentTime
        val to = GameCalendar.advanceHours(from, 1)
        if (to <= from) return
        val fromGameDate = gameDate(from)

        processInstrumentLifecycle(from)
        applyDueCorporateActions(from, to)
        updateMacro(from)
        generateEvents(from, to)
        prepareUsCircuitBreaker(from)
        val activeStocks = stocks.filterNot { isInstrumentMatured(it, from) }
        val scheduledImpactEvents = scheduledEventEngine.impactEventsBetween(from, to, activeStocks)

        val previousClosesByStockId = quotes.mapValues { (_, quote) -> quote.price }
        val generatedBars = linkedMapOf<String, PriceBar>()
        val ordinaryTradingFractions = mutableMapOf<Market, Double>()
        val tradingFractions = mutableMapOf<Market, Double>()
        for (market in Market.entries) {
            val ordinaryFraction = regularTradingFraction(market, from, to)
            ordinaryTradingFractions[market] = ordinaryFraction
            tradingFractions[market] = if (!market.isUnitedStates) {
                ordinaryFraction
            } else {
                when (macro.usCircuitBreakerLevel) {
                    1, 2 -> ordinaryFraction * 0.75 // 15-minute halt within the one-hour turn.
                    3 -> 0.0
                    else -> ordinaryFraction
                }
            }
        }

        for (stock in stocks) {
            val previousQuote = quotes.getValue(stock.id)
            if (isInstrumentMatured(stock, from)) {
                val flatBar = PriceBar(
                    stockId = stock.id,
                    startTime = from,
                    endTime = to,
                    step = TurnStep.ONE_HOUR,
                    open = previousQuote.price,
                    high = previousQuote.price,
                    low = previousQuote.price,
                    close = previousQuote.price,
                    volume = 0L,
                )
                quotes[stock.id] = previousQuote.copy(
                    timestamp = to,
                    volume = 0L,
                    bidPrice = null,
                    askPrice = null,
                    bidQuantity = 0.0,
                    askQuantity = 0.0,
                    session = MarketSession.CLOSED,
                )
                generatedBars[stock.id] = flatBar
                appendHistory(stock.id, flatBar)
                continue
            }
            val tracker = trackerFor(stock, from, previousQuote.price)
            val fraction = tradingFractions.getValue(stock.market)
            val session = if (fraction > 0.0) MarketSession.REGULAR else marketSession(stock.market, from)
            val impulse = EventShockCalculator.aggregate(
                activeEvents + scheduledImpactEvents,
                stock,
                from,
                to,
            )
            val ordinaryFraction = ordinaryTradingFractions.getValue(stock.market)
            val fairValueFraction = if (
                stock.market.isUnitedStates && macro.usCircuitBreakerLevel in 1..2
            ) {
                // L1/L2 reopens inside this one-hour turn. The 15-minute halt reduces
                // executions and underlying U.S. returns, but FX fair value and fund
                // cost clocks do not wait until the next trading day.
                ordinaryFraction
            } else {
                fraction
            }
            val totalReferenceFraction = stock.etfProfile?.let {
                regionalTradingFraction(it.exposureRegion, from, tradingFractions)
            } ?: fraction
            // Under the uniform-within-the-hour approximation, at most the listing's
            // observable fraction can be reflected in this bar. L1/L2 reopens inside
            // the bar, so its temporary halt is not mistaken for an overnight closure.
            val activeReferenceFraction = minOf(totalReferenceFraction, fairValueFraction)
            val closedReferenceFraction = (totalReferenceFraction - activeReferenceFraction)
                .coerceIn(0.0, 1.0)
            val closedFxFraction = (1.0 - fairValueFraction).coerceIn(0.0, 1.0)
            val previousCarry = if (fraction > 0.0) {
                pendingEtfReferenceReturns.remove(stock.id) ?: 0.0
            } else {
                0.0
            }
            val closedFairValueReturn = if (
                stock.isFundLike && (closedReferenceFraction > 0.0 || closedFxFraction > 0.0)
            ) {
                priceEngine.referenceLogReturn(
                    stock = stock,
                    macro = macro,
                    referenceTradingFraction = closedReferenceFraction,
                    fxTradingFraction = closedFxFraction,
                    eventImpulse = impulse,
                )
            } else {
                0.0
            }
            // U.S. 09:00-09:30 is the leading closed half of the 09:00 hour, so it
            // belongs in that day's 09:30 opening gap. KRX 15:30-16:00 is trailing,
            // therefore it remains pending for the following opening. L3 has no open.
            val hasLeadingClosedComplement = fraction > 0.0 &&
                marketSession(stock.market, from) != MarketSession.REGULAR
            val carriedReference = previousCarry + if (hasLeadingClosedComplement) {
                closedFairValueReturn
            } else {
                0.0
            }
            if (!hasLeadingClosedComplement && closedFairValueReturn != 0.0) {
                if (stock.isFundLike) {
                    pendingEtfReferenceReturns[stock.id] =
                        (pendingEtfReferenceReturns[stock.id] ?: 0.0) + closedFairValueReturn
                }
            }
            val firstRegularBar = fraction > 0.0 && !tracker.hasRegularTrading
            val result = priceEngine.generateHour(
                PriceGenerationInput(
                    stock = stock,
                    startTime = from,
                    previousPrice = previousQuote.price,
                    dailyBasePrice = tracker.basePrice,
                    session = session,
                    macro = macro,
                    eventImpulse = impulse,
                    dayOpen = tracker.open,
                    dayHigh = tracker.high,
                    dayLow = tracker.low,
                    regularTradingFraction = fraction,
                    fairValueTradingFraction = fairValueFraction,
                    referenceTradingFraction = activeReferenceFraction,
                    carriedReferenceLogReturn = carriedReference,
                    isFirstRegularBarOfDay = firstRegularBar,
                ),
            )
            quotes[stock.id] = result.quote
            generatedBars[stock.id] = result.bar
            appendHistory(stock.id, result.bar)
            if (fraction > 0.0) {
                if (firstRegularBar) {
                    tracker.open = result.bar.open
                    tracker.high = result.bar.high
                    tracker.low = result.bar.low
                    tracker.hasRegularTrading = true
                } else {
                    tracker.high = maxOf(tracker.high, result.bar.high)
                    tracker.low = minOf(tracker.low, result.bar.low)
                }
            }
        }

        updateMarketIndices(to, generatedBars, previousClosesByStockId, tradingFractions)
        updateMarketChange(generatedBars, tradingFractions)
        processOpenOrders(generatedBars, tradingFractions, to)
        updateHoldingPrices()
        processInstrumentLifecycle(to)
        processScheduledDividends(from, to)
        maybeAnnounceCorporateActions(from, to)
        applyDueCorporateActionsAtBoundary(to)
        expireDayOrders(to)
        updateBenchmark(generatedBars, tradingFractions)

        currentTime = to
        turn = GameCalendar.turnAt(to)
        processTaxExchangeRateSettlements(to)
        processDueTaxPayments(gameDate(to))
        updateDrawdown()

        val toGameDate = gameDate(to)
        if (toGameDate != fromGameDate) {
            recalculateAnnualTax(fromGameDate.year)
            if (toGameDate.year != fromGameDate.year && toGameDate.year <= 2040) {
                recalculateAnnualTax(toGameDate.year)
            }
            recordDailySnapshot(fromGameDate, to)
        }
    }

    private fun applyDueCorporateActions(from: Instant, to: Instant) {
        val due = pendingCorporateActions.filter { action ->
            action.effectiveNotBefore <= from &&
                action.stockId !in terminatedInstrumentIds &&
                stockById[action.stockId]?.let { regularTradingFraction(it.market, from, to) > 0.0 } == true
        }
        for (action in due) {
            applyCorporateAction(action, from)
            pendingCorporateActions.removeAll { it.id == action.id }
        }
    }

    /** 다음 정규장 시작 경계에 들어가기 전에 가격·수량을 조정해 오래된 호가 체결 창을 없앤다. */
    private fun applyDueCorporateActionsAtBoundary(at: Instant) {
        val next = at + 1.hours
        val due = pendingCorporateActions.filter { action ->
            action.effectiveNotBefore <= at &&
                action.stockId !in terminatedInstrumentIds &&
                stockById[action.stockId]?.let { regularTradingFraction(it.market, at, next) > 0.0 } == true
        }
        for (action in due) {
            applyCorporateAction(action, at)
            pendingCorporateActions.removeAll { it.id == action.id }
        }
    }

    /** ETN처럼 계약상 만기가 있는 상품은 사전 알림과 실제 상환을 캠페인 원장에 남긴다. */
    private fun processInstrumentLifecycle(at: Instant) {
        for (stock in stocks) {
            if (stock.id in terminatedInstrumentIds) continue
            val maturity = instrumentMaturityDate(stock) ?: continue
            val marketDate = marketDate(stock.market, at)
            val milestone = when (marketDate) {
                maturity.minus(5, DateTimeUnit.YEAR) -> "5년"
                maturity.minus(1, DateTimeUnit.YEAR) -> "1년"
                maturity.minus(90, DateTimeUnit.DAY) -> "90일"
                maturity.minus(30, DateTimeUnit.DAY) -> "30일"
                else -> null
            }
            if (milestone != null) announceMaturityMilestone(stock, maturity, milestone, at)
            if (marketDate >= maturity) {
                redeemMaturedInstrument(stock, maturity, at)
                continue
            }
            val earlyTermination = newsEvents.asSequence()
                .filter { event ->
                    stock.id in event.affectedStockIds && at >= event.endsAt &&
                        (event.id.startsWith(ETN_CALL_EVENT_PREFIX) ||
                            event.id.startsWith(ETN_ACCELERATION_EVENT_PREFIX))
                }
                .filter { event ->
                    !event.id.startsWith(ETN_CALL_EVENT_PREFIX) || stock.identityProfile?.callable == true
                }
                .minByOrNull(GameEvent::startsAt)
            if (earlyTermination != null) {
                redeemMaturedInstrument(stock, maturity, at, earlyTermination)
            }
        }
    }

    private fun announceMaturityMilestone(
        stock: StockDefinition,
        maturity: LocalDate,
        milestone: String,
        at: Instant,
    ) {
        val eventId = "instrument-maturity-notice:${stock.id}:$milestone"
        if (newsEvents.any { it.id == eventId }) return
        newsEvents += GameEvent(
            id = eventId,
            title = "${stock.name} 만기 $milestone 전",
            description = "계약상 만기일 $maturity 전 사전 안내입니다. ETN은 ETF와 달리 발행사의 무담보 채무이며 만기상환·조기상환·발행사 신용 위험이 있습니다.",
            scope = EventScope.STOCK,
            type = EventType.FUND_OPERATION,
            severity = if (milestone == "30일") EventSeverity.MAJOR else EventSeverity.MODERATE,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = at,
            durationHours = if (milestone == "30일") 720 else 168,
            affectedMarkets = setOf(stock.market),
            affectedSectors = setOf(stock.sector),
            affectedStockIds = setOf(stock.id),
            sourceLabel = "공식 상품조건 기반 캠페인 일정",
        )
    }

    private fun redeemMaturedInstrument(
        stock: StockDefinition,
        maturity: LocalDate,
        at: Instant,
        earlyTerminationEvent: GameEvent? = null,
    ) {
        val isAcceleration = earlyTerminationEvent?.id?.startsWith(ETN_ACCELERATION_EVENT_PREFIX) == true
        val isIssuerCall = earlyTerminationEvent?.id?.startsWith(ETN_CALL_EVENT_PREFIX) == true
        val reasonLabel = when {
            isAcceleration -> "발행사 가속상환"
            isIssuerCall -> "발행사 선택적 가속상환"
            else -> "계약상 만기상환"
        }
        val eventId = if (earlyTerminationEvent == null) {
            "instrument-maturity-redemption:${stock.id}:$maturity"
        } else {
            "instrument-early-redemption:${stock.id}:${earlyTerminationEvent.id}"
        }
        if (newsEvents.any { it.id == eventId }) return

        terminatedInstrumentIds += stock.id
        pendingCorporateActions.removeAll { it.stockId == stock.id }
        pendingEtfReferenceReturns.remove(stock.id)

        for (index in orders.indices) {
            val order = orders[index]
            if (order.stockId == stock.id && order.isOpen) {
                orders[index] = order.copy(
                    status = OrderStatus.CANCELLED,
                    updatedAt = at,
                    rejectionReason = "$reasonLabel 처리로 미체결 주문을 취소했습니다.",
                )
            }
        }

        val holding = holdings.remove(stock.id)
        if (holding != null) {
            val marketPrice = quotes.getValue(stock.id).price
            val indicativeValueProxy = history.getValue(stock.id)
                .filter { it.volume > 0L }
                .groupBy { marketDate(stock.market, it.endTime) }
                .toSortedMap()
                .values
                .map { it.last().close }
                .takeLast(5)
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?: marketPrice
            val redemptionFactor = when {
                isAcceleration -> {
                    val recoveryBucket = (PriceEngine.stableHash64(earlyTerminationEvent.id) and Long.MAX_VALUE) % 41L
                    0.40 + recoveryBucket.toDouble() / 100.0
                }
                isIssuerCall -> 1.0
                else -> 1.0
            }
            val redemptionPrice = MarketMicrostructure.roundNearest(
                stock.market,
                (indicativeValueProxy * redemptionFactor)
                    .coerceAtLeast(MarketMicrostructure.minimumPrice(stock.market)),
            )
            val gross = roundCurrency(redemptionPrice * holding.quantity, stock.currency)
            cash[stock.currency] = roundCurrency(cash.getValue(stock.currency) + gross, stock.currency)
            val tradedOn = marketDate(stock.market, at)
            val settledOn = settlementDate(stock.market, tradedOn)
            val exchangeRateToKrw = if (stock.currency == Currency.USD) macro.usdKrw else 1.0
            val orderId = nextId("lifecycle-order")
            val tradeId = nextId("trade")
            val accountingSequence = nextSequence++
            val fifoSale = fifoCostBasisBook.sell(
                stockId = stock.id,
                soldOn = settledOn,
                quantity = holding.quantity,
                grossProceedsKrw = round(gross * exchangeRateToKrw).toLong(),
                directSellingCostsKrw = 0L,
            )
            fifoCostBasisBook = fifoSale.updatedBook
            val (taxTreatment, assessmentNotes) = assessStockGainTreatment(stock, tradedOn, holding)
            orders += Order(
                id = orderId,
                stockId = stock.id,
                side = OrderSide.SELL,
                type = OrderType.MARKET,
                quantity = holding.quantity,
                createdAt = at,
                status = OrderStatus.FILLED,
                filledQuantity = holding.quantity,
                averageFilledPrice = redemptionPrice,
                updatedAt = at,
                timeInForce = TimeInForce.DAY,
                rejectionReason = reasonLabel,
            )
            trades += Trade(
                id = tradeId,
                orderId = orderId,
                stockId = stock.id,
                side = OrderSide.SELL,
                quantity = holding.quantity,
                price = redemptionPrice,
                currency = stock.currency,
                executedAt = at,
                accountingSequence = accountingSequence,
            )
            transactionCosts += TransactionCostRecord(
                tradeId = tradeId,
                stockId = stock.id,
                market = stock.market,
                paidAt = at,
                currency = stock.currency,
                commission = 0.0,
                saleTax = 0.0,
                exchangeRateToKrw = exchangeRateToKrw,
            )
            realizedGains += RealizedGainRecord(
                tradeId = tradeId,
                stockId = stock.id,
                market = stock.market,
                soldAt = at,
                settlementDate = settledOn,
                quantity = holding.quantity,
                proceeds = gross,
                costBasis = holding.averagePrice * holding.quantity,
                commission = 0.0,
                saleTax = 0.0,
                currency = stock.currency,
                exchangeRateToKrw = exchangeRateToKrw,
                taxTreatment = taxTreatment,
                assessmentNotes = assessmentNotes + "$reasonLabel 처분을 양도로 반영했습니다.",
                taxGrossProceedsKrw = fifoSale.grossProceedsKrw,
                taxCostBasisKrw = fifoSale.allocatedCostBasisKrw,
                taxDirectSellingCostsKrw = 0L,
                taxGainKrw = fifoSale.realizedGainKrw,
            )
            taxExchangeRatesByTradeId[tradeId] = exchangeRateToKrw
            if (stock.market.isUnitedStates) pendingTaxSettlementTradeIds += tradeId
            recalculateAnnualTax(settledOn.year)
        }

        newsEvents += GameEvent(
            id = eventId,
            title = "${stock.name} $reasonLabel 완료",
            description = if (holding == null) {
                if (earlyTerminationEvent == null) {
                    "$maturity 계약상 만기가 도래해 거래를 종료했습니다. 보유 잔고는 없었습니다."
                } else {
                    "캠페인 $reasonLabel 조건이 충족돼 거래를 종료했습니다. 보유 잔고는 없었습니다."
                }
            } else {
                if (earlyTerminationEvent == null) {
                    "$maturity 계약상 만기가 도래해 ${holding.quantity}${stock.quantityUnit}를 마지막 게임 지표가치로 자동상환하고 양도손익 원장에 반영했습니다."
                } else {
                    "캠페인 $reasonLabel 시나리오에 따라 ${holding.quantity}${stock.quantityUnit}를 지표가치 대용 상환가격으로 처분하고 양도손익 원장에 반영했습니다. 실제 발행조건의 확정 상환액을 예측하는 값은 아닙니다."
                }
            },
            scope = EventScope.STOCK,
            type = EventType.FUND_OPERATION,
            severity = EventSeverity.MAJOR,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = at,
            durationHours = 720,
            affectedMarkets = setOf(stock.market),
            affectedSectors = setOf(stock.sector),
            affectedStockIds = setOf(stock.id),
            sourceLabel = if (earlyTerminationEvent == null) {
                "공식 상품조건 기반 캠페인 일정"
            } else {
                "공식 위험공시 기반 캠페인 시나리오"
            },
        )
    }

    private fun instrumentMaturityDate(stock: StockDefinition): LocalDate? =
        stock.identityProfile?.maturityDate?.let(LocalDate::parse)

    private fun isInstrumentMatured(stock: StockDefinition, at: Instant): Boolean =
        stock.id in terminatedInstrumentIds ||
            instrumentMaturityDate(stock)?.let { maturity -> marketDate(stock.market, at) >= maturity } == true

    private fun validateCorporateActionState(
        pending: List<PendingCorporateAction>,
        applied: List<CorporateActionRecord>,
        validStockIds: Set<String>,
    ) {
        val pendingIds = pending.map(PendingCorporateAction::id)
        val appliedIds = applied.map(CorporateActionRecord::id)
        require(pendingIds.distinct().size == pendingIds.size) { "예정 기업행동 ID가 중복되었습니다." }
        require(appliedIds.distinct().size == appliedIds.size) { "적용 기업행동 ID가 중복되었습니다." }
        require(pendingIds.none(appliedIds.toSet()::contains)) { "같은 기업행동이 예정·적용 원장에 동시에 있습니다." }
        require(pending.all { action ->
            action.id.isNotBlank() && action.stockId in validStockIds && action.rationale.isNotBlank() &&
                action.effectiveNotBefore > action.announcedAt && action.quantityMultiplier.isFinite() &&
                ((action.kind == CorporateActionKind.FORWARD_SPLIT && action.quantityMultiplier > 1.0) ||
                    (action.kind == CorporateActionKind.REVERSE_SPLIT && action.quantityMultiplier in 0.0..<1.0))
        }) { "예정 기업행동 값 또는 시간 순서가 올바르지 않습니다." }
        require(applied.all { action ->
            action.id.isNotBlank() && action.stockId in validStockIds && action.rationale.isNotBlank() &&
                action.effectiveAt >= action.announcedAt && action.quantityMultiplier.isFinite() &&
                action.preActionPrice > 0.0 && action.postActionPrice > 0.0 &&
                abs(action.preActionPrice / action.quantityMultiplier - action.postActionPrice) <=
                maxOf(0.02, action.postActionPrice * 0.02) &&
                ((action.kind == CorporateActionKind.FORWARD_SPLIT && action.quantityMultiplier > 1.0) ||
                    (action.kind == CorporateActionKind.REVERSE_SPLIT && action.quantityMultiplier in 0.0..<1.0)) &&
                (action.accountingSequence == null || action.accountingSequence > 0L)
        }) { "적용 기업행동 값 또는 시간 순서가 올바르지 않습니다." }
        require(applied.zipWithNext().all { (left, right) -> left.effectiveAt <= right.effectiveAt }) {
            "기업행동 원장의 시간 순서가 올바르지 않습니다."
        }
    }

    private fun rebuildDynamicStockDefinitions(actions: List<CorporateActionRecord>) {
        mutableStocks.clear()
        mutableStocks += baseStockDefinitions
        stockById.clear()
        stockById.putAll(mutableStocks.associateBy(StockDefinition::id))
        actions.forEach { action -> applyDynamicShareMultiplier(action.stockId, action.quantityMultiplier) }
    }

    private fun applyDynamicShareMultiplier(stockId: String, multiplier: Double) {
        val current = stockById.getValue(stockId)
        val adjustedShares = round(current.sharesOutstanding.toDouble() * multiplier)
            .toLong()
            .coerceAtLeast(1L)
        val adjusted = current.copy(sharesOutstanding = adjustedShares)
        val index = mutableStocks.indexOfFirst { it.id == stockId }
        require(index >= 0) { "기업행동 대상 종목이 카탈로그에 없습니다." }
        mutableStocks[index] = adjusted
        stockById[stockId] = adjusted
    }

    private fun sharesOutstandingAt(stockId: String, at: Instant): Long {
        var shares = baseStockById.getValue(stockId).sharesOutstanding
        corporateActionLedger.asSequence()
            .filter { it.stockId == stockId && it.effectiveAt <= at }
            .sortedBy(CorporateActionRecord::effectiveAt)
            .forEach { action ->
                shares = round(shares.toDouble() * action.quantityMultiplier).toLong().coerceAtLeast(1L)
            }
        return shares
    }

    private fun applyCorporateAction(action: PendingCorporateAction, effectiveAt: Instant) {
        val stock = stockById.getValue(action.stockId)
        val multiplier = action.quantityMultiplier
        val actionAccountingSequence = nextSequence++
        val before = quotes.getValue(stock.id)
        fun adjustedPrice(value: Double): Double = MarketMicrostructure.roundNearest(
            stock.market,
            (value / multiplier).coerceAtLeast(MarketMicrostructure.minimumPrice(stock.market)),
        )
        val postPrice = adjustedPrice(before.price)
        quotes[stock.id] = before.copy(
            timestamp = effectiveAt,
            price = postPrice,
            previousClose = adjustedPrice(before.previousClose),
            open = adjustedPrice(before.open),
            high = adjustedPrice(before.high),
            low = adjustedPrice(before.low),
            bidPrice = before.bidPrice?.let(::adjustedPrice),
            askPrice = before.askPrice?.let(::adjustedPrice),
            bidQuantity = before.bidQuantity * multiplier,
            askQuantity = before.askQuantity * multiplier,
        )
        dailyTrackers[stock.id]?.let { tracker ->
            tracker.basePrice = adjustedPrice(tracker.basePrice)
            tracker.open = adjustedPrice(tracker.open)
            tracker.high = adjustedPrice(tracker.high)
            tracker.low = adjustedPrice(tracker.low)
        }
        history[stock.id]?.let { bars ->
            val adjustedBars = bars.map { bar ->
                bar.copy(
                    open = adjustedPrice(bar.open),
                    high = adjustedPrice(bar.high),
                    low = adjustedPrice(bar.low),
                    close = adjustedPrice(bar.close),
                    volume = round(bar.volume.toDouble() * multiplier).toLong().coerceAtLeast(0L),
                )
            }
            bars.clear()
            bars.addAll(adjustedBars)
        }
        holdings[stock.id]?.let { holding ->
            holdings[stock.id] = holding.copy(
                quantity = holding.quantity * multiplier,
                averagePrice = holding.averagePrice / multiplier,
                currentPrice = postPrice,
            )
            fifoCostBasisBook = fifoCostBasisBook.applyQuantityMultiplier(stock.id, multiplier)
        }
        for (index in orders.indices) {
            val order = orders[index]
            if (order.stockId != stock.id || !order.isOpen) continue
            val adjustedQuantity = order.quantity * multiplier
            orders[index] = if (!stock.acceptsQuantity(adjustedQuantity)) {
                order.copy(
                    status = OrderStatus.CANCELLED,
                    updatedAt = effectiveAt,
                    rejectionReason = "분할·병합 후 주문 수량 단위가 맞지 않아 자동 취소했습니다.",
                )
            } else {
                order.copy(
                    quantity = adjustedQuantity,
                    filledQuantity = order.filledQuantity * multiplier,
                    limitPrice = order.limitPrice?.let(::adjustedPrice),
                    averageFilledPrice = order.averageFilledPrice?.let(::adjustedPrice),
                    updatedAt = effectiveAt,
                )
            }
        }
        applyDynamicShareMultiplier(stock.id, multiplier)
        val settledFraction = action.kind == CorporateActionKind.REVERSE_SPLIT &&
            !stock.supportsFractional && settleCorporateActionFraction(stock, postPrice, effectiveAt)
        val record = CorporateActionRecord(
            id = action.id,
            stockId = stock.id,
            kind = action.kind,
            announcedAt = action.announcedAt,
            effectiveAt = effectiveAt,
            quantityMultiplier = multiplier,
            preActionPrice = before.price,
            postActionPrice = postPrice,
            source = action.source,
            rationale = action.rationale,
            accountingSequence = actionAccountingSequence,
        )
        corporateActionLedger += record
        val ratioLabel = corporateActionRatioLabel(action)
        newsEvents += GameEvent(
            id = "${action.id}:effective",
            title = "${stock.name} ${action.kind.displayName} 효력 발생",
            description = if (settledFraction) {
                "${ratioLabel}이 반영됐습니다. 정수 거래단위 미만 단주는 조정가격으로 현금정산하고 FIFO 원가와 양도손익 원장에 기록했습니다."
            } else {
                "${ratioLabel}이 반영됐습니다. 보유 수량과 주당원가를 서로 반대 비율로 조정해 총 평가액과 FIFO 총원가는 보존했습니다."
            },
            scope = EventScope.STOCK,
            type = EventType.CORPORATE_ACTION,
            severity = EventSeverity.MINOR,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = effectiveAt,
            durationHours = 24,
            affectedMarkets = setOf(stock.market),
            affectedSectors = setOf(stock.sector),
            affectedStockIds = setOf(stock.id),
            sourceLabel = action.source.displayName,
        )
        lastMessage = "${stock.name} ${action.kind.displayName}($ratioLabel)을 반영했습니다."
    }

    /** 정수 수량 시장의 병합 단주는 자동 현금정산하고 FIFO 원가·양도손익을 함께 기록한다. */
    private fun settleCorporateActionFraction(
        stock: StockDefinition,
        postActionPrice: Double,
        effectiveAt: Instant,
    ): Boolean {
        val holding = holdings[stock.id] ?: return false
        val tradableQuantity = floor((holding.quantity + QUANTITY_EPSILON) / stock.quantityStep) *
            stock.quantityStep
        val remainder = (holding.quantity - tradableQuantity).coerceAtLeast(0.0)
        if (remainder < QUANTITY_EPSILON) return false

        val gross = maxOf(
            if (stock.currency == Currency.KRW) 1.0 else 0.01,
            roundCurrency(postActionPrice * remainder, stock.currency),
        )
        val tradedOn = marketDate(stock.market, effectiveAt)
        val settledOn = settlementDate(stock.market, tradedOn)
        val taxBreakdown = when {
            !stock.market.isKorean -> null
            stock.etfProfile != null -> {
                val acquisitionValueKrw = round(holding.averagePrice * remainder).toLong()
                val positiveTradingGain = (gross.toLong() - acquisitionValueKrw).coerceAtLeast(0L)
                domesticEtfSaleTaxCalculator.calculate(
                    DomesticEtfSaleTaxRequest(
                        taxCategory = stock.etfProfile.taxCategory,
                        grossProceedsKrw = gross.toLong(),
                        acquisitionValueKrw = acquisitionValueKrw,
                        taxableStandardGainKrw = round(
                            positiveTradingGain * stock.etfProfile.taxablePriceGainRatio,
                        ).toLong(),
                        soldOn = tradedOn,
                    ),
                )
            }

            else -> domesticSaleTaxCalculator.calculate(
                DomesticSaleTaxRequest(
                    market = stock.market,
                    grossProceedsKrw = gross.toLong(),
                    soldOn = tradedOn,
                ),
            )
        }
        val saleTax = taxBreakdown?.totalTax?.amount ?: 0.0
        val exchangeRateToKrw = if (stock.currency == Currency.USD) macro.usdKrw else 1.0
        val fifoSale = fifoCostBasisBook.sell(
            stockId = stock.id,
            soldOn = settledOn,
            quantity = remainder,
            grossProceedsKrw = round(gross * exchangeRateToKrw).toLong(),
            directSellingCostsKrw = round(saleTax * exchangeRateToKrw).toLong(),
        )
        fifoCostBasisBook = fifoSale.updatedBook
        val (taxTreatment, assessmentNotes) = assessStockGainTreatment(stock, tradedOn, holding)
        val realized = gross - holding.averagePrice * remainder - saleTax
        if (tradableQuantity < stock.quantityStep / 2.0) {
            holdings.remove(stock.id)
        } else {
            holdings[stock.id] = holding.copy(
                quantity = tradableQuantity,
                currentPrice = postActionPrice,
                realizedProfit = holding.realizedProfit + realized,
            )
        }
        cash[stock.currency] = roundCurrency(
            cash.getValue(stock.currency) + gross - saleTax,
            stock.currency,
        )

        val orderId = nextId("cash-in-lieu-order")
        val tradeId = nextId("trade")
        val accountingSequence = nextSequence++
        orders += Order(
            id = orderId,
            stockId = stock.id,
            side = OrderSide.SELL,
            type = OrderType.MARKET,
            quantity = remainder,
            createdAt = effectiveAt,
            status = OrderStatus.FILLED,
            filledQuantity = remainder,
            averageFilledPrice = postActionPrice,
            updatedAt = effectiveAt,
            timeInForce = TimeInForce.DAY,
            rejectionReason = "주식병합 단주 현금정산",
        )
        trades += Trade(
            id = tradeId,
            orderId = orderId,
            stockId = stock.id,
            side = OrderSide.SELL,
            quantity = remainder,
            price = postActionPrice,
            currency = stock.currency,
            executedAt = effectiveAt,
            tax = saleTax,
            accountingSequence = accountingSequence,
        )
        transactionCosts += TransactionCostRecord(
            tradeId = tradeId,
            stockId = stock.id,
            market = stock.market,
            paidAt = effectiveAt,
            currency = stock.currency,
            commission = 0.0,
            saleTax = saleTax,
            exchangeRateToKrw = exchangeRateToKrw,
            taxBreakdown = taxBreakdown,
        )
        realizedGains += RealizedGainRecord(
            tradeId = tradeId,
            stockId = stock.id,
            market = stock.market,
            soldAt = effectiveAt,
            settlementDate = settledOn,
            quantity = remainder,
            proceeds = gross,
            costBasis = holding.averagePrice * remainder,
            commission = 0.0,
            saleTax = saleTax,
            currency = stock.currency,
            exchangeRateToKrw = exchangeRateToKrw,
            taxTreatment = taxTreatment,
            assessmentNotes = assessmentNotes + "주식병합 단주를 현금정산 처분으로 반영했습니다.",
            taxGrossProceedsKrw = fifoSale.grossProceedsKrw,
            taxCostBasisKrw = fifoSale.allocatedCostBasisKrw,
            taxDirectSellingCostsKrw = fifoSale.directSellingCostsKrw,
            taxGainKrw = fifoSale.realizedGainKrw,
            taxableFinancialIncomeKrw = if (
                stock.etfProfile?.taxCategory == EtfTaxCategory.KOREAN_OTHER
            ) {
                taxBreakdown?.taxableBase?.minorUnits ?: 0L
            } else {
                0L
            },
        )
        taxExchangeRatesByTradeId[tradeId] = exchangeRateToKrw
        if (stock.market.isUnitedStates) pendingTaxSettlementTradeIds += tradeId
        recalculateAnnualTax(settledOn.year)
        return true
    }

    private fun maybeAnnounceCorporateActions(from: Instant, to: Instant) {
        for (stock in stocks) {
            if (isInstrumentMatured(stock, to)) continue
            val crossedVenueClose = regularTradingFraction(stock.market, from, to) > 0.0 &&
                regularTradingFraction(stock.market, to, to + 1.hours) == 0.0
            if (!crossedVenueClose) continue
            val campaignDate = marketDate(stock.market, to)
            if (pendingCorporateActions.any { it.stockId == stock.id }) continue
            val lastApplied = corporateActionLedger.lastOrNull { it.stockId == stock.id }
            if (lastApplied != null && to - lastApplied.effectiveAt < CORPORATE_ACTION_COOLDOWN_HOURS.hours) continue
            val closes = history.getValue(stock.id)
                .filter { it.volume > 0L }
                .groupBy { marketDate(stock.market, it.endTime) }
                .toSortedMap()
                .values
                .map { it.last().close }
            if (closes.isEmpty()) continue
            val price = closes.last()
            val lowTrigger = if (stock.currency == Currency.KRW) 1_500.0 else 5.0
            val highTrigger = if (stock.currency == Currency.KRW) 1_000_000.0 else 750.0
            val requiredLowDays = if (
                stock.behavior.strategy in setOf(
                    com.amond.kmpbook.domain.model.InstrumentStrategy.DAILY_LEVERAGED,
                    com.amond.kmpbook.domain.model.InstrumentStrategy.DAILY_INVERSE,
                    com.amond.kmpbook.domain.model.InstrumentStrategy.COVERED_CALL,
                )
            ) 7 else 15
            val reverse = closes.takeLast(requiredLowDays).size == requiredLowDays &&
                closes.takeLast(requiredLowDays).all { it < lowTrigger }
            val forward = closes.takeLast(FORWARD_SPLIT_STREAK_DAYS).size == FORWARD_SPLIT_STREAK_DAYS &&
                closes.takeLast(FORWARD_SPLIT_STREAK_DAYS).all { it > highTrigger }
            if (!reverse && !forward) continue
            // 임계값만으로 즉시 발동하지 않고, 종목·날짜별 결정론적 이사회 게이트를 거친다.
            val gate = (PriceEngine.stableHash64("${stock.id}:$campaignDate:corporate-action") and Long.MAX_VALUE) %
                CORPORATE_ACTION_BOARD_GATE
            if (gate != 0L) continue

            val kind = if (reverse) CorporateActionKind.REVERSE_SPLIT else CorporateActionKind.FORWARD_SPLIT
            val target = when {
                reverse && stock.currency == Currency.KRW -> 10_000.0
                reverse -> 25.0
                stock.currency == Currency.KRW -> 100_000.0
                else -> 150.0
            }
            val multiplier = if (reverse) {
                CorporateActionMath.reverseMultiplier(price, target)
            } else {
                CorporateActionMath.forwardMultiplier(price, target)
            }
            val rationale = if (reverse) {
                "${requiredLowDays}거래일 이상 저가 구간이 지속됐고 ${stock.behavior.principalRisk.displayName} 구조를 반영한 가상 이사회가 유통가격 조정을 승인했습니다."
            } else {
                "${FORWARD_SPLIT_STREAK_DAYS}거래일 이상 고가 구간이 지속돼 거래 접근성을 높이기 위한 가상 이사회 분할을 승인했습니다."
            }
            val action = PendingCorporateAction(
                id = nextId("corporate-action"),
                stockId = stock.id,
                kind = kind,
                announcedAt = to,
                effectiveNotBefore = to + CORPORATE_ACTION_NOTICE_HOURS.hours,
                quantityMultiplier = multiplier,
                rationale = rationale,
            )
            pendingCorporateActions += action
            val ratioLabel = corporateActionRatioLabel(action)
            newsEvents += GameEvent(
                id = "${action.id}:announcement",
                title = "${stock.name} ${kind.displayName} 결의",
                description = "$ratioLabel 조정이 ${CORPORATE_ACTION_NOTICE_HOURS / 24}일 후 첫 정규장에서 반영됩니다. $rationale 이 공시는 실제 기업 공시가 아닌 캠페인 규칙상 가상 사건입니다.",
                scope = EventScope.STOCK,
                type = EventType.CORPORATE_ACTION,
                severity = EventSeverity.MODERATE,
                impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
                startsAt = to,
                durationHours = CORPORATE_ACTION_NOTICE_HOURS,
                affectedMarkets = setOf(stock.market),
                affectedSectors = setOf(stock.sector),
                affectedStockIds = setOf(stock.id),
                sourceLabel = CorporateActionSource.CAMPAIGN_RULE.displayName,
            )
        }
    }

    private fun corporateActionRatioLabel(action: PendingCorporateAction): String =
        if (action.quantityMultiplier > 1.0) {
            "1:${action.quantityMultiplier.toInt()} 분할"
        } else {
            "${kotlin.math.round(1.0 / action.quantityMultiplier).toInt()}:1 병합"
        }

    private fun updateMacro(time: Instant) {
        val date = gameDate(time)
        val resetMarketChange = date != macroDate
        macroDate = date
        val previousUsdKrw = macro.usdKrw
        val previousFxRates = macro.fxRatesToKrw ?: initialFxRates(previousUsdKrw)
        val meanReversion = ln(options.initialUsdKrw / previousUsdKrw) * FX_MEAN_REVERSION
        val usdKrw = (previousUsdKrw * exp(meanReversion + random.nextGaussian() * FX_HOURLY_VOLATILITY))
            .coerceIn(MIN_USD_KRW, MAX_USD_KRW)
        val usdLogReturn = ln(usdKrw / previousUsdKrw)
        val fxRates = ReferenceCurrency.entries.associateWith { currency ->
            when (currency) {
                ReferenceCurrency.KRW -> 1.0
                ReferenceCurrency.USD -> usdKrw
                else -> {
                    val previous = previousFxRates[currency] ?: initialFxRates(previousUsdKrw).getValue(currency)
                    val target = initialFxRates(options.initialUsdKrw).getValue(currency)
                    val crossMeanReversion = ln(target / previous) * FX_CROSS_MEAN_REVERSION
                    (previous * exp(
                        crossMeanReversion + usdLogReturn * FX_GLOBAL_KRW_LOADING +
                            random.nextGaussian() * FX_CROSS_HOURLY_VOLATILITY,
                    )).coerceIn(target * 0.35, target * 2.75)
                }
            }
        }
        val policyChange = if (random.nextDouble() < POLICY_CHANGE_PROBABILITY_PER_HOUR) {
            if (random.nextBoolean()) 0.0025 else -0.0025
        } else {
            0.0
        }
        val policyRate = (macro.policyRate + policyChange).coerceIn(0.0, 0.12)
        val inflation = (macro.inflationRate + (0.02 - macro.inflationRate) * 0.002 +
            random.nextGaussian() * 0.00008).coerceIn(-0.02, 0.15)
        val growth = (macro.growthRate + (0.02 - macro.growthRate) * 0.0015 +
            random.nextGaussian() * 0.0001).coerceIn(-0.10, 0.15)
        val riskSentiment = (macro.riskSentiment * 0.97 + random.nextGaussian() * 0.035)
            .coerceIn(-1.0, 1.0)
        val volatilityRegime = (1.0 + abs(riskSentiment) * 0.8 + random.nextGaussian() * 0.04)
            .coerceIn(0.4, 3.0)
        val krCommonReturn = random.nextGaussian() * MARKET_FACTOR_VOLATILITY * volatilityRegime
        val usCommonReturn = random.nextGaussian() * MARKET_FACTOR_VOLATILITY * volatilityRegime
        val marketReturns = Market.entries.associateWith { market ->
            val fraction = regularTradingFraction(market, time, time + 1.hours)
            if (fraction == 0.0) {
                0.0
            } else {
                val common = if (market.isKorean) krCommonReturn else usCommonReturn
                // Store an unscaled hourly factor. PriceEngine applies the regular/reference
                // fraction once when it builds the instrument return.
                common + random.nextGaussian() * VENUE_FACTOR_VOLATILITY * volatilityRegime
            }
        }
        val sectorReturns = Sector.entries.associateWith {
            random.nextGaussian() * SECTOR_FACTOR_VOLATILITY * volatilityRegime
        }
        val regionalReturns = linkedMapOf<EtfExposureRegion, Double>()
        regionalReturns[EtfExposureRegion.KOREA] = marketReturns
            .filterKeys(Market::isKorean).values.filter { it != 0.0 }.averageOrZero()
        regionalReturns[EtfExposureRegion.UNITED_STATES] = marketReturns
            .filterKeys(Market::isUnitedStates).values.filter { it != 0.0 }.averageOrZero()
        regionalReturns[EtfExposureRegion.DEVELOPED_EX_US] = if (
            regionalTradingFraction(EtfExposureRegion.DEVELOPED_EX_US, time) > 0.0
        ) random.nextGaussian() * MARKET_FACTOR_VOLATILITY * volatilityRegime else 0.0
        regionalReturns[EtfExposureRegion.EMERGING_MARKETS] = if (
            regionalTradingFraction(EtfExposureRegion.EMERGING_MARKETS, time) > 0.0
        ) random.nextGaussian() * MARKET_FACTOR_VOLATILITY * 1.12 * volatilityRegime else 0.0
        regionalReturns[EtfExposureRegion.GLOBAL] = regionalReturns.values
            .filter { it != 0.0 }.averageOrZero()
        macro = MacroEnvironment(
            policyRate = policyRate,
            policyRateChange = policyChange,
            inflationRate = inflation,
            inflationSurprise = (inflation - 0.02) / 0.01,
            growthRate = growth,
            growthSurprise = (growth - 0.02) / 0.02,
            usdKrw = usdKrw,
            previousUsdKrw = previousUsdKrw,
            fxRatesToKrw = fxRates,
            previousFxRatesToKrw = previousFxRates,
            riskSentiment = riskSentiment,
            volatilityRegime = volatilityRegime,
            marketHourlyReturns = marketReturns,
            sectorHourlyReturns = sectorReturns,
            regionalEtfHourlyReturns = regionalReturns,
            marketChangeFromPreviousClose = if (resetMarketChange) emptyMap() else macro.marketChangeFromPreviousClose,
        )
    }

    private fun generateEvents(from: Instant, to: Instant) {
        val eligibleStocks = stocks.filterNot { isInstrumentMatured(it, from) }
        val result = eventEngine.generate(
            EventGenerationContext(
                timestamp = from,
                stocks = eligibleStocks,
                macro = macro,
                elapsedHours = 1,
                existingEvents = activeEvents,
                maxNewEvents = 2,
            ),
        )
        activeEvents.clear()
        activeEvents += result.activeEvents
        if (result.newEvents.isNotEmpty()) {
            newsEvents += result.newEvents
            trimStochasticNews()
        }

        // Scheduled emissions deliberately bypass EventGenerationContext.maxNewEvents and the
        // stochastic-news cap. Their ids and [from, to) membership come from the pure calendar.
        val scheduled = scheduledEventEngine.generate(from, to, eligibleStocks)
        if (scheduled.emissions.isNotEmpty()) {
            val existingIds = newsEvents.mapTo(mutableSetOf(), GameEvent::id)
            newsEvents += scheduled.newEvents.filter { existingIds.add(it.id) }
            applyScheduledMacro(scheduled.emissions)
        }

        val allNewEvents = result.newEvents + scheduled.newEvents
        if (allNewEvents.isNotEmpty()) {
            val sentiment = allNewEvents.map { it.impact.sentiment }.average()
            macro = macro.copy(riskSentiment = (macro.riskSentiment + sentiment * 0.15).coerceIn(-1.0, 1.0))
        }
    }

    private fun trimStochasticNews() {
        var stochasticCount = newsEvents.count { !isProtectedLedgerNews(it) }
        if (stochasticCount <= MAX_NEWS_EVENTS) return
        val iterator = newsEvents.listIterator()
        while (iterator.hasNext() && stochasticCount > MAX_NEWS_EVENTS) {
            if (!isProtectedLedgerNews(iterator.next())) {
                iterator.remove()
                stochasticCount -= 1
            }
        }
    }

    private fun isProtectedLedgerNews(event: GameEvent): Boolean =
        ScheduledEventOccurrence.isScheduledId(event.id) ||
            event.id.startsWith("instrument-maturity-") ||
            event.id.startsWith("instrument-early-redemption:") ||
            event.id.startsWith(ETN_CALL_EVENT_PREFIX) ||
            event.id.startsWith(ETN_ACCELERATION_EVENT_PREFIX) ||
            event.id.contains("corporate-action")

    private fun applyScheduledMacro(emissions: List<ScheduledEventEmission>) {
        for (emission in emissions) {
            val outcome = emission.outcome
            val actual = outcome.metrics.first().actual
            macro = when (emission.occurrence.kind) {
                ScheduledEventKind.US_CPI,
                ScheduledEventKind.US_PCE,
                ScheduledEventKind.KR_CPI,
                -> macro.copy(
                    inflationRate = actual / 100.0,
                    inflationSurprise = outcome.surpriseScore,
                )

                ScheduledEventKind.US_GDP,
                ScheduledEventKind.KR_GDP,
                -> macro.copy(
                    growthRate = actual / 100.0,
                    growthSurprise = outcome.surpriseScore,
                )

                ScheduledEventKind.US_FOMC,
                ScheduledEventKind.KR_BOK,
                -> {
                    val nextRate = actual / 100.0
                    macro.copy(
                        policyRate = nextRate,
                        policyRateChange = nextRate - macro.policyRate,
                    )
                }

                ScheduledEventKind.US_WEEKLY_CLAIMS -> macro.copy(
                    growthSurprise = -outcome.surpriseScore,
                )

                ScheduledEventKind.US_EMPLOYMENT,
                ScheduledEventKind.KR_EMPLOYMENT,
                ScheduledEventKind.US_RETAIL_SALES,
                -> macro.copy(growthSurprise = outcome.surpriseScore)

                ScheduledEventKind.EARNINGS -> macro
            }
        }
    }

    private fun trackerFor(stock: StockDefinition, time: Instant, previousPrice: Double): DailyPriceTracker {
        val localDate = marketDate(stock.market, time)
        val tracker = dailyTrackers.getValue(stock.id)
        if (tracker.date != localDate) {
            tracker.date = localDate
            tracker.basePrice = previousPrice
            tracker.open = previousPrice
            tracker.high = previousPrice
            tracker.low = previousPrice
            tracker.hasRegularTrading = false
        }
        return tracker
    }

    private fun appendHistory(stockId: String, bar: PriceBar) {
        val bars = history.getValue(stockId)
        bars.addLast(bar)
        while (bars.size > MAX_RECENT_BARS) bars.removeFirst()
    }

    private fun processOpenOrders(
        bars: Map<String, PriceBar>,
        fractions: Map<Market, Double>,
        executedAt: Instant,
    ) {
        for (index in orders.indices) {
            val order = orders[index]
            if (!order.isOpen || order.status == OrderStatus.PENDING) continue
            val stock = stockById.getValue(order.stockId)
            if (fractions.getValue(stock.market) <= 0.0) continue
            val bar = bars.getValue(stock.id)
            val fillPrice = when (order.type) {
                OrderType.MARKET -> bar.open
                OrderType.LIMIT -> limitFillPrice(order, bar)
            } ?: continue
            executeOrder(index, fillPrice, executedAt)
        }
    }

    private fun limitFillPrice(order: Order, bar: PriceBar): Double? {
        val limit = requireNotNull(order.limitPrice)
        return when (order.side) {
            OrderSide.BUY -> if (bar.low <= limit) minOf(bar.open, limit) else null
            OrderSide.SELL -> if (bar.high >= limit) maxOf(bar.open, limit) else null
        }
    }

    private fun tryImmediateExecution(index: Int, stock: StockDefinition) {
        val session = marketSessionAtCurrentTime(stock.market)
        if (session != MarketSession.REGULAR) return
        // At an hourly opening boundary the ETF quote still represents yesterday's
        // close until the opening bar consumes its fair-value carry. Queue the order so
        // processOpenOrders fills it at that bar's gap open instead of the stale quote.
        if (stock.isFundLike && stock.id in pendingEtfReferenceReturns) return
        val quote = quotes.getValue(stock.id)
        val book = orderBook(stock, quote, session)
        val order = orders[index]
        val price = when (order.side) {
            OrderSide.BUY -> book.bestAsk?.price ?: quote.price
            OrderSide.SELL -> book.bestBid?.price ?: quote.price
        }
        val executable = when (order.type) {
            OrderType.MARKET -> true
            OrderType.LIMIT -> when (order.side) {
                OrderSide.BUY -> price <= requireNotNull(order.limitPrice)
                OrderSide.SELL -> price >= requireNotNull(order.limitPrice)
            }
        }
        if (executable) executeOrder(index, price, currentTime)
    }

    private fun executeOrder(index: Int, price: Double, executedAt: Instant) {
        val order = orders[index]
        if (!order.isOpen) return
        val stock = stockById.getValue(order.stockId)
        val quantity = order.remainingQuantity
        val gross = roundCurrency(price * quantity, stock.currency)
        val tradedOn = marketDate(stock.market, executedAt)
        val feeBreakdown = brokerFeeCalculator.calculate(
            BrokerFeeRequest(
                market = stock.market,
                side = order.side,
                grossAmount = money(gross, stock.currency),
                quantity = quantity,
                tradedOn = tradedOn,
            ),
        )
        val commission = feeBreakdown.totalFees.amount
        val preSaleHolding = if (order.side == OrderSide.SELL) holdings[stock.id] else null
        val taxBreakdown = when {
            order.side != OrderSide.SELL || !stock.market.isKorean -> null
            stock.etfProfile != null -> {
                val acquisitionValueKrw = round((preSaleHolding?.averagePrice ?: price) * quantity).toLong()
                val positiveTradingGain = (gross.toLong() - acquisitionValueKrw).coerceAtLeast(0L)
                domesticEtfSaleTaxCalculator.calculate(
                    DomesticEtfSaleTaxRequest(
                        taxCategory = stock.etfProfile.taxCategory,
                        grossProceedsKrw = gross.toLong(),
                        acquisitionValueKrw = acquisitionValueKrw,
                        taxableStandardGainKrw = round(
                            positiveTradingGain * stock.etfProfile.taxablePriceGainRatio,
                        ).toLong(),
                        soldOn = tradedOn,
                    ),
                )
            }
            else -> domesticSaleTaxCalculator.calculate(
                DomesticSaleTaxRequest(
                    market = stock.market,
                    grossProceedsKrw = gross.toLong(),
                    soldOn = tradedOn,
                ),
            )
        }
        val saleTax = taxBreakdown?.totalTax?.amount ?: 0.0
        val exchangeRateToKrw = if (stock.currency == Currency.USD) macro.usdKrw else 1.0
        val settledOn = settlementDate(stock.market, tradedOn)
        val tradeId = nextId("trade")

        if (order.side == OrderSide.BUY) {
            val required = gross + commission
            if (!ensureCash(stock.currency, required)) {
                orders[index] = order.copy(
                    status = OrderStatus.REJECTED,
                    updatedAt = executedAt,
                    rejectionReason = "주문 체결에 필요한 잔고가 부족합니다.",
                )
                lastMessage = "잔고 부족으로 주문이 거부되었습니다."
                return
            }
            cash[stock.currency] = roundCurrency(cash.getValue(stock.currency) - required, stock.currency)
            val previous = holdings[stock.id]
            val newQuantity = (previous?.quantity ?: 0.0) + quantity
            val totalCost = (previous?.costBasis ?: 0.0) + gross + commission
            holdings[stock.id] = Holding(
                stockId = stock.id,
                quantity = newQuantity,
                averagePrice = totalCost / newQuantity,
                currentPrice = price,
                currency = stock.currency,
                realizedProfit = previous?.realizedProfit ?: 0.0,
            )
            fifoCostBasisBook = fifoCostBasisBook.addPurchase(
                lotId = tradeId,
                stockId = stock.id,
                acquiredOn = settledOn,
                quantity = quantity,
                purchasePriceKrw = round(gross * exchangeRateToKrw).toLong(),
                directPurchaseCostsKrw = round(commission * exchangeRateToKrw).toLong(),
            )
        } else {
            val previous = holdings[stock.id]
            if (previous == null || previous.quantity + QUANTITY_EPSILON < quantity) {
                orders[index] = order.copy(
                    status = OrderStatus.REJECTED,
                    updatedAt = executedAt,
                    rejectionReason = "매도 가능한 수량이 부족합니다.",
                )
                lastMessage = "보유 수량 부족으로 주문이 거부되었습니다."
                return
            }
            val proceeds = gross - commission - saleTax
            cash[stock.currency] = roundCurrency(cash.getValue(stock.currency) + proceeds, stock.currency)
            val costBasis = previous.averagePrice * quantity
            val realized = gross - costBasis - commission - saleTax
            val (taxTreatment, assessmentNotes) = assessStockGainTreatment(stock, tradedOn, previous)
            val fifoSale = fifoCostBasisBook.sell(
                stockId = stock.id,
                soldOn = settledOn,
                quantity = quantity,
                grossProceedsKrw = round(gross * exchangeRateToKrw).toLong(),
                directSellingCostsKrw = round((commission + saleTax) * exchangeRateToKrw).toLong(),
            )
            fifoCostBasisBook = fifoSale.updatedBook
            val remaining = (previous.quantity - quantity).coerceAtLeast(0.0)
            if (remaining < stock.quantityStep / 2.0) {
                holdings.remove(stock.id)
            } else {
                holdings[stock.id] = previous.copy(
                    quantity = remaining,
                    currentPrice = price,
                    realizedProfit = previous.realizedProfit + realized,
                )
            }
            realizedGains += RealizedGainRecord(
                tradeId = tradeId,
                stockId = stock.id,
                market = stock.market,
                soldAt = executedAt,
                settlementDate = settledOn,
                quantity = quantity,
                proceeds = gross,
                costBasis = costBasis,
                commission = commission,
                saleTax = saleTax,
                currency = stock.currency,
                exchangeRateToKrw = exchangeRateToKrw,
                taxTreatment = taxTreatment,
                assessmentNotes = assessmentNotes,
                taxGrossProceedsKrw = fifoSale.grossProceedsKrw,
                taxCostBasisKrw = fifoSale.allocatedCostBasisKrw,
                taxDirectSellingCostsKrw = fifoSale.directSellingCostsKrw,
                taxGainKrw = fifoSale.realizedGainKrw,
                taxableFinancialIncomeKrw = if (
                    stock.etfProfile?.taxCategory == EtfTaxCategory.KOREAN_OTHER
                ) {
                    taxBreakdown?.taxableBase?.minorUnits ?: 0L
                } else {
                    0L
                },
            )
        }

        val accountingSequence = nextSequence++
        val trade = Trade(
            id = tradeId,
            orderId = order.id,
            stockId = stock.id,
            side = order.side,
            quantity = quantity,
            price = price,
            currency = stock.currency,
            executedAt = executedAt,
            commission = commission,
            tax = saleTax,
            accountingSequence = accountingSequence,
        )
        trades += trade
        transactionCosts += TransactionCostRecord(
            tradeId = tradeId,
            stockId = stock.id,
            market = stock.market,
            paidAt = executedAt,
            currency = stock.currency,
            commission = commission,
            saleTax = saleTax,
            exchangeRateToKrw = exchangeRateToKrw,
            feeBreakdown = feeBreakdown,
            taxBreakdown = taxBreakdown,
        )
        taxExchangeRatesByTradeId[tradeId] = exchangeRateToKrw
        if (stock.market.isUnitedStates) pendingTaxSettlementTradeIds += tradeId
        orders[index] = order.copy(
            status = OrderStatus.FILLED,
            filledQuantity = order.quantity,
            averageFilledPrice = price,
            updatedAt = executedAt,
        )
        if (order.side == OrderSide.SELL) recalculateAnnualTax(settledOn.year)
    }

    private fun validateOrderResources(stock: StockDefinition, request: OrderRequest): Boolean {
        if (request.side == OrderSide.SELL) {
            val owned = holdings[stock.id]?.quantity ?: 0.0
            val reserved = orders.filter {
                it.stockId == stock.id && it.side == OrderSide.SELL && it.isOpen
            }.sumOf(Order::remainingQuantity)
            if (owned - reserved + QUANTITY_EPSILON < request.quantity) {
                return fail("주문 가능한 보유 수량이 부족합니다.")
            }
            return true
        }

        val referencePrice = request.limitPrice ?: quotes.getValue(stock.id).askPrice ?: quotes.getValue(stock.id).price
        val estimated = referencePrice * request.quantity * BUY_RESERVE_MULTIPLIER
        val existingReservation = orders.filter {
            it.side == OrderSide.BUY && it.stockId.let(stockById::get)?.currency == stock.currency && it.isOpen
        }.sumOf { open ->
            val openStock = stockById.getValue(open.stockId)
            val openPrice = open.limitPrice ?: quotes.getValue(open.stockId).price
            openPrice * open.remainingQuantity * BUY_RESERVE_MULTIPLIER
        }
        val nativeAvailable = cash.getValue(stock.currency) - existingReservation
        if (nativeAvailable + CASH_EPSILON >= estimated) return true
        if (stock.currency == Currency.USD && options.autoExchange) {
            val usdFromKrw = cash.getValue(Currency.KRW) / macro.usdKrw * (1.0 - FX_SPREAD_RATE)
            if (nativeAvailable + usdFromKrw + CASH_EPSILON >= estimated) return true
        }
        return fail("주문 가능 현금이 부족합니다.")
    }

    private fun ensureCash(currency: Currency, required: Double): Boolean {
        if (cash.getValue(currency) + CASH_EPSILON >= required) return true
        if (currency != Currency.USD || !options.autoExchange) return false
        val shortage = required - cash.getValue(Currency.USD)
        val krwRequired = shortage * macro.usdKrw / (1.0 - FX_SPREAD_RATE) + 1.0
        if (!exchangeInternal(Currency.KRW, Currency.USD, krwRequired, automatic = true)) return false
        return cash.getValue(Currency.USD) + CASH_EPSILON >= required
    }

    private fun exchangeInternal(
        from: Currency,
        to: Currency,
        sourceAmount: Double,
        automatic: Boolean,
    ): Boolean {
        val roundedSource = roundCurrency(sourceAmount, from)
        if (roundedSource <= 0.0 || cash.getValue(from) + CASH_EPSILON < roundedSource) {
            return fail("환전할 잔고가 부족합니다.")
        }
        val received = when {
            from == Currency.KRW && to == Currency.USD ->
                roundCurrency(roundedSource / macro.usdKrw * (1.0 - FX_SPREAD_RATE), Currency.USD)
            from == Currency.USD && to == Currency.KRW ->
                roundCurrency(roundedSource * macro.usdKrw * (1.0 - FX_SPREAD_RATE), Currency.KRW)
            else -> return fail("지원하지 않는 환전 통화입니다.")
        }
        if (received <= 0.0) return fail("환전 후 수령 금액이 최소 통화 단위보다 작습니다.")
        cash[from] = roundCurrency(cash.getValue(from) - roundedSource, from)
        cash[to] = roundCurrency(cash.getValue(to) + received, to)
        val spreadCostKrw = when (from) {
            Currency.KRW -> roundedSource * FX_SPREAD_RATE
            Currency.USD -> roundedSource * macro.usdKrw * FX_SPREAD_RATE
        }
        foreignExchanges += ForeignExchangeRecord(
            id = nextId("fx"),
            executedAt = currentTime,
            fromCurrency = from,
            toCurrency = to,
            sourceAmount = roundedSource,
            receivedAmount = received,
            usdKrwRate = macro.usdKrw,
            spreadCostKrw = spreadCostKrw,
            automatic = automatic,
        )
        return true
    }

    private fun processScheduledDividends(from: Instant, to: Instant) {
        for (stock in stocks) {
            if (isInstrumentMatured(stock, to)) continue
            if (stock.dividendYield <= 0.0) continue
            val fromDate = marketDate(stock.market, from)
            val payDate = marketDate(stock.market, to)
            val frequency = stock.behavior.distributionFrequency
            if (payDate == fromDate || !isDistributionDate(payDate, frequency)) continue

            // The game combines ex-date and payment date. Removing the gross per-unit
            // distribution from the quote prevents a buy-before-payment free-cash exploit.
            val quoteBeforeDistribution = quotes.getValue(stock.id)
            val periodsPerYear = frequency.periodsPerYear
            if (periodsPerYear <= 0) continue
            val grossPerUnit = quoteBeforeDistribution.price * stock.dividendYield / periodsPerYear
            val adjustedPrice = MarketMicrostructure.roundNearest(
                stock.market,
                (quoteBeforeDistribution.price - grossPerUnit)
                    .coerceAtLeast(MarketMicrostructure.tickSize(stock.market, quoteBeforeDistribution.price)),
            )
            quotes[stock.id] = quoteBeforeDistribution.copy(
                price = adjustedPrice,
                low = minOf(quoteBeforeDistribution.low, adjustedPrice),
                bidPrice = null,
                askPrice = null,
                bidQuantity = 0.0,
                askQuantity = 0.0,
            )
            holdings[stock.id]?.let { holding ->
                holdings[stock.id] = holding.copy(currentPrice = adjustedPrice)
            }

            val holding = holdings[stock.id] ?: continue
            val ledgerId = "dividend:${stock.id}:$payDate"
            if (dividends.any { it.id == ledgerId }) continue

            val gross = holding.quantity * grossPerUnit
            val rocEligible = stock.market.isUnitedStates && stock.instrumentType in setOf(
                InstrumentType.ETF,
                InstrumentType.CLOSED_END_FUND,
            )
            val taxableCoverage = if (rocEligible) {
                stock.behavior.distributionCoverageRatio.coerceIn(0.0, 1.0)
            } else {
                1.0
            }
            val taxableGross = gross * taxableCoverage
            val result = dividendTaxCalculator.calculate(
                DividendTaxRequest(
                    taxClass = when {
                        stock.market.isKorean && stock.isFundLike -> DividendTaxClass.KOREAN_ETF_DISTRIBUTION
                        stock.market.isKorean -> DividendTaxClass.KOREAN_ORDINARY_CASH
                        else -> when (stock.instrumentType) {
                            InstrumentType.ETF -> DividendTaxClass.US_RIC_ETF_DISTRIBUTION
                            InstrumentType.CLOSED_END_FUND -> DividendTaxClass.US_RIC_CLOSED_END_DISTRIBUTION
                            InstrumentType.ETN -> DividendTaxClass.US_ETN_CONTINGENT_COUPON
                            InstrumentType.REIT -> DividendTaxClass.US_REIT_DISTRIBUTION
                            InstrumentType.ADR -> DividendTaxClass.FOREIGN_ADR_DISTRIBUTION
                            InstrumentType.STOCK -> DividendTaxClass.US_ORDINARY_CORPORATION
                        }
                    },
                    grossAmount = money(taxableGross, stock.currency),
                    paidOn = payDate,
                    taxExchangeRateToKrw = if (stock.currency == Currency.USD) macro.usdKrw else 1.0,
                    w8BenValid = true,
                    otherFinancialIncomeGrossKrw = dividends
                        .filter { gameDate(it.paidAt).year == payDate.year }
                        .sumOf { round(it.financialIncomeAmountKrw).toLong() },
                ),
            )
            val roundedTaxableGross = result.breakdown.taxableBase.amount
            val roundedGross = roundCurrency(gross, stock.currency)
            val returnOfCapital = (roundedGross - roundedTaxableGross).coerceAtLeast(0.0)
            val tax = result.breakdown.totalTax.amount
            val net = roundCurrency(result.netCash.amount + returnOfCapital, stock.currency)
            val exchangeRate = if (stock.currency == Currency.USD) macro.usdKrw else 1.0
            val (updatedBasis, excessRocGainKrw) = fifoCostBasisBook.applyReturnOfCapital(
                stockId = stock.id,
                amountKrw = round(returnOfCapital * exchangeRate).toLong(),
            )
            fifoCostBasisBook = updatedBasis
            cash[stock.currency] = roundCurrency(cash.getValue(stock.currency) + net, stock.currency)
            dividends += DividendLedgerEntry(
                id = ledgerId,
                stockId = stock.id,
                paidAt = to,
                currency = stock.currency,
                grossAmount = roundedGross,
                withholdingTax = tax,
                netAmount = net,
                exchangeRateToKrw = exchangeRate,
                taxBreakdown = result.breakdown,
                taxableIncomeAmount = roundedTaxableGross,
                returnOfCapitalAmount = returnOfCapital,
                excessReturnOfCapitalGainKrw = excessRocGainKrw,
                accountingSequence = nextSequence++,
            )
            recalculateAnnualTax(payDate.year)
        }
    }

    private fun expireDayOrders(time: Instant) {
        for (index in orders.indices) {
            val order = orders[index]
            if (!order.isOpen || order.timeInForce != TimeInForce.DAY) continue
            val stock = stockById.getValue(order.stockId)
            val created = GameCalendar.marketLocalDateTime(stock.market, order.createdAt)
            val now = GameCalendar.marketLocalDateTime(stock.market, time)
            val close = if (stock.market.isKorean) LocalTime(15, 30) else LocalTime(16, 0)
            val targetTradingDate = dayOrderTargetTradingDate(stock.market, created.date, created.time, close)
            if (now.date > targetTradingDate || (now.date == targetTradingDate && now.time >= close)) {
                orders[index] = order.copy(status = OrderStatus.EXPIRED, updatedAt = time)
            }
        }
    }

    private fun isDistributionDate(date: LocalDate, frequency: DistributionFrequency): Boolean = when (frequency) {
        DistributionFrequency.NONE -> false
        DistributionFrequency.WEEKLY -> date.dayOfWeek == DayOfWeek.FRIDAY
        DistributionFrequency.MONTHLY -> date.day == DIVIDEND_DAY
        DistributionFrequency.QUARTERLY -> date.day == DIVIDEND_DAY && date.month in setOf(
            Month.MARCH,
            Month.JUNE,
            Month.SEPTEMBER,
            Month.DECEMBER,
        )
        DistributionFrequency.SEMIANNUAL -> date.day == DIVIDEND_DAY &&
            date.month in setOf(Month.JUNE, Month.DECEMBER)
        DistributionFrequency.ANNUAL -> date.day == DIVIDEND_DAY && date.month == Month.DECEMBER
    }

    private fun dayOrderTargetTradingDate(
        market: Market,
        createdDate: LocalDate,
        createdTime: LocalTime,
        close: LocalTime,
    ): LocalDate {
        val createdIsTradingDay = isTradingDate(market, createdDate)
        if (createdIsTradingDay && createdTime < close) return createdDate
        var candidate = createdDate.plus(1, DateTimeUnit.DAY)
        while (!isTradingDate(market, candidate) && candidate <= LocalDate(2040, 12, 31)) {
            candidate = candidate.plus(1, DateTimeUnit.DAY)
        }
        return candidate
    }

    private fun isTradingDate(market: Market, date: LocalDate): Boolean {
        if (date.dayOfWeek in WEEKEND) return false
        return date.year !in 2026..2040 || date !in DefaultMarketHolidays.closedDates(market, date.year)
    }

    private fun updateHoldingPrices() {
        for ((stockId, holding) in holdings.toMap()) {
            holdings[stockId] = holding.copy(currentPrice = quotes.getValue(stockId).price)
        }
    }

    private fun updateMarketChange(
        bars: Map<String, PriceBar>,
        fractions: Map<Market, Double>,
    ) {
        val updated = macro.marketChangeFromPreviousClose.toMutableMap()
        for (market in Market.entries) {
            if (fractions.getValue(market) <= 0.0) continue
            val marketBars = stocks.filter { stock ->
                stock.hasCorporateEarnings && if (market.isUnitedStates) {
                    stock.market.isUnitedStates
                } else {
                    stock.market == market
                }
            }.map { bars.getValue(it.id) }
            if (marketBars.isEmpty()) continue
            val hourly = marketBars.map { if (it.open == 0.0) 0.0 else it.close / it.open - 1.0 }.average()
            updated[market] = (1.0 + (updated[market] ?: 0.0)) * (1.0 + hourly) - 1.0
        }
        macro = macro.copy(marketChangeFromPreviousClose = updated)
    }

    private fun updateMarketIndices(
        timestamp: Instant,
        bars: Map<String, PriceBar>,
        previousClosesByStockId: Map<String, Double>,
        fractions: Map<Market, Double>,
    ) {
        val hasUsTrading = fractions.any { (market, fraction) -> market.isUnitedStates && fraction > 0.0 }
        val calculated = marketIndexEngine.calculate(
            MarketIndexCalculationInput(
                timestamp = timestamp,
                stocks = stocks,
                barsByStockId = bars,
                previousCloseByStockId = previousClosesByStockId,
                previousIndices = marketIndices,
                macro = macro,
                // Price bars already contain the 09:30 half-hour fraction, so do not scale twice.
                usTradingFraction = if (hasUsTrading) 1.0 else 0.0,
            ),
        )
        marketIndices.clear()
        marketIndices.putAll(calculated)
        for ((id, snapshot) in calculated) {
            val values = marketIndexHistory.getOrPut(id) { ArrayDeque() }
            values.addLast(snapshot)
            while (values.size > MAX_INDEX_BARS) values.removeFirst()
        }
    }

    private fun prepareUsCircuitBreaker(time: Instant) {
        val local = GameCalendar.marketLocalDateTime(Market.NYSE, time)
        val hasCoreTrading = Market.entries.filter(Market::isUnitedStates).any {
            regularTradingFraction(it, time, time + 1.hours) > 0.0
        }
        val sp500 = marketIndices[MarketIndexId.SP_500]
        val decision = UsCircuitBreakerPolicy.evaluate(
            previous = usCircuitBreakerState,
            tradingDate = local.date,
            localTime = local.time,
            sp500SessionDate = sp500?.sessionDate,
            sp500ChangeRate = sp500?.changeRate ?: 0.0,
            hasCoreTrading = hasCoreTrading,
        )
        usCircuitBreakerState = decision.state
        macro = macro.copy(usCircuitBreakerLevel = decision.levelThisHour)
    }

    private fun updateBenchmark(
        bars: Map<String, PriceBar>,
        fractions: Map<Market, Double>,
    ) {
        val returns = stocks.asSequence()
            .filter { it.hasCorporateEarnings && fractions.getValue(it.market) > 0.0 }
            .map { bars.getValue(it.id) }
            .map { if (it.open == 0.0) 0.0 else it.close / it.open - 1.0 }
            .toList()
        if (returns.isNotEmpty()) benchmarkValue *= 1.0 + returns.average()
    }

    private fun updateDrawdown() {
        val assets = totalAssetsKrw()
        peakAssetsKrw = maxOf(peakAssetsKrw, assets)
        val drawdown = if (peakAssetsKrw == 0.0) 0.0 else (peakAssetsKrw - assets) / peakAssetsKrw
        maximumDrawdown = maxOf(maximumDrawdown, drawdown)
    }

    private fun recordDailySnapshot(date: LocalDate, timestamp: Instant) {
        val snapshot = portfolioSnapshot(timestamp)
        if (portfolioSnapshots.lastOrNull()?.timestamp?.let(::gameDate) == date) portfolioSnapshots.removeLast()
        portfolioSnapshots += snapshot

        val previous = dailyStatistics.lastOrNull { it.date != date }
        val dailyReturn = if (previous == null || previous.totalAssetsKrw == 0.0) {
            snapshot.totalAssetValueKrw / options.initialCapitalKrw - 1.0
        } else {
            snapshot.totalAssetValueKrw / previous.totalAssetsKrw - 1.0
        }
        if (dailyStatistics.lastOrNull()?.date == date) dailyStatistics.removeLast()
        dailyStatistics += DailyPortfolioStat(
            date = date,
            totalAssetsKrw = snapshot.totalAssetValueKrw,
            cashValueKrw = snapshot.cashValueKrw,
            stockValueKrw = snapshot.stockValueKrw,
            dailyReturn = dailyReturn,
            drawdown = if (peakAssetsKrw == 0.0) 0.0 else (peakAssetsKrw - snapshot.totalAssetValueKrw) / peakAssetsKrw,
            benchmarkValue = benchmarkValue,
            usdKrw = macro.usdKrw,
        )
        if (benchmarkHistory.lastOrNull()?.timestamp?.let(::gameDate) == date) benchmarkHistory.removeLast()
        benchmarkHistory += BenchmarkPoint(
            timestamp = timestamp,
            value = benchmarkValue,
            cumulativeReturn = benchmarkValue / BENCHMARK_START - 1.0,
        )
    }

    private fun portfolioSnapshot(timestamp: Instant): PortfolioSnapshot = PortfolioSnapshot(
        timestamp = timestamp,
        cashByCurrency = cash.toMap(),
        holdings = holdings.values.toList(),
        exchangeRatesToKrw = mapOf(Currency.USD to macro.usdKrw),
        initialCapitalKrw = options.initialCapitalKrw,
        realizedProfitKrw = realizedGains.sumOf(RealizedGainRecord::gainKrw),
        cumulativeCommissionKrw = transactionCosts.sumOf(TransactionCostRecord::commissionKrw),
        cumulativeTaxKrw = transactionCosts.sumOf(TransactionCostRecord::saleTaxKrw) +
            dividends.sumOf(DividendLedgerEntry::withholdingTaxKrw) +
            taxPaymentNotices.filter {
                it.status == com.amond.kmpbook.domain.tax.TaxLiabilityStatus.PAID
            }.sumOf { it.amountKrw.toDouble() },
        holdingCostBasisKrw = fifoCostBasisBook.lots.groupBy { it.stockId }
            .mapValues { (_, lots) -> lots.sumOf { it.remainingCostBasisKrw }.toDouble() },
    )

    private fun totalAssetsKrw(): Double {
        val cashValue = cash.getValue(Currency.KRW) + cash.getValue(Currency.USD) * macro.usdKrw
        val stockValue = holdings.values.sumOf { holding ->
            holding.marketValue * if (holding.currency == Currency.USD) macro.usdKrw else 1.0
        }
        return cashValue + stockValue
    }

    private fun assessStockGainTreatment(
        stock: StockDefinition,
        assessedOn: LocalDate,
        preSaleHolding: Holding,
    ): Pair<StockGainTaxTreatment, List<String>> {
        if (stock.market.isUnitedStates) {
            return StockGainTaxTreatment.FOREIGN_STANDARD to listOf(
                "${stock.instrumentType.displayName} 구조로 분류하고 대한민국 거주자의 국외주식 양도소득 규칙을 적용했습니다.",
            )
        }
        stock.etfProfile?.let { profile ->
            return when (profile.taxCategory) {
                EtfTaxCategory.KOREAN_DOMESTIC_EQUITY -> StockGainTaxTreatment.DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE to
                    listOf("국내주식형 ETF 장내 매매차익 비과세와 ETF 증권거래세 면제를 적용했습니다.")
                EtfTaxCategory.KOREAN_OTHER -> StockGainTaxTreatment.DOMESTIC_ETF_HOLDING_PERIOD_WITHHELD to
                    listOf("매매차익과 게임 과표기준가격 증가분 중 작은 금액에 15.4%를 원천징수했습니다.")
                EtfTaxCategory.FOREIGN_LISTED -> error("한국 시장 ETF에 국외상장 세무 분류가 지정되었습니다.")
            }
        }

        val priorYear = assessedOn.year - 1
        val priorSnapshot = portfolioSnapshots
            .asReversed()
            .firstOrNull { gameDate(it.timestamp).year == priorYear }
        val priorHolding = priorSnapshot?.holdings?.firstOrNull { it.stockId == stock.id }
        val priorQuantity = priorHolding?.quantity ?: 0.0
        val priorMarketValue = (priorHolding?.marketValue ?: 0.0).coerceAtLeast(0.0).toLong()
        val currentSharesOutstanding = stockById.getValue(stock.id).sharesOutstanding
        val priorSharesOutstanding = priorSnapshot?.let { sharesOutstandingAt(stock.id, it.timestamp) }
            ?: baseStockById.getValue(stock.id).sharesOutstanding
        val currentOwnershipRatio = (preSaleHolding.quantity / currentSharesOutstanding.toDouble())
            .coerceIn(0.0, 1.0)
        val assessment = majorShareholderCalculator.assess(
            MajorShareholderAssessmentRequest(
                market = stock.market,
                assessedOn = assessedOn,
                priorBusinessYearEndHoldings = listOf(
                    ShareholderHoldingSnapshot(
                        ownerId = "game-player",
                        relation = ShareholderRelation.SELF,
                        ownershipRatio = (priorQuantity / priorSharesOutstanding.toDouble()).coerceIn(0.0, 1.0),
                        marketValueKrw = priorMarketValue,
                    ),
                ),
                isLargestShareholderGroup = false,
                ownershipRatioAfterCurrentYearAcquisition = currentOwnershipRatio,
            ),
        )
        val treatment = if (assessment.isMajorShareholder) {
            StockGainTaxTreatment.DOMESTIC_MAJOR_GENERAL
        } else {
            StockGainTaxTreatment.DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE
        }
        val notes = assessment.notes + listOf(
            "외부 증권계좌와 친족·경영지배관계인 보유분이 없는 게임 계좌 기준 추정입니다.",
            "직전 연말 스냅샷과 당해연도 취득 후 게임 계좌 지분율만 반영했습니다.",
        )
        return treatment to notes
    }

    private fun fifoBookMatchesHoldings(): Boolean {
        val lotQuantities = fifoCostBasisBook.lots.groupBy { it.stockId }
            .mapValues { (_, lots) -> lots.sumOf { it.remainingQuantity } }
        val allIds = lotQuantities.keys + holdings.keys
        return allIds.all { stockId ->
            abs((lotQuantities[stockId] ?: 0.0) - (holdings[stockId]?.quantity ?: 0.0)) < QUANTITY_EPSILON
        }
    }

    private fun restoreTaxExchangeRateLedger(state: SimulatorUiState) {
        val tradeIds = trades.map(Trade::id)
        require(tradeIds.distinct().size == tradeIds.size) { "체결 ID가 중복되었습니다." }
        require(trades.zipWithNext().all { (left, right) -> left.executedAt <= right.executedAt }) {
            "체결 원장의 시간 순서가 올바르지 않습니다."
        }
        val costsByTrade = transactionCosts.associateBy(TransactionCostRecord::tradeId)
        require(costsByTrade.keys.containsAll(tradeIds)) { "모든 체결의 비용 원장이 필요합니다." }

        taxExchangeRatesByTradeId.clear()
        pendingTaxSettlementTradeIds.clear()
        val savedRates = state.taxExchangeRatesByTradeId
        if (savedRates == null) {
            require(state.pendingTaxSettlementTradeIds.isNullOrEmpty()) {
                "구형 저장에는 미결제 세무 환율 ID가 있을 수 없습니다."
            }
            for (trade in trades) {
                taxExchangeRatesByTradeId[trade.id] = if (trade.currency == Currency.KRW) {
                    1.0
                } else {
                    costsByTrade.getValue(trade.id).exchangeRateToKrw
                }
            }
        } else {
            require(savedRates.keys == tradeIds.toSet()) { "모든 체결의 세무 환율이 필요합니다." }
            require(savedRates.values.all { it.isFinite() && it > 0.0 }) {
                "세무 환율은 유한한 양수여야 합니다."
            }
            taxExchangeRatesByTradeId.putAll(savedRates)
            pendingTaxSettlementTradeIds += state.pendingTaxSettlementTradeIds.orEmpty()
        }

        val tradesById = trades.associateBy(Trade::id)
        require(pendingTaxSettlementTradeIds.all { tradeId ->
            val trade = tradesById[tradeId] ?: return@all false
            stockById.getValue(trade.stockId).market.isUnitedStates &&
                tradeId in taxExchangeRatesByTradeId
        }) { "미결제 세무 환율 원장에는 해외 체결만 들어갈 수 있습니다." }
        require(trades.filter { it.currency == Currency.KRW }.all { trade ->
            abs(taxExchangeRatesByTradeId.getValue(trade.id) - 1.0) < TAX_RATE_EPSILON
        }) { "국내 체결의 세무 환율은 1.0이어야 합니다." }
    }

    /**
     * 체결 원장을 원래 순서 그대로 다시 재생한다. 해외 미결제 체결도 임시 환율로 lot 수량을
     * 유지하고, 결제일에 환율이 확정되면 같은 재생으로 취득가액과 양도가액을 함께 고친다.
     */
    private fun replayTaxAccountingLedger(): Set<Int> {
        val gainTemplates = realizedGains.associateBy(RealizedGainRecord::tradeId)
        val saleIds = trades.filter { it.side == OrderSide.SELL }.map(Trade::id).toSet()
        require(gainTemplates.keys == saleIds) { "매도 체결과 실현손익 원장이 일치하지 않습니다." }

        var rebuiltBook = FifoCostBasisBook()
        val rebuiltGains = mutableListOf<RealizedGainRecord>()
        data class ReplayEntry(
            val at: Instant,
            val priority: Int,
            val fallbackSequence: Int,
            val accountingSequence: Long?,
            val id: String,
        )
        val actionsById = corporateActionLedger.associateBy(CorporateActionRecord::id)
        val tradesById = trades.associateBy(Trade::id)
        val rocById = dividends.filter { it.rocAmount > 0.0 }.associateBy(DividendLedgerEntry::id)
        val dividendIndexById = dividends.mapIndexed { index, dividend -> dividend.id to index }.toMap()
        val replayEntries = buildList {
            corporateActionLedger.forEachIndexed { index, action ->
                add(
                    ReplayEntry(
                        action.effectiveAt,
                        priority = 0,
                        fallbackSequence = index,
                        accountingSequence = action.accountingSequence,
                        id = action.id,
                    ),
                )
            }
            trades.forEachIndexed { index, trade ->
                add(
                    ReplayEntry(
                        trade.executedAt,
                        priority = 1,
                        fallbackSequence = index,
                        accountingSequence = trade.accountingSequence,
                        id = trade.id,
                    ),
                )
            }
            dividends.filter { it.rocAmount > 0.0 }.forEachIndexed { index, dividend ->
                add(
                    ReplayEntry(
                        dividend.paidAt,
                        priority = 2,
                        fallbackSequence = index,
                        accountingSequence = dividend.accountingSequence,
                        id = dividend.id,
                    ),
                )
            }
        }.sortedWith { left, right ->
            val timeOrder = left.at.compareTo(right.at)
            if (timeOrder != 0) {
                timeOrder
            } else if (left.accountingSequence != null && right.accountingSequence != null) {
                left.accountingSequence.compareTo(right.accountingSequence)
            } else {
                val typeOrder = left.priority.compareTo(right.priority)
                if (typeOrder != 0) typeOrder else left.fallbackSequence.compareTo(right.fallbackSequence)
            }
        }

        for (entry in replayEntries) {
            when (entry.priority) {
                0 -> {
                    val action = actionsById.getValue(entry.id)
                    rebuiltBook = rebuiltBook.applyQuantityMultiplier(action.stockId, action.quantityMultiplier)
                }

                1 -> {
                    val trade = tradesById.getValue(entry.id)
                    val stock = stockById.getValue(trade.stockId)
                    val rate = taxExchangeRatesByTradeId.getValue(trade.id)
                    val settledOn = settlementDate(stock.market, marketDate(stock.market, trade.executedAt))
                    val roundedGross = roundCurrency(trade.grossAmount, trade.currency)
                    if (trade.side == OrderSide.BUY) {
                        rebuiltBook = rebuiltBook.addPurchase(
                            lotId = trade.id,
                            stockId = stock.id,
                            acquiredOn = settledOn,
                            quantity = trade.quantity,
                            purchasePriceKrw = round(roundedGross * rate).toLong(),
                            directPurchaseCostsKrw = round(trade.commission * rate).toLong(),
                        )
                    } else {
                        val template = gainTemplates.getValue(trade.id)
                        val sale = rebuiltBook.sell(
                            stockId = stock.id,
                            soldOn = settledOn,
                            quantity = trade.quantity,
                            grossProceedsKrw = round(roundedGross * rate).toLong(),
                            directSellingCostsKrw = round((trade.commission + trade.tax) * rate).toLong(),
                        )
                        rebuiltBook = sale.updatedBook
                        rebuiltGains += template.copy(
                            settlementDate = settledOn,
                            exchangeRateToKrw = rate,
                            taxGrossProceedsKrw = sale.grossProceedsKrw,
                            taxCostBasisKrw = sale.allocatedCostBasisKrw,
                            taxDirectSellingCostsKrw = sale.directSellingCostsKrw,
                            taxGainKrw = sale.realizedGainKrw,
                        )
                    }
                }

                2 -> {
                    val dividend = rocById.getValue(entry.id)
                    val rocKrw = round(dividend.rocAmount * dividend.exchangeRateToKrw).toLong()
                    val (updatedBook, excessGainKrw) = rebuiltBook.applyReturnOfCapital(
                        dividend.stockId,
                        rocKrw,
                    )
                    rebuiltBook = updatedBook
                    val index = dividendIndexById.getValue(dividend.id)
                    dividends[index] = dividends[index].copy(
                        excessReturnOfCapitalGainKrw = excessGainKrw,
                    )
                }

                else -> error("지원하지 않는 세무 원장 재생 항목입니다.")
            }
        }
        fifoCostBasisBook = rebuiltBook
        realizedGains.clear()
        realizedGains += rebuiltGains
        return buildSet {
            realizedGains.mapTo(this) { it.settlementDate.year }
            dividends.filter { it.rocAmount > 0.0 }.mapTo(this) { gameDate(it.paidAt).year }
        }
    }

    private fun processTaxExchangeRateSettlements(at: Instant) {
        if (pendingTaxSettlementTradeIds.isEmpty()) return
        val tradesById = trades.associateBy(Trade::id)
        val dueTradeIds = pendingTaxSettlementTradeIds.filter { tradeId ->
            val trade = tradesById.getValue(tradeId)
            val stock = stockById.getValue(trade.stockId)
            val settledOn = settlementDate(stock.market, marketDate(stock.market, trade.executedAt))
            marketDate(stock.market, at) >= settledOn
        }
        if (dueTradeIds.isEmpty()) return

        for (tradeId in dueTradeIds) {
            taxExchangeRatesByTradeId[tradeId] = macro.usdKrw
            pendingTaxSettlementTradeIds.remove(tradeId)
        }
        val replayedTaxYears = replayTaxAccountingLedger()
        (replayedTaxYears.asSequence() + realizedGains.asSequence()
            .filter { it.tradeId in dueTradeIds }
            .map { it.settlementDate.year })
            .distinct()
            .forEach(::recalculateAnnualTax)
    }

    private fun recalculateAnnualTax(year: Int) {
        if (year !in 2026..2040) return
        val tradeGains = realizedGains.filter { it.settlementDate.year == year }.map { record ->
            RealizedStockGain(
                id = record.tradeId,
                stockId = record.stockId,
                realizedOn = record.settlementDate,
                gainKrw = record.taxGainKrw,
                treatment = record.taxTreatment,
                instrumentTaxClass = if (record.market.isUnitedStates) {
                    stockById[record.stockId]?.let(::foreignInstrumentTaxClass)
                        ?: ForeignInstrumentTaxClass.OTHER_FOREIGN_EQUITY
                } else {
                    null
                },
            )
        }
        val yearDividends = dividends.filter { gameDate(it.paidAt).year == year }
        val rocGains = yearDividends.mapNotNull { entry ->
            val gain = entry.excessReturnOfCapitalGainKrw ?: 0L
            if (gain <= 0L) return@mapNotNull null
            val stock = stockById[entry.stockId] ?: return@mapNotNull null
            RealizedStockGain(
                id = "${entry.id}:excess-roc",
                stockId = entry.stockId,
                realizedOn = gameDate(entry.paidAt),
                gainKrw = gain,
                treatment = StockGainTaxTreatment.FOREIGN_STANDARD,
                instrumentTaxClass = foreignInstrumentTaxClass(stock),
            )
        }
        val gains = tradeGains + rocGains
        val ledger = annualStockTaxCalculator.calculate(
            AnnualStockTaxRequest(
                taxYear = year,
                gains = gains,
                financialIncomeGrossKrw = yearDividends.sumOf { round(it.financialIncomeAmountKrw).toLong() } +
                    realizedGains.filter {
                        it.settlementDate.year == year &&
                            it.taxTreatment == StockGainTaxTreatment.DOMESTIC_ETF_HOLDING_PERIOD_WITHHELD
                    }.sumOf(RealizedGainRecord::taxableFinancialIncomeKrw),
                foreignTaxPaidKrw = yearDividends
                    .filter { it.currency == Currency.USD }
                    .sumOf { round(it.withholdingTaxKrw).toLong() },
                withholdingCreditsKrw = realizedGains.filter {
                    it.settlementDate.year == year &&
                        it.taxTreatment == StockGainTaxTreatment.DOMESTIC_ETF_HOLDING_PERIOD_WITHHELD
                }.sumOf { round(it.saleTax * it.exchangeRateToKrw).toLong() },
            ),
        )
        annualTaxLedgers[year] = ledger
        taxPaymentNotices.removeAll { it.taxYear == year }
        taxPaymentNotices += ledger.liabilities.map { liability ->
            TaxPaymentNotice(
                id = liability.id,
                taxYear = year,
                dueDate = liability.dueDate ?: LocalDate(year + 1, 5, 31),
                amountKrw = liability.payableKrw,
                status = liability.status,
                message = "${year}년 ${liability.label} ${liability.payableKrw}원은 ${liability.dueDate ?: LocalDate(year + 1, 5, 31)}까지 납부 예정입니다.",
            )
        }
    }

    private fun foreignInstrumentTaxClass(stock: StockDefinition): ForeignInstrumentTaxClass =
        when (stock.instrumentType) {
            InstrumentType.STOCK -> ForeignInstrumentTaxClass.US_COMMON_STOCK
            InstrumentType.ETF -> ForeignInstrumentTaxClass.US_ETF_RIC
            InstrumentType.CLOSED_END_FUND -> ForeignInstrumentTaxClass.US_CLOSED_END_FUND_RIC
            InstrumentType.ETN -> ForeignInstrumentTaxClass.US_ETN_DEBT_SECURITY
            InstrumentType.REIT -> ForeignInstrumentTaxClass.US_REIT_USRPI
            InstrumentType.ADR -> ForeignInstrumentTaxClass.ADR
        }

    private fun processDueTaxPayments(currentDate: LocalDate) {
        for (index in taxPaymentNotices.indices) {
            val notice = taxPaymentNotices[index]
            if (notice.taxYear >= 2040 || notice.status != com.amond.kmpbook.domain.tax.TaxLiabilityStatus.DUE ||
                currentDate < notice.dueDate
            ) {
                continue
            }
            val required = notice.amountKrw.toDouble()
            if (cash.getValue(Currency.KRW) + CASH_EPSILON < required) continue
            cash[Currency.KRW] = roundCurrency(cash.getValue(Currency.KRW) - required, Currency.KRW)
            taxPaymentNotices[index] = notice.copy(
                status = com.amond.kmpbook.domain.tax.TaxLiabilityStatus.PAID,
                message = "${notice.taxYear}년 귀속 세금 ${notice.amountKrw}원을 ${currentDate}에 납부했습니다.",
            )
            annualTaxLedgers[notice.taxYear]?.let { ledger ->
                annualTaxLedgers[notice.taxYear] = ledger.copy(
                    liabilities = ledger.liabilities.map { liability ->
                        if (liability.id == notice.id) liability.copy(status = com.amond.kmpbook.domain.tax.TaxLiabilityStatus.PAID)
                        else liability
                    },
                )
            }
            lastMessage = taxPaymentNotices[index].message
        }
    }

    private fun settlementDate(market: Market, tradedOn: LocalDate): LocalDate {
        var date = tradedOn
        var days = 0
        val requiredBusinessDays = if (market.isUnitedStates) 1 else 2
        while (days < requiredBusinessDays) {
            date = date.plus(1, DateTimeUnit.DAY)
            val holidays = if (date.year in 2026..2040) DefaultMarketHolidays.closedDates(market, date.year) else emptySet()
            if (date.dayOfWeek !in WEEKEND && date !in holidays) days += 1
        }
        return date.coerceAtMost(LocalDate(2040, 12, 31))
    }

    private fun enterSettlement() {
        currentTime = GameCalendar.endInstant
        turn = GameCalendar.turnAt(currentTime)
        phase = GamePhase.SETTLEMENT
        screen = Screen.ENDING
        for (index in orders.indices) {
            val order = orders[index]
            if (order.isOpen) orders[index] = order.copy(status = OrderStatus.EXPIRED, updatedAt = currentTime)
        }
        updateHoldingPrices()
        updateDrawdown()
        recalculateAnnualTax(2040)
        recordDailySnapshot(LocalDate(2040, 12, 31), currentTime)
        lastMessage = GameEndReason.DATE_LIMIT.displayName
    }

    private fun marketSessionAtCurrentTime(market: Market): MarketSession {
        val calendarSession = marketSession(market, currentTime)
        val haltedToday = market.isUnitedStates &&
            usCircuitBreakerState.haltedForDay &&
            usCircuitBreakerState.tradingDate == marketDate(market, currentTime)
        return if (haltedToday) MarketSession.CLOSED else calendarSession
    }

    private fun marketSession(market: Market, time: Instant): MarketSession {
        val localDate = marketDate(market, time)
        return GameCalendar.marketSession(
            market = market,
            time = time,
            closedDates = DefaultMarketHolidays.closedDates(market, localDate.year),
        )
    }

    private fun regularTradingFraction(market: Market, from: Instant, to: Instant): Double {
        require(to >= from)
        val date = marketDate(market, from)
        return GameCalendar.regularTradingFraction(
            market = market,
            hourStart = from,
            closedDates = DefaultMarketHolidays.closedDates(market, date.year),
        )
    }

    private fun regionalTradingFraction(
        region: EtfExposureRegion,
        time: Instant,
        effectiveMarketFractions: Map<Market, Double>? = null,
    ): Double {
        val utcHour = time.toLocalDateTime(kotlinx.datetime.TimeZone.UTC).hour
        fun marketFraction(market: Market): Double = effectiveMarketFractions?.get(market)
            ?: regularTradingFraction(market, time, time + 1.hours)
        return when (region) {
            EtfExposureRegion.KOREA -> maxOf(
                marketFraction(Market.KOSPI),
                marketFraction(Market.KOSDAQ),
            )
            EtfExposureRegion.UNITED_STATES -> Market.entries
                .filter(Market::isUnitedStates)
                .maxOfOrNull(::marketFraction)
                ?: 0.0
            // 일본·호주와 유럽 세션을 하나의 복합 선진국 팩터로 근사한다.
            EtfExposureRegion.DEVELOPED_EX_US -> if (utcHour in 0..15) 1.0 else 0.0
            EtfExposureRegion.EMERGING_MARKETS -> if (utcHour in 0..9) 1.0 else 0.0
            EtfExposureRegion.GLOBAL -> if (Market.entries.any {
                    marketFraction(it) > 0.0
                } || utcHour in 0..15
            ) 1.0 else 0.0
        }
    }

    private fun Iterable<Double>.averageOrZero(): Double {
        val values = toList()
        return if (values.isEmpty()) 0.0 else values.average()
    }

    private fun orderBook(
        stock: StockDefinition,
        quote: Quote,
        session: MarketSession,
    ): OrderBookSnapshot {
        val tracker = dailyTrackers.getValue(stock.id)
        val scheduledActive = scheduledEventEngine.activeImpactEventsAt(currentTime, stocks)
        val liquidity = EventShockCalculator.liquidityMultiplierAt(
            activeEvents + scheduledActive,
            stock,
            currentTime,
        )
        return orderBookEngine.generate(
            OrderBookGenerationInput(
                stock = stock,
                timestamp = currentTime,
                lastPrice = quote.price,
                dailyBasePrice = tracker.basePrice,
                session = session,
                buyPressure = macro.riskSentiment * 0.25,
                marketStress = ((macro.volatilityRegime - 1.0) / 2.0 + (1.0 / liquidity - 1.0) * 0.25)
                    .coerceIn(0.0, 1.0),
            ),
        )
    }

    private fun marketDate(market: Market, time: Instant): LocalDate =
        GameCalendar.marketLocalDateTime(market, time).date

    private fun gameDate(time: Instant): LocalDate = time.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE).date

    private fun normalizeFxState(value: MacroEnvironment): MacroEnvironment {
        if (value.fxRatesToKrw != null && value.previousFxRatesToKrw != null) return value
        return value.copy(
            fxRatesToKrw = value.fxRatesToKrw ?: initialFxRates(value.usdKrw),
            previousFxRatesToKrw = value.previousFxRatesToKrw ?: initialFxRates(value.previousUsdKrw),
        )
    }

    private fun money(amount: Double, currency: Currency): MoneyAmount =
        MoneyRoundingPolicy.MINOR_UNIT_HALF_UP.fromMajorUnits(amount.coerceAtLeast(0.0), currency)

    private fun roundCurrency(amount: Double, currency: Currency): Double {
        val factor = if (currency == Currency.KRW) 1.0 else 100.0
        return round(amount * factor) / factor
    }

    private fun nextId(prefix: String): String = "$prefix-${options.seed}-${nextSequence++}"

    private fun fail(message: String): Boolean {
        lastMessage = message
        return false
    }

    private data class DailyPriceTracker(
        var date: LocalDate,
        var basePrice: Double,
        var open: Double,
        var high: Double,
        var low: Double,
        var hasRegularTrading: Boolean,
    )

    companion object {
        /** The full instrument universe stays comfortably below the 64 MiB save limit. */
        const val MAX_RECENT_BARS = 256
        const val MAX_INDEX_BARS = 256
        const val MAX_NEWS_EVENTS = 1_000
        const val BENCHMARK_START = 100.0
        const val BUY_RESERVE_MULTIPLIER = 1.003
        const val FX_SPREAD_RATE = 0.001
        const val FX_MEAN_REVERSION = 0.00025
        const val FX_HOURLY_VOLATILITY = 0.0015
        const val FX_CROSS_HOURLY_VOLATILITY = 0.00055
        const val FX_CROSS_MEAN_REVERSION = 0.00018
        const val FX_GLOBAL_KRW_LOADING = 0.72
        const val MIN_USD_KRW = 800.0
        const val MAX_USD_KRW = 2_500.0
        const val POLICY_CHANGE_PROBABILITY_PER_HOUR = 1.0 / (24.0 * 120.0)
        const val MARKET_FACTOR_VOLATILITY = 0.0016
        const val VENUE_FACTOR_VOLATILITY = 0.00018
        const val SECTOR_FACTOR_VOLATILITY = 0.0010
        const val FORWARD_SPLIT_STREAK_DAYS = 20
        const val CORPORATE_ACTION_NOTICE_HOURS = 24 * 5
        const val CORPORATE_ACTION_COOLDOWN_HOURS = 24 * 365
        const val CORPORATE_ACTION_BOARD_GATE = 8L
        const val ETN_CALL_EVENT_PREFIX = "etn_issuer_call_decision:"
        const val ETN_ACCELERATION_EVENT_PREFIX = "etn_issuer_acceleration:"
        const val DIVIDEND_DAY = 15
        const val PRICE_EPSILON = 1e-7
        const val QUANTITY_EPSILON = 1e-7
        const val TAX_RATE_EPSILON = 1e-9
        const val CASH_EPSILON = 0.01

        fun initialFxRates(usdKrw: Double): Map<ReferenceCurrency, Double> {
            val scale = usdKrw / 1_350.0
            return mapOf(
                ReferenceCurrency.KRW to 1.0,
                ReferenceCurrency.USD to usdKrw,
                ReferenceCurrency.EUR to 1_570.0 * scale,
                ReferenceCurrency.JPY to 9.15 * scale,
                ReferenceCurrency.CNY to 188.0 * scale,
                ReferenceCurrency.HKD to 173.0 * scale,
                ReferenceCurrency.GBP to 1_820.0 * scale,
                ReferenceCurrency.CAD to 985.0 * scale,
                ReferenceCurrency.CHF to 1_665.0 * scale,
                ReferenceCurrency.AUD to 890.0 * scale,
                ReferenceCurrency.SGD to 1_060.0 * scale,
                ReferenceCurrency.TWD to 44.0 * scale,
                ReferenceCurrency.INR to 15.4 * scale,
                ReferenceCurrency.BRL to 252.0 * scale,
            )
        }
        const val MACRO_STREAM_ID = 0x4D4143524FL
        const val PRICE_STREAM_ID = 0x5052494345L
        const val BOOK_STREAM_ID = 0x424F4F4BL
        const val EVENT_STREAM_ID = 0x4556454E54L
        const val SCHEDULED_EVENT_STREAM_ID = 0x5343484544554C45L
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun restore(state: SimulatorUiState): SimulatorRuntime? = runCatching {
            SimulatorRuntime(state.options).apply { restoreFrom(state) }
        }.getOrNull()
    }
}
