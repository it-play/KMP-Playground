package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.floor

data class HighDividendElectionRequest(
    val taxYear: Int,
    val paidOn: LocalDate,
    val grossEligibleDividendKrw: Long,
    /** Consume the official KRX KIND eligibility flag instead of inferring it from yield. */
    val isKrxKindEligibleCompany: Boolean,
    val electionRequested: Boolean,
    val withholdingCreditsKrw: Long = 0L,
    val roundingPolicy: MoneyRoundingPolicy = MoneyRoundingPolicy.TAX_WON_DOWN,
) {
    init {
        require(taxYear == paidOn.year) { "taxYear must match the dividend payment year." }
        require(grossEligibleDividendKrw >= 0L && withholdingCreditsKrw >= 0L) {
            "Dividend and credits cannot be negative."
        }
    }
}
