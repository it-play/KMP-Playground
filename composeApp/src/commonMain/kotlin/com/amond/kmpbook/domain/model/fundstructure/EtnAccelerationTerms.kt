package com.amond.kmpbook.domain.model.fundstructure

/** Contractual acceleration terms, distinct from an ordinary issuer call. */
data class EtnAccelerationTerms(
    val issuerMayAccelerate: Boolean,
    val partialAccelerationAllowed: Boolean,
    val minimumPartialAccelerationNotes: Long?,
    val partialAccelerationNoteIncrement: Long?,
    val creditDefaultCausesAcceleration: Boolean,
    val fullAccelerationValuationRule: EtnSettlementValuationRule?,
    val partialAccelerationValuationRule: EtnSettlementValuationRule?,
    val accelerationSettlementMultiplier: Double,
    val nonCreditAccelerationIncludesAccruedCoupon: Boolean,
    val creditDefaultIncludesAccruedCouponBeforeRecovery: Boolean,
) {
    init {
        require(
            accelerationSettlementMultiplier.isFinite() &&
                accelerationSettlementMultiplier in 0.0..MAX_RATE,
        )
        if (issuerMayAccelerate) {
            require(fullAccelerationValuationRule != null)
            if (partialAccelerationAllowed) {
                require(minimumPartialAccelerationNotes != null)
                require(minimumPartialAccelerationNotes in 1..MAX_EXACT_INTEGER_QUANTITY)
                require(partialAccelerationNoteIncrement != null)
                require(partialAccelerationNoteIncrement in 1..MAX_EXACT_INTEGER_QUANTITY)
                require(partialAccelerationValuationRule != null)
            } else {
                require(minimumPartialAccelerationNotes == null)
                require(partialAccelerationNoteIncrement == null)
                require(partialAccelerationValuationRule == null)
            }
        } else {
            require(!partialAccelerationAllowed)
            require(minimumPartialAccelerationNotes == null)
            require(partialAccelerationNoteIncrement == null)
            require(fullAccelerationValuationRule == null)
            require(partialAccelerationValuationRule == null)
        }
        if (issuerMayAccelerate || creditDefaultCausesAcceleration) {
            require(accelerationSettlementMultiplier > 0.0)
        } else {
            require(accelerationSettlementMultiplier == 0.0)
            require(!nonCreditAccelerationIncludesAccruedCoupon)
            require(!creditDefaultIncludesAccruedCouponBeforeRecovery)
        }
        if (!issuerMayAccelerate) require(!nonCreditAccelerationIncludesAccruedCoupon)
        if (!creditDefaultCausesAcceleration) {
            require(!creditDefaultIncludesAccruedCouponBeforeRecovery)
        }
    }
}
