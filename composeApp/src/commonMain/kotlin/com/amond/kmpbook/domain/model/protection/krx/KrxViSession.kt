package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.protection.krx.KrxViSession

enum class KrxViSession {
    OPENING_CALL_AUCTION,
    CONTINUOUS_AUCTION,
    CLOSING_CALL_AUCTION,
    AFTER_HOURS_PERIODIC_CALL_AUCTION,
}
