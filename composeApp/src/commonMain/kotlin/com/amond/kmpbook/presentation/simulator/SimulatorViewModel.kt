package com.amond.kmpbook.presentation.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amond.kmpbook.domain.data.InstrumentCatalogSnapshot
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionRecord
import com.amond.kmpbook.domain.model.game.GamePhase
import com.amond.kmpbook.domain.model.game.Screen
import com.amond.kmpbook.domain.model.game.TurnStep
import com.amond.kmpbook.domain.model.history.HistoricalScenarioPack
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.pricing.PriceBar
import com.amond.kmpbook.domain.model.pricing.PriceBarInterval
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.OrderType
import com.amond.kmpbook.domain.model.trading.TimeInForce
import com.amond.kmpbook.domain.model.venue.MarketSession
import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import com.amond.kmpbook.domain.simulation.market.MarketMicrostructure
import com.amond.kmpbook.domain.simulation.event.DebugEventGuide
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.modding.model.ModCapability
import com.amond.kmpbook.persistence.validation.diagnoseHistoricalScenarioCanonicalConsistency
import com.amond.kmpbook.persistence.validation.diagnoseSimulatorUiStateCanonicalConsistency
import com.amond.kmpbook.persistence.validation.validateHistoricalScenarioBinding
import com.amond.kmpbook.persistence.validation.validateInstrumentCatalogBinding
import com.amond.kmpbook.persistence.validation.validateSimulatorUiStatePersistenceSafety
import com.amond.kmpbook.presentation.trading.OrderRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.math.round
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.TimeSource

