package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.reference.FuturesAllocationMode
import com.amond.kmpbook.domain.model.reference.FuturesCurveSnapshot
import com.amond.kmpbook.domain.model.reference.FuturesReferenceState
import com.amond.kmpbook.domain.model.reference.FuturesReferenceTerms
import com.amond.kmpbook.domain.model.reference.FuturesRollCalendar
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Injected curves, calendar closes and optional externally compiled basket targets. */
data class FuturesAdvanceInput(
    val state: FuturesReferenceState,
    val terms: FuturesReferenceTerms,
    val curvesBySleeveId: Map<String, FuturesCurveSnapshot>,
    val cashRateAnnual: Double,
    val elapsedYearFraction: Double,
    val referenceTradingDates: Map<FuturesRollCalendar, LocalDate>,
    val tradingClosesAtEnd: Set<FuturesRollCalendar>,
    val rebalanceAtEnd: Boolean,
    val externalTargetWeights: Map<String, Double>?,
    val to: Instant,
) {
    init {
        require(state.benchmarkRef == terms.benchmarkRef)
        require(state.baseCurrency == terms.baseCurrency)
        require(state.portfolioStyle == terms.portfolioStyle)
        require(state.allocationMode == terms.allocationMode)
        require(to > state.asOf)
        val sleeveIds = terms.sleeves.mapTo(linkedSetOf()) { it.sleeveId }
        require(state.sleeves.mapTo(linkedSetOf()) { it.sleeveId } == sleeveIds)
        require(curvesBySleeveId.keys == sleeveIds)
        require(curvesBySleeveId.all { (sleeveId, curve) ->
            curve.sleeveId == sleeveId && curve.asOf == to
        })
        require(cashRateAnnual.isFinite() && cashRateAnnual in -0.10..1.0)
        require(elapsedYearFraction.isFinite() && elapsedYearFraction in 0.0..1.0)
        val calendars = terms.sleeves.mapTo(linkedSetOf()) { it.rollCalendar }
        require(referenceTradingDates.keys == calendars)
        require(tradingClosesAtEnd.all(calendars::contains))
        require(tradingClosesAtEnd.all { calendar ->
            calendar.isTradingDate(referenceTradingDates.getValue(calendar))
        }) { "Futures roll closes must be valid dates on their explicit roll calendars." }
        require(state.sleeves.all { sleeve ->
            sleeve.lastRollTradingDate == null ||
                referenceTradingDates.getValue(sleeve.rollCalendar) >= sleeve.lastRollTradingDate
        })
        when (terms.allocationMode) {
            FuturesAllocationMode.STATIC_TARGETS -> require(externalTargetWeights == null)
            FuturesAllocationMode.EXTERNAL_TARGETS -> {
                if (rebalanceAtEnd) requireNotNull(externalTargetWeights)
                if (!rebalanceAtEnd) require(externalTargetWeights == null)
            }
        }
        externalTargetWeights?.let { weights ->
            require(weights.keys == sleeveIds)
            require(weights.values.all { it.isFinite() && it in 0.0..1.0 })
            require(abs(weights.values.sum() - 1.0) <= FuturesReferenceTerms.WEIGHT_EPSILON)
        }
    }
}
