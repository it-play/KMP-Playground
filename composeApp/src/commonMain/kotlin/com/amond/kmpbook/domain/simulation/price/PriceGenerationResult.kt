package com.amond.kmpbook.domain.simulation.price

import com.amond.kmpbook.domain.model.pricing.PriceBar
import com.amond.kmpbook.domain.model.pricing.Quote
import com.amond.kmpbook.domain.simulation.protection.TradingStabilizer

data class PriceGenerationResult(
    val bar: PriceBar,
    val quote: Quote,
    val closeValueKrw: Double,
    val attribution: PriceAttribution,
    val stabilizer: TradingStabilizer,
    val wasClamped: Boolean,
)
