package com.amond.kmpbook.domain.model.protection.us

import com.amond.kmpbook.domain.model.protection.us.UsMwcbEvent
import com.amond.kmpbook.domain.model.protection.us.UsMwcbState
import com.amond.kmpbook.domain.model.protection.us.UsMwcbTransition

data class UsMwcbTransition(
    val state: UsMwcbState,
    val event: UsMwcbEvent = UsMwcbEvent.NONE,
)
