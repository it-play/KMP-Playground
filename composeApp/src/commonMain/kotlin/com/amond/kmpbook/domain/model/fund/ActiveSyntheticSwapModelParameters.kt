package com.amond.kmpbook.domain.model.fund

/**
 * 액티브 합성 ETF가 기준지수 위에 더하는 운용·스왑·거래상대방 오버레이 가정이다.
 *
 * 모두 연율 소수로 저장하며 [activeAlphaAnnualMean]은 비용 차감 전 gross log alpha다.
 * 거래상대방 항목은 무담보 노출분의 기대손실
 * `hazard * (1 - recovery) * exposure`를 뜻하며 실제 상품 공시 수치가 아니다.
 */
data class ActiveSyntheticSwapModelParameters(
    val assumptionId: String,
    val activeAlphaAnnualMean: Double,
    val activeAlphaAnnualVolatility: Double,
    val annualSwapFundingSpread: Double,
    val counterpartyDefaultHazardRateAnnual: Double,
    val counterpartyRecoveryRate: Double,
    val counterpartyExposureFraction: Double,
) {
    init {
        require(ASSUMPTION_ID_PATTERN.matches(assumptionId)) {
            "액티브 합성 상품 가정 ID가 유효하지 않습니다."
        }
        require(activeAlphaAnnualMean.isFinite() && activeAlphaAnnualMean in -1.0..1.0)
        require(
            activeAlphaAnnualVolatility.isFinite() &&
                activeAlphaAnnualVolatility in 0.0..1.0,
        )
        require(annualSwapFundingSpread.isFinite() && annualSwapFundingSpread in 0.0..1.0)
        require(
            counterpartyDefaultHazardRateAnnual.isFinite() &&
                counterpartyDefaultHazardRateAnnual in 0.0..1.0,
        )
        require(counterpartyRecoveryRate.isFinite() && counterpartyRecoveryRate in 0.0..1.0)
        require(
            counterpartyExposureFraction.isFinite() &&
                counterpartyExposureFraction in 0.0..1.0,
        )
    }

    companion object {
        const val MAX_ASSUMPTION_ID_LENGTH: Int = 160
        private val ASSUMPTION_ID_PATTERN =
            Regex("[a-z0-9][a-z0-9._-]{2,${MAX_ASSUMPTION_ID_LENGTH - 1}}")
    }
}
