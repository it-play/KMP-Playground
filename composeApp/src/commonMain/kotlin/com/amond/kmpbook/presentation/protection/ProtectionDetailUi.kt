package com.amond.kmpbook.presentation.protection

import kotlinx.datetime.plus

data class ProtectionDetailUi(
    val contextLabel: String,
    val primary: ProtectionUiStatus,
    val additional: List<ProtectionUiStatus> = emptyList(),
) {
    init {
        require(contextLabel.isNotBlank())
        require(additional.none { it.id == primary.id })
        require((listOf(primary) + additional).map(ProtectionUiStatus::id).distinct().size == additional.size + 1)
    }

    val statuses: List<ProtectionUiStatus> get() = listOf(primary) + additional
    val additionalCount: Int get() = additional.size
    val badge: ProtectionStatusBadgeUi get() = statuses.toBadge()
}
