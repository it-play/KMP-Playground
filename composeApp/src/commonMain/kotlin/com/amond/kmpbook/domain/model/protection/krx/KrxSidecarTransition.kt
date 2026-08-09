package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarEvent
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarState
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarTransition

data class KrxSidecarTransition(
    val state: KrxSidecarState,
    val event: KrxSidecarEvent = KrxSidecarEvent.NONE,
)
