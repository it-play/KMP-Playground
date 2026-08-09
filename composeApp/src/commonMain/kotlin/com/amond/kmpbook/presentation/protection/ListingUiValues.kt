package com.amond.kmpbook.presentation.protection

import kotlinx.datetime.plus

internal data class ListingUiValues(
    val badgeLabel: String,
    val title: String,
    val summary: String,
    val orderImpact: String,
    val resumeGuidance: String,
    val ruleExplanation: String,
    val tone: ProtectionUiTone,
    val emphasis: ProtectionBadgeEmphasis,
    val priority: Int,
)
