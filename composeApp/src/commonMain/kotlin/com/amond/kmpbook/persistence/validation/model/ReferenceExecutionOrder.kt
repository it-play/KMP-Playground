package com.amond.kmpbook.persistence.validation.model

import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import kotlinx.datetime.LocalDate

/** Deterministic ordering key for persisted reference-portfolio actions. */
internal data class ReferenceExecutionOrder(
    val effectiveDate: LocalDate,
    val kind: ReferencePortfolioActionKind,
    val corporateEventId: String,
)
