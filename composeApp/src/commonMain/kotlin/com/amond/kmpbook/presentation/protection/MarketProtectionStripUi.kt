package com.amond.kmpbook.presentation.protection

import kotlinx.datetime.plus

data class MarketProtectionStripUi(
    val title: String,
    val summary: String,
    val detail: ProtectionDetailUi,
    val stateDescription: String,
) {
    val badge: ProtectionStatusBadgeUi get() = detail.badge
    val additionalCount: Int get() = detail.additionalCount
}
