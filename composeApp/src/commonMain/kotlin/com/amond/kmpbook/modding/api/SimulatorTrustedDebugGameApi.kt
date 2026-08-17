package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.simulation.event.DebugEventGuide
import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import com.amond.kmpbook.modding.model.ModCapability
import com.amond.kmpbook.presentation.simulator.DebugPriceCurrency
import com.amond.kmpbook.presentation.simulator.DebugRuntimeResult
import com.amond.kmpbook.presentation.simulator.SimulatorUiState
import com.amond.kmpbook.presentation.simulator.SimulatorViewModel

internal class SimulatorTrustedDebugGameApi(
    private val viewModel: SimulatorViewModel,
    private val modId: String,
    private val modVersion: String,
    private val executableFingerprint: String,
) : TrustedDebugGameApi {
    @Volatile
    private var active: Boolean = true

    override val currentState: SimulatorUiState
        get() {
            check(active) { "The trusted debug API has been revoked." }
            return viewModel.currentState
        }

    override fun isDebugConsoleEnabled(): Boolean = active && viewModel.currentState.options.activeMods.any { activeMod ->
        activeMod.id == modId &&
            activeMod.version == modVersion &&
            activeMod.executableFingerprint == executableFingerprint &&
            ModCapability.DEBUG_CONSOLE in activeMod.grantedCapabilities
    }

    override fun debugSetInstrumentPrice(
        stockId: String,
        amount: Double,
        inputCurrency: DebugPriceCurrency,
    ): DebugRuntimeResult = withAccess { viewModel.debugSetInstrumentPrice(stockId, amount, inputCurrency) }

    override fun debugChangeInstrumentPrice(stockId: String, percent: Double): DebugRuntimeResult =
        withAccess { viewModel.debugChangeInstrumentPrice(stockId, percent) }

    override fun debugSetCash(currency: Currency, amount: Double): DebugRuntimeResult =
        withAccess { viewModel.debugSetCash(currency, amount) }

    override fun debugAddCash(currency: Currency, delta: Double): DebugRuntimeResult =
        withAccess { viewModel.debugAddCash(currency, delta) }

    override fun debugSetUsdKrw(rate: Double): DebugRuntimeResult = withAccess { viewModel.debugSetUsdKrw(rate) }

    override fun debugSetAutoExchange(enabled: Boolean): DebugRuntimeResult =
        withAccess { viewModel.debugSetAutoExchange(enabled) }

    override fun debugSetIronman(enabled: Boolean): DebugRuntimeResult = withAccess { viewModel.debugSetIronman(enabled) }

    override fun debugSetFractionalTrading(enabled: Boolean): DebugRuntimeResult =
        withAccess { viewModel.debugSetFractionalTrading(enabled) }

    override fun debugSetExternalMarketForces(forces: ExternalMarketForces): DebugRuntimeResult =
        withAccess { viewModel.debugSetExternalMarketForces(forces) }

    override fun debugCancelAllOrders(): DebugRuntimeResult = withAccess(viewModel::debugCancelAllOrders)

    override fun debugEventGuide(query: String?): List<DebugEventGuide> =
        if (active) viewModel.debugEventGuide(query) else emptyList()

    override fun debugTriggerEvent(templateId: String, target: String?): DebugRuntimeResult =
        withAccess { viewModel.debugTriggerEvent(templateId, target) }

    override fun debugPause(): DebugRuntimeResult = withAccess(viewModel::debugPause)

    override fun debugResume(): DebugRuntimeResult = withAccess(viewModel::debugResume)

    override fun debugValidationStatus(): DebugRuntimeResult = withAccess(viewModel::debugValidationStatus)

    override fun debugStartTurnJump(
        targetTurn: Long,
        resetForBackwardJump: Boolean,
        finishSettlement: Boolean,
    ): DebugRuntimeResult = withAccess {
        viewModel.debugStartTurnJump(targetTurn, resetForBackwardJump, finishSettlement)
    }

    override fun debugCancelTurnJump(): DebugRuntimeResult = withAccess(viewModel::debugCancelTurnJump)

    internal fun revoke() {
        active = false
    }

    private inline fun withAccess(action: () -> DebugRuntimeResult): DebugRuntimeResult =
        if (active) action() else DebugRuntimeResult.failure("이 실행 모드의 디버그 API 권한이 해제되었습니다.")
}
