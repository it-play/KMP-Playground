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
    val US_MWCB_LEVEL_1_2_CUTOFF = LocalTime(15, 25)
    val US_REGULAR_OPEN = LocalTime(9, 30)

    val US_LULD_LIMIT_STATE = 15.seconds
    val US_LULD_PAUSE = 5.minutes
    val US_LULD_OPTIONAL_EXTENSION = 5.minutes
    val US_LULD_REFERENCE_MINIMUM_AGE = 30.seconds
    const val US_LULD_REFERENCE_MINIMUM_CHANGE = 0.01
    val US_LULD_DOUBLED_BANDS_FROM = LocalTime(15, 35)
    val US_LULD_CLOSE_ONLY_FROM = LocalTime(15, 50)
}
