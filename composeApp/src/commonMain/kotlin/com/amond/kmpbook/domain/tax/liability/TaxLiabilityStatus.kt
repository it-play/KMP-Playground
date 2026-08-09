package com.amond.kmpbook.domain.tax.liability

enum class TaxLiabilityStatus(val displayName: String) {
    ESTIMATED("추정"),
    WITHHELD("원천징수됨"),
    DUE("납부 예정"),
    PAID("납부 완료"),
    REFUNDABLE("환급 예정"),
}
