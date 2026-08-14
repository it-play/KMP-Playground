package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef

/** Read-only metadata for one executable methodology implementation. */
data class EquityMethodologyDescriptor(
    val ref: EquityMethodologyRef,
    val displayName: String,
) {
    init {
        require(displayName.isNotBlank() && displayName == displayName.trim())
        require(displayName.length <= MAX_DISPLAY_NAME_LENGTH)
        require(displayName.none(Char::isISOControl))
    }

    companion object {
        const val MAX_DISPLAY_NAME_LENGTH: Int = 160
    }
}
