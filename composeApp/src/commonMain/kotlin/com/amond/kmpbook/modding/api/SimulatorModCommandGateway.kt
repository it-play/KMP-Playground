package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.game.GamePhase
import com.amond.kmpbook.domain.model.trading.OrderType
import com.amond.kmpbook.modding.model.ModCapability
import com.amond.kmpbook.presentation.simulator.SimulatorViewModel
import com.amond.kmpbook.presentation.trading.OrderRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SimulatorModCommandGateway(
    private val viewModel: SimulatorViewModel,
    grantedCapabilities: Set<ModCapability>,
) : ModCommandGateway {
    private val grantedCapabilities = grantedCapabilities.toSet()

    override suspend fun execute(command: ModCommand): ModCommandResult = try {
        withContext(Dispatchers.Main.immediate) { executeOnMain(command) }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: RuntimeException) {
        ModCommandFailure(
            message = "모드 명령을 게임 런타임 스레드에서 실행하지 못했습니다.",
            exceptionType = exception::class.simpleName ?: "RuntimeException",
        )
    }

    private fun executeOnMain(command: ModCommand): ModCommandResult {
        if (command.requiredCapability !in grantedCapabilities) {
            return ModCommandRejected(
                message = "이 모드에는 '${command.requiredCapability.label}' 권한이 없습니다.",
                code = ModCommandRejectionCode.MISSING_CAPABILITY,
                missingCapability = command.requiredCapability,
            )
        }
        if (viewModel.currentState.isAdvancing) {
            return ModCommandRejected(
                message = "게임 진행 계산 중에는 모드가 상태를 변경할 수 없습니다.",
                code = ModCommandRejectionCode.GAME_ADVANCING,
            )
        }
        return try {
            executeGranted(command)
        } catch (exception: RuntimeException) {
            ModCommandFailure(
                message = "모드 명령을 실행하는 중 예기치 못한 오류가 발생했습니다.",
                exceptionType = exception::class.simpleName ?: "RuntimeException",
            )
        }
    }

    private fun executeGranted(command: ModCommand): ModCommandResult = when (command) {
        is SelectScreenCommand -> {
            viewModel.selectScreen(command.screen)
            success("화면을 선택했습니다.", command.screen.name)
        }
        is SelectInstrumentCommand -> if (viewModel.selectStock(command.instrumentId)) {
            success("종목을 선택했습니다.", command.instrumentId)
        } else {
            engineRejected("종목을 선택할 수 없습니다.")
        }
        is SelectTurnStepCommand -> {
            viewModel.selectTurnStep(command.step)
            success("진행 단위를 선택했습니다.", command.step.name)
        }
        is AdvanceGameCommand -> advance(command)
        is PlaceOrderCommand -> placeOrder(command)
        is CancelOrderCommand -> if (viewModel.cancelOrder(command.orderId)) {
            success("주문을 취소했습니다.", command.orderId)
        } else {
            engineRejected("주문을 취소할 수 없습니다.")
        }
        is ExchangeCurrencyCommand -> if (viewModel.exchange(command.from, command.to, command.amount)) {
            success("환전이 완료되었습니다.", command.to.name)
        } else {
            engineRejected("환전할 수 없습니다.")
        }
        is SetAutoExchangeCommand -> {
            viewModel.setAutoExchange(command.enabled)
            success("자동 환전 설정을 변경했습니다.", command.enabled.toString())
        }
        is SetExternalMarketForcesCommand -> setExternalMarketForces(command)
        is MarkEventReadCommand -> markEventRead(command)
        is MarkInstrumentEventsReadCommand -> markInstrumentEventsRead(command)
        MarkAllEventsReadCommand -> {
            viewModel.markAllEventsRead()
            success("모든 이벤트를 읽음 처리했습니다.")
        }
        is ToggleWatchlistCommand -> toggleWatchlist(command)
        PauseGameCommand -> pause()
        ResumeGameCommand -> resume()
        FinishSettlementCommand -> finishSettlement()
    }

    private fun advance(command: AdvanceGameCommand): ModCommandResult {
        if (viewModel.currentState.phase != GamePhase.PLAYING) {
            return ModCommandRejected(
                message = "진행 중인 게임에서만 턴을 진행할 수 있습니다.",
                code = ModCommandRejectionCode.INVALID_GAME_PHASE,
            )
        }
        val step = command.step ?: viewModel.currentState.selectedTurnStep
        viewModel.advance(step)
        return ModCommandSuccess(
            message = "${step.displayName} 게임 진행을 시작했습니다.",
            value = step.name,
        )
    }

    private fun placeOrder(command: PlaceOrderCommand): ModCommandResult {
        if (
            !command.quantity.isFinite() ||
            command.quantity <= 0.0 ||
            command.limitPrice?.let { !it.isFinite() || it <= 0.0 } == true ||
            command.type == OrderType.LIMIT && command.limitPrice == null ||
            command.type != OrderType.LIMIT && command.limitPrice != null
        ) {
            return ModCommandRejected(
                message = "주문 수량과 지정가는 유한한 양수여야 합니다.",
                code = ModCommandRejectionCode.INVALID_ARGUMENT,
            )
        }
        val accepted = viewModel.placeOrder(
            OrderRequest(
                stockId = command.instrumentId,
                side = command.side,
                type = command.type,
                quantity = command.quantity,
                limitPrice = command.limitPrice,
                timeInForce = command.timeInForce,
            ),
        )
        if (!accepted) return engineRejected("주문이 거부되었습니다.")
        return ModCommandSuccess(
            message = viewModel.currentState.lastMessage ?: "주문을 접수했습니다.",
            value = viewModel.currentState.orders.lastOrNull()?.id,
        )
    }

    private fun setExternalMarketForces(command: SetExternalMarketForcesCommand): ModCommandResult {
        if (viewModel.currentState.options.ironmanMode) {
            return ModCommandRejected(
                message = "철인 모드에서는 시장 환경을 변경할 수 없습니다.",
                code = ModCommandRejectionCode.ENGINE_REJECTED,
            )
        }
        viewModel.setExternalMarketForces(command.forces.copy())
        return success("외부 시장 환경 목표를 변경했습니다.")
    }

    private fun markEventRead(command: MarkEventReadCommand): ModCommandResult {
        if (viewModel.currentState.newsEvents.none { it.id == command.eventId }) {
            return ModCommandRejected(
                message = "읽음 처리할 이벤트를 찾을 수 없습니다.",
                code = ModCommandRejectionCode.INVALID_ARGUMENT,
            )
        }
        viewModel.markEventRead(command.eventId)
        return success("이벤트를 읽음 처리했습니다.", command.eventId)
    }

    private fun markInstrumentEventsRead(
        command: MarkInstrumentEventsReadCommand,
    ): ModCommandResult {
        val instrument = viewModel.currentState.stocks.firstOrNull { it.id == command.instrumentId }
        if (instrument == null) {
            return ModCommandRejected(
                message = "이벤트를 읽음 처리할 종목을 찾을 수 없습니다.",
                code = ModCommandRejectionCode.INVALID_ARGUMENT,
            )
        }
        val eventIds = command.eventIds.toSet()
        if (eventIds.isEmpty()) {
            return ModCommandRejected(
                message = "읽음 처리할 이벤트 ID가 필요합니다.",
                code = ModCommandRejectionCode.INVALID_ARGUMENT,
            )
        }
        val readableEventIds = viewModel.currentState.newsEvents.asSequence()
            .filter { event -> event.id in eventIds && event.affects(instrument) }
            .mapTo(linkedSetOf()) { event -> event.id }
        if (readableEventIds.isEmpty()) {
            return ModCommandRejected(
                message = "읽음 처리할 수 있는 이벤트를 찾을 수 없습니다.",
                code = ModCommandRejectionCode.INVALID_ARGUMENT,
            )
        }
        viewModel.markStockNewsListViewed(command.instrumentId, readableEventIds)
        return success("종목 뉴스를 읽음 처리했습니다.", readableEventIds.size.toString())
    }

    private fun toggleWatchlist(command: ToggleWatchlistCommand): ModCommandResult {
        if (viewModel.currentState.stocks.none { it.id == command.instrumentId }) {
            return ModCommandRejected(
                message = "관심 목록을 변경할 종목을 찾을 수 없습니다.",
                code = ModCommandRejectionCode.INVALID_ARGUMENT,
            )
        }
        val added = viewModel.toggleWatchlist(command.instrumentId)
        return success(
            defaultMessage = if (added) {
                "관심 종목에 추가했습니다."
            } else {
                "관심 종목에서 해제했습니다."
            },
            value = if (added) "added" else "removed",
        )
    }

    private fun pause(): ModCommandResult {
        if (viewModel.currentState.phase != GamePhase.PLAYING) {
            return ModCommandRejected(
                message = "진행 중인 게임만 일시 정지할 수 있습니다.",
                code = ModCommandRejectionCode.INVALID_GAME_PHASE,
            )
        }
        viewModel.pause()
        return success("게임을 일시 정지했습니다.")
    }

    private fun resume(): ModCommandResult {
        if (viewModel.currentState.phase != GamePhase.PAUSED) {
            return ModCommandRejected(
                message = "일시 정지된 게임만 다시 시작할 수 있습니다.",
                code = ModCommandRejectionCode.INVALID_GAME_PHASE,
            )
        }
        viewModel.resume()
        return success("게임 진행을 재개했습니다.")
    }

    private fun finishSettlement(): ModCommandResult {
        if (viewModel.currentState.phase != GamePhase.SETTLEMENT) {
            return ModCommandRejected(
                message = "최종 정산 단계에서만 정산을 완료할 수 있습니다.",
                code = ModCommandRejectionCode.INVALID_GAME_PHASE,
            )
        }
        viewModel.finishSettlement()
        return success("최종 정산을 완료했습니다.")
    }

    private fun success(defaultMessage: String, value: String? = null): ModCommandSuccess =
        ModCommandSuccess(
            message = defaultMessage,
            value = value,
        )

    private fun engineRejected(defaultMessage: String): ModCommandRejected = ModCommandRejected(
        message = viewModel.currentState.lastMessage ?: defaultMessage,
        code = ModCommandRejectionCode.ENGINE_REJECTED,
    )
}
