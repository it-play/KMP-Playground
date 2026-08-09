package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarObservation
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class KrxSidecarObservation(
    val market: Market,
    val tradingDate: LocalDate,
    val observedAt: Instant,
    val futuresChangeRate: Double,
    /** Required for KOSDAQ150; ignored for KOSPI200. */
    val spotIndexChangeRate: Double? = null,
    val minutesAfterOpen: Double,
    val minutesUntilClose: Double,
    val futuresTradingHalted: Boolean = false,
    val circuitBreakerPhase: KrxCircuitBreakerPhase = KrxCircuitBreakerPhase.NORMAL,
    /** Intrabar time when all direction-specific futures/spot conditions became true. */
    val conditionSatisfiedSince: Instant? = null,
) {
    init {
        require(market == Market.KOSPI || market == Market.KOSDAQ)
        require(futuresChangeRate.isFinite())
        require(spotIndexChangeRate == null || spotIndexChangeRate.isFinite())
        require(minutesAfterOpen >= 0.0 && minutesAfterOpen.isFinite())
        require(minutesUntilClose >= 0.0 && minutesUntilClose.isFinite())
        require(market != Market.KOSDAQ || spotIndexChangeRate != null) { "KOSDAQ 사이드카에는 현물지수 변동률이 필요합니다." }
        require(conditionSatisfiedSince == null || conditionSatisfiedSince <= observedAt)
    }
}
