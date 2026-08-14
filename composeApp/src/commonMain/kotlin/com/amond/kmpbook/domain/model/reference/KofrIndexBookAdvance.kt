package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef

/** KOFR fixing·공표·지수 복리를 한 구간 진행한 순수 결과다. */
data class KofrIndexBookAdvance(
    val book: KofrIndexBook,
    val grossReferenceLogReturns: Map<BenchmarkRef, Double>,
    val publishedAnnualRates: Map<BenchmarkRef, Double>,
) {
    init {
        require(grossReferenceLogReturns.keys == book.states.keys)
        require(publishedAnnualRates.keys == book.states.keys)
        require(grossReferenceLogReturns.values.all(Double::isFinite))
        require(publishedAnnualRates.values.all { it.isFinite() && it in KofrIndexState.MIN_RATE..KofrIndexState.MAX_RATE })
    }
}
