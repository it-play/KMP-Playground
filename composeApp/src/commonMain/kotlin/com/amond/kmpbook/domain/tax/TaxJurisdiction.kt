package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

enum class TaxJurisdiction(val displayName: String) {
    KOREA_NATIONAL("대한민국 국세"),
    KOREA_LOCAL("대한민국 지방소득세"),
    UNITED_STATES_FEDERAL("미국 연방"),
}
