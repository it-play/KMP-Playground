package com.amond.kmpbook.domain.model

import kotlin.time.Instant

data class ScheduledEventMetric(
    val label: String,
    val actual: Double,
    val consensus: Double,
    val unit: String,
    val decimalPlaces: Int,
) {
    init {
        require(label.isNotBlank() && unit.isNotBlank())
        require(actual.isFinite() && consensus.isFinite())
        require(decimalPlaces in 0..4)
    }
}
