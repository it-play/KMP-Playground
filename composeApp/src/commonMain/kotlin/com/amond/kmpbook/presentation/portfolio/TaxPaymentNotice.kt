package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus
import kotlinx.datetime.LocalDate

data class TaxPaymentNotice(
    val id: String,
    val taxYear: Int,
    val dueDate: LocalDate,
    val amountKrw: Long,
    val status: TaxLiabilityStatus,
    val message: String,
)
