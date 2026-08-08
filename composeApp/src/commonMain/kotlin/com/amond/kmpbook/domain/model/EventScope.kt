package com.amond.kmpbook.domain.model

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

enum class EventScope(val displayName: String) {
    GLOBAL("전 세계"),
    COUNTRY("국가"),
    MARKET("시장"),
    SECTOR("산업"),
    STOCK("개별 종목"),
}
