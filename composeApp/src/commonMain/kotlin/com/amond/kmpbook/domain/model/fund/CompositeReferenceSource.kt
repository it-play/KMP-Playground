package com.amond.kmpbook.domain.model.fund

/** Strict tagged source for a composite or alternative-risk-premia sleeve. */
data class CompositeReferenceSource(
    val kind: CompositeReferenceSourceKind,
    val benchmarkRef: BenchmarkRef?,
    val instrumentId: String?,
) {
    init {
        when (kind) {
            CompositeReferenceSourceKind.BENCHMARK -> {
                requireNotNull(benchmarkRef)
                require(instrumentId == null)
            }
            CompositeReferenceSourceKind.INSTRUMENT -> {
                require(benchmarkRef == null)
                requireNotNull(instrumentId)
                require(INSTRUMENT_ID_PATTERN.matches(instrumentId))
            }
        }
    }

    companion object {
        const val MAX_INSTRUMENT_ID_LENGTH: Int = 128
        private val INSTRUMENT_ID_PATTERN = Regex("[A-Z_]+:[A-Za-z0-9.]{1,96}")
    }
}
