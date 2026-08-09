package com.amond.kmpbook.domain.model.protection.us

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.protection.us.UsLuldBands
import com.amond.kmpbook.domain.model.protection.us.UsLuldLimitSide
import com.amond.kmpbook.domain.model.protection.us.UsLuldPhase
import com.amond.kmpbook.domain.model.protection.us.UsLuldState
import com.amond.kmpbook.domain.model.protection.us.UsLuldTier
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class UsLuldState(
    val stockId: String,
    val primaryMarket: Market,
    val tradingDate: LocalDate,
    val tier: UsLuldTier,
    val previousClose: Double,
    val referencePrice: Double,
    val referencePriceEffectiveAt: Instant,
    val bands: UsLuldBands,
    val phase: UsLuldPhase = UsLuldPhase.NORMAL,
    val limitSide: UsLuldLimitSide? = null,
    val limitStateStartedAt: Instant? = null,
    val limitStateDeadline: Instant? = null,
    val pauseStartedAt: Instant? = null,
    val pauseEndsAt: Instant? = null,
    val pauseExtensionCount: Int = 0,
    val reopeningStartedAt: Instant? = null,
) {
    init {
        require(stockId.isNotBlank())
        require(primaryMarket.isUnitedStates)
        require(previousClose > 0.0 && referencePrice > 0.0)
        require(bands.referencePrice == referencePrice)
        require(pauseExtensionCount in 0..1)
        if (phase == UsLuldPhase.LIMIT_STATE) {
            require(limitSide != null && limitStateStartedAt != null && limitStateDeadline != null)
        }
        if (phase == UsLuldPhase.TRADING_PAUSE) require(pauseStartedAt != null && pauseEndsAt != null)
        if (phase == UsLuldPhase.REOPENING_AUCTION) require(reopeningStartedAt != null)
    }
}
