package com.amond.kmpbook.domain.model.listing.lifecycle

import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleEvaluation
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleState

data class ListingLifecycleEvaluation(
    val state: ListingLifecycleState,
    val ledgerEvents: List<ListingLifecycleLedgerEvent>,
)
