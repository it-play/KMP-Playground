package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef

/** Vectorized hourly returns and any representative-basket ledger entries. */
class EquityReferenceBookAdvance private constructor(
    val book: EquityReferenceBook,
    grossReferenceLogReturns: Map<BenchmarkRef, Double>,
    estimatedAnnualIncomeYields: Map<BenchmarkRef, Double>,
    rebalanceRecords: List<EquityReferenceRebalanceRecord>,
    copyCollectionInputs: Boolean,
) {
    constructor(
        book: EquityReferenceBook,
        grossReferenceLogReturns: Map<BenchmarkRef, Double>,
        estimatedAnnualIncomeYields: Map<BenchmarkRef, Double>,
        rebalanceRecords: List<EquityReferenceRebalanceRecord>,
    ) : this(
        book = book,
        grossReferenceLogReturns = grossReferenceLogReturns,
        estimatedAnnualIncomeYields = estimatedAnnualIncomeYields,
        rebalanceRecords = rebalanceRecords,
        copyCollectionInputs = true,
    )

    val grossReferenceLogReturns: Map<BenchmarkRef, Double> = if (copyCollectionInputs) {
        grossReferenceLogReturns.toSortedMap().toMap()
    } else {
        grossReferenceLogReturns
    }
    val estimatedAnnualIncomeYields: Map<BenchmarkRef, Double> = if (copyCollectionInputs) {
        estimatedAnnualIncomeYields.toSortedMap().toMap()
    } else {
        estimatedAnnualIncomeYields
    }
    val rebalanceRecords: List<EquityReferenceRebalanceRecord> = if (copyCollectionInputs) {
        rebalanceRecords.toList()
    } else {
        rebalanceRecords
    }

    init {
        if (copyCollectionInputs) {
            require(this.grossReferenceLogReturns.keys == book.states.keys)
            require(this.estimatedAnnualIncomeYields.keys == book.states.keys)
            require(this.grossReferenceLogReturns.values.all(Double::isFinite))
            require(this.estimatedAnnualIncomeYields.values.all { it.isFinite() && it in 0.0..1.0 })
            require(this.rebalanceRecords == this.rebalanceRecords.sortedWith(
                compareBy<EquityReferenceRebalanceRecord> { it.benchmarkRef }.thenBy { it.revision },
            ))
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is EquityReferenceBookAdvance &&
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

    companion object {
        /** Internal fast path for collections freshly allocated and exclusively owned by the engine. */
        internal fun fromOwnedCollections(
            book: EquityReferenceBook,
            grossReferenceLogReturns: Map<BenchmarkRef, Double>,
            estimatedAnnualIncomeYields: Map<BenchmarkRef, Double>,
            rebalanceRecords: List<EquityReferenceRebalanceRecord>,
        ): EquityReferenceBookAdvance = EquityReferenceBookAdvance(
            book = book,
            grossReferenceLogReturns = grossReferenceLogReturns,
            estimatedAnnualIncomeYields = estimatedAnnualIncomeYields,
            rebalanceRecords = rebalanceRecords,
            copyCollectionInputs = false,
        )
    }
}
