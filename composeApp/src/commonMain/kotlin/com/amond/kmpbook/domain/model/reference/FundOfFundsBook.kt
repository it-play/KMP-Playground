package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.time.Instant

/** Shared states keyed by methodology benchmark rather than listed product. */
class FundOfFundsBook(states: Map<BenchmarkRef, FundOfFundsState>) {
    val states: Map<BenchmarkRef, FundOfFundsState> = states.toSortedMap().toMap()

    init {
        require(states.isNotEmpty() && states.size <= BenchmarkDefinition.MAX_BENCHMARKS_PER_PACK)
        require(states.all { (ref, state) -> ref == state.benchmarkRef })
        require(states.values.map(FundOfFundsState::asOf).distinct().size == 1)
    }

    val asOf: Instant get() = states.values.first().asOf

    override fun equals(other: Any?): Boolean =
        this === other || other is FundOfFundsBook && states == other.states

    override fun hashCode(): Int = states.hashCode()

    override fun toString(): String = "FundOfFundsBook(states=${states.size}, asOf=$asOf)"
}
