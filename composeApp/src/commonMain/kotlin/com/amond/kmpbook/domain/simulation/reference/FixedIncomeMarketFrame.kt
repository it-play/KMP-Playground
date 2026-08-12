package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.reference.CreditSpreadSnapshot
import com.amond.kmpbook.domain.model.reference.YieldCurveSnapshot

/** 한 시각의 다중통화 명목·실질곡선과 신용스프레드 묶음이다. */
data class FixedIncomeMarketFrame(
    val nominalCurves: Map<ReferenceCurrency, YieldCurveSnapshot>,
    val realCurves: Map<ReferenceCurrency, YieldCurveSnapshot>,
    val creditSpreads: Map<ReferenceCurrency, CreditSpreadSnapshot>,
) {
    init {
        require(nominalCurves.isNotEmpty())
        require(nominalCurves.keys == creditSpreads.keys)
        require(realCurves.keys == nominalCurves.keys)
    }
}
