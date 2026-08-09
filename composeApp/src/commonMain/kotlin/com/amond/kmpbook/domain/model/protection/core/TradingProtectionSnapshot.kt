package com.amond.kmpbook.domain.model.protection.core

import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertDesignation
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.protection.core.InstrumentTradingHalt
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerState
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarState
import com.amond.kmpbook.domain.model.protection.krx.KrxViState
import com.amond.kmpbook.domain.model.protection.us.UsLuldState
import com.amond.kmpbook.domain.model.protection.us.UsMwcbState

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
