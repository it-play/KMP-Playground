package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import kotlinx.datetime.LocalDate

/** Canonical dates for one provider-owned scheduled composition action. */
data class EquityMethodologyScheduledAction(
    val kind: ReferencePortfolioActionKind,
    val selectionDate: LocalDate,
    val weightReferenceDate: LocalDate,
    val effectiveDate: LocalDate,
) {
    init {
        require(kind.isScheduled)
        require(selectionDate <= weightReferenceDate && weightReferenceDate < effectiveDate)
    }
}

internal val ReferencePortfolioActionKind.isScheduled: Boolean
    get() = this == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION ||
        this == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT
