package com.amond.kmpbook.domain.model.protection.us

import com.amond.kmpbook.domain.model.protection.us.UsLuldEvent
import com.amond.kmpbook.domain.model.protection.us.UsLuldState
import com.amond.kmpbook.domain.model.protection.us.UsLuldTransition

data class UsLuldTransition(
    val state: UsLuldState,
    val event: UsLuldEvent = UsLuldEvent.NONE,
)
