package com.amond.kmpbook.presentation.portfolio

import kotlinx.datetime.LocalDate

/** Long-lived daily surveillance series used by KRX alert and listing-maintenance rules. */
data class DailyTradingSurveillancePoint(
    val date: LocalDate,
    val close: Double,
    val volume: Long,
    val turnoverRate: Double,
    /** 같은 거래일 KOSPI/KOSDAQ 유동시가총액 프록시. 미국 종목은 null이다. */
    val marketProxyClose: Double? = null,
    /** 같은 거래일 종가 기준 KOSPI·KOSDAQ 보통주 합산 시가총액 순위. */
    val krxMarketCapRank: Int? = null,
) {
    init {
        require(close >= 0.0 && close.isFinite())
        require(volume >= 0L)
        require(turnoverRate >= 0.0 && turnoverRate.isFinite())
        require(marketProxyClose == null || marketProxyClose > 0.0 && marketProxyClose.isFinite())
        require(krxMarketCapRank == null || krxMarketCapRank > 0)
    }
}
