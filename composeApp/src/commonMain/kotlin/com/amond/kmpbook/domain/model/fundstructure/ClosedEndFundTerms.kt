package com.amond.kmpbook.domain.model.fundstructure

import com.amond.kmpbook.domain.model.market.ReferenceCurrency

/**
 * Legal capital-structure terms of one exchange-listed closed-end fund.
 *
 * This model must not be used for PCEF, YYY, or another ETF that owns CEF shares. Those products
 * retain ETF legal and creation/redemption mechanics and use [EtfOfClosedEndFundsReference] only
 * to point at their underlying CEF exposure.
 */
data class ClosedEndFundTerms(
    val fundId: String,
    val settlementCurrency: ReferenceCurrency,
    val distributionPolicy: ClosedEndFundDistributionPolicy,
    val allowsTenderOffers: Boolean,
    val allowsShareRepurchases: Boolean,
    val allowsRightsOfferings: Boolean,
    val allowsAtTheMarketOfferings: Boolean,
    val allowsDebtLeverage: Boolean,
    val allowsPreferredLeverage: Boolean,
    val minimumDebtAssetCoverageRatio: Double?,
    val minimumPreferredAssetCoverageRatio: Double?,
    val termsProvenance: FundStructureTermsProvenance,
    val officialSourceUrl: String?,
) {
    init {
        requireFundStructureId(fundId, "fundId")
        if (allowsDebtLeverage) {
            require(
                minimumDebtAssetCoverageRatio != null &&
                    minimumDebtAssetCoverageRatio.isFinite() &&
                    minimumDebtAssetCoverageRatio in 1.0..MAX_ASSET_COVERAGE_RATIO,
            )
        } else {
            require(minimumDebtAssetCoverageRatio == null)
        }
        if (allowsPreferredLeverage) {
            require(
                minimumPreferredAssetCoverageRatio != null &&
                    minimumPreferredAssetCoverageRatio.isFinite() &&
                    minimumPreferredAssetCoverageRatio in 1.0..MAX_ASSET_COVERAGE_RATIO,
            )
        } else {
            require(minimumPreferredAssetCoverageRatio == null)
        }
        officialSourceUrl?.let(::requireOfficialSourceUrl)
        if (termsProvenance != FundStructureTermsProvenance.MODEL_ASSUMPTION) {
            requireNotNull(officialSourceUrl) {
                "검증 또는 부분 검증한 CEF 조건에는 공식 1차 출처가 필요합니다."
            }
        }
    }

    companion object {
        private const val MAX_ASSET_COVERAGE_RATIO: Double = 1_000.0
    }
}
