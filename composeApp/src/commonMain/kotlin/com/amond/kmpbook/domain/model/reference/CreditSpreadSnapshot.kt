package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlin.time.Instant

/** 한 통화의 등급별 연율 신용 스프레드다. 국채도 0인 SOVEREIGN 값을 명시한다. */
data class CreditSpreadSnapshot(
    val currency: ReferenceCurrency,
    val annualSpreads: Map<CreditQuality, Double>,
    val asOf: Instant,
) {
    init {
        require(annualSpreads.keys == CreditQuality.entries.toSet()) {
            "신용 스프레드에는 모든 등급이 정확히 한 번씩 필요합니다."
        }
        require(annualSpreads.values.all { it.isFinite() && it in 0.0..MAX_SPREAD })
        require(annualSpreads.getValue(CreditQuality.SOVEREIGN) == 0.0)
        val risky = CreditQuality.entries.filterNot { it == CreditQuality.SOVEREIGN }
        require(risky.zipWithNext().all { (better, worse) ->
            annualSpreads.getValue(better) <= annualSpreads.getValue(worse)
        }) { "낮은 신용등급의 스프레드는 높은 등급보다 작을 수 없습니다." }
    }

    companion object {
        private const val MAX_SPREAD: Double = 2.0
    }
}
