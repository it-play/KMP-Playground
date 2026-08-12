package com.amond.kmpbook.domain.simulation.fundproduct

import com.amond.kmpbook.domain.model.fund.FundProductProfile
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import kotlin.math.sqrt
import kotlin.time.Instant

/**
 * 공유 벤치마크 수익률과 분리된 상장 상품 고유의 추적오차를 생성한다.
 *
 * 같은 벤치마크를 추종하는 상품은 동일한 기초수익률을 소비하지만, 복제 방식과 운용 오차는
 * 상품 ID별 독립 스트림으로만 가격에 더해진다. 검증되지 않은 null 추적오차는 숫자를 지어내지
 * 않고 0으로 처리한다.
 */
class FundProductOverlayEngine private constructor(private val seed: Long) {
    fun trackingErrorLogReturn(
        productId: String,
        profile: FundProductProfile,
        from: Instant,
        referenceTradingFraction: Double,
    ): Double {
        require(productId.isNotBlank())
        require(referenceTradingFraction.isFinite() && referenceTradingFraction in 0.0..1.0)
        val annualVolatility = profile.trackingErrorAnnualVolatility ?: return 0.0
        if (annualVolatility == 0.0 || referenceTradingFraction == 0.0) return 0.0
        val key = buildString {
            append("fund-product-tracking:")
            append(productId)
            append(':')
            append(profile.benchmarkRef.benchmarkId)
            append(":v")
            append(profile.benchmarkRef.version)
            append(':')
            append(from.epochSeconds)
        }
        val shock = DeterministicRandom.keyed(seed, key).nextGaussian()
        return shock * annualVolatility * sqrt(referenceTradingFraction / TRADING_HOURS_PER_YEAR)
    }

    companion object {
        fun forCampaignSeed(seed: Long): FundProductOverlayEngine =
            FundProductOverlayEngine(DeterministicRandom.mixSeed(seed, STREAM_ID))

        private const val TRADING_HOURS_PER_YEAR: Double = 252.0 * 6.5
        private const val STREAM_ID: Long = 0x46554E4450524F44L // "FUNDPROD"
    }
}
