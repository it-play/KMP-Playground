package com.amond.kmpbook.domain.model.fundstructure

/** Contract event supplied to the deterministic ETN engine. */
enum class EtnCreditEvent(
    val requiresFullSettlement: Boolean,
) {
    NONE(false),
    HOLDER_REDEMPTION(false),
    ISSUER_CALL(false),
    CONTRACTUAL_MATURITY(true),
    ISSUER_ACCELERATION(false),
    CREDIT_DEFAULT(true),
}
