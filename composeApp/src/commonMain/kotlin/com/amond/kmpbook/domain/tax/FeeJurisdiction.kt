package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.OrderSide
import kotlinx.datetime.LocalDate
import kotlin.math.min

enum class FeeJurisdiction(val displayName: String) {
    BROKER_CONTRACT("증권사 약정"),
    UNITED_STATES_REGULATORY("미국 규제기관"),
}
