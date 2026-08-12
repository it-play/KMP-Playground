package com.amond.kmpbook.domain.model.fundstructure

/**
 * Coupon terms only describe the contract. The engine never estimates an option premium or
 * reference distribution: each accrued amount is supplied as an explicit deterministic input.
 */
data class EtnCouponRule(
    val kind: EtnCouponKind,
    val paymentFrequencyMonths: Int,
    val annualFixedRate: Double,
    val participationRate: Double,
    val accrualReducesIndicativeValue: Boolean,
    val accruedCouponPaidAtTermination: Boolean,
) {
    init {
        require(paymentFrequencyMonths in 0..120)
        require(annualFixedRate.isFinite() && annualFixedRate in 0.0..MAX_RATE)
        require(participationRate.isFinite() && participationRate in 0.0..MAX_RATE)
        when (kind) {
            EtnCouponKind.NONE -> {
                require(paymentFrequencyMonths == 0)
                require(annualFixedRate == 0.0)
                require(participationRate == 0.0)
                require(!accrualReducesIndicativeValue)
                require(!accruedCouponPaidAtTermination)
            }
            EtnCouponKind.FIXED_RATE -> {
                require(paymentFrequencyMonths > 0)
                require(annualFixedRate > 0.0)
                require(participationRate == 0.0)
            }
            EtnCouponKind.REFERENCE_CASH_FLOW,
            EtnCouponKind.OPTION_PREMIUM_LINKED,
            -> {
                require(paymentFrequencyMonths > 0)
                require(annualFixedRate == 0.0)
                require(participationRate > 0.0)
            }
        }
    }
}
