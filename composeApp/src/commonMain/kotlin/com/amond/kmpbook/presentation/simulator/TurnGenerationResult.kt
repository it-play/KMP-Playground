package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.pricing.PriceBar
import com.amond.kmpbook.domain.model.reference.FundOfFundsBookAdvance
import com.amond.kmpbook.domain.simulation.price.PriceAttribution
import kotlin.time.Instant
import kotlinx.datetime.minus
import kotlinx.datetime.plus

internal data class TurnGenerationResult(
    val bars: Map<String, PriceBar>,
    val stockTradingFractions: Map<String, Double>,
    val stockFirstExecutionTimes: Map<String, Instant>,
    val priceAttributions: Map<String, PriceAttribution>,
    val baseReferenceAdvances: BaseReferenceAdvanceFrame,
    val fundOfFundsAdvance: FundOfFundsBookAdvance?,
)
