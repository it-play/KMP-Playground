package com.amond.kmpbook.domain.tax.domestic

import com.amond.kmpbook.domain.model.instrument.EtfTaxCategory
import com.amond.kmpbook.domain.tax.core.MoneyRoundingPolicy
import kotlinx.datetime.LocalDate

data class DomesticEtfSaleTaxRequest(
    val taxCategory: EtfTaxCategory,
    val grossProceedsKrw: Long,
    val acquisitionValueKrw: Long,
    /** Simulated increase in the ETF tax-base price for the units sold. */
    val taxableStandardGainKrw: Long,
    val soldOn: LocalDate,
    val roundingPolicy: MoneyRoundingPolicy = MoneyRoundingPolicy.TAX_WON_DOWN,
) {
    init {
        require(taxCategory != EtfTaxCategory.FOREIGN_LISTED) {
            "Foreign-listed ETFs use the foreign-stock capital-gains path."
        }
        require(grossProceedsKrw >= 0L && acquisitionValueKrw >= 0L && taxableStandardGainKrw >= 0L) {
            "ETF sale values cannot be negative."
        }
    }
}
