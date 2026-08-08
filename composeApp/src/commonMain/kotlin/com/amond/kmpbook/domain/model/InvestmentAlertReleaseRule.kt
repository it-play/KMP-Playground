package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/** KRX 공시의 지정 사유군별 해제 상승률 기준을 저장한다. */
enum class InvestmentAlertReleaseRule {
    CAUTION_PRICE_VOLUME,
    WARNING_45_75,
    WARNING_60_100,
    DANGER_60_100,
}
