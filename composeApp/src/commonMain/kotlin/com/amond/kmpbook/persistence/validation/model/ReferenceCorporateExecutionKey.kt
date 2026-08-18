package com.amond.kmpbook.persistence.validation.model

import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import kotlinx.datetime.LocalDate

/** Stable identity used while validating persisted reference-portfolio corporate actions. */
internal data class ReferenceCorporateExecutionKey(
    val corporateEventId: String,
    val kind: ReferencePortfolioActionKind,
    val effectiveDate: LocalDate,
)
