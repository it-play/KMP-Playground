package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.time.Instant

/** Shared states for all typed alternative-risk-premia component references. */
class AlternativeRiskPremiaBook(states: Map<BenchmarkRef, AlternativeRiskPremiaState>) {
    val states = states.toSortedMap().toMap()

    init {
        require(this.states.isNotEmpty())
        require(this.states.all { (ref, state) -> ref == state.benchmarkRef })
        require(this.states.values.map(AlternativeRiskPremiaState::asOf).distinct().size == 1)
    }

    val asOf: Instant get() = states.values.first().asOf

    override fun equals(other: Any?): Boolean =
        this === other || other is AlternativeRiskPremiaBook && states == other.states

    override fun hashCode(): Int = states.hashCode()
}
