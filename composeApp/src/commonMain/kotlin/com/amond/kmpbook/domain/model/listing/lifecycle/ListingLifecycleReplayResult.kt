package com.amond.kmpbook.domain.model.listing.lifecycle

import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleReplayResult
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleState

data class ListingLifecycleReplayResult(
    val state: ListingLifecycleState,
    val ledgerEvents: List<ListingLifecycleLedgerEvent>,
)
