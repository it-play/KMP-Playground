package com.amond.kmpbook.domain.model.fundstructure

import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlin.time.Instant

/** Immutable accounting evidence produced by one ETN advance. */
data class EtnLedgerEntry(
    val id: String,
    val productId: String,
    val kind: EtnLedgerKind,
    val effectiveAt: Instant,
    val revision: Long,
    val sequenceInBatch: Int,
    val settlementCurrency: ReferenceCurrency,
    val referenceLevelBefore: Double,
    val referenceLevelAfter: Double,
    val indicativeValueBefore: Double,
    val indicativeValueAfter: Double,
    val notesOutstandingBefore: Long,
    val notesOutstandingAfter: Long,
    val notesIssued: Long,
    val notesCancelled: Long,
    val notesSettled: Long,
    val notesDelta: Long,
    val cashPaidToNoteholders: Double,
    val cashReceivedFromNoteholders: Double,
    val contractEvent: EtnCreditEvent,
    val settlementIndicativeValueObservations: List<EtnIndicativeValueObservation>,
) {
    init {
        requireFundStructureId(id, "id")
        requireFundStructureId(productId, "productId")
        require(revision > 0L)
        require(sequenceInBatch in 0..MAX_BATCH_ENTRIES)
        requirePositiveAmount(referenceLevelBefore, "referenceLevelBefore")
        requirePositiveAmount(referenceLevelAfter, "referenceLevelAfter")
        requireNonNegativeAmount(indicativeValueBefore, "indicativeValueBefore")
        requireNonNegativeAmount(indicativeValueAfter, "indicativeValueAfter")
        require(notesOutstandingBefore in 0..MAX_EXACT_INTEGER_QUANTITY)
        require(notesOutstandingAfter in 0..MAX_EXACT_INTEGER_QUANTITY)
        require(notesIssued in 0..MAX_EXACT_INTEGER_QUANTITY)
        require(notesCancelled in 0..MAX_EXACT_INTEGER_QUANTITY)
        require(notesSettled in 0..MAX_EXACT_INTEGER_QUANTITY)
        require(notesDelta in -MAX_EXACT_INTEGER_QUANTITY..MAX_EXACT_INTEGER_QUANTITY)
        require(notesDelta == notesIssued - notesCancelled - notesSettled)
        require(notesOutstandingAfter == notesOutstandingBefore + notesDelta)
        requireNonNegativeAmount(cashPaidToNoteholders, "cashPaidToNoteholders")
        requireNonNegativeAmount(cashReceivedFromNoteholders, "cashReceivedFromNoteholders")
        when (kind) {
            EtnLedgerKind.COUPON_PAYMENT -> {
                require(notesDelta == 0L)
                require(notesIssued == 0L && notesCancelled == 0L && notesSettled == 0L)
                require(contractEvent == EtnCreditEvent.NONE)
                require(settlementIndicativeValueObservations.isEmpty())
                require(cashPaidToNoteholders > 0.0)
                require(cashReceivedFromNoteholders == 0.0)
            }
            EtnLedgerKind.NOTE_FLOW -> {
                require(notesSettled == 0L)
                require(
                    notesIssued > 0L || notesCancelled > 0L,
                )
                require(contractEvent == EtnCreditEvent.NONE)
                require(settlementIndicativeValueObservations.isEmpty())
                require(notesIssued > 0L || cashReceivedFromNoteholders == 0.0)
                require(notesIssued == 0L || cashReceivedFromNoteholders > 0.0)
                require(notesCancelled > 0L || cashPaidToNoteholders == 0.0)
                require(notesCancelled == 0L || cashPaidToNoteholders > 0.0)
            }
            EtnLedgerKind.CONTRACT_SETTLEMENT -> {
                require(notesIssued == 0L && notesCancelled == 0L)
                require(notesSettled > 0L || contractEvent.requiresFullSettlement)
                require(notesDelta <= 0L)
                require(cashReceivedFromNoteholders == 0.0)
                require(contractEvent != EtnCreditEvent.NONE)
                require(settlementIndicativeValueObservations.isNotEmpty())
                require(settlementIndicativeValueObservations.size <= MAX_SETTLEMENT_OBSERVATIONS)
                require(
                    settlementIndicativeValueObservations
                        .map(EtnIndicativeValueObservation::observationDate)
                        .zipWithNext()
                        .all { (previous, next) -> previous < next },
                )
                require(
                    amountsAreClose(
                        settlementIndicativeValueObservations.last().indicativeValuePerNote,
                        indicativeValueAfter,
                    ),
                )
            }
        }
    }

    companion object {
        private const val MAX_BATCH_ENTRIES: Int = 100
        private const val MAX_SETTLEMENT_OBSERVATIONS: Int = 31
    }
}
