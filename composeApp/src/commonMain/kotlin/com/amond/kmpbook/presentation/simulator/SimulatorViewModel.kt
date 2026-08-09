package com.amond.kmpbook.presentation.simulator

import androidx.lifecycle.ViewModel
import com.amond.kmpbook.domain.model.game.Screen
import com.amond.kmpbook.domain.model.game.TurnStep
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.OrderType
import com.amond.kmpbook.domain.model.trading.TimeInForce
import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import com.amond.kmpbook.presentation.trading.OrderRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SimulatorViewModel(
    initialOptions: NewGameOptions = NewGameOptions(),
) : ViewModel() {
    private var lastOptions: NewGameOptions = initialOptions
    private var runtime: SimulatorRuntime = SimulatorRuntime(initialOptions, startInSetup = true)
    private val _uiState = MutableStateFlow(runtime.snapshot())

    val uiState: StateFlow<SimulatorUiState> = _uiState.asStateFlow()
    val currentState: SimulatorUiState get() = _uiState.value

    fun newGame(options: NewGameOptions = NewGameOptions()) {
        lastOptions = options
        runtime = SimulatorRuntime(options)
        publish()
    }

    fun resetGame() {
        runtime = SimulatorRuntime(lastOptions, startInSetup = true)
        publish()
    }

    /** 파일 I/O 없이 저장 계층이 전달한 불변 상태를 검증·복원한다. */
    fun restoreGame(state: SimulatorUiState): Boolean {
        val restored = SimulatorRuntime.restore(state) ?: run {
            _uiState.value = _uiState.value.copy(lastMessage = "저장 데이터를 복원할 수 없습니다.")
            return false
        }
        runtime = restored
        lastOptions = state.options
        publish()
        return true
    }

    fun selectScreen(screen: Screen) {
        runtime.selectScreen(screen)
        publish()
    }

    fun selectStock(stockId: String): Boolean {
        val result = runtime.selectStock(stockId)
        publish()
        return result
    }

    fun selectTurnStep(step: TurnStep) {
        runtime.selectTurnStep(step)
        publish()
    }

    fun advance(step: TurnStep = _uiState.value.selectedTurnStep) {
        _uiState.value = _uiState.value.copy(isAdvancing = true, lastMessage = null)
        runtime.advance(step)
        publish()
    }

    fun placeOrder(request: OrderRequest): Boolean {
        val result = runtime.placeOrder(request)
        publish()
        return result
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

    fun cancelOrder(orderId: String): Boolean {
        val result = runtime.cancelOrder(orderId)
        publish()
        return result
    }

    fun exchange(from: Currency, to: Currency, amount: Double): Boolean {
        val result = runtime.exchange(from, to, amount)
        publish()
        return result
    }

    fun setAutoExchange(enabled: Boolean) {
        runtime.setAutoExchange(enabled)
        publish()
    }

    fun setExternalMarketForces(forces: ExternalMarketForces) {
        runtime.setExternalMarketForces(forces)
        publish()
    }

    fun markEventRead(eventId: String) {
        runtime.markEventRead(eventId)
        publish()
    }

    fun markStockNewsListViewed(stockId: String, eventIds: Set<String>) {
        runtime.markStockNewsListViewed(stockId, eventIds)
        publish()
    }

    fun markAllEventsRead() {
        runtime.markAllEventsRead()
        publish()
    }

    fun toggleWatchlist(stockId: String): Boolean {
        val added = runtime.toggleWatchlist(stockId)
        publish()
        return added
    }

    fun clearMessage() {
        runtime.clearMessage()
        publish()
    }

    fun pause() {
        runtime.pause()
        publish()
    }

    fun resume() {
        runtime.resume()
        publish()
    }

    fun finishSettlement() {
        runtime.finishSettlement()
        publish()
    }

    private fun publish() {
        _uiState.value = runtime.snapshot()
    }
}
