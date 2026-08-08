package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

data class ListingLifecycleEvaluation(
    val state: ListingLifecycleState,
    val ledgerEvents: List<ListingLifecycleLedgerEvent>,
)
