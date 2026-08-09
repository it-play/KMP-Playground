package com.amond.kmpbook.presentation.protection

import kotlinx.datetime.plus

data class ProtectionStatusBadgeUi(
    val text: String,
    val tone: ProtectionUiTone,
    val emphasis: ProtectionBadgeEmphasis,
    val additionalCount: Int,
    val stateDescription: String,
) {
    init {
        require(text.isNotBlank() && stateDescription.isNotBlank())
        require(additionalCount >= 0)
    }
}
