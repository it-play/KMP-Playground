package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef

/** 모든 고정수익 benchmark를 한 번씩 평가한 원자적 결과다. */
data class FixedIncomeReferenceBookAdvance(
    val book: FixedIncomeReferenceBook,
    val grossReferenceLogReturns: Map<BenchmarkRef, Double>,
    val annualIncomeYields: Map<BenchmarkRef, Double>,
    val rollRecords: List<FixedIncomeRollRecord>,
) {
    init {
        require(grossReferenceLogReturns.keys == book.states.keys)
        require(annualIncomeYields.keys == book.states.keys)
        require(grossReferenceLogReturns.values.all(Double::isFinite))
        require(annualIncomeYields.values.all { it.isFinite() && it in 0.0..1.0 })
        require(rollRecords == rollRecords.sortedWith(
            compareBy<FixedIncomeRollRecord> { it.benchmarkRef }.thenBy { it.id },
        ))
        require(rollRecords.map { it.benchmarkRef }.distinct().size == rollRecords.size)
    }
}
