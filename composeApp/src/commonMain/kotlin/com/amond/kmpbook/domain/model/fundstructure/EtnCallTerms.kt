package com.amond.kmpbook.domain.model.fundstructure

/** Issuer-call and holder-redemption rights stated in an ETN pricing supplement. */
data class EtnCallTerms(
    val issuerCallable: Boolean,
    val issuerCallMayBePartial: Boolean,
    val holderRedeemable: Boolean,
    val minimumHolderRedemptionNotes: Long?,
    val holderRedemptionNoteIncrement: Long?,
    val minimumNoticeBusinessDays: Int,
    val issuerCallValuationRule: EtnSettlementValuationRule?,
    val holderRedemptionValuationRule: EtnSettlementValuationRule?,
    val issuerCallSettlementMultiplier: Double,
    val holderRedemptionSettlementMultiplier: Double,
    val holderRedemptionChargeRate: Double,
    val includesAccruedCoupon: Boolean,
) {
    init {
        require(minimumNoticeBusinessDays in 0..365)
        require(
            issuerCallSettlementMultiplier.isFinite() &&
                issuerCallSettlementMultiplier in 0.0..MAX_RATE,
        )
        require(
            holderRedemptionSettlementMultiplier.isFinite() &&
                holderRedemptionSettlementMultiplier in 0.0..MAX_RATE,
        )
        require(holderRedemptionChargeRate.isFinite() && holderRedemptionChargeRate in 0.0..1.0)
        require(holderRedemptionChargeRate <= holderRedemptionSettlementMultiplier)
        if (holderRedeemable) {
            require(minimumHolderRedemptionNotes != null)
            require(minimumHolderRedemptionNotes in 1..MAX_EXACT_INTEGER_QUANTITY)
            require(holderRedemptionNoteIncrement != null)
            require(holderRedemptionNoteIncrement in 1..MAX_EXACT_INTEGER_QUANTITY)
            require(holderRedemptionValuationRule != null)
            require(holderRedemptionSettlementMultiplier > 0.0)
        } else {
            require(minimumHolderRedemptionNotes == null)
            require(holderRedemptionNoteIncrement == null)
            require(holderRedemptionValuationRule == null)
            require(holderRedemptionSettlementMultiplier == 0.0)
            require(holderRedemptionChargeRate == 0.0)
        }
        if (issuerCallable) {
            require(issuerCallValuationRule != null)
            require(issuerCallSettlementMultiplier > 0.0)
        } else {
            require(!issuerCallMayBePartial)
            require(issuerCallValuationRule == null)
            require(issuerCallSettlementMultiplier == 0.0)
        }
        require(issuerCallable || holderRedeemable || minimumNoticeBusinessDays == 0)
        if (!issuerCallable && !holderRedeemable) require(!includesAccruedCoupon)
    }
}
