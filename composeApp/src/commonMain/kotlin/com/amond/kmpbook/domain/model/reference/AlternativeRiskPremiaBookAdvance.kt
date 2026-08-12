package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef

/** Returns and ledger rows for alternative-risk-premia components, computed once per ref. */
class AlternativeRiskPremiaBookAdvance(
    val book: AlternativeRiskPremiaBook,
    referenceLogReturns: Map<BenchmarkRef, Double>,
    estimatedAnnualIncomeYields: Map<BenchmarkRef, Double>,
    effectiveDurationsYears: Map<BenchmarkRef, Double>,
    rebalanceRecords: List<AlternativeRiskPremiaRebalanceRecord>,
) {
    val referenceLogReturns = referenceLogReturns.toSortedMap().toMap()
    val estimatedAnnualIncomeYields = estimatedAnnualIncomeYields.toSortedMap().toMap()
    val effectiveDurationsYears = effectiveDurationsYears.toSortedMap().toMap()
    val rebalanceRecords = rebalanceRecords.toList()

    init {
        require(this.referenceLogReturns.keys == book.states.keys)
        require(this.estimatedAnnualIncomeYields.keys == book.states.keys)
        require(this.effectiveDurationsYears.keys == book.states.keys)
        require(this.referenceLogReturns.values.all(Double::isFinite))
        require(this.estimatedAnnualIncomeYields.values.all { it.isFinite() && it in 0.0..1.0 })
        require(this.effectiveDurationsYears.values.all(Double::isFinite))
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is AlternativeRiskPremiaBookAdvance &&
            book == other.book && referenceLogReturns == other.referenceLogReturns &&
            estimatedAnnualIncomeYields == other.estimatedAnnualIncomeYields &&
            effectiveDurationsYears == other.effectiveDurationsYears &&
            rebalanceRecords == other.rebalanceRecords

    override fun hashCode(): Int {
        var result = book.hashCode()
        result = 31 * result + referenceLogReturns.hashCode()
        result = 31 * result + estimatedAnnualIncomeYields.hashCode()
        result = 31 * result + effectiveDurationsYears.hashCode()
        result = 31 * result + rebalanceRecords.hashCode()
        return result
    }
}
