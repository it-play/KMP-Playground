package com.amond.kmpbook.domain.model.fund

import kotlinx.datetime.LocalDate

/**
 * Persistable, provider-neutral corporate action observed by a reference-portfolio methodology.
 *
 * For a merger, [primaryAssetId] is the target and [secondaryAssetId] is the acquirer. For a
 * spin-off they are the parent and child, respectively. A terminal removal has only a primary
 * asset. For a spin-off, [valueTransferFraction] is the parent's value allocated to the child for
 * a mathematically equivalent position split; it is not the child's temporary index price. The
 * event records facts, while the registered methodology separately decides index treatment.
 */
data class ReferencePortfolioCorporateAction(
    val eventId: String,
    val kind: ReferencePortfolioCorporateActionKind,
    val announcementDate: LocalDate,
    val effectiveDate: LocalDate,
    val primaryAssetId: String,
    val secondaryAssetId: String?,
    val considerationKind: ReferencePortfolioCorporateActionConsiderationKind,
    val valueTransferFraction: Double,
    val followUpEffectiveDate: LocalDate?,
) {
    init {
        require(EVENT_ID.matches(eventId))
        require(announcementDate < effectiveDate)
        require(ASSET_ID.matches(primaryAssetId))
        require(secondaryAssetId == null || ASSET_ID.matches(secondaryAssetId))
        require(secondaryAssetId == null || secondaryAssetId != primaryAssetId)
        require(valueTransferFraction.isFinite() && valueTransferFraction in 0.0..1.0)
        require(followUpEffectiveDate == null || followUpEffectiveDate > effectiveDate)
        when (kind) {
            ReferencePortfolioCorporateActionKind.MERGER -> {
                require(secondaryAssetId != null)
                require(followUpEffectiveDate == null)
                when (considerationKind) {
                    ReferencePortfolioCorporateActionConsiderationKind.CASH ->
                        require(valueTransferFraction == 0.0)
                    ReferencePortfolioCorporateActionConsiderationKind.STOCK ->
                        require(valueTransferFraction == 1.0)
                    ReferencePortfolioCorporateActionConsiderationKind.MIXED ->
                        require(valueTransferFraction > 0.0 && valueTransferFraction < 1.0)
                    ReferencePortfolioCorporateActionConsiderationKind.NONE ->
                        error("A merger must declare its consideration kind.")
                }
            }
            ReferencePortfolioCorporateActionKind.SPIN_OFF -> {
                require(secondaryAssetId != null)
                require(considerationKind == ReferencePortfolioCorporateActionConsiderationKind.NONE)
                require(valueTransferFraction > 0.0 && valueTransferFraction < 1.0)
                require(followUpEffectiveDate != null)
            }
            ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL -> {
                require(secondaryAssetId == null)
                require(considerationKind == ReferencePortfolioCorporateActionConsiderationKind.NONE)
                require(valueTransferFraction == 0.0)
                require(followUpEffectiveDate == null)
            }
        }
    }

    companion object {
        private val EVENT_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,511}")
        private val ASSET_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
    }
}
