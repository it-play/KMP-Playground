package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxViKind
import com.amond.kmpbook.domain.model.protection.krx.KrxViObservation
import com.amond.kmpbook.domain.model.protection.krx.KrxViProductClass
import com.amond.kmpbook.domain.model.protection.krx.KrxViSession
import kotlin.time.Instant

data class KrxViObservation(
    val stockId: String,
    val market: Market,
    val observedAt: Instant,
    val kind: KrxViKind,
    val productClass: KrxViProductClass,
    val session: KrxViSession,
    val referencePrice: Double,
    val potentialExecutionPrice: Double,
    /** Existing periodic/closing call-auction end; VI extends that auction by two minutes. */
    val existingCallAuctionEndsAt: Instant? = null,
    val isEquityDerivativesExpirationClosingAuction: Boolean = false,
    val isViExcluded: Boolean = false,
    val circuitBreakerPhase: KrxCircuitBreakerPhase = KrxCircuitBreakerPhase.NORMAL,
) {
    init {
        require(stockId.isNotBlank())
        require(market == Market.KOSPI || market == Market.KOSDAQ)
        require(referencePrice > 0.0 && referencePrice.isFinite())
        require(potentialExecutionPrice > 0.0 && potentialExecutionPrice.isFinite())
        require(existingCallAuctionEndsAt == null || existingCallAuctionEndsAt >= observedAt)
    }
}
