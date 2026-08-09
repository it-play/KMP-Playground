package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.protection.core.TriggeringQuotationDisposition
import com.amond.kmpbook.domain.model.protection.krx.KrxViEvent
import com.amond.kmpbook.domain.model.protection.krx.KrxViState
import com.amond.kmpbook.domain.model.protection.krx.KrxViTransition

data class KrxViTransition(
    val state: KrxViState,
    val event: KrxViEvent = KrxViEvent.NONE,
    val triggeringQuotationDisposition: TriggeringQuotationDisposition = TriggeringQuotationDisposition.UNAFFECTED,
)
