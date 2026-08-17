package com.amond.kmpbook.domain.tax.liability

import kotlin.time.Instant

/**
 * 저장된 회계 관측점의 좌·우극한을 전역 회계 순번으로 구분한다.
 * 같은 시각의 사건은 [accountingSequenceExclusiveUpperBound] 미만만 관측점에 포함된다.
 */
data class AccountingObservationBoundary(
    val timestamp: Instant,
    val accountingSequenceExclusiveUpperBound: Long,
) {
    init {
        require(accountingSequenceExclusiveUpperBound >= 0L)
    }

    fun includes(occurredAt: Instant, accountingSequence: Long): Boolean =
        occurredAt < timestamp ||
            occurredAt == timestamp && accountingSequence < accountingSequenceExclusiveUpperBound
}
