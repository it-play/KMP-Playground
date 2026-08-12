package com.amond.kmpbook.domain.model.fundstructure

/** Pure CEF calculation result and its immutable settlement ledger. */
data class ClosedEndFundAdvance(
    val state: ClosedEndFundState,
    val previousRevision: Long,
    val previousCommonSharesOutstanding: Double,
    val previousDebtLiability: Double,
    val previousPreferredShareLiability: Double,
    val assetTotalLogReturn: Double,
    val elapsedYearFraction: Double,
    val ledgerEntries: List<ClosedEndFundLedgerEntry>,
) {
    init {
        require(assetTotalLogReturn.isFinite())
        require(previousRevision >= 0L)
        requirePositiveAmount(previousCommonSharesOutstanding, "previousCommonSharesOutstanding")
        requireNonNegativeAmount(previousDebtLiability, "previousDebtLiability")
        requireNonNegativeAmount(
            previousPreferredShareLiability,
            "previousPreferredShareLiability",
        )
        require(
            elapsedYearFraction.isFinite() &&
                elapsedYearFraction >= 0.0 &&
                elapsedYearFraction <= MAX_YEAR_FRACTION,
        )
        require(ledgerEntries.map(ClosedEndFundLedgerEntry::id).distinct().size == ledgerEntries.size)
        require(
            ledgerEntries.map(ClosedEndFundLedgerEntry::sequenceInBatch) == ledgerEntries.indices.toList(),
        )
        require(ledgerEntries.map(ClosedEndFundLedgerEntry::kind).distinct().size == ledgerEntries.size)
        require(ledgerEntries == ledgerEntries.sortedBy { it.kind.ordinal })
        require(ledgerEntries.map(ClosedEndFundLedgerEntry::settlementCurrency).distinct().size <= 1)
        require(
            ledgerEntries.zipWithNext().all { (left, right) ->
                amountsAreClose(left.navPerShareAfter, right.navPerShareBefore)
            },
        )
        require(ledgerEntries.all { entry ->
            entry.fundId == state.fundId &&
                entry.revision == state.revision &&
                entry.effectiveAt == state.asOf
        })
        if (ledgerEntries.isEmpty()) {
            require(elapsedYearFraction > 0.0)
            require(state.revision == previousRevision)
        } else {
            require(state.revision == previousRevision + 1L)
            require(amountsAreClose(ledgerEntries.last().navPerShareAfter, state.navPerCommonShare))
        }
        require(
            amountsAreClose(
                state.commonSharesOutstanding,
                previousCommonSharesOutstanding +
                    ledgerEntries.sumOf(ClosedEndFundLedgerEntry::commonSharesDelta),
            ),
        )
        require(
            amountsAreClose(
                state.debtLiability,
                previousDebtLiability + ledgerEntries.sumOf(ClosedEndFundLedgerEntry::debtLiabilityDelta),
            ),
        )
        require(
            amountsAreClose(
                state.preferredShareLiability,
                previousPreferredShareLiability +
                    ledgerEntries.sumOf(ClosedEndFundLedgerEntry::preferredShareLiabilityDelta),
            ),
        )
    }
}
