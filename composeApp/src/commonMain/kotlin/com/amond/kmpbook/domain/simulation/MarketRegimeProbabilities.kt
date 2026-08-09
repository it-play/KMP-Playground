package com.amond.kmpbook.domain.simulation

import kotlin.math.abs

/** 연속적인 국면 혼합 확률. 하나의 상태를 갑자기 전환하지 않아 경계에서 가격이 튀지 않는다. */
data class MarketRegimeProbabilities(
    val calm: Double,
    val balanced: Double,
    val stress: Double,
    val crisis: Double,
) {
    init {
        require(values.all { it.isFinite() && it in 0.0..1.0 })
        require(abs(values.sum() - 1.0) <= SUM_EPSILON) {
            "Market regime probabilities must sum to one"
        }
    }

    val values: List<Double> get() = listOf(calm, balanced, stress, crisis)

    /** 안정=0, 보통=1, 스트레스=2, 위기=3의 확률 가중 강도다. */
    val stressIndex: Double get() = balanced + stress * 2.0 + crisis * 3.0

    companion object {
        const val SUM_EPSILON: Double = 1e-6

        val AUGUST_2026_BASELINE = MarketRegimeProbabilities(
            calm = 0.12,
            balanced = 0.64,
            stress = 0.21,
            crisis = 0.03,
        )

        fun normalized(values: List<Double>): MarketRegimeProbabilities {
            require(values.size == 4 && values.all { it.isFinite() && it >= 0.0 })
            val sum = values.sum().coerceAtLeast(1e-12)
            return MarketRegimeProbabilities(
                calm = values[0] / sum,
                balanced = values[1] / sum,
                stress = values[2] / sum,
                crisis = values[3] / sum,
            )
        }
    }
}
