package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSource
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSourceKind
import com.amond.kmpbook.domain.model.market.ReferenceCurrency

/** Typed source registry; instrument entries must be operating companies, never fund products. */
class ReferenceSourceCatalog(
    benchmarkDefinitions: Map<BenchmarkRef, BenchmarkDefinition>,
    operatingCompanyCurrencies: Map<String, ReferenceCurrency>,
) {
    val benchmarkDefinitions: Map<BenchmarkRef, BenchmarkDefinition> =
        benchmarkDefinitions.toSortedMap().toMap()
    val operatingCompanyCurrencies: Map<String, ReferenceCurrency> =
        operatingCompanyCurrencies.toSortedMap().toMap()

    init {
        require(this.benchmarkDefinitions.isNotEmpty())
        require(this.benchmarkDefinitions.all { (ref, definition) -> ref == definition.ref })
        require(this.operatingCompanyCurrencies.keys.all(INSTRUMENT_ID_PATTERN::matches))
    }

    fun contains(source: CompositeReferenceSource): Boolean = when (source.kind) {
        CompositeReferenceSourceKind.BENCHMARK -> source.benchmarkRef in benchmarkDefinitions
        CompositeReferenceSourceKind.INSTRUMENT -> source.instrumentId in operatingCompanyCurrencies
    }

    fun currencyOf(source: CompositeReferenceSource): ReferenceCurrency = when (source.kind) {
        CompositeReferenceSourceKind.BENCHMARK ->
            benchmarkDefinitions.getValue(requireNotNull(source.benchmarkRef)).baseCurrency
        CompositeReferenceSourceKind.INSTRUMENT ->
            operatingCompanyCurrencies.getValue(requireNotNull(source.instrumentId))
    }

    companion object {
        private val INSTRUMENT_ID_PATTERN = Regex("[A-Z_]+:[A-Za-z0-9.]{1,96}")
    }
}
