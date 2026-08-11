package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.index.MarketIndexId
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** 대표 시장 지수의 현재값이다. */
data class ModMarketIndexSnapshot(
    val id: MarketIndexId,
    val timestamp: Instant,
    val value: Double,
    val previousClose: Double,
    val changeRate: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val constituentCount: Int,
    val sessionDate: LocalDate?,
)
