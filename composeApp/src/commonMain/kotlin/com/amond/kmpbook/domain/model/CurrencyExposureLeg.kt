package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

/** 기초자산 통화 한 개의 명목 노출과 상장통화 대비 헤지 비율. */
data class CurrencyExposureLeg(
    val currency: ReferenceCurrency,
    val grossNotional: Double,
    val hedgeRatioToListingCurrency: Double,
) {
    init {
        require(grossNotional in 0.0..3.0) { "ETF 통화 명목 노출은 0 이상 3 이하여야 합니다." }
        require(hedgeRatioToListingCurrency in 0.0..1.0) {
            "ETF 통화 헤지 비율은 0 이상 1 이하여야 합니다."
        }
    }

    val netNotional: Double get() = grossNotional * (1.0 - hedgeRatioToListingCurrency)
}
