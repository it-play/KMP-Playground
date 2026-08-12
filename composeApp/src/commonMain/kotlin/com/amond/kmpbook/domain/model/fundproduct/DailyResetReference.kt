package com.amond.kmpbook.domain.model.fundproduct

import com.amond.kmpbook.domain.model.fund.BenchmarkRef

/** 목표배율의 기초가 공유 benchmark인지 단일 거래종목인지 구분하는 tagged reference다. */
data class DailyResetReference(
    val kind: DailyResetReferenceKind,
    val benchmarkRef: BenchmarkRef?,
    val instrumentId: String?,
) {
    init {
        when (kind) {
            DailyResetReferenceKind.BENCHMARK -> {
                requireNotNull(benchmarkRef)
                require(instrumentId == null)
            }
            DailyResetReferenceKind.INSTRUMENT -> {
                require(benchmarkRef == null)
                requireNotNull(instrumentId)
                require(INSTRUMENT_ID.matches(instrumentId))
            }
        }
    }

    companion object {
        private val INSTRUMENT_ID = Regex("[A-Z_]+:[A-Za-z0-9.]+")
    }
}
