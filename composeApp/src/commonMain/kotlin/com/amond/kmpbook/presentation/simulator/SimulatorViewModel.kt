package com.amond.kmpbook.presentation.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amond.kmpbook.domain.data.InstrumentCatalogSnapshot
import com.amond.kmpbook.domain.model.game.GamePhase
import com.amond.kmpbook.domain.model.game.Screen
import com.amond.kmpbook.domain.model.game.TurnStep
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.OrderType
import com.amond.kmpbook.domain.model.trading.TimeInForce
import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import com.amond.kmpbook.domain.simulation.event.DebugEventGuide
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.modding.builtin.debug.DebugMod
import com.amond.kmpbook.persistence.validation.validateSimulatorUiState
import com.amond.kmpbook.presentation.trading.OrderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SimulatorViewModel(
    initialCatalog: InstrumentCatalogSnapshot,
    initialOptions: NewGameOptions = NewGameOptions(),
) : ViewModel() {
    private var lastOptions: NewGameOptions = initialOptions.withDetachedActiveMods()
    private var lastCatalog: InstrumentCatalogSnapshot = initialCatalog
    private var runtime: SimulatorRuntime = SimulatorRuntime(lastOptions, lastCatalog, startInSetup = true)
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

    val uiState: StateFlow<SimulatorUiState> = _uiState.asStateFlow()
    internal val stateChanges: SharedFlow<SimulatorStateChange> = _stateChanges.asSharedFlow()
    internal val currentStateChangeSequence: Long get() = stateChangeSequence
    val currentState: SimulatorUiState get() = _uiState.value

    /**
     * Validates the exact snapshot that will be handed to persistence against the active catalog.
     * The storage layer can only enforce intrinsic wire invariants because it does not own the
     * executable catalog; canonical methodology checks therefore belong at this boundary.
     */
    fun validateStateForPersistence(state: SimulatorUiState): String? =
        validateSimulatorUiState(state, lastCatalog)

    fun newGame(
        options: NewGameOptions = NewGameOptions(),
        catalog: InstrumentCatalogSnapshot = lastCatalog,
    ): String? {
        val detachedOptions = options.withDetachedActiveMods()
        val candidateRuntime = runCatching {
            SimulatorRuntime(detachedOptions, catalog)
        }.getOrElse { error ->
            val detail = error.message?.take(240)?.takeIf(String::isNotBlank) ?: "알 수 없는 오류"
            return "새 게임의 종목 방법론을 초기화하지 못했습니다: $detail"
        }
        cancelBackgroundCommands()
        lastOptions = detachedOptions
        lastCatalog = catalog
        runtime = candidateRuntime
        runtimeAccessGate = Mutex()
        publish(SimulatorStateChangeKind.GAME_STARTED)
        return null
    }

    fun resetGame() {
        val resetRuntime = SimulatorRuntime(lastOptions, lastCatalog, startInSetup = true)
        cancelBackgroundCommands()
        runtime = resetRuntime
        runtimeAccessGate = Mutex()
        publish()
    }

    /** 파일 I/O 없이 저장 계층이 전달한 불변 상태를 검증·복원한다. */
    fun restoreGame(
        state: SimulatorUiState,
        catalog: InstrumentCatalogSnapshot,
    ): Boolean {
        val previousState = _uiState.value
        if (
            _uiState.value.options.ironmanMode &&
            _uiState.value.phase in setOf(GamePhase.PLAYING, GamePhase.PAUSED)
        ) {
            _uiState.value = _uiState.value.copy(lastMessage = "철인 모드에서는 게임을 불러올 수 없습니다.")
            return false
        }
        val candidate = state.withDetachedActiveMods()
        val restored = SimulatorRuntime.restore(candidate, catalog) ?: run {
            val validationError = validateSimulatorUiState(candidate, catalog)
            _uiState.value = _uiState.value.copy(
                lastMessage = validationError?.let { problem ->
                    "저장 데이터가 유효하지 않습니다: $problem"
                } ?: "저장 데이터를 복원할 수 없습니다.",
            )
            return false
        }
        cancelBackgroundCommands()
        runtime = restored
        runtimeAccessGate = Mutex()
        publish(
            if (candidate.startsCampaignComparedWith(previousState)) {
                SimulatorStateChangeKind.GAME_STARTED
            } else {
                SimulatorStateChangeKind.STATE_CHANGED
            },
        )
        lastOptions = _uiState.value.options
        lastCatalog = catalog
        return true
    }

    fun selectScreen(screen: Screen) = withRuntimeAccess(blocked = {}) {
        runtime.selectScreen(screen)
        publish()
    }

    fun selectStock(stockId: String): Boolean = withRuntimeAccess(blocked = { false }) {
        val result = runtime.selectStock(stockId)
        publish()
        result
    }

    fun selectTurnStep(step: TurnStep) = withRuntimeAccess(blocked = {}) {
        runtime.selectTurnStep(step)
        publish()
    }

    fun advance(step: TurnStep = _uiState.value.selectedTurnStep) {
        val commandGate = runtimeAccessGate
        if (!commandGate.tryLock()) return
        var completionOwnsGate = false
        try {
            val advancingRuntime = runtime
            val commandCatalog = lastCatalog
            val publishedState = _uiState.value
            val previousTurn = publishedState.turn
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
                        // This suspend variant is the canonical hourly loop with ensureActive() at
                        // each simulated-hour boundary. A single hour remains atomic for rollback.
                        advancingRuntime.advance(step)
                        advancingRuntime.snapshot()
                    }
                    if (runtime === advancingRuntime) {
                        if (advancedState.turn > previousTurn) {
                            publishSnapshot(
                                advancedState,
                                SimulatorStateChangeKind.TURN_COMPLETED,
                                previousTurn = previousTurn,
                            )
                        } else {
                            publishSnapshot(advancedState, SimulatorStateChangeKind.STATE_CHANGED)
                        }
                        when (advancedState.phase) {
                            GamePhase.SETTLEMENT -> publishSnapshot(
                                advancedState,
                                SimulatorStateChangeKind.SETTLEMENT_STARTED,
                            )
                            GamePhase.FINISHED -> publishSnapshot(
                                advancedState,
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
                    if (advanceJob === commandJob) advanceJob = null
                }
            }
            advanceJob = job
            job.invokeOnCompletion {
                if (advanceJob === job) advanceJob = null
                commandGate.unlock()
            }
            completionOwnsGate = true
            job.start()
        } finally {
            if (!completionOwnsGate) commandGate.unlock()
        }
    }

    /** Requests cancellation at the next atomic simulated-hour boundary. */
    fun cancelAdvance(): Boolean {
        val jobs = listOfNotNull(advanceJob, debugJumpJob).filter(Job::isActive)
        if (jobs.isEmpty()) return false
        _uiState.value = _uiState.value.copy(
            lastMessage = "현재 시간 계산이 끝나는 즉시 진행을 취소하고 이전 상태로 복원합니다.",
        )
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
        publish()
    }

    fun setExternalMarketForces(forces: ExternalMarketForces) = withRuntimeAccess(blocked = {}) {
        runtime.setExternalMarketForces(forces)
        publish()
    }

    fun markEventRead(eventId: String) = withRuntimeAccess(blocked = {}) {
        runtime.markEventRead(eventId)
        publish()
    }

    fun markStockNewsListViewed(stockId: String, eventIds: Set<String>) =
        withRuntimeAccess(blocked = {}) {
        runtime.markStockNewsListViewed(stockId, eventIds)
        publish()
    }

    fun markAllEventsRead() = withRuntimeAccess(blocked = {}) {
        runtime.markAllEventsRead()
        publish()
    }

    fun toggleWatchlist(stockId: String): Boolean = withRuntimeAccess(blocked = { false }) {
        val added = runtime.toggleWatchlist(stockId)
        publish()
        added
    }

    fun clearMessage() {
        if (_uiState.value.isAdvancing) {
            _uiState.value = _uiState.value.copy(lastMessage = null)
            return
        }
        withRuntimeAccess(blocked = {}) {
            runtime.clearMessage()
            publish()
        }
    }

    fun pause() = withRuntimeAccess(blocked = {}) {
        runtime.pause()
        publish()
    }

    fun resume() = withRuntimeAccess(blocked = {}) {
        runtime.resume()
        publish()
    }

    fun finishSettlement() = withRuntimeAccess(blocked = {}) {
        val previousPhase = _uiState.value.phase
        runtime.finishSettlement()
        val snapshot = runtime.snapshot()
        publishSnapshot(
            snapshot = snapshot,
            kind = if (previousPhase != GamePhase.FINISHED && snapshot.phase == GamePhase.FINISHED) {
                SimulatorStateChangeKind.GAME_ENDED
            } else {
                SimulatorStateChangeKind.STATE_CHANGED
            },
        )
    }

    internal fun isDebugConsoleEnabled(): Boolean = currentState.options.activeMods.any { activeMod ->
        DebugMod.isCompatible(activeMod.id, activeMod.version)
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
        val problem = validateSimulatorUiState(currentState, lastCatalog)
        return if (problem == null) {
            DebugRuntimeResult.success("현재 게임 상태가 저장 불변식 검사를 통과했습니다.")
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
            val problem = validateSimulatorUiState(candidate, lastCatalog)
            if (problem != null) {
                runtime = requireNotNull(SimulatorRuntime.restore(before, lastCatalog))
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
            checkNotNull(SimulatorRuntime.restore(canonicalState, commandCatalog)) {
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
        val PLAYABLE_GAME_PHASES: Set<GamePhase> = setOf(GamePhase.PLAYING, GamePhase.PAUSED)
    }
}
