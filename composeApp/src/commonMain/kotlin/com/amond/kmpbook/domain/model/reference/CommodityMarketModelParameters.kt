package com.amond.kmpbook.domain.model.reference

/**
 * Asset-class market-data calibration, deliberately separate from a product or index methodology.
 * No field may be inferred from an exchange symbol.
 */
data class CommodityMarketModelParameters(
    val assetClass: CommodityAssetClass,
    val initialSpotLevel: Double,
    val annualSpotDrift: Double,
    val annualSpotMeanReversionRate: Double,
    val annualSpotVolatility: Double,
    val inflationSurpriseLoading: Double,
    val growthSurpriseLoading: Double,
    val riskSentimentLoading: Double,
    val liquidityStressLoading: Double,
    val baseAnnualizedCurveBasis: Double,
    val curveBasisPolicyRateLoading: Double,
    val curveBasisInflationLoading: Double,
    val curveBasisVolatility: Double,
    val expiryRule: CommodityContractExpiryRule,
    val negativePriceStressThreshold: Double,
    val negativePriceStressScale: Double,
    val origin: CommodityMarketCalibrationOrigin,
    val officialSourceUrl: String?,
    val assumptionId: String?,
) {
    init {
        require(initialSpotLevel.isFinite() && initialSpotLevel in MIN_SPOT..MAX_SPOT)
        require(annualSpotDrift.isFinite() && annualSpotDrift in -1.0..1.0)
        require(
            annualSpotMeanReversionRate.isFinite() &&
                annualSpotMeanReversionRate in 0.0..MAX_MEAN_REVERSION_RATE,
        )
        require(annualSpotVolatility.isFinite() && annualSpotVolatility in 0.0..MAX_VOLATILITY)
        require(inflationSurpriseLoading.isFinite() && inflationSurpriseLoading in -10.0..10.0)
        require(growthSurpriseLoading.isFinite() && growthSurpriseLoading in -10.0..10.0)
        require(riskSentimentLoading.isFinite() && riskSentimentLoading in -10.0..10.0)
        require(liquidityStressLoading.isFinite() && liquidityStressLoading in -10.0..10.0)
        require(baseAnnualizedCurveBasis.isFinite() && baseAnnualizedCurveBasis in -5.0..5.0)
        require(curveBasisPolicyRateLoading.isFinite() && curveBasisPolicyRateLoading in -10.0..10.0)
        require(curveBasisInflationLoading.isFinite() && curveBasisInflationLoading in -10.0..10.0)
        require(curveBasisVolatility.isFinite() && curveBasisVolatility in 0.0..5.0)
        require(negativePriceStressThreshold.isFinite() && negativePriceStressThreshold in 0.0..1.0)
        require(negativePriceStressScale.isFinite() && negativePriceStressScale in 0.0..10.0)
        when (origin) {
            CommodityMarketCalibrationOrigin.VERIFIED_HISTORICAL_ESTIMATE -> {
                requireNotNull(officialSourceUrl)
                require(isValidHttpsUrl(officialSourceUrl))
                require(assumptionId == null)
            }
            CommodityMarketCalibrationOrigin.CALIBRATED_MODEL_ASSUMPTION -> {
                require(officialSourceUrl == null)
                requireNotNull(assumptionId)
                require(ASSUMPTION_ID_PATTERN.matches(assumptionId))
            }
        }
    }

    private fun isValidHttpsUrl(value: String): Boolean =
        value.startsWith("https://") && value.length in 9..2_048 && value.none(Char::isISOControl)

    companion object {
        private const val MIN_SPOT: Double = 1e-9
        private const val MAX_SPOT: Double = 1e12
        private const val MAX_VOLATILITY: Double = 5.0
        private const val MAX_MEAN_REVERSION_RATE: Double = 20.0
        private val ASSUMPTION_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
