package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.time.Instant

/** One state per composite benchmark, never per listed ETF. */
class CompositeReferenceBook(states: Map<BenchmarkRef, CompositeReferenceState>) {
    val states = states.toSortedMap().toMap()

    init {
        require(this.states.isNotEmpty())
        require(this.states.all { (ref, state) -> ref == state.benchmarkRef })
        require(this.states.values.map(CompositeReferenceState::asOf).distinct().size == 1)
    }

    val asOf: Instant get() = states.values.first().asOf

    override fun equals(other: Any?): Boolean =
        this === other || other is CompositeReferenceBook && states == other.states

    override fun hashCode(): Int = states.hashCode()
}
