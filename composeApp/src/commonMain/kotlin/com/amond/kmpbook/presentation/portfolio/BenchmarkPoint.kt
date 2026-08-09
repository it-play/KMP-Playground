package com.amond.kmpbook.presentation.portfolio

import kotlin.time.Instant

data class BenchmarkPoint(
    val timestamp: Instant,
    val value: Double,
    val cumulativeReturn: Double,
)
