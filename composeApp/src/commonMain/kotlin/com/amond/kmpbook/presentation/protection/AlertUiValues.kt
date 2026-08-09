package com.amond.kmpbook.presentation.protection

import kotlinx.datetime.plus

internal data class AlertUiValues(
    val label: String,
    val title: String,
    val summary: String,
    val tone: ProtectionUiTone,
    val emphasis: ProtectionBadgeEmphasis,
    val priority: Int,
)
