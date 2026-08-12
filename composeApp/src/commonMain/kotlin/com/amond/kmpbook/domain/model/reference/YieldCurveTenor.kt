package com.amond.kmpbook.domain.model.reference

/** 표준 금리곡선 만기점이다. [years]는 보간과 롤다운 계산에만 사용한다. */
enum class YieldCurveTenor(val years: Double) {
    ONE_MONTH(1.0 / 12.0),
    THREE_MONTHS(0.25),
    SIX_MONTHS(0.5),
    ONE_YEAR(1.0),
    TWO_YEARS(2.0),
    THREE_YEARS(3.0),
    FIVE_YEARS(5.0),
    SEVEN_YEARS(7.0),
    TEN_YEARS(10.0),
    TWENTY_YEARS(20.0),
    THIRTY_YEARS(30.0),
}
