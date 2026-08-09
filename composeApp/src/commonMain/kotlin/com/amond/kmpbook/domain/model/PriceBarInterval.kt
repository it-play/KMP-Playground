package com.amond.kmpbook.domain.model

/** 가격 봉 하나가 집계하는 시장 관측 기간. 게임 진행 단위인 [TurnStep]과 독립적이다. */
enum class PriceBarInterval {
    ONE_HOUR,
    ONE_DAY,
    ONE_WEEK,
    ONE_MONTH,
    THREE_MONTHS,
}
