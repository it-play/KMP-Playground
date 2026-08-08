package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class TradingProtectionSnapshot(
    val krxCircuitBreakers: Map<Market, KrxCircuitBreakerState> = emptyMap(),
    val krxSidecars: Map<Market, KrxSidecarState> = emptyMap(),
    val krxVolatilityInterruptions: Map<String, KrxViState> = emptyMap(),
    val instrumentTradingHalts: Map<String, InstrumentTradingHalt> = emptyMap(),
    /** 다음 정규장에 효력이 생기는 KRX 전일 공시 정지. */
    val scheduledInstrumentTradingHalts: Map<String, InstrumentTradingHalt> = emptyMap(),
    val investmentAlerts: Map<String, InvestmentAlertDesignation> = emptyMap(),
    val usMarketWideCircuitBreaker: UsMwcbState? = null,
    val usLuldStates: Map<String, UsLuldState> = emptyMap(),
)
