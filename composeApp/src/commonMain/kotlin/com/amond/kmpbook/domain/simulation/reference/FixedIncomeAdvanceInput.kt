package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.reference.CreditSpreadSnapshot
import com.amond.kmpbook.domain.model.reference.FixedIncomeDefaultEvent
import com.amond.kmpbook.domain.model.reference.FixedIncomeInstrumentKind
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferenceState
import com.amond.kmpbook.domain.model.reference.YieldCurveSnapshot
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlin.time.Instant

/** 채권 수익률 계산에 필요한 시장곡선 변화와 확정 신용사건이다. */
data class FixedIncomeAdvanceInput(
    val state: FixedIncomeReferenceState,
    val currentNominalCurves: Map<ReferenceCurrency, YieldCurveSnapshot>,
    val currentRealCurves: Map<ReferenceCurrency, YieldCurveSnapshot>,
    val currentCreditSpreads: Map<ReferenceCurrency, CreditSpreadSnapshot>,
    val inflationAccrualRateAnnual: Double,
    val elapsedYearFraction: Double,
    /** 분산 채권 sleeve의 연환산 기대 부도손실률(회수 후)이다. */
    val expectedCreditLossRatesAnnual: Map<String, Double>,
    val defaultEvents: List<FixedIncomeDefaultEvent>,
    val to: Instant,
) {
    init {
        require(to > state.asOf)
        val currencies = state.positions.mapTo(linkedSetOf()) { it.currency }
        require(currentNominalCurves.keys.containsAll(currencies))
        require(currentCreditSpreads.keys.containsAll(currencies))
        require(currentNominalCurves.keys == state.nominalCurves.keys)
        require(currentCreditSpreads.keys == state.creditSpreads.keys)
        require(currentNominalCurves.all { (currency, curve) ->
            curve.currency == currency && curve.asOf == to
        })
        require(currentCreditSpreads.all { (currency, spreads) ->
            spreads.currency == currency && spreads.asOf == to
        })
        val inflationLinkedCurrencies = state.positions
            .filter { it.kind == FixedIncomeInstrumentKind.INFLATION_LINKED }
            .mapTo(linkedSetOf()) { it.currency }
        require(currentRealCurves.keys.containsAll(inflationLinkedCurrencies))
        require(currentRealCurves.keys == state.realCurves.keys)
        require(currentRealCurves.all { (currency, curve) ->
            curve.currency == currency && curve.asOf == to
        })
        require(inflationAccrualRateAnnual.isFinite() && inflationAccrualRateAnnual in -0.20..1.0)
        require(elapsedYearFraction.isFinite() && elapsedYearFraction in 0.0..1.0)
        val assetIds = state.positions.mapTo(hashSetOf()) { it.assetId }
        require(expectedCreditLossRatesAnnual.keys == assetIds)
        require(expectedCreditLossRatesAnnual.values.all { it.isFinite() && it in 0.0..1.0 })
        require(defaultEvents.map(FixedIncomeDefaultEvent::assetId).distinct().size == defaultEvents.size)
        require(defaultEvents.all { it.assetId in assetIds })
    }
}
