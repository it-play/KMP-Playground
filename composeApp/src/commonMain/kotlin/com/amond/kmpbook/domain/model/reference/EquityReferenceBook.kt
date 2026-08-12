package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.time.Instant

/** Unique shared states keyed by benchmark version, never by listed product. */
class EquityReferenceBook private constructor(
    states: Map<BenchmarkRef, EquityReferenceState>,
    copyCollectionInput: Boolean,
) {
    constructor(states: Map<BenchmarkRef, EquityReferenceState>) : this(states, true)

    val states: Map<BenchmarkRef, EquityReferenceState> = if (copyCollectionInput) {
        states.toSortedMap().toMap()
    } else {
        states
    }

    init {
        if (copyCollectionInput) {
            require(this.states.isNotEmpty() && this.states.size <= BenchmarkDefinition.MAX_BENCHMARKS_PER_PACK)
            require(this.states.all { (ref, state) -> state.benchmarkRef == ref })
            require(this.states.values.map(EquityReferenceState::asOf).distinct().size == 1)
        }
    }

    val asOf: Instant get() = states.values.first().asOf

    override fun equals(other: Any?): Boolean =
        this === other || other is EquityReferenceBook && states == other.states

    override fun hashCode(): Int = states.hashCode()

    override fun toString(): String = "EquityReferenceBook(states=${states.size}, asOf=$asOf)"

    companion object {
        /** Internal fast path for a map freshly allocated and exclusively owned by the engine. */
        internal fun fromOwnedStates(
            states: Map<BenchmarkRef, EquityReferenceState>,
        ): EquityReferenceBook = EquityReferenceBook(states, false)
    }
}
