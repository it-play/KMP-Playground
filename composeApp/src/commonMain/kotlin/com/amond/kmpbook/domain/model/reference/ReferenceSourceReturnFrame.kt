package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef

/** One interval of already-generated benchmark and operating-company source observations. */
class ReferenceSourceReturnFrame(
    benchmarkLogReturns: Map<BenchmarkRef, Double>,
    benchmarkAnnualIncomeYields: Map<BenchmarkRef, Double>,
    benchmarkDurationsYears: Map<BenchmarkRef, Double>,
    instrumentLogReturns: Map<String, Double>,
    instrumentAnnualIncomeYields: Map<String, Double>,
    instrumentDurationsYears: Map<String, Double>,
    instrumentAvailability: Map<String, Boolean>,
    fxLogReturns: Map<ReferenceCurrencyPair, Double>,
) {
    val benchmarkLogReturns = benchmarkLogReturns.toSortedMap().toMap()
    val benchmarkAnnualIncomeYields = benchmarkAnnualIncomeYields.toSortedMap().toMap()
    val benchmarkDurationsYears = benchmarkDurationsYears.toSortedMap().toMap()
    val instrumentLogReturns = instrumentLogReturns.toSortedMap().toMap()
    val instrumentAnnualIncomeYields = instrumentAnnualIncomeYields.toSortedMap().toMap()
    val instrumentDurationsYears = instrumentDurationsYears.toSortedMap().toMap()
    val instrumentAvailability = instrumentAvailability.toSortedMap().toMap()
    val fxLogReturns = fxLogReturns.toSortedMap().toMap()

    init {
        require(this.benchmarkLogReturns.values.all(Double::isFinite))
        require(this.instrumentLogReturns.values.all(Double::isFinite))
        require(this.fxLogReturns.values.all(Double::isFinite))
        require(this.benchmarkAnnualIncomeYields.values.all(::validYield))
        require(this.instrumentAnnualIncomeYields.values.all(::validYield))
        require(this.benchmarkDurationsYears.values.all(::validDuration))
        require(this.instrumentDurationsYears.values.all(::validDuration))
        require(this.instrumentAvailability.keys == this.instrumentLogReturns.keys)
        require(this.instrumentAvailability.keys == this.instrumentAnnualIncomeYields.keys)
    }

    private fun validYield(value: Double): Boolean = value.isFinite() && value in 0.0..1.0

    private fun validDuration(value: Double): Boolean = value.isFinite() && value in -50.0..50.0
}
