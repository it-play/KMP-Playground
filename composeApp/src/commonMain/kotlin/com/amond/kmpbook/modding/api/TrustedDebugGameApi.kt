package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.simulation.event.DebugEventGuide
import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import com.amond.kmpbook.presentation.simulator.DebugPriceCurrency
import com.amond.kmpbook.presentation.simulator.DebugRuntimeResult
import com.amond.kmpbook.presentation.simulator.SimulatorUiState

/**
 * Privileged surface granted only to a signature-verified executable bundle whose signer policy
 * explicitly permits [com.amond.kmpbook.modding.model.ModCapability.DEBUG_CONSOLE].
 *
 * Possessing a manifest permission is never sufficient to obtain this object. The runtime host
 * creates a scoped adapter after every trust check and discards it when the bundle is detached.
 */
interface TrustedDebugGameApi {
    val currentState: SimulatorUiState

    fun isDebugConsoleEnabled(): Boolean
    fun debugSetInstrumentPrice(stockId: String, amount: Double, inputCurrency: DebugPriceCurrency): DebugRuntimeResult
    fun debugChangeInstrumentPrice(stockId: String, percent: Double): DebugRuntimeResult
    fun debugSetCash(currency: Currency, amount: Double): DebugRuntimeResult
    fun debugAddCash(currency: Currency, delta: Double): DebugRuntimeResult
    fun debugSetUsdKrw(rate: Double): DebugRuntimeResult
    fun debugSetAutoExchange(enabled: Boolean): DebugRuntimeResult
    fun debugSetIronman(enabled: Boolean): DebugRuntimeResult
    fun debugSetFractionalTrading(enabled: Boolean): DebugRuntimeResult
    fun debugSetExternalMarketForces(forces: ExternalMarketForces): DebugRuntimeResult
    fun debugCancelAllOrders(): DebugRuntimeResult
    fun debugEventGuide(query: String?): List<DebugEventGuide>
    fun debugTriggerEvent(templateId: String, target: String?): DebugRuntimeResult
    fun debugPause(): DebugRuntimeResult
    fun debugResume(): DebugRuntimeResult
    fun debugValidationStatus(): DebugRuntimeResult
    fun debugStartTurnJump(
        targetTurn: Long,
        resetForBackwardJump: Boolean,
        finishSettlement: Boolean = false,
    ): DebugRuntimeResult

    fun debugCancelTurnJump(): DebugRuntimeResult
}
