package com.amond.kmpbook.domain.model

import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/** 종료일 현금 지급 단가를 산정하는 캠페인 평가 규칙이다. */
enum class InstrumentTerminationValuationMethod {
    /** ETN의 종료일 최종 지표가치를 종가로 근사한다. */
    FINAL_INDICATIVE_VALUE_PROXY,

    /** ETF·폐쇄형 펀드의 최종 순자산가치를 종가로 근사한다. */
    FINAL_NET_ASSET_VALUE_PROXY,

    /** 최근 5개 거래일 종가 평균에 공시에 고정된 발행사 회수율을 적용한다. */
    TRAILING_FIVE_SESSION_AVERAGE_WITH_RECOVERY,
}
