package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.game.GamePhase
import com.amond.kmpbook.domain.model.game.Screen
import com.amond.kmpbook.domain.model.game.TurnStep
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import kotlin.time.Instant

/**
 * 모드에 공개되는 현재 게임의 완전 분리된 읽기 스냅샷이다.
 * 모든 컬렉션과 중첩 컬렉션은 원본 상태와 공유되지 않는다.
 */
data class ModGameSnapshot(
    val apiVersion: Int,
    val scenarioName: String,
    val difficultyName: String,
    val seed: Long,
    val usFractionalTrading: Boolean,
    val autoExchange: Boolean,
    val ironmanMode: Boolean,
    val activeMods: List<ModActiveModSnapshot>,
    val phase: GamePhase,
    val screen: Screen,
    val currentTime: Instant,
    val turn: Long,
    val campaignProgress: Double,
    val isAtEnd: Boolean,
    val selectedTurnStep: TurnStep,
    val selectedInstrumentId: String?,
    val isAdvancing: Boolean,
    val instruments: List<ModInstrumentSnapshot>,
    val marketIndices: List<ModMarketIndexSnapshot>,
    val cashByCurrency: Map<Currency, Double>,
    val holdings: List<ModHoldingSnapshot>,
    val orders: List<ModOrderSnapshot>,
    val trades: List<ModTradeSnapshot>,
    val news: List<ModNewsSnapshot>,
    val activeEventIds: Set<String>,
    val readEventIds: Set<String>,
    val readInstrumentNewsEventIds: Map<String, Set<String>>,
    val watchlistedInstrumentIds: Set<String>,
    val macro: ModMacroSnapshot,
    val marketSessions: List<ModMarketSessionSnapshot>,
    val externalMarketForcesTarget: ExternalMarketForces,
    val portfolio: ModPortfolioSummary,
    val lastMessage: String?,
)
