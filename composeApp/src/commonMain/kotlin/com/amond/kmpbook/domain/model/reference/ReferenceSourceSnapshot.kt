package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef

/** Initial source income and duration observations used to bootstrap reference state. */
class ReferenceSourceSnapshot(
    benchmarkAnnualIncomeYields: Map<BenchmarkRef, Double>,
    benchmarkDurationsYears: Map<BenchmarkRef, Double>,
    instrumentAnnualIncomeYields: Map<String, Double>,
    instrumentDurationsYears: Map<String, Double>,
    instrumentAvailability: Map<String, Boolean>,
    val mortgageRateAnnual: Double,
) {
    val benchmarkAnnualIncomeYields = benchmarkAnnualIncomeYields.toSortedMap().toMap()
    val benchmarkDurationsYears = benchmarkDurationsYears.toSortedMap().toMap()
    val instrumentAnnualIncomeYields = instrumentAnnualIncomeYields.toSortedMap().toMap()
    val instrumentDurationsYears = instrumentDurationsYears.toSortedMap().toMap()
    val instrumentAvailability = instrumentAvailability.toSortedMap().toMap()

    init {
        require(this.benchmarkAnnualIncomeYields.values.all(::validYield))
        require(this.instrumentAnnualIncomeYields.values.all(::validYield))
        require(this.benchmarkDurationsYears.values.all(::validDuration))
        require(this.instrumentDurationsYears.values.all(::validDuration))
        require(this.instrumentAvailability.keys == this.instrumentAnnualIncomeYields.keys)
        require(mortgageRateAnnual.isFinite() && mortgageRateAnnual in 0.0..1.0)
    }

    private fun validYield(value: Double): Boolean = value.isFinite() && value in 0.0..1.0

    private fun validDuration(value: Double): Boolean = value.isFinite() && value in -50.0..50.0
}
