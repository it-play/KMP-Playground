package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.market.Currency
import kotlin.time.Instant

/**
 * Records an explicit debug-console cash override. The requested balance is an observed debug
 * fact; [balanceBefore] and the global sequence bind it to the otherwise replayable cash ledger.
 */
data class CashAdjustmentRecord(
    val id: String,
    val adjustedAt: Instant,
    val currency: Currency,
    val balanceBefore: Double,
    val balanceAfter: Double,
    val reason: String,
    val accountingSequence: Long,
) {
    init {
        require(id.isNotBlank())
        require(balanceBefore.isFinite() && balanceBefore >= 0.0)
        require(balanceAfter.isFinite() && balanceAfter >= 0.0)
        require(reason == DEBUG_SET_CASH_REASON)
        require(accountingSequence > 0L)
    }

    companion object {
        const val DEBUG_SET_CASH_REASON: String = "DEBUG_SET_CASH"
    }
}
