package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.data.StockCatalog
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.GameEndReason
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GamePhase
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.Order
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderStatus
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PortfolioSnapshot
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.domain.model.Trade
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.simulation.DeterministicRandom
import com.amond.kmpbook.domain.simulation.EventEngine
import com.amond.kmpbook.domain.simulation.EventGenerationContext
import com.amond.kmpbook.domain.simulation.EventShockCalculator
import com.amond.kmpbook.domain.simulation.MacroEnvironment
import com.amond.kmpbook.domain.simulation.MarketMicrostructure
import com.amond.kmpbook.domain.simulation.OrderBookEngine
import com.amond.kmpbook.domain.simulation.OrderBookGenerationInput
import com.amond.kmpbook.domain.simulation.OrderBookSnapshot
import com.amond.kmpbook.domain.simulation.PriceEngine
import com.amond.kmpbook.domain.simulation.PriceGenerationInput
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
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.exp
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

    val stocks: List<StockDefinition> = if (options.usFractionalTrading) {
        StockCatalog.withUsFractionalTrading()
    } else {
        StockCatalog.all
    }

    private val stockById = stocks.associateBy(StockDefinition::id)
    private val quotes = linkedMapOf<String, Quote>()
    private val history = linkedMapOf<String, ArrayDeque<PriceBar>>()
    private val dailyTrackers = mutableMapOf<String, DailyPriceTracker>()
    private val cash = mutableMapOf(Currency.KRW to options.initialCapitalKrw, Currency.USD to 0.0)
    private val holdings = linkedMapOf<String, Holding>()
    private val orders = mutableListOf<Order>()
    private val trades = mutableListOf<Trade>()
    private val transactionCosts = mutableListOf<TransactionCostRecord>()
    private val realizedGains = mutableListOf<RealizedGainRecord>()
    private var fifoCostBasisBook = FifoCostBasisBook()
    private val dividends = mutableListOf<DividendLedgerEntry>()
    private val foreignExchanges = mutableListOf<ForeignExchangeRecord>()
    private val activeEvents = mutableListOf<GameEvent>()
    private val newsEvents = mutableListOf<GameEvent>()
    private val readEventIds = mutableSetOf<String>()
    private val portfolioSnapshots = mutableListOf<PortfolioSnapshot>()
    private val dailyStatistics = mutableListOf<DailyPortfolioStat>()
    private val benchmarkHistory = mutableListOf<BenchmarkPoint>()
    private val annualTaxLedgers = linkedMapOf<Int, com.amond.kmpbook.domain.tax.AnnualTaxLedger>()
    private val taxPaymentNotices = mutableListOf<TaxPaymentNotice>()

    private var macro = MacroEnvironment(usdKrw = options.initialUsdKrw)
    private var macroDate = gameDate(currentTime)
    private var benchmarkValue = BENCHMARK_START
    private var peakAssetsKrw = options.initialCapitalKrw
    private var maximumDrawdown = 0.0
    private var nextSequence = 1L

    private val random = DeterministicRandom(
        DeterministicRandom.mixSeed(options.seed, MACRO_STREAM_ID),
    )
    private val priceEngine = PriceEngine(DeterministicRandom.mixSeed(options.seed, PRICE_STREAM_ID))
    private val orderBookEngine = OrderBookEngine(DeterministicRandom.mixSeed(options.seed, BOOK_STREAM_ID))
    private val eventEngine = EventEngine(DeterministicRandom.mixSeed(options.seed, EVENT_STREAM_ID))
    private val domesticSaleTaxCalculator = DomesticSaleTaxCalculator()
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
        if (!request.quantity.isFinite() || !stock.acceptsQuantity(request.quantity)) {
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
        val stateQuotes = quotes.mapValues { (stockId, quote) ->
            val stock = stockById.getValue(stockId)
            quote.copy(session = sessions.getValue(stock.market))
        }.toMutableMap()
        val selectedBook = selectedStockId?.let { id ->
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
            activeEvents = activeEvents.filter { it.isActiveAt(currentTime) }.toList(),
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
        )
    }

    private fun restoreFrom(state: SimulatorUiState) {
        require(GameCalendar.isWithinGameRange(state.currentTime)) { "저장 시각이 게임 범위를 벗어났습니다." }
        require(state.turn == GameCalendar.turnAt(state.currentTime)) { "저장 턴과 시각이 일치하지 않습니다." }
        require(state.stocks.map(StockDefinition::id) == stocks.map(StockDefinition::id)) {
            "저장 종목 카탈로그가 현재 옵션과 일치하지 않습니다."
        }
        val ids = stockById.keys
        require(state.quotes.keys == ids && state.priceHistory.keys == ids) {
            "모든 종목의 시세와 차트 기록이 필요합니다."
        }
        require(state.cashByCurrency.keys.containsAll(Currency.entries)) { "통화별 현금 잔액이 누락되었습니다." }
        require(state.cashByCurrency.values.all { it >= 0.0 && it.isFinite() }) { "현금 잔액이 올바르지 않습니다." }
        require(state.holdings.keys.all(ids::contains)) { "알 수 없는 보유 종목이 있습니다." }
        require(state.orders.all { it.stockId in ids } && state.trades.all { it.stockId in ids }) {
            "주문·체결 원장에 알 수 없는 종목이 있습니다."
        }

        options = state.options
        phase = state.phase
        screen = state.screen
        currentTime = state.currentTime
        turn = state.turn
        selectedTurnStep = state.selectedTurnStep
        selectedStockId = state.selectedStockId?.also { require(it in ids) }
        isAdvancing = false
        lastMessage = "저장 게임을 불러왔습니다."
        macro = state.macro
        macroDate = gameDate(currentTime)
        benchmarkValue = state.benchmarkHistory.lastOrNull()?.value ?: BENCHMARK_START
        peakAssetsKrw = state.peakAssetsKrw
        maximumDrawdown = state.maximumDrawdown
        nextSequence = state.nextSequence
        require(nextSequence > 0L) { "저장 시퀀스가 올바르지 않습니다." }

        random.restore(state.rngState)
        eventEngine.restore(state.eventEngineSnapshot)
        quotes.clear()
        quotes.putAll(state.quotes)
        history.clear()
        state.priceHistory.forEach { (stockId, bars) ->
            require(bars.isNotEmpty() && bars.size <= MAX_RECENT_BARS) { "차트 기록 크기가 올바르지 않습니다." }
            history[stockId] = ArrayDeque(bars)
        }
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
        fifoCostBasisBook = state.fifoCostBasisBook
        if (!fifoBookMatchesHoldings()) fifoCostBasisBook = rebuildFifoBook()
        require(fifoCostBasisBook.lots.all { it.stockId in ids } && fifoBookMatchesHoldings()) {
            "FIFO 세무원장과 보유 수량이 일치하지 않습니다."
        }
        dividends.clear()
        dividends += state.dividendLedger
        foreignExchanges.clear()
        foreignExchanges += state.foreignExchangeLedger
        activeEvents.clear()
        activeEvents += state.eventEngineSnapshot.activeEvents
        newsEvents.clear()
        newsEvents += state.newsEvents.sortedBy(GameEvent::startsAt)
        readEventIds.clear()
        readEventIds += state.readEventIds
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

        dailyTrackers.clear()
        for (stock in stocks) {
            val quote = quotes.getValue(stock.id)
            dailyTrackers[stock.id] = DailyPriceTracker(
                date = marketDate(stock.market, currentTime),
                basePrice = quote.previousClose,
                open = quote.open,
                high = quote.high,
                low = quote.low,
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
            val session = marketSession(stock.market, currentTime)
            quotes[stock.id] = Quote(
                stockId = stock.id,
                timestamp = currentTime,
                price = stock.initialPrice,
                previousClose = stock.initialPrice,
                session = session,
            )
            history[stock.id] = ArrayDeque<PriceBar>().apply {
                addLast(
                    PriceBar(
                        stockId = stock.id,
                        startTime = currentTime - 1.hours,
                        endTime = currentTime,
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
                date = marketDate(stock.market, currentTime),
                basePrice = stock.initialPrice,
                open = stock.initialPrice,
                high = stock.initialPrice,
                low = stock.initialPrice,
            )
        }
    }

    private fun advanceOneHour() {
        val from = currentTime
        val to = GameCalendar.advanceHours(from, 1)
        if (to <= from) return
        val fromGameDate = gameDate(from)

        updateMacro(from)
        generateEvents(from)

        val generatedBars = linkedMapOf<String, PriceBar>()
        val tradingFractions = mutableMapOf<Market, Double>()
        for (market in Market.entries) {
            tradingFractions[market] = regularTradingFraction(market, from, to)
        }

        for (stock in stocks) {
            val previousQuote = quotes.getValue(stock.id)
            val tracker = trackerFor(stock, from, previousQuote.price)
            val fraction = tradingFractions.getValue(stock.market)
            val session = if (fraction > 0.0) MarketSession.REGULAR else marketSession(stock.market, from)
            val impulse = EventShockCalculator.aggregate(activeEvents, stock, from, to)
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
                ),
            )
            quotes[stock.id] = result.quote
            generatedBars[stock.id] = result.bar
            appendHistory(stock.id, result.bar)
            tracker.high = maxOf(tracker.high, result.bar.high)
            tracker.low = minOf(tracker.low, result.bar.low)
        }

        updateMarketChange(generatedBars, tradingFractions)
        processOpenOrders(generatedBars, tradingFractions, to)
        updateHoldingPrices()
        processScheduledDividends(from, to)
        expireDayOrders(to)
        updateBenchmark(generatedBars, tradingFractions)

        currentTime = to
        turn = GameCalendar.turnAt(to)
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

    private fun updateMacro(time: Instant) {
        val date = gameDate(time)
        val resetMarketChange = date != macroDate
        macroDate = date
        val previousUsdKrw = macro.usdKrw
        val meanReversion = ln(options.initialUsdKrw / previousUsdKrw) * FX_MEAN_REVERSION
        val usdKrw = (previousUsdKrw * exp(meanReversion + random.nextGaussian() * FX_HOURLY_VOLATILITY))
            .coerceIn(MIN_USD_KRW, MAX_USD_KRW)
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
        val marketReturns = Market.entries.associateWith {
            random.nextGaussian() * MARKET_FACTOR_VOLATILITY * volatilityRegime
        }
        val sectorReturns = Sector.entries.associateWith {
            random.nextGaussian() * SECTOR_FACTOR_VOLATILITY * volatilityRegime
        }
        macro = MacroEnvironment(
            policyRate = policyRate,
            policyRateChange = policyChange,
            inflationRate = inflation,
            inflationSurprise = (inflation - 0.02) / 0.01,
            growthRate = growth,
            growthSurprise = (growth - 0.02) / 0.02,
            usdKrw = usdKrw,
            previousUsdKrw = previousUsdKrw,
            riskSentiment = riskSentiment,
            volatilityRegime = volatilityRegime,
            marketHourlyReturns = marketReturns,
            sectorHourlyReturns = sectorReturns,
            marketChangeFromPreviousClose = if (resetMarketChange) emptyMap() else macro.marketChangeFromPreviousClose,
        )
    }

    private fun generateEvents(time: Instant) {
        val result = eventEngine.generate(
            EventGenerationContext(
                timestamp = time,
                stocks = stocks,
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
            while (newsEvents.size > MAX_NEWS_EVENTS) newsEvents.removeAt(0)
            val sentiment = result.newEvents.map { it.impact.sentiment }.average()
            macro = macro.copy(riskSentiment = (macro.riskSentiment + sentiment * 0.15).coerceIn(-1.0, 1.0))
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
        val session = marketSession(stock.market, currentTime)
        if (session != MarketSession.REGULAR) return
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
        val taxBreakdown = if (order.side == OrderSide.SELL && stock.market.isKorean) {
            domesticSaleTaxCalculator.calculate(
                DomesticSaleTaxRequest(
                    market = stock.market,
                    grossProceedsKrw = gross.toLong(),
                    soldOn = tradedOn,
                ),
            )
        } else {
            null
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
            )
        }

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
        if (holdings.isEmpty()) return
        for (holding in holdings.values.toList()) {
            val stock = stockById.getValue(holding.stockId)
            if (stock.dividendYield <= 0.0) continue
            val fromDate = marketDate(stock.market, from)
            val payDate = marketDate(stock.market, to)
            if (payDate == fromDate || payDate.day != DIVIDEND_DAY || payDate.month !in DIVIDEND_MONTHS) continue
            val ledgerId = "dividend:${stock.id}:$payDate"
            if (dividends.any { it.id == ledgerId }) continue

            val gross = holding.quantity * holding.currentPrice * stock.dividendYield / DIVIDENDS_PER_YEAR
            val result = dividendTaxCalculator.calculate(
                DividendTaxRequest(
                    taxClass = if (stock.market.isKorean) {
                        DividendTaxClass.KOREAN_ORDINARY_CASH
                    } else {
                        DividendTaxClass.US_ORDINARY_CORPORATION
                    },
                    grossAmount = money(gross, stock.currency),
                    paidOn = payDate,
                    taxExchangeRateToKrw = if (stock.currency == Currency.USD) macro.usdKrw else 1.0,
                    w8BenValid = true,
                    otherFinancialIncomeGrossKrw = dividends
                        .filter { gameDate(it.paidAt).year == payDate.year }
                        .sumOf { it.grossAmountKrw.toLong() },
                ),
            )
            val roundedGross = result.breakdown.taxableBase.amount
            val tax = result.breakdown.totalTax.amount
            val net = result.netCash.amount
            cash[stock.currency] = roundCurrency(cash.getValue(stock.currency) + net, stock.currency)
            dividends += DividendLedgerEntry(
                id = ledgerId,
                stockId = stock.id,
                paidAt = to,
                currency = stock.currency,
                grossAmount = roundedGross,
                withholdingTax = tax,
                netAmount = net,
                exchangeRateToKrw = if (stock.currency == Currency.USD) macro.usdKrw else 1.0,
                taxBreakdown = result.breakdown,
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
            val marketBars = stocks.filter { it.market == market }.map { bars.getValue(it.id) }
            if (marketBars.isEmpty()) continue
            val hourly = marketBars.map { if (it.open == 0.0) 0.0 else it.close / it.open - 1.0 }.average()
            updated[market] = (1.0 + (updated[market] ?: 0.0)) * (1.0 + hourly) - 1.0
        }
        macro = macro.copy(marketChangeFromPreviousClose = updated)
    }

    private fun updateBenchmark(
        bars: Map<String, PriceBar>,
        fractions: Map<Market, Double>,
    ) {
        val returns = stocks.asSequence()
            .filter { fractions.getValue(it.market) > 0.0 }
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
            return StockGainTaxTreatment.FOREIGN_STANDARD to listOf("미국 일반법인 주식으로 분류했습니다.")
        }

        val priorYear = assessedOn.year - 1
        val priorSnapshot = portfolioSnapshots
            .asReversed()
            .firstOrNull { gameDate(it.timestamp).year == priorYear }
        val priorHolding = priorSnapshot?.holdings?.firstOrNull { it.stockId == stock.id }
        val priorQuantity = priorHolding?.quantity ?: 0.0
        val priorMarketValue = (priorHolding?.marketValue ?: 0.0).coerceAtLeast(0.0).toLong()
        val currentOwnershipRatio = (preSaleHolding.quantity / stock.sharesOutstanding.toDouble())
            .coerceIn(0.0, 1.0)
        val assessment = majorShareholderCalculator.assess(
            MajorShareholderAssessmentRequest(
                market = stock.market,
                assessedOn = assessedOn,
                priorBusinessYearEndHoldings = listOf(
                    ShareholderHoldingSnapshot(
                        ownerId = "game-player",
                        relation = ShareholderRelation.SELF,
                        ownershipRatio = (priorQuantity / stock.sharesOutstanding.toDouble()).coerceIn(0.0, 1.0),
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

    private fun rebuildFifoBook(): FifoCostBasisBook {
        var rebuilt = FifoCostBasisBook()
        val costsByTrade = transactionCosts.associateBy(TransactionCostRecord::tradeId)
        val gainsByTrade = realizedGains.associateBy(RealizedGainRecord::tradeId)
        for (trade in trades.sortedWith(compareBy<Trade> { it.executedAt }.thenBy(Trade::id))) {
            val stock = stockById.getValue(trade.stockId)
            val cost = costsByTrade[trade.id]
            val rate = cost?.exchangeRateToKrw ?: if (trade.currency == Currency.USD) macro.usdKrw else 1.0
            val settledOn = gainsByTrade[trade.id]?.settlementDate
                ?: settlementDate(stock.market, marketDate(stock.market, trade.executedAt))
            if (trade.side == OrderSide.BUY) {
                rebuilt = rebuilt.addPurchase(
                    lotId = trade.id,
                    stockId = stock.id,
                    acquiredOn = settledOn,
                    quantity = trade.quantity,
                    purchasePriceKrw = round(trade.grossAmount * rate).toLong(),
                    directPurchaseCostsKrw = round(trade.commission * rate).toLong(),
                )
            } else {
                val gain = gainsByTrade[trade.id]
                rebuilt = rebuilt.sell(
                    stockId = stock.id,
                    soldOn = settledOn,
                    quantity = trade.quantity,
                    grossProceedsKrw = gain?.taxGrossProceedsKrw
                        ?: round(trade.grossAmount * rate).toLong(),
                    directSellingCostsKrw = gain?.taxDirectSellingCostsKrw
                        ?: round((trade.commission + trade.tax) * rate).toLong(),
                ).updatedBook
            }
        }
        return rebuilt
    }

    private fun recalculateAnnualTax(year: Int) {
        if (year !in 2026..2040) return
        val gains = realizedGains.filter { it.settlementDate.year == year }.map { record ->
            RealizedStockGain(
                id = record.tradeId,
                stockId = record.stockId,
                realizedOn = record.settlementDate,
                gainKrw = record.taxGainKrw,
                treatment = record.taxTreatment,
                instrumentTaxClass = if (record.market.isUnitedStates) {
                    ForeignInstrumentTaxClass.US_COMMON_STOCK
                } else {
                    null
                },
            )
        }
        val yearDividends = dividends.filter { gameDate(it.paidAt).year == year }
        val ledger = annualStockTaxCalculator.calculate(
            AnnualStockTaxRequest(
                taxYear = year,
                gains = gains,
                financialIncomeGrossKrw = yearDividends.sumOf { round(it.grossAmountKrw).toLong() },
                foreignTaxPaidKrw = yearDividends
                    .filter { it.currency == Currency.USD }
                    .sumOf { round(it.withholdingTaxKrw).toLong() },
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
        while (days < 2) {
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

    private fun marketSessionAtCurrentTime(market: Market): MarketSession = marketSession(market, currentTime)

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

    private fun orderBook(
        stock: StockDefinition,
        quote: Quote,
        session: MarketSession,
    ): OrderBookSnapshot {
        val tracker = dailyTrackers.getValue(stock.id)
        val liquidity = EventShockCalculator.liquidityMultiplierAt(activeEvents, stock, currentTime)
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
    )

    companion object {
        const val MAX_RECENT_BARS = 2_000
        const val MAX_NEWS_EVENTS = 1_000
        const val BENCHMARK_START = 100.0
        const val BUY_RESERVE_MULTIPLIER = 1.003
        const val FX_SPREAD_RATE = 0.001
        const val FX_MEAN_REVERSION = 0.00025
        const val FX_HOURLY_VOLATILITY = 0.0015
        const val MIN_USD_KRW = 800.0
        const val MAX_USD_KRW = 2_500.0
        const val POLICY_CHANGE_PROBABILITY_PER_HOUR = 1.0 / (24.0 * 120.0)
        const val MARKET_FACTOR_VOLATILITY = 0.0016
        const val SECTOR_FACTOR_VOLATILITY = 0.0010
        const val DIVIDEND_DAY = 15
        const val DIVIDENDS_PER_YEAR = 4.0
        val DIVIDEND_MONTHS = setOf(Month.MARCH, Month.JUNE, Month.SEPTEMBER, Month.DECEMBER)
        const val PRICE_EPSILON = 1e-7
        const val QUANTITY_EPSILON = 1e-7
        const val CASH_EPSILON = 0.01
        const val MACRO_STREAM_ID = 0x4D4143524FL
        const val PRICE_STREAM_ID = 0x5052494345L
        const val BOOK_STREAM_ID = 0x424F4F4BL
        const val EVENT_STREAM_ID = 0x4556454E54L
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun restore(state: SimulatorUiState): SimulatorRuntime? = runCatching {
            SimulatorRuntime(state.options).apply { restoreFrom(state) }
        }.getOrNull()
    }
}
