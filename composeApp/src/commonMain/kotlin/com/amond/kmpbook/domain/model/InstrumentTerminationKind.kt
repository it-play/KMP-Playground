package com.amond.kmpbook.domain.model

import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/** 상품 종료 공시가 선언하는 법적·계약상 종료 사유다. */
enum class InstrumentTerminationKind(
    /** 같은 효력일 공시가 겹칠 때 적용하는 계약 우선순위다. 낮을수록 우선한다. */
    val noticePriority: Int,
) {
    CONTRACTUAL_MATURITY(0),
    ISSUER_ACCELERATION(1),
    OPTIONAL_CALL(2),
    FUND_LIQUIDATION(3),
}
