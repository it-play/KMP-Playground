package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlin.math.abs
import kotlin.time.Instant

/** 저장되는 채권 benchmark의 현재 가격비중과 현금흐름 상태다. */
data class FixedIncomeReferenceState(
    val benchmarkRef: BenchmarkRef,
    val positions: List<FixedIncomeReferencePosition>,
    val nominalCurves: Map<ReferenceCurrency, YieldCurveSnapshot>,
    val realCurves: Map<ReferenceCurrency, YieldCurveSnapshot>,
    val creditSpreads: Map<ReferenceCurrency, CreditSpreadSnapshot>,
    val revision: Long,
    val asOf: Instant,
) {
    init {
        require(positions.isNotEmpty() && positions.size <= MAX_POSITIONS)
        require(positions == positions.sortedBy(FixedIncomeReferencePosition::assetId))
        require(positions.map(FixedIncomeReferencePosition::assetId).distinct().size == positions.size)
        require(abs(positions.sumOf(FixedIncomeReferencePosition::currentWeight) - 1.0) <= WEIGHT_EPSILON)
        require(abs(positions.sumOf(FixedIncomeReferencePosition::targetWeight) - 1.0) <= WEIGHT_EPSILON)
        require(revision >= 0L)
        val currencies = positions.mapTo(linkedSetOf(), FixedIncomeReferencePosition::currency)
        require(nominalCurves.keys == currencies && creditSpreads.keys == currencies)
        require(realCurves.keys.all(currencies::contains))
        require(nominalCurves.all { (currency, curve) ->
            curve.currency == currency && curve.asOf == asOf
        })
        require(realCurves.all { (currency, curve) ->
            curve.currency == currency && curve.asOf == asOf
        })
        require(creditSpreads.all { (currency, spreads) ->
            spreads.currency == currency && spreads.asOf == asOf
        })
        val realRateCurrencies = positions
            .filter { it.kind == FixedIncomeInstrumentKind.INFLATION_LINKED }
            .mapTo(linkedSetOf(), FixedIncomeReferencePosition::currency)
        require(realCurves.keys.containsAll(realRateCurrencies))
    }

    val referenceId: String get() = referenceIdFor(benchmarkRef)

    /** 현재 구성과 곡선에서 파생되는 연환산 현금수익률이다. 저장 중복 없이 분배 계산에 쓴다. */
    val estimatedAnnualIncomeYield: Double
        get() = positions.sumOf { position ->
            val nominalCurve = nominalCurves.getValue(position.currency)
            val cashIncome = when (position.kind) {
                FixedIncomeInstrumentKind.CASH_EQUIVALENT -> nominalCurve.rateAtYears(0.25)
                FixedIncomeInstrumentKind.FLOATING_RATE,
                FixedIncomeInstrumentKind.CLO_TRANCHE,
                -> maxOf(
                    nominalCurve.rateAtYears(0.25) + position.floatingSpreadAnnual,
                    position.floatingRateFloorAnnual,
                )
                else -> position.couponRateAnnual
            }
            position.currentWeight * cashIncome
        }.coerceIn(0.0, 1.0)

    companion object {
        fun referenceIdFor(ref: BenchmarkRef): String =
            "fixed-income:${ref.benchmarkId}:v${ref.version}"

        const val WEIGHT_EPSILON: Double = 1e-8
        const val MAX_POSITIONS: Int = 4_096
    }
}
