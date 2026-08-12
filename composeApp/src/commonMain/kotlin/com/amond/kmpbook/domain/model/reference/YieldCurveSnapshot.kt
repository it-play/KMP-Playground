package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlin.time.Instant

/** 한 통화의 명목 또는 실질 무위험 연속복리 zero-rate 곡선이다. */
data class YieldCurveSnapshot(
    val currency: ReferenceCurrency,
    val annualZeroRates: Map<YieldCurveTenor, Double>,
    val asOf: Instant,
) {
    init {
        require(annualZeroRates.keys == YieldCurveTenor.entries.toSet()) {
            "금리곡선에는 모든 표준 만기점이 정확히 한 번씩 필요합니다."
        }
        require(annualZeroRates.values.all { it.isFinite() && it in MIN_RATE..MAX_RATE })
    }

    /** 선형보간하며 곡선 바깥은 가장 가까운 표준 만기 금리를 유지한다. */
    fun rateAtYears(years: Double): Double {
        require(years.isFinite() && years >= 0.0)
        val ordered = YieldCurveTenor.entries
        if (years <= ordered.first().years) return annualZeroRates.getValue(ordered.first())
        if (years >= ordered.last().years) return annualZeroRates.getValue(ordered.last())
        val upperIndex = ordered.indexOfFirst { it.years >= years }
        val lower = ordered[upperIndex - 1]
        val upper = ordered[upperIndex]
        val fraction = (years - lower.years) / (upper.years - lower.years)
        return annualZeroRates.getValue(lower) * (1.0 - fraction) +
            annualZeroRates.getValue(upper) * fraction
    }

    companion object {
        private const val MIN_RATE: Double = -0.10
        private const val MAX_RATE: Double = 1.00
    }
}
