package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.math.abs
import kotlin.math.round
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** 마지막 공표 KOFR, 다음 공표 fixing과 일복리 지수 수준을 함께 저장한다. */
data class KofrIndexState(
    val benchmarkRef: BenchmarkRef,
    val publishedRateAnnual: Double,
    val publishedRateObservationDate: LocalDate,
    val indexLevel: Double,
    val indexPublicationDate: LocalDate,
    val pendingRateAnnual: Double?,
    val pendingRateObservationDate: LocalDate?,
    val revision: Long,
    val asOf: Instant,
) {
    init {
        require(publishedRateAnnual.isFinite() && publishedRateAnnual in MIN_RATE..MAX_RATE)
        require(isCanonicalRate(publishedRateAnnual))
        require(indexLevel.isFinite() && indexLevel > 0.0)
        require(publishedRateObservationDate < indexPublicationDate)
        require((pendingRateAnnual == null) == (pendingRateObservationDate == null))
        require(pendingRateAnnual == null || pendingRateAnnual.isFinite() && pendingRateAnnual in MIN_RATE..MAX_RATE)
        require(pendingRateAnnual == null || isCanonicalRate(pendingRateAnnual))
        require(pendingRateObservationDate == null || pendingRateObservationDate > publishedRateObservationDate)
        require(revision >= 0L)
    }

    val referenceId: String get() = referenceIdFor(benchmarkRef)

    companion object {
        const val MIN_RATE: Double = -0.05
        const val MAX_RATE: Double = 1.0
        const val PUBLISHED_ANNUAL_RATE_DECIMAL_PLACES: Int = 5
        private const val ANNUAL_RATE_SCALE: Double = 100_000.0

        private fun isCanonicalRate(value: Double): Boolean =
            abs(value * ANNUAL_RATE_SCALE - round(value * ANNUAL_RATE_SCALE)) <= 1e-6

        fun referenceIdFor(ref: BenchmarkRef): String = "kofr-index:${ref.benchmarkId}:v${ref.version}"
    }
}
