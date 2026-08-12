package com.amond.kmpbook.domain.model.fundstructure

/** Pure ETN calculation result and the immutable entries needed for save-game replay. */
data class EtnAdvance(
    val state: EtnState,
    val previousRevision: Long,
    val previousNotesOutstanding: Long,
    val referenceLogReturn: Double,
    val elapsedYearFraction: Double,
    val ledgerEntries: List<EtnLedgerEntry>,
) {
    init {
        require(referenceLogReturn.isFinite())
        require(previousRevision >= 0L)
        require(previousNotesOutstanding in 0..MAX_EXACT_INTEGER_QUANTITY)
        require(
            elapsedYearFraction.isFinite() &&
                elapsedYearFraction >= 0.0 &&
                elapsedYearFraction <= MAX_YEAR_FRACTION,
        )
        require(ledgerEntries.map(EtnLedgerEntry::id).distinct().size == ledgerEntries.size)
        require(ledgerEntries.map(EtnLedgerEntry::sequenceInBatch) == ledgerEntries.indices.toList())
        require(ledgerEntries.map(EtnLedgerEntry::kind).distinct().size == ledgerEntries.size)
        require(ledgerEntries == ledgerEntries.sortedBy { it.kind.ordinal })
        require(ledgerEntries.map(EtnLedgerEntry::settlementCurrency).distinct().size <= 1)
        require(
            ledgerEntries.zipWithNext().all { (left, right) ->
                left.notesOutstandingAfter == right.notesOutstandingBefore
            },
        )
        require(ledgerEntries.all { entry ->
            entry.productId == state.productId &&
                entry.revision == state.revision &&
                entry.effectiveAt == state.asOf
        })
        if (ledgerEntries.isEmpty()) {
            require(elapsedYearFraction > 0.0)
            require(state.revision == previousRevision)
            require(state.notesOutstanding == previousNotesOutstanding)
        } else {
            require(state.revision == previousRevision + 1L)
            require(ledgerEntries.first().notesOutstandingBefore == previousNotesOutstanding)
            require(ledgerEntries.last().notesOutstandingAfter == state.notesOutstanding)
        }
    }
}
