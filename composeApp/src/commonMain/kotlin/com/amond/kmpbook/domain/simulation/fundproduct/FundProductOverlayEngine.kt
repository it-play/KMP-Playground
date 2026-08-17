package com.amond.kmpbook.domain.simulation.fundproduct

import com.amond.kmpbook.domain.model.fund.FundProductProfile
import com.amond.kmpbook.domain.model.fund.FundReplicationMode
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import kotlin.math.sqrt
import kotlin.time.Instant

/**
 * 공유 벤치마크 수익률과 분리된 상장 상품 고유의 추적오차와 명시적 운용 오버레이를 생성한다.
 *
 * 같은 벤치마크를 추종하는 상품은 동일한 기초수익률을 소비하지만, 복제 방식과 운용 오차는
 * 상품 ID별 독립 스트림으로만 가격에 더해진다. null은 완벽 추종을 뜻하지 않는다. 공식 또는
 * 실증 추정값이 없는 상품에는 복제 구조별로 고정된 보수적 모델 가정을 적용하고, 명시적인
 * 0만 상품 고유 추적오차를 비활성화한다. 액티브 alpha·스왑 funding·거래상대방 기대손실은
 * [FundProductProfile.operationProfile]에 버전 고정 수치가 있을 때만 한 번 더해진다.
 */
class FundProductOverlayEngine private constructor(private val seed: Long) {
    fun productOverlayLogReturn(
        productId: String,
        profile: FundProductProfile,
        from: Instant,
        referenceTradingFraction: Double,
    ): Double {
        require(productId.isNotBlank())
        require(referenceTradingFraction.isFinite() && referenceTradingFraction in 0.0..1.0)
        if (referenceTradingFraction == 0.0) return 0.0
        val trackingError = trackingErrorLogReturn(
            productId = productId,
            profile = profile,
            from = from,
            referenceTradingFraction = referenceTradingFraction,
        )
        val parameters = profile.operationProfile?.activeSyntheticSwapModelParameters
            ?: return trackingError
        val yearFraction = referenceTradingFraction / TRADING_HOURS_PER_YEAR
        val activeAlphaShock = DeterministicRandom.keyed(
            seed,
            "fund-product-active-alpha:$productId:${parameters.assumptionId}:${from.epochSeconds}",
        ).nextGaussian()
        val activeAlpha = parameters.activeAlphaAnnualMean * yearFraction +
            activeAlphaShock * parameters.activeAlphaAnnualVolatility * sqrt(yearFraction)
        val swapFunding = -parameters.annualSwapFundingSpread * yearFraction
        val counterpartyExpectedLoss = -parameters.counterpartyDefaultHazardRateAnnual *
            (1.0 - parameters.counterpartyRecoveryRate) *
            parameters.counterpartyExposureFraction * yearFraction
        return trackingError + activeAlpha + swapFunding + counterpartyExpectedLoss
    }

    private fun trackingErrorLogReturn(
        productId: String,
        profile: FundProductProfile,
        from: Instant,
        referenceTradingFraction: Double,
    ): Double {
        val annualVolatility = profile.trackingErrorAnnualVolatility
            ?: defaultAnnualTrackingErrorVolatility(profile.replicationMode)
        if (annualVolatility == 0.0) return 0.0
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

        /**
         * Deterministic engine assumptions used only when a product has no verified estimate.
         * Values are annual standard deviations of product-minus-reference return, not drags.
         */
        private fun defaultAnnualTrackingErrorVolatility(mode: FundReplicationMode): Double =
            when (mode) {
                FundReplicationMode.PHYSICAL_FULL_REPLICATION -> 0.0005
                FundReplicationMode.PHYSICAL_SAMPLING -> 0.0015
                FundReplicationMode.DERIVATIVE_SYNTHETIC -> 0.0010
                FundReplicationMode.HYBRID -> 0.0020
                FundReplicationMode.ACTIVE_MANAGEMENT -> 0.0040
                FundReplicationMode.SYNTHETIC_NOTE -> 0.0010
                FundReplicationMode.UNVERIFIED -> 0.0030
            }
    }
}
