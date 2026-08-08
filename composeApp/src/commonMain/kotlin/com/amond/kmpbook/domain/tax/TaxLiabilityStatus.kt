package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

enum class TaxLiabilityStatus(val displayName: String) {
    ESTIMATED("추정"),
    WITHHELD("원천징수됨"),
    DUE("납부 예정"),
    PAID("납부 완료"),
    REFUNDABLE("환급 예정"),
}
