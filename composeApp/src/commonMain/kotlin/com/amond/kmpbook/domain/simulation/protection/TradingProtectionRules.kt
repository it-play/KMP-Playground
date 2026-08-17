package com.amond.kmpbook.domain.simulation.protection

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.LocalTime

/**
 * Official thresholds and durations used by [TradingProtectionEngine].
 *
 * KRX source: https://global.krx.co.kr/contents/GLB/01/0109/0109000000/guide_to_trading_in_the_korean_stock_market.pdf
 * KRX VI source: https://global.krx.co.kr/contents/GLB/06/0602/0602020204/GLB0602020204T7.jsp
 * U.S. MWCB source: https://www.nyse.com/publicdocs/nyse/NYSE_MWCB_FAQ.pdf
 * LULD source: https://www.luldplan.com/
 */
object TradingProtectionRules {
    const val KRX_CB_LEVEL_1_DECLINE = 0.08
    const val KRX_CB_LEVEL_2_DECLINE = 0.15
    const val KRX_CB_LEVEL_3_DECLINE = 0.20
    const val KRX_CB_ADDITIONAL_DECLINE = 0.01
    val KRX_CB_PERSISTENCE = 1.minutes
    val KRX_CB_HALT = 20.minutes
    val KRX_CB_REOPENING_CALL = 10.minutes
    const val KRX_CB_CUTOFF_MINUTES_BEFORE_CLOSE = 40.0

    const val KOSPI_SIDECAR_FUTURES_RATE = 0.05
    const val KOSDAQ_SIDECAR_FUTURES_RATE = 0.06
    const val KOSDAQ_SIDECAR_SPOT_RATE = 0.03
    val KRX_SIDECAR_PERSISTENCE = 1.minutes
    val KRX_SIDECAR_SUSPENSION = 5.minutes
    const val KRX_SIDECAR_FIRST_ELIGIBLE_MINUTE = 5.0
    const val KRX_SIDECAR_CUTOFF_MINUTES_BEFORE_CLOSE = 40.0

    val KRX_VI_CALL_AUCTION = 2.minutes
    const val KRX_VI_STATIC_RATE = 0.10
    const val KRX_VI_DERIVATIVES_EXPIRATION_CLOSE_RATE = 0.01

    const val US_MWCB_LEVEL_1_DECLINE = 0.07
    const val US_MWCB_LEVEL_2_DECLINE = 0.13
    const val US_MWCB_LEVEL_3_DECLINE = 0.20
    val US_MWCB_HALT = 15.minutes
    val US_REGULAR_OPEN = LocalTime(9, 30)

    /** NYSE Rule 7.12: Level 1/2 halts stop 35 minutes before that date's scheduled close. */
    fun usMwcbLevel12Cutoff(regularSessionClose: LocalTime): LocalTime =
        minutesBefore(regularSessionClose, 35)

    val US_LULD_LIMIT_STATE = 15.seconds
    val US_LULD_PAUSE = 5.minutes
    val US_LULD_OPTIONAL_EXTENSION = 5.minutes
    val US_LULD_REFERENCE_MINIMUM_AGE = 30.seconds
    const val US_LULD_REFERENCE_MINIMUM_CHANGE = 0.01

    /** The LULD Plan doubles applicable bands for the final 25 minutes of the session. */
    fun usLuldDoubledBandsFrom(regularSessionClose: LocalTime): LocalTime =
        minutesBefore(regularSessionClose, 25)

    /** A Limit State entering the final ten minutes remains in the closing-auction path. */
    fun usLuldCloseOnlyFrom(regularSessionClose: LocalTime): LocalTime =
        minutesBefore(regularSessionClose, 10)

    private fun minutesBefore(close: LocalTime, minutes: Int): LocalTime {
        require(close.second == 0 && close.nanosecond == 0) {
            "정규장 마감 시각은 분 단위여야 합니다."
        }
        val targetMinute = close.hour * 60 + close.minute - minutes
        require(targetMinute >= 0) { "정규장 마감 전 규제 경계가 전날로 넘어갈 수 없습니다." }
        return LocalTime(targetMinute / 60, targetMinute % 60)
    }
}
