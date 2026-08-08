package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

data class TaxLiability(
    val id: String,
    val label: String,
    val taxYear: Int,
    val assessedTaxKrw: Long,
    val withholdingCreditsKrw: Long = 0L,
    val dueDate: LocalDate? = null,
    val status: TaxLiabilityStatus = TaxLiabilityStatus.ESTIMATED,
    val items: List<TaxLineItem> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    init {
        require(id.isNotBlank() && label.isNotBlank()) { "A liability needs an id and label." }
        require(taxYear >= 1900) { "The tax year is invalid." }
        require(assessedTaxKrw >= 0L && withholdingCreditsKrw >= 0L) {
            "Assessed tax and credits cannot be negative."
        }
    }

    val payableKrw: Long get() = (assessedTaxKrw - withholdingCreditsKrw).coerceAtLeast(0L)
    val refundableKrw: Long get() = (withholdingCreditsKrw - assessedTaxKrw).coerceAtLeast(0L)
}
