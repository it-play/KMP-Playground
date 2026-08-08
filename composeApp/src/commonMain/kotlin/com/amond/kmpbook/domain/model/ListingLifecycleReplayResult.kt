package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

data class ListingLifecycleReplayResult(
    val state: ListingLifecycleState,
    val ledgerEvents: List<ListingLifecycleLedgerEvent>,
)
