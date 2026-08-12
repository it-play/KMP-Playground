package com.amond.kmpbook.domain.model.fundstructure

import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlinx.datetime.LocalDate

/**
 * Legal and economic terms of an issuer's senior unsecured exchange-traded note.
 *
 * There is deliberately no holdings or NAV field: an ETN is issuer debt whose contractual value
 * references [referenceId]. Its market credit spread is also separate from its indicative value.
 */
data class EtnProductTerms(
    val productId: String,
    val referenceId: String,
    val issuerId: String,
    val settlementCurrency: ReferenceCurrency,
    val statedPrincipalPerNote: Double,
    val annualInvestorFeeRate: Double,
    val investorFeeDayCountBasis: Int,
    val issueDate: LocalDate,
    val maturityDate: LocalDate,
    val maturityValuationRule: EtnSettlementValuationRule,
    val maturitySettlementMultiplier: Double,
    val maturityIncludesAccruedCoupon: Boolean,
    val couponRule: EtnCouponRule,
    val callTerms: EtnCallTerms,
    val accelerationTerms: EtnAccelerationTerms,
    val termsProvenance: FundStructureTermsProvenance,
    val officialSourceUrl: String?,
) {
    init {
        requireFundStructureId(productId, "productId")
        requireFundStructureId(referenceId, "referenceId")
        requireFundStructureId(issuerId, "issuerId")
        requirePositiveAmount(statedPrincipalPerNote, "statedPrincipalPerNote")
        require(annualInvestorFeeRate.isFinite() && annualInvestorFeeRate in 0.0..MAX_RATE)
        require(investorFeeDayCountBasis in 1..366)
        require(annualInvestorFeeRate < investorFeeDayCountBasis.toDouble())
        require(issueDate < maturityDate)
        require(
            maturitySettlementMultiplier.isFinite() &&
                maturitySettlementMultiplier in MIN_FUND_STRUCTURE_VALUE..MAX_RATE,
        )
        officialSourceUrl?.let(::requireOfficialSourceUrl)
        if (termsProvenance != FundStructureTermsProvenance.MODEL_ASSUMPTION) {
            requireNotNull(officialSourceUrl) {
                "검증 또는 부분 검증한 ETN 조건에는 공식 1차 출처가 필요합니다."
            }
        }
    }
}
