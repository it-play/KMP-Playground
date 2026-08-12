package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef

/** 캠페인에서 실행되는 모든 고정수익 benchmark 상태다. */
data class FixedIncomeReferenceBook(
    val states: Map<BenchmarkRef, FixedIncomeReferenceState>,
) {
    init {
        require(states.isNotEmpty() && states.size <= MAX_REFERENCES)
        require(states.all { (ref, state) -> state.benchmarkRef == ref })
    }

    companion object {
        const val MAX_REFERENCES: Int = 4_096
    }
}