class SimulatorViewModel(
    initialCatalog: InstrumentCatalogSnapshot,
    initialOptions: NewGameOptions = NewGameOptions(),
    private val historicalScenario: HistoricalScenarioPack? = null,
) : ViewModel() {
    private var lastOptions: NewGameOptions = initialOptions.withDetachedActiveMods()
    private var lastCatalog: InstrumentCatalogSnapshot = initialCatalog
    private var runtime: SimulatorRuntime = SimulatorRuntime(
        lastOptions,
        lastCatalog,
        historicalScenario,
        startInSetup = true,
    )
    private val _uiState = MutableStateFlow(runtime.snapshot())
    private val _stateChanges = MutableSharedFlow<SimulatorStateChange>(
        replay = MOD_EVENT_REPLAY_CAPACITY,
        extraBufferCapacity = MOD_EVENT_BUFFER_CAPACITY - MOD_EVENT_REPLAY_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var stateChangeSequence: Long = 0L
    private var advanceJob: Job? = null
    private var debugJumpJob: Job? = null
    private var runtimeAccessGate = Mutex()
    private var turnProcessingSequence: Long = 0L
    private val _turnProcessingUiState = MutableStateFlow<TurnProcessingUiState?>(null)

    val uiState: StateFlow<SimulatorUiState> = _uiState.asStateFlow()
    val turnProcessingUiState: StateFlow<TurnProcessingUiState?> =
        _turnProcessingUiState.asStateFlow()
    internal val stateChanges: SharedFlow<SimulatorStateChange> = _stateChanges.asSharedFlow()
    internal val currentStateChangeSequence: Long get() = stateChangeSequence
    val currentState: SimulatorUiState get() = _uiState.value

    /**
     * 저장 상태에는 게임 시작 뒤의 가변 suffix만 유지한다. 선택 종목의 불변 게임 시작 전
     * prefix는 번들에서 필요할 때 투영해 5년 일봉 차트를 제공한다.
     */
    fun historicalDailyPrefixForDisplay(state: SimulatorUiState): List<PriceBar> {
        val scenario = historicalScenario ?: return emptyList()
        val stockId = state.selectedStockId ?: return emptyList()
        val stock = state.stocks.firstOrNull { candidate -> candidate.id == stockId } ?: return emptyList()
        val appliedActions = state.corporateActionLedger.asSequence()
            .filter { action -> action.stockId == stockId && action.effectiveAt <= state.currentTime }
            .sortedWith(
                compareBy<CorporateActionRecord>(CorporateActionRecord::effectiveAt)
                    .thenBy(CorporateActionRecord::accountingSequence),
            )
            .toList()
        return scenario.dailyBarsByInstrument[stockId].orEmpty().mapNotNull { historicalBar ->
            val session = GameCalendar.regularSessionWindow(stock.market, historicalBar.tradingDate)
                ?: return@mapNotNull null
            if (session.closesAt > scenario.definition.gameplayStartsAt) return@mapNotNull null
            val pregameFactor = historicalBar.pregameSplitAdjustedPriceFactor
            var projected = PriceBar(
                stockId = stockId,
                startTime = session.opensAt,
                endTime = session.closesAt,
                step = PriceBarInterval.ONE_DAY,
                open = MarketMicrostructure.roundNearest(stock, historicalBar.open * pregameFactor),
                high = MarketMicrostructure.roundUp(stock, historicalBar.high * pregameFactor),
                low = MarketMicrostructure.roundDown(stock, historicalBar.low * pregameFactor),
                close = MarketMicrostructure.roundNearest(stock, historicalBar.close * pregameFactor),
                volume = round(historicalBar.volume.toDouble() / pregameFactor)
                    .toLong()
                    .coerceAtLeast(0L),
            )
            appliedActions.forEach { action ->
                if (action.effectiveAt <= projected.endTime) return@forEach
                fun adjustedPrice(value: Double): Double = MarketMicrostructure.roundNearest(
                    stock,
                    (value / action.quantityMultiplier).coerceAtLeast(
                        MarketMicrostructure.minimumPrice(stock.market),
                    ),
                )
                projected = projected.copy(
                    open = adjustedPrice(projected.open),
                    high = adjustedPrice(projected.high),
                    low = adjustedPrice(projected.low),
                    close = adjustedPrice(projected.close),
                    volume = round(projected.volume.toDouble() * action.quantityMultiplier)
                        .toLong()
                        .coerceAtLeast(0L),
                )
            }
            projected
        }
    }

    /** Expensive deterministic replay reserved for explicit debug diagnostics. */
    private fun diagnoseAgainstActiveContent(
        state: SimulatorUiState,
        catalog: InstrumentCatalogSnapshot,
    ): String? = validateSimulatorUiStatePersistenceSafety(state)
        ?: validateInstrumentCatalogBinding(state, catalog)
        ?: validateHistoricalScenarioBinding(state, historicalScenario)
        ?: diagnoseSimulatorUiStateCanonicalConsistency(state, catalog)
        ?: historicalScenario?.let { scenario ->
            diagnoseHistoricalScenarioCanonicalConsistency(state, scenario)
        }

    suspend fun newGame(
        options: NewGameOptions = NewGameOptions(),
        catalog: InstrumentCatalogSnapshot = lastCatalog,
    ): String? {
        val detachedOptions = options.withDetachedActiveMods()
        val (candidateRuntime, candidateSnapshot) = runCatching {
            withContext(Dispatchers.Default) {
                val candidate = SimulatorRuntime(detachedOptions, catalog, historicalScenario)
                candidate to candidate.snapshot()
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            val detail = error.message?.take(240)?.takeIf(String::isNotBlank) ?: "알 수 없는 오류"
            return "새 게임의 종목 방법론을 초기화하지 못했습니다: $detail"
        }
        return withContext(Dispatchers.Main.immediate) {
            withRuntimeAccess(blocked = { "다른 게임 작업이 끝난 뒤 새 게임을 시작해 주세요." }) {
                cancelBackgroundCommands()
                lastOptions = detachedOptions
                lastCatalog = catalog
                runtime = candidateRuntime
                runtimeAccessGate = Mutex()
                publishSnapshot(candidateSnapshot, SimulatorStateChangeKind.GAME_STARTED)
                null
            }
        }
    }

    suspend fun resetGame(): Boolean {
        val options = lastOptions
        val catalog = lastCatalog
        val (resetRuntime, resetSnapshot) = withContext(Dispatchers.Default) {
            val candidate = SimulatorRuntime(
                options,
                catalog,
                historicalScenario,
                startInSetup = true,
            )
            candidate to candidate.snapshot()
        }
        return withContext(Dispatchers.Main.immediate) {
            withRuntimeAccess(blocked = { false }) {
                cancelBackgroundCommands()
                runtime = resetRuntime
                runtimeAccessGate = Mutex()
                publishSnapshot(resetSnapshot, SimulatorStateChangeKind.STATE_CHANGED)
                true
            }
        }
    }

    /** 저장 계층이 필수 구조를 확인한 상태를 현재 카탈로그·시나리오에 결박해 복원한다. */
    suspend fun restoreGame(
        state: SimulatorUiState,
        catalog: InstrumentCatalogSnapshot,
    ): Boolean {
        val ironmanBlocked = withContext(Dispatchers.Main.immediate) {
            val current = _uiState.value
            if (
                current.options.ironmanMode &&
                current.phase in setOf(GamePhase.PLAYING, GamePhase.PAUSED)
            ) {
                _uiState.value = current.copy(lastMessage = "철인 모드에서는 게임을 불러올 수 없습니다.")
                true
            } else {
                false
            }
        }
        if (ironmanBlocked) {
            return false
        }
        val candidate = state.withDetachedActiveMods()
        val (restored, restoredSnapshot, validationError) = withContext(Dispatchers.Default) {
            // Storage has already checked the untrusted wire payload. Rebind it once here to the
            // catalog and historical scenario selected after resolving the save's active mods.
            val candidateError = validateInstrumentCatalogBinding(candidate, catalog)
                ?: validateHistoricalScenarioBinding(candidate, historicalScenario)
            val preparedRuntime = if (candidateError == null) {
                SimulatorRuntime.restorePreparedState(candidate, catalog, historicalScenario)
            } else {
                null
            }
            if (preparedRuntime == null) {
                Triple(null, null, candidateError)
            } else {
                Triple(preparedRuntime, preparedRuntime.snapshot(), null)
            }
        }
        if (restored == null || restoredSnapshot == null) {
            withContext(Dispatchers.Main.immediate) {
                _uiState.value = _uiState.value.copy(
                    lastMessage = validationError?.let { problem ->
                        "저장 데이터가 유효하지 않습니다: $problem"
                    } ?: "저장 데이터를 복원할 수 없습니다.",
                )
            }
            return false
        }
        return withContext(Dispatchers.Main.immediate) {
            withRuntimeAccess(blocked = { false }) {
                val previousState = _uiState.value
                cancelBackgroundCommands()
                runtime = restored
                runtimeAccessGate = Mutex()
                publishSnapshot(
                    restoredSnapshot,
                    if (candidate.startsCampaignComparedWith(previousState)) {
                        SimulatorStateChangeKind.GAME_STARTED
                    } else {
                        SimulatorStateChangeKind.STATE_CHANGED
                    },
                )
                lastOptions = restoredSnapshot.options.withDetachedActiveMods()
                lastCatalog = catalog
                true
            }
        }
    }

    fun selectScreen(screen: Screen) = withRuntimeAccess(blocked = {}) {
        runtime.selectScreen(screen)
        publishSnapshot(
            _uiState.value.copy(screen = screen, lastMessage = null),
            SimulatorStateChangeKind.STATE_CHANGED,
        )
    }

    fun selectStock(stockId: String): Boolean = withRuntimeAccess(blocked = { false }) {
        val current = _uiState.value
        val result = runtime.selectStock(stockId)
        if (result) {
            val marketUi = runtime.currentMarketUiProjection()
            publishSnapshot(
                current.copy(
                    selectedStockId = stockId,
                    quotes = marketUi.quotes,
                    selectedOrderBook = marketUi.selectedOrderBook,
                    marketSessions = marketUi.marketSessions,
                    lastMessage = null,
                ),
                SimulatorStateChangeKind.STATE_CHANGED,
            )
        } else {
            publishSnapshot(
                current.copy(lastMessage = "존재하지 않는 종목입니다."),
                SimulatorStateChangeKind.STATE_CHANGED,
            )
        }
        result
    }

    fun selectTurnStep(step: TurnStep) = withRuntimeAccess(blocked = {}) {
        runtime.selectTurnStep(step)
        publishSnapshot(
            _uiState.value.copy(selectedTurnStep = step, lastMessage = null),
            SimulatorStateChangeKind.STATE_CHANGED,
        )
    }

    fun advance(step: TurnStep = _uiState.value.selectedTurnStep) {
        if (_uiState.value.phase != GamePhase.PLAYING) {
            withRuntimeAccess(blocked = {}) {
                val message = runtime.rejectAdvanceForCurrentPhase()
                publishSnapshot(
                    _uiState.value.copy(lastMessage = message),
                    SimulatorStateChangeKind.STATE_CHANGED,
                )
            }
            return
        }
        val commandGate = runtimeAccessGate
        if (!commandGate.tryLock()) return
        val processingSequence = ++turnProcessingSequence
        var completionOwnsGate = false
        try {
            val advancingRuntime = runtime
            val commandCatalog = lastCatalog
            val publishedState = _uiState.value
            val previousTurn = publishedState.turn
            val totalHours = minOf(
                step.hours.toLong(),
                GameCalendar.remainingHours(publishedState.currentTime),
            ).coerceAtLeast(1L).toInt()
            _turnProcessingUiState.value = TurnProcessingUiState(
                step = step,
                startedAt = publishedState.currentTime,
                targetTime = GameCalendar.advanceHours(publishedState.currentTime, totalHours),
                currentTime = publishedState.currentTime,
                completedHours = 0,
                totalHours = totalHours,
                stage = processingStage(publishedState, isComplete = false),
                latestActivity = "거래 세션과 대기 주문을 확인하고 있습니다.",
                marketSessionSummary = marketSessionSummary(publishedState),
            )
            publishSnapshot(
                publishedState.copy(isAdvancing = true, lastMessage = null),
                SimulatorStateChangeKind.STATE_CHANGED,
            )
            val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
                val commandJob = currentCoroutineContext()[Job]
                var preCommandState: SimulatorUiState? = null
                try {
                    val advancedState = withContext(Dispatchers.Default) {
                        // Capture the rollback boundary away from Main. Do not trust the publicly
                        // exposed UI collection views as a canonical persistence checkpoint.
                        preCommandState = advancingRuntime.snapshot().withDetachedActiveMods()
                        currentCoroutineContext().ensureActive()
                        // Runtime owns the canonical atomic-hour transaction. Invoking that same
                        // transaction once per hour exposes real progress without allowing the UI
                        // to observe or cancel a partially committed simulated hour.
                        advanceRuntimeWithProgress(
                            advancingRuntime = advancingRuntime,
                            preCommandState = checkNotNull(preCommandState),
                            totalHours = totalHours,
                            processingSequence = processingSequence,
                        )
                    }
                    if (runtime === advancingRuntime) {
                        val completedHours = (advancedState.turn - previousTurn)
                            .coerceIn(0L, totalHours.toLong())
                            .toInt()
                        val presentedState = if (
                            advancedState.phase == GamePhase.PLAYING && completedHours > 0
                        ) {
                            advancedState.copy(lastMessage = "${completedHours}시간 진행했습니다.")
                        } else {
                            advancedState
                        }
                        if (presentedState.turn > previousTurn) {
                            publishSnapshot(
                                presentedState,
                                SimulatorStateChangeKind.TURN_COMPLETED,
                                previousTurn = previousTurn,
                            )
                        } else {
                            publishSnapshot(presentedState, SimulatorStateChangeKind.STATE_CHANGED)
                        }
                        when (presentedState.phase) {
                            GamePhase.SETTLEMENT -> publishSnapshot(
                                presentedState,
                                SimulatorStateChangeKind.SETTLEMENT_STARTED,
                            )
                            GamePhase.FINISHED -> publishSnapshot(
                                presentedState,
                                SimulatorStateChangeKind.GAME_ENDED,
                            )
                            else -> Unit
                        }
                    }
                } catch (cancelled: CancellationException) {
                    if (runtime === advancingRuntime) {
                        val checkpoint = preCommandState
                            ?: withContext(NonCancellable + Dispatchers.Default) {
                                advancingRuntime.snapshot().withDetachedActiveMods()
                            }
                        rollbackCommand(
                            advancingRuntime,
                            checkpoint,
                            commandCatalog,
                            "게임 진행이 취소되어 시작 전 상태로 복원했습니다.",
                        )
                    }
                    throw cancelled
                } catch (error: Throwable) {
                    if (runtime === advancingRuntime) {
                        val checkpoint = preCommandState
                            ?: withContext(NonCancellable + Dispatchers.Default) {
                                advancingRuntime.snapshot().withDetachedActiveMods()
                            }
                        rollbackCommand(
                            advancingRuntime,
                            checkpoint,
                            commandCatalog,
                            "게임 진행에 실패해 시작 전 상태로 복원했습니다: ${error.conciseDetail()}",
                        )
                    }
                } finally {
                    if (advanceJob === commandJob) {
                        advanceJob = null
                        if (turnProcessingSequence == processingSequence) {
                            _turnProcessingUiState.value = null
                        }
                    }
                }
            }
            advanceJob = job
            job.invokeOnCompletion {
                if (advanceJob === job) {
                    advanceJob = null
                    if (turnProcessingSequence == processingSequence) {
                        _turnProcessingUiState.value = null
                    }
                }
                commandGate.unlock()
            }
            completionOwnsGate = true
            job.start()
        } finally {
            if (!completionOwnsGate) {
                if (turnProcessingSequence == processingSequence) {
                    _turnProcessingUiState.value = null
                }
                commandGate.unlock()
            }
        }
    }

    private suspend fun advanceRuntimeWithProgress(
        advancingRuntime: SimulatorRuntime,
        preCommandState: SimulatorUiState,
        totalHours: Int,
        processingSequence: Long,
    ): SimulatorUiState {
        val startingTurn = preCommandState.turn
        var lastNewsAdditionCount = advancingRuntime.turnProgressNewsAdditionCount
        var lastTradeCount = preCommandState.trades.size
        var recentEventTitle: String? = null
        var lastPublishedDate = GameCalendar.campaignDate(preCommandState.currentTime)
        var lastPublishedAt = TimeSource.Monotonic.markNow()

        while (
            advancingRuntime.phase == GamePhase.PLAYING &&
            advancingRuntime.turn - startingTurn < totalHours
        ) {
            currentCoroutineContext().ensureActive()
            advancingRuntime.advance(TurnStep.ONE_HOUR)
            currentCoroutineContext().ensureActive()

            val completedHours = (advancingRuntime.turn - startingTurn)
                .coerceIn(0L, totalHours.toLong())
                .toInt()
            val currentDate = GameCalendar.campaignDate(advancingRuntime.currentTime)
            val crossedDateBoundary = currentDate != lastPublishedDate
            val shouldPublish = completedHours <= 1 ||
                completedHours >= totalHours ||
                advancingRuntime.phase != GamePhase.PLAYING ||
                crossedDateBoundary ||
                lastPublishedAt.elapsedNow() >= TURN_PROCESSING_UPDATE_INTERVAL

            if (shouldPublish) {
                val newsAdditionCount = advancingRuntime.turnProgressNewsAdditionCount
                val tradeCount = advancingRuntime.turnProgressTradeCount
                val newEventCount = (newsAdditionCount - lastNewsAdditionCount)
                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
                val newTradeCount = (tradeCount - lastTradeCount).coerceAtLeast(0)
                if (newEventCount > 0) {
                    recentEventTitle = advancingRuntime.latestTurnProgressEventTitle ?: recentEventTitle
                }
                val sessions = advancingRuntime.turnProgressMarketSessions()
                val isComplete = completedHours >= totalHours || advancingRuntime.phase != GamePhase.PLAYING
                publishTurnProcessingProgress(
                    currentTime = advancingRuntime.currentTime,
                    phase = advancingRuntime.phase,
                    marketSessions = sessions,
                    completedHours = completedHours,
                    isComplete = isComplete,
                    latestActivity = processingActivity(
                        newEventCount = newEventCount,
                        newTradeCount = newTradeCount,
                        crossedDateBoundary = crossedDateBoundary,
                        isComplete = isComplete,
                    ),
                    recentEventTitle = recentEventTitle,
                    processingSequence = processingSequence,
                )
                lastNewsAdditionCount = newsAdditionCount
                lastTradeCount = tradeCount
                lastPublishedDate = currentDate
                lastPublishedAt = TimeSource.Monotonic.markNow()
            }
        }

        val finalSnapshot = advancingRuntime.snapshot()
        val finalCompletedHours = (finalSnapshot.turn - startingTurn)
            .coerceIn(0L, totalHours.toLong())
            .toInt()
        if (_turnProcessingUiState.value?.completedHours != finalCompletedHours) {
            val newEventCount = (
                advancingRuntime.turnProgressNewsAdditionCount - lastNewsAdditionCount
            ).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            val newTradeCount = (finalSnapshot.trades.size - lastTradeCount).coerceAtLeast(0)
            if (newEventCount > 0) {
                recentEventTitle = advancingRuntime.latestTurnProgressEventTitle ?: recentEventTitle
            }
            publishTurnProcessingProgress(
                currentTime = finalSnapshot.currentTime,
                phase = finalSnapshot.phase,
                marketSessions = finalSnapshot.marketSessions,
                completedHours = finalCompletedHours,
                isComplete = true,
                latestActivity = processingActivity(
                    newEventCount = newEventCount,
                    newTradeCount = newTradeCount,
                    crossedDateBoundary = GameCalendar.campaignDate(finalSnapshot.currentTime) != lastPublishedDate,
                    isComplete = true,
                ),
                recentEventTitle = recentEventTitle,
                processingSequence = processingSequence,
            )
        }
        return finalSnapshot
    }

    private fun publishTurnProcessingProgress(
        currentTime: Instant,
        phase: GamePhase,
        marketSessions: Map<Market, MarketSession>,
        completedHours: Int,
        isComplete: Boolean,
        latestActivity: String,
        recentEventTitle: String?,
        processingSequence: Long,
    ) {
        _turnProcessingUiState.update { current ->
            if (turnProcessingSequence != processingSequence || current == null) {
                current
            } else if (current.cancellationRequested) {
                current.copy(
                    currentTime = currentTime,
                    completedHours = completedHours.coerceIn(0, current.totalHours),
                    marketSessionSummary = marketSessionSummary(marketSessions),
                )
            } else {
                current.copy(
                    currentTime = currentTime,
                    completedHours = completedHours.coerceIn(0, current.totalHours),
                    stage = processingStage(phase, marketSessions, isComplete),
                    latestActivity = latestActivity,
                    recentEventTitle = recentEventTitle,
                    marketSessionSummary = marketSessionSummary(marketSessions),
                )
            }
        }
    }

    private fun processingStage(snapshot: SimulatorUiState, isComplete: Boolean): String =
        processingStage(snapshot.phase, snapshot.marketSessions, isComplete)

    private fun processingStage(
        phase: GamePhase,
        marketSessions: Map<Market, MarketSession>,
        isComplete: Boolean,
    ): String {
        if (isComplete) {
            return if (phase == GamePhase.PLAYING) {
                "선택 구간 · 시장 장부 확정"
            } else {
                "캠페인 마감 · 결산 이관"
            }
        }
        val krxSession = koreanMarketSession(marketSessions)
        val usSession = unitedStatesMarketSession(marketSessions)
        return when {
            krxSession == MarketSession.REGULAR -> "KRX 정규장 · 시세와 주문 체결"
            usSession == MarketSession.REGULAR -> "미국 정규장 · 시세와 주문 체결"
            usSession == MarketSession.PRE_MARKET -> "미국 프리마켓 · 대기 주문 확인"
            usSession == MarketSession.AFTER_HOURS -> "미국 애프터마켓 · 이벤트 반영"
            else -> "폐장 시간 · 뉴스와 원장 정리"
        }
    }

    private fun processingActivity(
        newEventCount: Int,
        newTradeCount: Int,
        crossedDateBoundary: Boolean,
        isComplete: Boolean,
    ): String = when {
        newEventCount > 0 && newTradeCount > 0 ->
            "시장 이벤트 ${newEventCount}건과 주문 체결 ${newTradeCount}건을 반영했습니다."
        newEventCount > 0 -> "새 시장 이벤트 ${newEventCount}건을 반영했습니다."
        newTradeCount > 0 -> "주문 체결 ${newTradeCount}건을 원장에 기록했습니다."
        isComplete -> "선택한 시간 구간의 시세와 원장을 확정했습니다."
        crossedDateBoundary -> "일일 장부와 포트폴리오 기준가를 마감했습니다."
        else -> "시세·지수·대기 주문을 시간 순서대로 반영했습니다."
    }

    private fun marketSessionSummary(snapshot: SimulatorUiState): String =
        marketSessionSummary(snapshot.marketSessions)

    private fun marketSessionSummary(marketSessions: Map<Market, MarketSession>): String =
        "KRX ${koreanMarketSession(marketSessions).displayName}  ·  " +
            "US ${unitedStatesMarketSession(marketSessions).displayName}"

    private fun koreanMarketSession(marketSessions: Map<Market, MarketSession>): MarketSession =
        marketSessions[Market.KOSPI]
            ?: marketSessions[Market.KOSDAQ]
            ?: MarketSession.CLOSED

    private fun unitedStatesMarketSession(marketSessions: Map<Market, MarketSession>): MarketSession =
        marketSessions[Market.NASDAQ]
            ?: marketSessions[Market.NYSE]
            ?: MarketSession.CLOSED

    /** Requests cancellation at the next atomic simulated-hour boundary. */
    fun cancelAdvance(): Boolean {
        val jobs = listOfNotNull(advanceJob, debugJumpJob).filter(Job::isActive)
        if (jobs.isEmpty()) return false
        _uiState.value = _uiState.value.copy(
            lastMessage = "현재 시간 계산이 끝나는 즉시 진행을 취소하고 이전 상태로 복원합니다.",
        )
        _turnProcessingUiState.update { current ->
            current?.copy(
                stage = "취소 경계 대기",
                latestActivity = "현재 1시간 계산을 마치면 시작 전 상태로 복원합니다.",
                cancellationRequested = true,
            )
        }
        jobs.forEach(Job::cancel)
        return true
    }

    fun placeOrder(request: OrderRequest): Boolean = withRuntimeAccess(blocked = { false }) {
        val result = runtime.placeOrder(request)
        publish()
        result
    }

    fun placeOrder(
        stockId: String,
        side: OrderSide,
        type: OrderType,
        quantity: Double,
        limitPrice: Double? = null,
        timeInForce: TimeInForce = TimeInForce.DAY,
    ): Boolean = placeOrder(
        OrderRequest(
            stockId = stockId,
            side = side,
            type = type,
            quantity = quantity,
            limitPrice = limitPrice,
            timeInForce = timeInForce,
        ),
    )

    fun cancelOrder(orderId: String): Boolean = withRuntimeAccess(blocked = { false }) {
        val result = runtime.cancelOrder(orderId)
        publish()
        result
    }

    fun exchange(from: Currency, to: Currency, amount: Double): Boolean =
        withRuntimeAccess(blocked = { false }) {
        val result = runtime.exchange(from, to, amount)
        publish()
        result
    }

    fun setAutoExchange(enabled: Boolean) = withRuntimeAccess(blocked = {}) {
        runtime.setAutoExchange(enabled)
        val current = _uiState.value
        publishSnapshot(
            current.copy(
                options = current.options.copy(autoExchange = enabled),
                lastMessage = if (enabled) "자동 환전을 켰습니다." else "자동 환전을 껐습니다.",
            ),
            SimulatorStateChangeKind.STATE_CHANGED,
        )
    }

    fun setExternalMarketForces(forces: ExternalMarketForces) = withRuntimeAccess(blocked = {}) {
        runtime.setExternalMarketForces(forces)
        val current = _uiState.value
        publishSnapshot(
            if (current.options.ironmanMode) {
                current.copy(lastMessage = "철인 모드에서는 시장 동역학을 변경할 수 없습니다.")
            } else {
                current.copy(
                    externalMarketForcesTarget = forces.copy(),
                    lastMessage = "시장 환경 목표를 변경했습니다. 실제 시장에는 시간에 따라 반영됩니다.",
                )
            },
            SimulatorStateChangeKind.STATE_CHANGED,
        )
    }

    fun markEventRead(eventId: String) = withRuntimeAccess(blocked = {}) {
        runtime.markEventRead(eventId)
        val current = _uiState.value
        publishSnapshot(
            current.copy(
                readEventIds = if (current.newsEvents.any { it.id == eventId }) {
                    current.readEventIds + eventId
                } else {
                    current.readEventIds
                },
            ),
            SimulatorStateChangeKind.STATE_CHANGED,
        )
    }

    fun markStockNewsListViewed(stockId: String, eventIds: Set<String>) =
        withRuntimeAccess(blocked = {}) {
        val current = _uiState.value
        val readLedgerUpdate = runtime.markStockNewsListViewed(stockId, eventIds)
        val updatedReadEventIds = when {
            readLedgerUpdate == null -> current.readStockNewsEventIds
            readLedgerUpdate.isEmpty() -> current.readStockNewsEventIds - stockId
            else -> current.readStockNewsEventIds + readLedgerUpdate
        }
        publishSnapshot(
            current.copy(readStockNewsEventIds = updatedReadEventIds),
            SimulatorStateChangeKind.STATE_CHANGED,
        )
    }

    fun markAllEventsRead() = withRuntimeAccess(blocked = {}) {
        runtime.markAllEventsRead()
        val current = _uiState.value
        publishSnapshot(
            current.copy(readEventIds = current.readEventIds + current.newsEvents.map { it.id }),
            SimulatorStateChangeKind.STATE_CHANGED,
        )
    }

    fun toggleWatchlist(stockId: String): Boolean = withRuntimeAccess(blocked = { false }) {
        val current = _uiState.value
        val added = runtime.toggleWatchlist(stockId)
        val stockExists = current.stocks.any { it.id == stockId }
        val updatedWatchlist = when {
            !stockExists -> current.watchlistedStockIds
            added -> current.watchlistedStockIds + stockId
            else -> current.watchlistedStockIds - stockId
        }
        publishSnapshot(
            current.copy(
                watchlistedStockIds = updatedWatchlist,
                lastMessage = if (!stockExists) {
                    "존재하지 않는 종목입니다."
                } else if (added) {
                    "관심 종목에 추가했습니다."
                } else {
                    "관심 종목에서 해제했습니다."
                },
            ),
            SimulatorStateChangeKind.STATE_CHANGED,
        )
        added
    }

    fun clearMessage() {
        if (_uiState.value.isAdvancing) {
            _uiState.value = _uiState.value.copy(lastMessage = null)
            return
        }
        withRuntimeAccess(blocked = {}) {
            runtime.clearMessage()
            publishSnapshot(
                _uiState.value.copy(lastMessage = null),
                SimulatorStateChangeKind.STATE_CHANGED,
            )
        }
    }

    fun pause() = withRuntimeAccess(blocked = {}) {
        val current = _uiState.value
        runtime.pause()
        publishSnapshot(
            current.copy(
                phase = if (current.phase == GamePhase.PLAYING) GamePhase.PAUSED else current.phase,
            ),
            SimulatorStateChangeKind.STATE_CHANGED,
        )
    }

    fun resume() = withRuntimeAccess(blocked = {}) {
        val current = _uiState.value
        runtime.resume()
        publishSnapshot(
            current.copy(
                phase = if (current.phase == GamePhase.PAUSED) GamePhase.PLAYING else current.phase,
            ),
            SimulatorStateChangeKind.STATE_CHANGED,
        )
    }

    fun finishSettlement() = withRuntimeAccess(blocked = {}) {
        val current = _uiState.value
        runtime.finishSettlement()
        val snapshot = if (current.phase == GamePhase.SETTLEMENT) {
            current.copy(
                phase = GamePhase.FINISHED,
                lastMessage = "최종 정산을 완료했습니다.",
            )
        } else {
            current
        }
        publishSnapshot(
            snapshot = snapshot,
            kind = if (current.phase != GamePhase.FINISHED && snapshot.phase == GamePhase.FINISHED) {
                SimulatorStateChangeKind.GAME_ENDED
            } else {
                SimulatorStateChangeKind.STATE_CHANGED
            },
        )
    }

    internal fun isDebugConsoleEnabled(): Boolean = currentState.options.activeMods.any { activeMod ->
        activeMod.executableFingerprint != null &&
            ModCapability.DEBUG_CONSOLE in activeMod.grantedCapabilities
    }

    internal fun debugSetInstrumentPrice(
        stockId: String,
        amount: Double,
        inputCurrency: DebugPriceCurrency,
    ): DebugRuntimeResult = debugMutate {
        runtime.debugSetInstrumentPrice(stockId, amount, inputCurrency)
    }

    internal fun debugChangeInstrumentPrice(stockId: String, percent: Double): DebugRuntimeResult =
        debugMutate { runtime.debugChangeInstrumentPrice(stockId, percent) }

    internal fun debugSetCash(currency: Currency, amount: Double): DebugRuntimeResult =
        debugMutate { runtime.debugSetCash(currency, amount) }

    internal fun debugAddCash(currency: Currency, delta: Double): DebugRuntimeResult =
        debugMutate { runtime.debugAddCash(currency, delta) }

    internal fun debugSetUsdKrw(rate: Double): DebugRuntimeResult =
        debugMutate { runtime.debugSetUsdKrw(rate) }

    internal fun debugSetAutoExchange(enabled: Boolean): DebugRuntimeResult =
        debugMutate { runtime.debugSetAutoExchange(enabled) }

    internal fun debugSetIronman(enabled: Boolean): DebugRuntimeResult =
        debugMutate { runtime.debugSetIronman(enabled) }

    internal fun debugSetFractionalTrading(enabled: Boolean): DebugRuntimeResult =
        debugMutate { runtime.debugSetFractionalTrading(enabled) }

    internal fun debugSetExternalMarketForces(forces: ExternalMarketForces): DebugRuntimeResult =
        debugMutate { runtime.debugSetExternalMarketForces(forces) }

    internal fun debugCancelAllOrders(): DebugRuntimeResult =
        debugMutate { runtime.debugCancelAllOrders() }

    internal fun debugEventGuide(query: String?): List<DebugEventGuide> =
        withRuntimeAccess(blocked = { emptyList() }) {
            if (
                debugAccessFailure() == null &&
                !currentState.isAdvancing &&
                debugJumpJob?.isActive != true
            ) {
                runtime.debugEventGuide(query)
            } else {
                emptyList()
            }
        }

    internal fun debugTriggerEvent(templateId: String, target: String?): DebugRuntimeResult =
        debugMutate { runtime.debugTriggerEvent(templateId, target) }

    internal fun debugPause(): DebugRuntimeResult = debugMutate {
        if (runtime.phase != GamePhase.PLAYING) {
            DebugRuntimeResult.failure("진행 중인 게임에서만 일시 정지할 수 있습니다.")
        } else {
            runtime.pause()
            DebugRuntimeResult.success("게임을 일시 정지했습니다.")
        }
    }

    internal fun debugResume(): DebugRuntimeResult = debugMutate {
        if (runtime.phase != GamePhase.PAUSED) {
            DebugRuntimeResult.failure("일시 정지된 게임에서만 재개할 수 있습니다.")
        } else {
            runtime.resume()
            DebugRuntimeResult.success("게임을 재개했습니다.")
        }
    }

    internal fun debugValidationStatus(): DebugRuntimeResult {
        val problem = diagnoseAgainstActiveContent(currentState, lastCatalog)
        return if (problem == null) {
            DebugRuntimeResult.success("현재 게임 상태가 정밀 일관성 진단을 통과했습니다.")
        } else {
            DebugRuntimeResult.failure("현재 게임 상태가 유효하지 않습니다: $problem")
        }
    }

    internal fun debugStartTurnJump(
        targetTurn: Long,
        resetForBackwardJump: Boolean,
        finishSettlement: Boolean = false,
    ): DebugRuntimeResult {
        debugAccessFailure()?.let { return it }
        if (debugJumpJob?.isActive == true || currentState.isAdvancing) {
            return DebugRuntimeResult.failure("이미 게임 진행 작업이 실행 중입니다. 'turn cancel'로 취소할 수 있습니다.")
        }
        val maxTurn = GameCalendar.turnAt(GameCalendar.endInstant)
        if (targetTurn !in 0L..maxTurn) {
            return DebugRuntimeResult.failure("목표 턴은 0..$maxTurn 범위여야 합니다.")
        }
        val commandGate = runtimeAccessGate
        if (!commandGate.tryLock()) {
            return DebugRuntimeResult.failure("이미 게임 진행 작업이 실행 중입니다. 'turn cancel'로 취소할 수 있습니다.")
        }
        var backgroundOwnsGate = false
        try {
            val originalRuntime = runtime
            // Debug commands are rare and may mutate many fields. Use a private Runtime snapshot
            // so rollback cannot be influenced by externally held UI collection views.
            val originalState = runtime.snapshot().withDetachedActiveMods()
            val commandCatalog = lastCatalog
            val isBackward = targetTurn < originalState.turn
            if (isBackward && !resetForBackwardJump) {
                return DebugRuntimeResult.failure(
                    "과거 턴으로 이동하면 현재 진행을 잃습니다. '--reset'을 명시해 주세요.",
                )
            }
            if (targetTurn == originalState.turn && !finishSettlement) {
                return DebugRuntimeResult.success("이미 $targetTurn 턴입니다.", targetTurn.toString())
            }

            val workingRuntime = if (isBackward) {
                SimulatorRuntime(originalState.options.withDetachedActiveMods(), lastCatalog)
            } else {
                originalRuntime
            }
            val restorePause = originalState.phase == GamePhase.PAUSED
            if (workingRuntime.phase == GamePhase.PAUSED) workingRuntime.resume()
            publishSnapshot(
                originalState.copy(
                    isAdvancing = true,
                    lastMessage = "디버그 콘솔이 $targetTurn 턴으로 이동하고 있습니다.",
                ),
                SimulatorStateChangeKind.STATE_CHANGED,
            )

            val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
                val commandJob = currentCoroutineContext()[Job]
                var completed = false
                var cancelled = false
                var failure: Throwable? = null
                var settlementSnapshot: SimulatorUiState? = null
                try {
                    withContext(Dispatchers.Default) {
                        while (
                            workingRuntime.turn < targetTurn &&
                            workingRuntime.phase == GamePhase.PLAYING
                        ) {
                            currentCoroutineContext().ensureActive()
                            val remainingTurns = targetTurn - workingRuntime.turn
                            val step = TurnStep.entries.lastOrNull { candidate ->
                                candidate.hours.toLong() <= remainingTurns
                            } ?: TurnStep.ONE_HOUR
                            workingRuntime.advance(step)
                        }
                        currentCoroutineContext().ensureActive()
                        if (workingRuntime.phase == GamePhase.SETTLEMENT) {
                            settlementSnapshot = workingRuntime.snapshot()
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    if (finishSettlement && workingRuntime.phase == GamePhase.SETTLEMENT) {
                        workingRuntime.finishSettlement()
                    }
                    completed = true
                } catch (_: CancellationException) {
                    cancelled = true
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    // A command that throws or is cancelled may have mutated several hourly
                    // states. Rebuild from the immutable command boundary; a concurrently
                    // replaced Runtime always wins.
                    if (runtime === originalRuntime && completed) {
                        runtime = workingRuntime
                        if (restorePause && runtime.phase == GamePhase.PLAYING) runtime.pause()
                        val snapshot = runtime.snapshot().copy(
                            lastMessage = "디버그 턴 이동을 완료했습니다: ${runtime.turn}턴.",
                        )
                        val settlement = when {
                            settlementSnapshot != null ->
                                settlementSnapshot.copy(lastMessage = snapshot.lastMessage)
                            snapshot.phase == GamePhase.SETTLEMENT -> snapshot
                            else -> null
                        }
                        if (settlement != null) {
                            if (settlement.turn > originalState.turn) {
                                publishSnapshot(
                                    settlement,
                                    SimulatorStateChangeKind.TURN_COMPLETED,
                                    previousTurn = originalState.turn,
                                )
                            }
                            publishSnapshot(
                                settlement,
                                SimulatorStateChangeKind.SETTLEMENT_STARTED,
                            )
                            if (snapshot.phase == GamePhase.FINISHED) {
                                publishSnapshot(snapshot, SimulatorStateChangeKind.GAME_ENDED)
                            }
                        } else {
                            val kind = when {
                                isBackward -> SimulatorStateChangeKind.GAME_STARTED
                                snapshot.turn > originalState.turn ->
                                    SimulatorStateChangeKind.TURN_COMPLETED
                                else -> SimulatorStateChangeKind.STATE_CHANGED
                            }
                            publishSnapshot(
                                snapshot,
                                kind,
                                previousTurn = originalState.turn.takeIf {
                                    kind == SimulatorStateChangeKind.TURN_COMPLETED
                                },
                            )
                        }
                        lastOptions = snapshot.options.withDetachedActiveMods()
                    } else if (runtime === originalRuntime) {
                        val message = if (cancelled) {
                            "디버그 턴 이동을 취소해 시작 전 상태로 복원했습니다."
                        } else {
                            "디버그 턴 이동에 실패해 시작 전 상태로 복원했습니다: " +
                                requireNotNull(failure).conciseDetail()
                        }
                        rollbackCommand(originalRuntime, originalState, commandCatalog, message)
                    }
                    if (debugJumpJob === commandJob) debugJumpJob = null
                }
            }
            debugJumpJob = job
            job.invokeOnCompletion {
                if (debugJumpJob === job) debugJumpJob = null
                commandGate.unlock()
            }
            backgroundOwnsGate = true
            job.start()
            return DebugRuntimeResult.success(
                message = "$targetTurn 턴 이동을 시작했습니다. 계산 중에도 콘솔에서 'turn cancel'을 사용할 수 있습니다.",
                value = targetTurn.toString(),
            )
        } finally {
            if (!backgroundOwnsGate) commandGate.unlock()
        }
    }

    internal fun debugCancelTurnJump(): DebugRuntimeResult {
        debugAccessFailure()?.let { return it }
        val job = debugJumpJob
        return if (job?.isActive == true) {
            job.cancel()
            DebugRuntimeResult.success("턴 이동 취소를 요청했습니다.")
        } else {
            DebugRuntimeResult.failure("취소할 턴 이동 작업이 없습니다.")
        }
    }

    private fun debugMutate(block: () -> DebugRuntimeResult): DebugRuntimeResult =
        withRuntimeAccess(
            blocked = {
                DebugRuntimeResult.failure("게임 진행 계산 중에는 상태를 변경할 수 없습니다.")
            },
        ) {
            debugAccessFailure()?.let { return@withRuntimeAccess it }
            if (currentState.isAdvancing || debugJumpJob?.isActive == true) {
                return@withRuntimeAccess DebugRuntimeResult.failure(
                    "게임 진행 계산 중에는 상태를 변경할 수 없습니다.",
                )
            }
            val before = runtime.snapshot().withDetachedActiveMods()
            val result = block()
            if (!result.success) {
                publish()
                return@withRuntimeAccess result
            }
            val candidate = runtime.snapshot().withDetachedActiveMods()
            val problem = diagnoseAgainstActiveContent(candidate, lastCatalog)
            if (problem != null) {
                runtime = requireNotNull(
                    SimulatorRuntime.restorePreparedState(before, lastCatalog, historicalScenario),
                )
                publish()
                return@withRuntimeAccess DebugRuntimeResult.failure(
                    "변경이 게임 불변식을 위반해 되돌렸습니다: $problem",
                )
            }
            lastOptions = candidate.options.withDetachedActiveMods()
            publishSnapshot(candidate, SimulatorStateChangeKind.STATE_CHANGED)
            result
        }

    /** Restores the canonical command boundary after any partially mutating background failure. */
    private suspend fun rollbackCommand(
        failedRuntime: SimulatorRuntime,
        preCommandState: SimulatorUiState,
        commandCatalog: InstrumentCatalogSnapshot,
        message: String,
    ) = withContext(NonCancellable) {
        val canonicalState = preCommandState
            .withDetachedActiveMods()
            .copy(
                isAdvancing = false,
                lastMessage = message.take(300),
            )
        val restoredRuntime = withContext(Dispatchers.Default) {
            checkNotNull(
                SimulatorRuntime.restorePreparedState(
                    canonicalState,
                    commandCatalog,
                    historicalScenario,
                ),
            ) {
                "A Runtime-generated command boundary could not be restored"
            }
        }
        // Runtime replacement (new/load/reset) wins if it happened while reconstruction ran.
        if (runtime !== failedRuntime) return@withContext
        runtime = restoredRuntime
        lastCatalog = commandCatalog
        lastOptions = canonicalState.options.withDetachedActiveMods()
        publishSnapshot(
            canonicalState,
            SimulatorStateChangeKind.STATE_CHANGED,
        )
    }

    private fun Throwable.conciseDetail(): String =
        message
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(220)
            ?: "원인을 확인할 수 없는 내부 오류"

    /** Runtime replacement owns the screen; all work tied to the previous identity is obsolete. */
    private fun cancelBackgroundCommands() {
        advanceJob?.cancel()
        debugJumpJob?.cancel()
        advanceJob = null
        debugJumpJob = null
        turnProcessingSequence++
        _turnProcessingUiState.value = null
    }

    /** Serializes every access to the mutable Runtime while a background command owns it. */
    private inline fun <T> withRuntimeAccess(
        blocked: () -> T,
        action: () -> T,
    ): T {
        val gate = runtimeAccessGate
        if (!gate.tryLock()) return blocked()
        return try {
            action()
        } finally {
            gate.unlock()
        }
    }

    private fun debugAccessFailure(): DebugRuntimeResult? = when {
        !isDebugConsoleEnabled() -> DebugRuntimeResult.failure("현재 캠페인에 호환되는 디버그 모드가 활성화되지 않았습니다.")
        currentState.phase !in setOf(GamePhase.PLAYING, GamePhase.PAUSED) ->
            DebugRuntimeResult.failure("진행 중이거나 일시 정지된 게임에서만 디버그 콘솔을 사용할 수 있습니다.")
        else -> null
    }

    private fun publish(kind: SimulatorStateChangeKind = SimulatorStateChangeKind.STATE_CHANGED) {
        publishSnapshot(runtime.snapshot(), kind)
    }

    private fun publishSnapshot(
        snapshot: SimulatorUiState,
        kind: SimulatorStateChangeKind,
        previousTurn: Long? = null,
    ) {
        _uiState.value = snapshot
        stateChangeSequence++
        _stateChanges.tryEmit(
            SimulatorStateChange(
                sequence = stateChangeSequence,
                kind = kind,
                phase = snapshot.phase,
                currentTime = snapshot.currentTime,
                turn = snapshot.turn,
                totalAssetsKrw = snapshot.totalAssetsKrw,
                selectedInstrumentId = snapshot.selectedStockId,
                isAdvancing = snapshot.isAdvancing,
                scenarioName = snapshot.options.scenarioName,
                previousTurn = previousTurn,
            ),
        )
    }

    private fun SimulatorUiState.withDetachedActiveMods(): SimulatorUiState = copy(
        options = options.withDetachedActiveMods(),
    )

    private fun NewGameOptions.withDetachedActiveMods(): NewGameOptions = copy(
        activeMods = activeMods.map { activeMod ->
            activeMod.copy(settings = activeMod.settings.toMap())
        },
    )

    private fun SimulatorUiState.startsCampaignComparedWith(previous: SimulatorUiState): Boolean =
        phase in PLAYABLE_GAME_PHASES && (
            previous.phase == GamePhase.SETUP ||
                currentTime < previous.currentTime ||
                turn < previous.turn ||
                catalogReference != previous.catalogReference ||
                options.copy(autoExchange = previous.options.autoExchange) != previous.options
            )

    private companion object {
        const val MOD_EVENT_BUFFER_CAPACITY: Int = 64
        const val MOD_EVENT_REPLAY_CAPACITY: Int = MOD_EVENT_BUFFER_CAPACITY
        val TURN_PROCESSING_UPDATE_INTERVAL = 120.milliseconds
        val PLAYABLE_GAME_PHASES: Set<GamePhase> = setOf(GamePhase.PLAYING, GamePhase.PAUSED)
    }
}
