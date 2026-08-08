package com.amond.kmpbook.domain.model

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** 뉴스가 거래소 규칙에 따라 직접 유발하는 종목 거래정지의 업무 종류다. */
enum class EventTradingHaltKind {
    MATERIAL_DISCLOSURE,
}
