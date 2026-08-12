package com.amond.kmpbook.domain.model.fundstructure

/**
 * 발행자별 ETN 신용 스프레드·부도 위험 보정값이다.
 *
 * 법적 상품 조건인 [EtnProductTerms]와 분리하며, 같은 [issuerId]를 쓰는 상품은 카탈로그
 * 검증에서 동일한 보정값을 공유해야 한다.
 */
data class EtnIssuerCreditModelParameters(
    val issuerId: String,
    val initialCreditSpread: Double,
    val initialHazardRate: Double,
    val recoveryRate: Double,
    val annualSpreadMeanReversionRate: Double,
    val spreadShockAnnualVolatility: Double,
    val origin: FundStructureModelParameterOrigin,
    val sourceUrl: String?,
) {
    init {
        requireFundStructureId(issuerId, "issuerId")
        require(initialCreditSpread.isFinite() && initialCreditSpread in 0.0..MAX_CREDIT_SPREAD)
        require(initialHazardRate.isFinite() && initialHazardRate in 0.0..MAX_HAZARD_RATE)
        require(recoveryRate.isFinite() && recoveryRate in 0.0..1.0)
        require(
            annualSpreadMeanReversionRate.isFinite() &&
                annualSpreadMeanReversionRate in 0.0..MAX_MEAN_REVERSION_RATE,
        )
        require(
            spreadShockAnnualVolatility.isFinite() &&
                spreadShockAnnualVolatility in 0.0..MAX_SPREAD_VOLATILITY,
        )
        sourceUrl?.let(::requireOfficialSourceUrl)
        if (origin == FundStructureModelParameterOrigin.OFFICIAL_DISCLOSURE) {
            requireNotNull(sourceUrl) {
                "공식 공시 기반 ETN 발행자 신용 모수에는 공식 출처가 필요합니다."
            }
        }
    }

    companion object {
        const val MAX_CREDIT_SPREAD: Double = 1.0
        const val MAX_HAZARD_RATE: Double = 1.0
        const val MAX_MEAN_REVERSION_RATE: Double = 100.0
        const val MAX_SPREAD_VOLATILITY: Double = 5.0
    }
}
