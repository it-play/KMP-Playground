package com.amond.kmpbook.domain.model

import kotlin.time.Instant

data class ScheduledEventMetric(
    val label: String,
    val actual: Double,
    val consensus: Double,
    val unit: String,
    val decimalPlaces: Int,
    val kind: ScheduledEventMetricKind = ScheduledEventMetricKind.GENERIC,
    /** 표시 단위의 값을 상장 통화 기준 원시 금액으로 바꾸는 배율이다. */
    val valueScale: Double = 1.0,
) {
    init {
        require(label.isNotBlank() && unit.isNotBlank())
        require(actual.isFinite() && consensus.isFinite())
        require(decimalPlaces in 0..4)
        require(valueScale.isFinite() && valueScale > 0.0)
    }

    val actualInBaseUnits: Double get() = actual * valueScale
    val consensusInBaseUnits: Double get() = consensus * valueScale
}
