package com.amond.kmpbook.domain.methodology.builtin

import com.amond.kmpbook.domain.methodology.EquityMethodologySchedule
import com.amond.kmpbook.domain.methodology.EquityMethodologyScheduledAction
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Canonical KRX dates and the May 2026 three-session transition for the KODEX benchmark. */
internal object KodexFinancialHighDividendTop10Schedule : EquityMethodologySchedule {
    override val market: Market = Market.KOSPI
    override val exposureRegion: EtfExposureRegion = EtfExposureRegion.KOREA

    override fun marketDate(at: Instant): LocalDate = GameCalendar.marketLocalDateTime(market, at).date

    override fun initialScheduledAction(profile: EquityMethodologyProfile): EquityMethodologyScheduledAction =
        requireNotNull(scheduledActionOn(profile, profile.effectiveFrom)) {
            "KOSPI 200 Financial High Dividend TOP10 effectiveFrom must be a canonical T+2 transition date."
        }

    override fun scheduledActionOn(
        profile: EquityMethodologyProfile,
        effectiveDate: LocalDate,
    ): EquityMethodologyScheduledAction? {
        val month = effectiveDate.month.ordinal + 1
        if (month !in reconstitutionMonths(profile) ||
            effectiveDate != transitionFinalDate(effectiveDate.year, month)
        ) return null
        val referenceDate = futuresLastTradingDate(effectiveDate.year, month)
        return EquityMethodologyScheduledAction(
            kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
            selectionDate = referenceDate,
            weightReferenceDate = referenceDate,
            effectiveDate = effectiveDate,
        )
    }

    override fun nextScheduledAction(
        profile: EquityMethodologyProfile,
        afterExclusive: LocalDate,
        kind: ReferencePortfolioActionKind?,
    ): EquityMethodologyScheduledAction {
        require(kind == null || kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) {
            "KOSPI 200 Financial High Dividend TOP10 has no scheduled reweight-only action."
        }
        return (afterExclusive.year..afterExclusive.year + 3).asSequence()
            .flatMap { year -> reconstitutionMonths(profile).sorted().asSequence().map { month -> year to month } }
            .map { (year, month) -> transitionFinalDate(year, month) }
            .filter { effectiveDate -> effectiveDate > afterExclusive }
            .mapNotNull { effectiveDate -> scheduledActionOn(profile, effectiveDate) }
            .first()
    }

    override fun isTradingDate(date: LocalDate): Boolean =
        KoreaEquityMethodologyCalendar.isKrxTradingDate(date)

    override fun addTradingDays(date: LocalDate, days: Int): LocalDate =
        KoreaEquityMethodologyCalendar.addKrxTradingDays(date, days)

    override fun hasPassedRegularOpen(effectiveDate: LocalDate, at: Instant): Boolean =
        KoreaEquityMethodologyCalendar.hasPassedKrxRegularOpen(effectiveDate, at)

    override fun hasReachedRegularClose(referenceDate: LocalDate, at: Instant): Boolean =
        KoreaEquityMethodologyCalendar.hasReachedKrxRegularClose(referenceDate, at)

    override fun intersectsRegularSession(from: Instant, to: Instant): Boolean =
        KoreaEquityMethodologyCalendar.intersectsKrxRegularSession(from, to)

    override fun reachesRegularClose(from: Instant, to: Instant): Boolean =
        KoreaEquityMethodologyCalendar.reachesKrxRegularClose(from, to)

    /** KOSPI 200 futures expiry: the second Thursday, rolled to the preceding KRX session. */
    internal fun futuresLastTradingDate(year: Int, month: Int): LocalDate =
        KoreaEquityMethodologyCalendar.lastKrxTradingDateOnOrBefore(
            KoreaEquityMethodologyCalendar.secondThursday(year, month),
        )

    /** T: first KRX session after the relevant futures expiry. */
    internal fun transitionStartDate(year: Int, month: Int): LocalDate =
        KoreaEquityMethodologyCalendar.addKrxTradingDays(futuresLastTradingDate(year, month), 1)

    /** T+1: incoming/outgoing constituents have reached 70%/30% of the change. */
    internal fun transitionIntermediateDate(year: Int, month: Int): LocalDate =
        KoreaEquityMethodologyCalendar.addKrxTradingDays(transitionStartDate(year, month), 1)

    /** T+2: the regular reconstitution is fully effective and becomes the host action date. */
    internal fun transitionFinalDate(year: Int, month: Int): LocalDate =
        KoreaEquityMethodologyCalendar.addKrxTradingDays(transitionStartDate(year, month), 2)

    internal fun incomingTransitionFraction(transitionOffset: Int): Double = when (transitionOffset) {
        0 -> 0.30
        1 -> 0.70
        2 -> 1.00
        else -> error("The KRX transition offset must be T, T+1 or T+2.")
    }

    internal fun outgoingTransitionFraction(transitionOffset: Int): Double = when (transitionOffset) {
        0 -> 0.70
        1 -> 0.30
        2 -> 0.00
        else -> error("The KRX transition offset must be T, T+1 or T+2.")
    }

    internal fun reconstitutionMonths(profile: EquityMethodologyProfile): Set<Int> =
        profile.parameters.integerSets.getValue("reconstitutionMonths")
}
