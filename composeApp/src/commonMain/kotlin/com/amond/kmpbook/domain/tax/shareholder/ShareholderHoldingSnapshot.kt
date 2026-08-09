package com.amond.kmpbook.domain.tax.shareholder

import com.amond.kmpbook.domain.model.market.Market

/** Snapshot values are per issuer, across every brokerage account. */
data class ShareholderHoldingSnapshot(
    val ownerId: String,
    val relation: ShareholderRelation,
    val ownershipRatio: Double,
    val marketValueKrw: Long,
) {
    init {
        require(ownerId.isNotBlank()) { "A shareholder snapshot needs an owner id." }
        require(ownershipRatio in 0.0..1.0) { "Ownership must be a ratio from zero to one." }
        require(marketValueKrw >= 0L) { "Market value cannot be negative." }
    }
}
