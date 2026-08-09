package com.amond.kmpbook.domain.model.corporateaction

import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleLedgerEvent
import kotlin.time.Instant

fun PendingCorporateAction.toAnnouncementNewsReference(): CorporateActionNewsReference =
    CorporateActionNewsReference(
        occurrenceId = id,
        transition = CorporateActionNewsTransition.ANNOUNCED,
        stockId = stockId,
        kind = kind,
        announcedAt = announcedAt,
        effectiveNotBefore = effectiveNotBefore,
        quantityMultiplier = quantityMultiplier,
        source = source,
        rationale = rationale,
    )

fun CorporateActionRecord.toAppliedNewsReference(): CorporateActionNewsReference =
    CorporateActionNewsReference(
        occurrenceId = id,
        transition = CorporateActionNewsTransition.APPLIED,
        stockId = stockId,
        kind = kind,
        announcedAt = announcedAt,
        effectiveNotBefore = effectiveNotBefore,
        quantityMultiplier = quantityMultiplier,
        source = source,
        rationale = rationale,
        appliedAt = effectiveAt,
        accountingSequence = accountingSequence,
    )

fun PendingCorporateAction.toCancellationNewsReference(
    cancelledAt: Instant,
    listingEvent: ListingLifecycleLedgerEvent,
): CorporateActionNewsReference = CorporateActionNewsReference(
    occurrenceId = id,
    transition = CorporateActionNewsTransition.CANCELLED,
    stockId = stockId,
    kind = kind,
    announcedAt = announcedAt,
    effectiveNotBefore = effectiveNotBefore,
    quantityMultiplier = quantityMultiplier,
    source = source,
    rationale = rationale,
    cancelledAt = cancelledAt,
    cancellingListingEventId = listingEvent.id,
    cancellingListingLedgerSequence = listingEvent.sequence,
    cancellingListingStatus = listingEvent.toStatus,
)
