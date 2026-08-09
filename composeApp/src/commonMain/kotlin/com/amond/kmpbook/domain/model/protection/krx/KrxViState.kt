package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.protection.krx.KrxViDirection
import com.amond.kmpbook.domain.model.protection.krx.KrxViKind
import com.amond.kmpbook.domain.model.protection.krx.KrxViPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxViSession
import com.amond.kmpbook.domain.model.protection.krx.KrxViState
import kotlin.time.Instant

data class KrxViState(
    val stockId: String,
    val market: Market,
    val phase: KrxViPhase = KrxViPhase.IDLE,
    val kind: KrxViKind? = null,
    val session: KrxViSession? = null,
    val referencePrice: Double? = null,
    val triggerRate: Double? = null,
    val direction: KrxViDirection? = null,
    val triggeredAt: Instant? = null,
    val auctionEndsAt: Instant? = null,
    /** VI is repeatable and therefore this counter has no daily cap. */
    val triggerCount: Int = 0,
) {
    init {
        require(stockId.isNotBlank())
        require(market == Market.KOSPI || market == Market.KOSDAQ)
        require(triggerCount >= 0)
        if (phase == KrxViPhase.CALL_AUCTION) {
            require(kind != null && session != null && direction != null)
            require(referencePrice != null && referencePrice > 0.0)
            require(triggerRate != null && triggerRate > 0.0)
            require(triggeredAt != null && auctionEndsAt != null)
        }
    }
}
