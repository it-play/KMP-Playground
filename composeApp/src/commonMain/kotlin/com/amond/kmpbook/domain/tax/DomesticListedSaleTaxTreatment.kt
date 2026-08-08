package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

enum class DomesticListedSaleTaxTreatment(val displayName: String) {
    EXEMPT_SMALL_SHAREHOLDER_ON_EXCHANGE("국내 장내 소액주주 비과세"),
    TAXABLE_MAJOR_SHAREHOLDER("국내 상장주식 대주주 과세"),
    TAXABLE_OFF_EXCHANGE("국내 상장주식 장외거래 과세"),
}
