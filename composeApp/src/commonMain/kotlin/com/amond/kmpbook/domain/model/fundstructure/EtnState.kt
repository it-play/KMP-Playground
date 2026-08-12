package com.amond.kmpbook.domain.model.fundstructure

import kotlin.time.Instant

/** Persistable ETN contract and issuer-credit state; it is not a fund balance sheet. */
data class EtnState(
    val productId: String,
    val referenceLevel: Double,
    val feeAdjustedIndicativeValuePerNote: Double,
    val notesOutstanding: Long,
    val accruedCouponPerNote: Double,
    val issuerCreditSpread: Double,
    val issuerHazardRate: Double,
    val issuerRecoveryRate: Double,
    val indicativeValueObservationWindow: List<EtnIndicativeValueObservation>,
    val lifecycle: EtnLifecycle,
    val terminalCreditEvent: EtnCreditEvent?,
    val asOf: Instant,
    val revision: Long,
) {
    init {
        requireFundStructureId(productId, "productId")
        requirePositiveAmount(referenceLevel, "referenceLevel")
        requireNonNegativeAmount(
            feeAdjustedIndicativeValuePerNote,
            "feeAdjustedIndicativeValuePerNote",
        )
        require(notesOutstanding in 0..MAX_EXACT_INTEGER_QUANTITY)
        requireNonNegativeAmount(accruedCouponPerNote, "accruedCouponPerNote")
        require(issuerCreditSpread.isFinite() && issuerCreditSpread in 0.0..MAX_RATE)
        require(issuerHazardRate.isFinite() && issuerHazardRate in 0.0..1.0)
        require(issuerRecoveryRate.isFinite() && issuerRecoveryRate in 0.0..1.0)
        require(indicativeValueObservationWindow.size <= MAX_OBSERVATIONS)
        require(
            indicativeValueObservationWindow.map(EtnIndicativeValueObservation::observationDate)
                .zipWithNext()
                .all { (previous, next) -> previous < next },
        ) { "ETN indicative-value observations must have unique ascending dates." }
        require(revision >= 0L)
        require(notesOutstanding > 0L || accruedCouponPerNote == 0.0)
        when (lifecycle) {
            EtnLifecycle.ACTIVE -> require(terminalCreditEvent == null)
            EtnLifecycle.SETTLED -> {
                require(notesOutstanding == 0L)
                require(accruedCouponPerNote == 0.0)
                require(
                    terminalCreditEvent != null &&
                        terminalCreditEvent != EtnCreditEvent.NONE &&
                        terminalCreditEvent != EtnCreditEvent.HOLDER_REDEMPTION,
                )
            }
        }
    }

    companion object {
        const val MAX_OBSERVATIONS: Int = 31
    }
}
