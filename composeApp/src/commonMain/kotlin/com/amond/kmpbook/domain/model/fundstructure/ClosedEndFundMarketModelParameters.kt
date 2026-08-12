package com.amond.kmpbook.domain.model.fundstructure

/** Game calibration for CEF market discount behavior; these are not legal fund terms. */
data class ClosedEndFundMarketModelParameters(
    val fundId: String,
    val targetMarketDiscountRate: Double,
    val annualDiscountMeanReversionRate: Double,
    val initialDebtToGrossAssets: Double,
    val initialPreferredToGrossAssets: Double,
    val annualBorrowingSpread: Double,
    val annualPreferredDistributionSpread: Double,
    val discountShockAnnualVolatility: Double,
    val origin: FundStructureModelParameterOrigin,
    val sourceUrl: String?,
) {
    init {
        requireFundStructureId(fundId, "fundId")
        require(targetMarketDiscountRate.isFinite() && targetMarketDiscountRate in -0.99..MAX_RATE)
        require(
            annualDiscountMeanReversionRate.isFinite() &&
                annualDiscountMeanReversionRate in 0.0..MAX_RATE,
        )
        require(initialDebtToGrossAssets.isFinite() && initialDebtToGrossAssets in 0.0..MAX_LEVERAGE_RATIO)
        require(
            initialPreferredToGrossAssets.isFinite() &&
                initialPreferredToGrossAssets in 0.0..MAX_LEVERAGE_RATIO,
        )
        require(initialDebtToGrossAssets + initialPreferredToGrossAssets <= MAX_LEVERAGE_RATIO) {
            "CEF 초기 부채·우선주 레버리지 합계가 총자산의 $MAX_LEVERAGE_RATIO 이하여야 합니다."
        }
        require(annualBorrowingSpread.isFinite() && annualBorrowingSpread in 0.0..MAX_FINANCING_SPREAD)
        require(
            annualPreferredDistributionSpread.isFinite() &&
                annualPreferredDistributionSpread in 0.0..MAX_FINANCING_SPREAD,
        )
        require(
            discountShockAnnualVolatility.isFinite() &&
                discountShockAnnualVolatility in 0.0..MAX_DISCOUNT_VOLATILITY,
        )
        sourceUrl?.let(::requireOfficialSourceUrl)
        if (origin == FundStructureModelParameterOrigin.OFFICIAL_DISCLOSURE) {
            requireNotNull(sourceUrl) {
                "공식 공시 기반 CEF 시장 모수에는 공식 출처가 필요합니다."
            }
        }
    }

    companion object {
        const val MAX_LEVERAGE_RATIO: Double = 0.95
        const val MAX_FINANCING_SPREAD: Double = 1.0
        const val MAX_DISCOUNT_VOLATILITY: Double = 5.0
    }
}
