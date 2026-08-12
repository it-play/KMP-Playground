package com.amond.kmpbook.domain.model.fundstructure

/** Contractual source of an exchange-traded note coupon. */
enum class EtnCouponKind {
    NONE,
    FIXED_RATE,
    REFERENCE_CASH_FLOW,
    OPTION_PREMIUM_LINKED,
}
