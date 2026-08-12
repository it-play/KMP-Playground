package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.reference.CommodityAssetClass
import com.amond.kmpbook.domain.model.reference.CommodityContractExpiryRule
import com.amond.kmpbook.domain.model.reference.CommodityMarketCalibrationOrigin
import com.amond.kmpbook.domain.model.reference.CommodityMarketModelParameters

/**
 * Coarse asset-class assumptions used by provisional game references. They are deliberately marked
 * as assumptions and are not representations of any named product's disclosed contract schedule.
 */
object CommodityMarketModelParameterCatalog {
    fun calibratedModelAssumptions(): List<CommodityMarketModelParameters> =
        CommodityAssetClass.entries.map(::parameters)

    private fun parameters(assetClass: CommodityAssetClass): CommodityMarketModelParameters {
        val values = when (assetClass) {
            CommodityAssetClass.GOLD -> Values(.025, .80, .18, .80, -.15, -.25, .05, .015, .20, .35, .04)
            CommodityAssetClass.SILVER -> Values(.030, 1.10, .32, .90, .25, .10, -.10, .025, .20, .40, .07)
            CommodityAssetClass.CRUDE_OIL -> Values(.020, 2.50, .45, .80, .90, .25, -.85, .060, .35, .45, .25)
            CommodityAssetClass.NATURAL_GAS -> Values(.010, 3.00, .65, .65, .75, .20, -1.10, .100, .40, .40, .35)
            CommodityAssetClass.REFINED_ENERGY -> Values(.020, 2.25, .40, .70, .80, .20, -.70, .055, .35, .40, .20)
            CommodityAssetClass.INDUSTRIAL_METALS -> Values(.025, 1.25, .30, .75, .70, .20, -.35, .035, .25, .35, .12)
            CommodityAssetClass.GRAINS -> Values(.020, 1.75, .28, .55, .10, -.05, -.20, .040, .15, .30, .12)
            CommodityAssetClass.SOFTS -> Values(.020, 1.75, .34, .50, .05, .00, -.15, .045, .15, .25, .14)
            CommodityAssetClass.LIVESTOCK -> Values(.015, 1.50, .24, .35, .25, .05, -.15, .030, .10, .20, .09)
            CommodityAssetClass.BITCOIN -> Values(.060, .35, .75, .30, .55, 1.20, -1.20, .080, .70, .20, .30)
            CommodityAssetClass.SOLANA -> Values(.080, .45, .95, .25, .65, 1.45, -1.40, .110, .75, .20, .40)
            CommodityAssetClass.OTHER_COMMODITY -> Values(.020, 1.25, .30, .60, .35, .10, -.25, .040, .20, .30, .12)
            CommodityAssetClass.REAL_ASSET_PROXY -> Values(.025, .20, .20, .45, .50, .35, -.30, .020, .15, .20, .07)
        }
        val expiryRule = when (assetClass) {
            CommodityAssetClass.GOLD,
            CommodityAssetClass.SILVER,
            CommodityAssetClass.CRUDE_OIL,
            CommodityAssetClass.NATURAL_GAS,
            CommodityAssetClass.REFINED_ENERGY,
            CommodityAssetClass.INDUSTRIAL_METALS,
            -> CommodityContractExpiryRule.THIRD_LAST_TRADING_DAY
            CommodityAssetClass.GRAINS,
            CommodityAssetClass.SOFTS,
            CommodityAssetClass.LIVESTOCK,
            -> CommodityContractExpiryRule.MID_MONTH_PRECEDING_TRADING_DAY
            CommodityAssetClass.BITCOIN,
            CommodityAssetClass.SOLANA,
            -> CommodityContractExpiryRule.LAST_FRIDAY_PRECEDING_TRADING_DAY
            CommodityAssetClass.OTHER_COMMODITY,
            CommodityAssetClass.REAL_ASSET_PROXY,
            -> CommodityContractExpiryRule.MONTH_END_TRADING_DAY
        }
        val allowsNegativeTail = assetClass == CommodityAssetClass.CRUDE_OIL
        return CommodityMarketModelParameters(
            assetClass = assetClass,
            initialSpotLevel = 100.0,
            annualSpotDrift = values.drift,
            annualSpotMeanReversionRate = values.meanReversion,
            annualSpotVolatility = values.volatility,
            inflationSurpriseLoading = values.inflation,
            growthSurpriseLoading = values.growth,
            riskSentimentLoading = values.sentiment,
            liquidityStressLoading = values.liquidity,
            baseAnnualizedCurveBasis = values.curveBasis,
            curveBasisPolicyRateLoading = values.policyBasis,
            curveBasisInflationLoading = values.inflationBasis,
            curveBasisVolatility = values.basisVolatility,
            expiryRule = expiryRule,
            negativePriceStressThreshold = if (allowsNegativeTail) .72 else 1.0,
            negativePriceStressScale = if (allowsNegativeTail) 7.0 else 0.0,
            origin = CommodityMarketCalibrationOrigin.CALIBRATED_MODEL_ASSUMPTION,
            officialSourceUrl = null,
            assumptionId = "commodity-market-model-v1.${assetClass.name.lowercase()}",
        )
    }

    /** Kept private because it is only a compact table row, not a domain concept. */
    private data class Values(
        val drift: Double,
        val meanReversion: Double,
        val volatility: Double,
        val inflation: Double,
        val growth: Double,
        val sentiment: Double,
        val liquidity: Double,
        val curveBasis: Double,
        val policyBasis: Double,
        val inflationBasis: Double,
        val basisVolatility: Double,
    )
}
