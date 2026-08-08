package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** 보호장치 상태와 뉴스 원장이 같은 발생 건을 가리키도록 공유하는 불변 식별자들이다. */
internal fun krxCircuitBreakerOccurrenceId(
    market: Market,
    level: KrxCircuitBreakerLevel,
    triggeredAt: Instant,
): String = "krx-cb:${market.name}:${level.name}:$triggeredAt"

internal fun krxSidecarOccurrenceId(market: Market, triggeredAt: Instant): String =
    "krx-sidecar:${market.name}:$triggeredAt"

internal fun krxViOccurrenceId(stockId: String, triggerSequence: Int, triggeredAt: Instant): String =
    "krx-vi:$stockId:$triggerSequence:$triggeredAt"

internal fun usMwcbOccurrenceId(level: UsMwcbLevel, triggeredAt: Instant): String =
    "us-mwcb:${level.name}:$triggeredAt"

internal fun usLuldOccurrenceId(stockId: String, pauseStartedAt: Instant): String =
    "us-luld:$stockId:$pauseStartedAt"

internal fun investmentAlertOccurrenceId(stockId: String, designatedAt: Instant): String =
    "investment-alert:$stockId:$designatedAt"
