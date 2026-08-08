package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class InstrumentTradingHalt(
    /** 발동·예정·해제 뉴스를 같은 규제 조치에 연결하는 영속 발생 ID다. */
    val occurrenceId: String,
    val stockId: String,
    val reason: TradingHaltReason,
    val detail: String,
    val startedAt: Instant,
    val policy: TradingHaltOrderPolicy,
    val scheduledReleaseAt: Instant? = null,
    val status: TradingHaltStatus = TradingHaltStatus.ACTIVE,
    val releasedAt: Instant? = null,
    val releaseNote: String? = null,
) {
    init {
        require(occurrenceId.isNotBlank())
        require(stockId.isNotBlank())
        require(detail.isNotBlank())
        require(scheduledReleaseAt == null || scheduledReleaseAt >= startedAt)
        if (status == TradingHaltStatus.RELEASED) require(releasedAt != null)
    }
}
