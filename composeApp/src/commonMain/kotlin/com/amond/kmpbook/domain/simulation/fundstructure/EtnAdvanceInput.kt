package com.amond.kmpbook.domain.simulation.fundstructure

import com.amond.kmpbook.domain.model.fundstructure.EtnCreditEvent
import com.amond.kmpbook.domain.model.fundstructure.MAX_EXACT_INTEGER_QUANTITY
import com.amond.kmpbook.domain.model.fundstructure.MAX_FUND_STRUCTURE_VALUE
import com.amond.kmpbook.domain.model.fundstructure.MAX_RATE
import com.amond.kmpbook.domain.model.fundstructure.MAX_YEAR_FRACTION
import com.amond.kmpbook.domain.model.fundstructure.requireNonNegativeAmount
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Fully deterministic input for an ETN interval. Reference coupon accrual is the observable amount
 * produced by the referenced index or option strategy before contractual participation is applied.
 * Issuance/cancellation flows are deliberately separate from contractual holder redemption.
 */
data class EtnAdvanceInput(
    val effectiveAt: Instant,
    val effectiveDate: LocalDate,
    val elapsedYearFraction: Double,
    val referenceLogReturn: Double,
    val referenceCouponAccrualPerNote: Double = 0.0,
    val couponPaymentPerNote: Double = 0.0,
    val issuerCreditSpreadShock: Double = 0.0,
    val issuerHazardRateShock: Double = 0.0,
    val issuerRecoveryRateUpdate: Double? = null,
    val notesIssued: Long = 0L,
    val noteIssuanceCashPerNote: Double = 0.0,
    val notesCancelled: Long = 0L,
    val noteCancellationCashPerNote: Double = 0.0,
    val contractEvent: EtnCreditEvent = EtnCreditEvent.NONE,
    val contractSettlementNotes: Long = 0L,
    /** Runtime calendar boundary: the first supported venue close on or after contractual maturity. */
    val contractualSettlementDeadlineReached: Boolean = false,
    /** True only when [effectiveDate]'s official venue-close indicative value is observed. */
    val recordIndicativeValueObservation: Boolean = false,
    val creditEventRecoveryRate: Double? = null,
) {
    init {
        val hasCashOrLegalEvent = couponPaymentPerNote > 0.0 || notesIssued > 0L ||
            notesCancelled > 0L || contractEvent != EtnCreditEvent.NONE
        require(
            elapsedYearFraction.isFinite() &&
                elapsedYearFraction >= 0.0 &&
                elapsedYearFraction <= MAX_YEAR_FRACTION,
        )
        require(elapsedYearFraction > 0.0 || hasCashOrLegalEvent) {
            "A zero-time ETN interval must contain a cash or legal event."
        }
        require(referenceLogReturn.isFinite() && referenceLogReturn in -MAX_RATE..MAX_RATE)
        requireNonNegativeAmount(referenceCouponAccrualPerNote, "referenceCouponAccrualPerNote")
        requireNonNegativeAmount(couponPaymentPerNote, "couponPaymentPerNote")
        require(issuerCreditSpreadShock.isFinite() && issuerCreditSpreadShock in -MAX_RATE..MAX_RATE)
        require(issuerHazardRateShock.isFinite() && issuerHazardRateShock in -1.0..1.0)
        require(issuerRecoveryRateUpdate == null || issuerRecoveryRateUpdate in 0.0..1.0)
        require(notesIssued in 0..MAX_EXACT_INTEGER_QUANTITY)
        require(notesCancelled in 0..MAX_EXACT_INTEGER_QUANTITY)
        require(contractSettlementNotes in 0..MAX_EXACT_INTEGER_QUANTITY)
        requireNonNegativeAmount(noteIssuanceCashPerNote, "noteIssuanceCashPerNote")
        requireNonNegativeAmount(noteCancellationCashPerNote, "noteCancellationCashPerNote")
        if (notesIssued == 0L) {
            require(noteIssuanceCashPerNote == 0.0)
        } else {
            require(noteIssuanceCashPerNote > 0.0)
        }
        if (notesCancelled == 0L) {
            require(noteCancellationCashPerNote == 0.0)
        } else {
            require(noteCancellationCashPerNote > 0.0)
        }
        if (contractEvent == EtnCreditEvent.NONE) {
            require(contractSettlementNotes == 0L)
            require(creditEventRecoveryRate == null)
        } else {
            if (!contractEvent.requiresFullSettlement) require(contractSettlementNotes > 0L)
            if (contractEvent == EtnCreditEvent.CREDIT_DEFAULT) {
                require(creditEventRecoveryRate != null && creditEventRecoveryRate in 0.0..1.0)
            } else {
                require(creditEventRecoveryRate == null)
            }
        }
        require(
            contractualSettlementDeadlineReached ==
                (contractEvent == EtnCreditEvent.CONTRACTUAL_MATURITY),
        ) { "Contractual maturity settlement must occur exactly at its calendar deadline." }
        require(
            noteIssuanceCashPerNote <= MAX_FUND_STRUCTURE_VALUE &&
                noteCancellationCashPerNote <= MAX_FUND_STRUCTURE_VALUE,
        )
        if (elapsedYearFraction == 0.0) {
            require(referenceLogReturn == 0.0)
            require(referenceCouponAccrualPerNote == 0.0)
            require(issuerCreditSpreadShock == 0.0)
            require(issuerHazardRateShock == 0.0)
            require(issuerRecoveryRateUpdate == null)
            require(!recordIndicativeValueObservation) {
                "An event-only ETN batch cannot duplicate the routine close observation."
            }
        }
    }
}
