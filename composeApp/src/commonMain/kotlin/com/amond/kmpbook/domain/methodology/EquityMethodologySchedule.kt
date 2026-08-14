package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
import com.amond.kmpbook.domain.model.market.Market
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Market clock and provider-owned scheduled-action contract for one methodology. */
interface EquityMethodologySchedule {
    val market: Market
    val exposureRegion: EtfExposureRegion

    fun marketDate(at: Instant): LocalDate
    fun initialScheduledAction(profile: EquityMethodologyProfile): EquityMethodologyScheduledAction
    fun scheduledActionOn(
        profile: EquityMethodologyProfile,
        effectiveDate: LocalDate,
    ): EquityMethodologyScheduledAction?
    fun nextScheduledAction(
        profile: EquityMethodologyProfile,
        afterExclusive: LocalDate,
        kind: ReferencePortfolioActionKind? = null,
    ): EquityMethodologyScheduledAction
    fun isTradingDate(date: LocalDate): Boolean
    fun addTradingDays(date: LocalDate, days: Int): LocalDate
    fun hasPassedRegularOpen(effectiveDate: LocalDate, at: Instant): Boolean
    fun hasReachedRegularClose(referenceDate: LocalDate, at: Instant): Boolean
    fun intersectsRegularSession(from: Instant, to: Instant): Boolean
    fun reachesRegularClose(from: Instant, to: Instant): Boolean
}
