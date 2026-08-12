package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef

/** Reference price returns, income yields and composition records for one hourly batch. */
class FundOfFundsBookAdvance(
    val book: FundOfFundsBook,
    grossReferenceLogReturns: Map<BenchmarkRef, Double>,
    estimatedAnnualIncomeYields: Map<BenchmarkRef, Double>,
    rebalanceRecords: List<FundOfFundsRebalanceRecord>,
) {
    val grossReferenceLogReturns: Map<BenchmarkRef, Double> =
        grossReferenceLogReturns.toSortedMap().toMap()
    val estimatedAnnualIncomeYields: Map<BenchmarkRef, Double> =
        estimatedAnnualIncomeYields.toSortedMap().toMap()
    val rebalanceRecords: List<FundOfFundsRebalanceRecord> = rebalanceRecords.toList()

    init {
        require(grossReferenceLogReturns.keys == book.states.keys)
        require(estimatedAnnualIncomeYields.keys == book.states.keys)
        require(grossReferenceLogReturns.values.all(Double::isFinite))
        require(estimatedAnnualIncomeYields.values.all { value -> value.isFinite() && value in 0.0..1.0 })
        require(rebalanceRecords == rebalanceRecords.sortedWith(
            compareBy<FundOfFundsRebalanceRecord> { record -> record.effectiveAt }
                .thenBy(FundOfFundsRebalanceRecord::benchmarkRef)
                .thenBy(FundOfFundsRebalanceRecord::revision),
        ))
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FundOfFundsBookAdvance &&
            book == other.book &&
            grossReferenceLogReturns == other.grossReferenceLogReturns &&
            estimatedAnnualIncomeYields == other.estimatedAnnualIncomeYields &&
            rebalanceRecords == other.rebalanceRecords

    override fun hashCode(): Int {
        var result = book.hashCode()
        result = 31 * result + grossReferenceLogReturns.hashCode()
        result = 31 * result + estimatedAnnualIncomeYields.hashCode()
        result = 31 * result + rebalanceRecords.hashCode()
        return result
    }
}
