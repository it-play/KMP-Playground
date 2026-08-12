package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.market.ReferenceCurrency

/** 채권 benchmark가 보유하는 한 기준 증권 또는 동질적 sleeve다. */
data class FixedIncomeReferencePosition(
    val assetId: String,
    val kind: FixedIncomeInstrumentKind,
    val currency: ReferenceCurrency,
    val creditQuality: CreditQuality,
    val currentWeight: Double,
    val targetWeight: Double,
    val dirtyMarketValue: Double,
    val remainingMaturityYears: Double,
    val modifiedDurationYears: Double,
    val convexityYearsSquared: Double,
    val spreadDurationYears: Double,
    val couponRateAnnual: Double,
    val floatingSpreadAnnual: Double,
    val floatingRateFloorAnnual: Double,
    val inflationIndexRatio: Double,
) {
    init {
        require(ID_PATTERN.matches(assetId))
        require(currentWeight.isFinite() && currentWeight in 0.0..1.0)
        require(targetWeight.isFinite() && targetWeight in 0.0..1.0)
        require(dirtyMarketValue.isFinite() && dirtyMarketValue in MIN_VALUE..MAX_VALUE)
        require(remainingMaturityYears.isFinite() && remainingMaturityYears in 0.0..MAX_YEARS)
        require(modifiedDurationYears.isFinite() && modifiedDurationYears in 0.0..MAX_YEARS)
        require(convexityYearsSquared.isFinite() && convexityYearsSquared in 0.0..MAX_CONVEXITY)
        require(spreadDurationYears.isFinite() && spreadDurationYears in 0.0..MAX_YEARS)
        require(couponRateAnnual.isFinite() && couponRateAnnual in MIN_RATE..MAX_RATE)
        require(floatingSpreadAnnual.isFinite() && floatingSpreadAnnual in MIN_RATE..MAX_RATE)
        require(floatingRateFloorAnnual.isFinite() && floatingRateFloorAnnual in MIN_RATE..MAX_RATE)
        require(inflationIndexRatio.isFinite() && inflationIndexRatio in MIN_INDEX_RATIO..MAX_INDEX_RATIO)
        require(kind != FixedIncomeInstrumentKind.TREASURY || creditQuality == CreditQuality.SOVEREIGN)
        require(kind != FixedIncomeInstrumentKind.INFLATION_LINKED || creditQuality == CreditQuality.SOVEREIGN)
        require(
            kind in setOf(FixedIncomeInstrumentKind.FLOATING_RATE, FixedIncomeInstrumentKind.CLO_TRANCHE) ||
                floatingSpreadAnnual == 0.0,
        )
        require(
            kind in setOf(FixedIncomeInstrumentKind.FLOATING_RATE, FixedIncomeInstrumentKind.CLO_TRANCHE) ||
                floatingRateFloorAnnual == 0.0,
        )
    }

    companion object {
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{2,199}")
        private const val MIN_VALUE: Double = 1e-9
        private const val MAX_VALUE: Double = 1e24
        private const val MAX_YEARS: Double = 100.0
        private const val MAX_CONVEXITY: Double = 10_000.0
        private const val MIN_RATE: Double = -0.10
        private const val MAX_RATE: Double = 2.0
        private const val MIN_INDEX_RATIO: Double = 0.01
        private const val MAX_INDEX_RATIO: Double = 100.0
    }
}
