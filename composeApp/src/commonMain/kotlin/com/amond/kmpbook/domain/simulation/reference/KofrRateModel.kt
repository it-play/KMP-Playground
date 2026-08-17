package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.reference.KofrIndexState
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import kotlin.math.max
import kotlin.math.min
import kotlinx.datetime.LocalDate

/**
 * KOFR 적격 RP 거래 표본을 결정론적으로 만들고 거래금액 양 끝을 절사한 가중평균을 계산한다.
 * 실제 거래 원장은 외부 데이터가 아니므로 정책금리 주변의 시뮬레이션 표본이다.
 */
class KofrRateModel(private val seed: Long) {
    fun fixingRateAnnual(
        benchmarkRef: BenchmarkRef,
        observationDate: LocalDate,
        koreanPolicyRateAnnual: Double,
        volumeTrimFractionPerTail: Double,
        calculationRatePercentDecimalPlaces: Int,
    ): Double {
        require(koreanPolicyRateAnnual.isFinite() &&
            koreanPolicyRateAnnual in KofrIndexState.MIN_RATE..KofrIndexState.MAX_RATE)
        require(volumeTrimFractionPerTail.isFinite() && volumeTrimFractionPerTail in 0.0..<0.5)
        require(calculationRatePercentDecimalPlaces in 0..12)
        val random = DeterministicRandom.keyed(
            seed,
            "kofr:${benchmarkRef.benchmarkId}:v${benchmarkRef.version}:$observationDate",
        )
        val marketCentre = (
            koreanPolicyRateAnnual + BASE_SECURED_REPO_BASIS_ANNUAL +
                random.nextGaussian() * DAILY_FIXING_VOLATILITY_ANNUAL
            ).coerceIn(KofrIndexState.MIN_RATE, KofrIndexState.MAX_RATE)
        val transactions = List(ELIGIBLE_TRANSACTION_COUNT) {
            val participantSkew = random.nextGaussian() * TRANSACTION_RATE_DISPERSION_ANNUAL
            val notional = MIN_TRANSACTION_NOTIONAL * (
                1.0 + NOTIONAL_RANGE_MULTIPLIER * random.nextDouble() * random.nextDouble()
                )
            RepoTransaction(
                rateAnnual = (marketCentre + participantSkew)
                    .coerceIn(KofrIndexState.MIN_RATE, KofrIndexState.MAX_RATE),
                notional = notional,
            )
        }
        val unrounded = trimmedVolumeWeightedMean(transactions, volumeTrimFractionPerTail)
            .coerceIn(KofrIndexState.MIN_RATE, KofrIndexState.MAX_RATE)
        return KofrOfficialRounding.halfUp(
            unrounded,
            calculationRatePercentDecimalPlaces + PERCENT_TO_ANNUAL_DECIMAL_PLACES,
        )
    }

    private fun trimmedVolumeWeightedMean(
        transactions: List<RepoTransaction>,
        trimFractionPerTail: Double,
    ): Double {
        require(transactions.isNotEmpty())
        val sorted = transactions.sortedBy(RepoTransaction::rateAnnual)
        val totalNotional = sorted.sumOf(RepoTransaction::notional)
        val retainedLowerBound = totalNotional * trimFractionPerTail
        val retainedUpperBound = totalNotional * (1.0 - trimFractionPerTail)
        var cumulative = 0.0
        var retainedNotional = 0.0
        var weightedRate = 0.0
        sorted.forEach { transaction ->
            val start = cumulative
            val end = start + transaction.notional
            val retained = max(
                0.0,
                min(end, retainedUpperBound) - max(start, retainedLowerBound),
            )
            retainedNotional += retained
            weightedRate += retained * transaction.rateAnnual
            cumulative = end
        }
        require(retainedNotional > 0.0)
        return weightedRate / retainedNotional
    }

    private data class RepoTransaction(
        val rateAnnual: Double,
        val notional: Double,
    )

    companion object {
        /** Runtime와 save canonical replay가 공유하는 고정 하위 스트림이다. */
        fun forCampaignSeed(campaignSeed: Long): KofrRateModel =
            KofrRateModel(DeterministicRandom.mixSeed(campaignSeed, STREAM_ID))

        private const val ELIGIBLE_TRANSACTION_COUNT: Int = 128
        private const val BASE_SECURED_REPO_BASIS_ANNUAL: Double = 0.00004
        private const val DAILY_FIXING_VOLATILITY_ANNUAL: Double = 0.00008
        private const val TRANSACTION_RATE_DISPERSION_ANNUAL: Double = 0.00018
        private const val MIN_TRANSACTION_NOTIONAL: Double = 10_000_000_000.0
        private const val NOTIONAL_RANGE_MULTIPLIER: Double = 30.0
        private const val PERCENT_TO_ANNUAL_DECIMAL_PLACES: Int = 2
        private const val STREAM_ID: Long = 0x4B4F4652L // "KOFR"
    }
}
