package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef

/** 캠페인에서 공유되는 모든 KOFR 지수 상태다. */
data class KofrIndexBook(
    val states: Map<BenchmarkRef, KofrIndexState>,
) {
    init {
        require(states.isNotEmpty() && states.size <= MAX_REFERENCES)
        require(states.all { (ref, state) -> ref == state.benchmarkRef })
    }

    companion object {
        const val MAX_REFERENCES: Int = 64
    }
}
