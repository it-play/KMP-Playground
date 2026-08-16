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

/** Semiannual selection and quarterly weighting clock for the Dow Jones Korea Dividend 30. */
internal object DowJonesKoreaDividend30Schedule : EquityMethodologySchedule {
    override val market: Market = Market.KOSPI
    override val exposureRegion: EtfExposureRegion = EtfExposureRegion.KOREA

    override fun marketDate(at: Instant): LocalDate = GameCalendar.marketLocalDateTime(market, at).date

    override fun initialScheduledAction(profile: EquityMethodologyProfile): EquityMethodologyScheduledAction =
        requireNotNull(scheduledActionOn(profile, profile.effectiveFrom)) {
            "Dow Jones Korea Dividend 30 effectiveFrom must be a canonical scheduled action date."
        }

    override fun scheduledActionOn(
        profile: EquityMethodologyProfile,
        effectiveDate: LocalDate,
    ): EquityMethodologyScheduledAction? {
        val month = effectiveDate.month.ordinal + 1
        if (month !in reweightMonths(profile) || effectiveDate !=
            KoreaEquityMethodologyCalendar.scheduledEffectiveDateAfterSecondThursday(
                effectiveDate.year,
                month,
            )
        ) return null

        val reconstitution = month in reconstitutionMonths(profile)
        val eventDate = KoreaEquityMethodologyCalendar.secondThursday(effectiveDate.year, month)
        val weightReferenceDate = KoreaEquityMethodologyCalendar.subtractKrxTradingDays(
            eventDate,
            INDEX_SHARE_REFERENCE_LEAD_TRADING_DAYS,
        )
        val selectionDate = if (reconstitution) {
            val selectionMonth = month - 1
            // The host exposes one selection snapshot. Anchor it to the May/November screen
            // date; its trailing signals carry the April/October fundamental observations.
            KoreaEquityMethodologyCalendar.lastKrxTradingDateOnOrBefore(
                KoreaEquityMethodologyCalendar.secondThursday(effectiveDate.year, selectionMonth),
            )
        } else {
            weightReferenceDate
        }
        return EquityMethodologyScheduledAction(
            kind = if (reconstitution) {
                ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
            } else {
                ReferencePortfolioActionKind.SCHEDULED_REWEIGHT
            },
            selectionDate = selectionDate,
            weightReferenceDate = weightReferenceDate,
            effectiveDate = effectiveDate,
        )
    }

    override fun nextScheduledAction(
        profile: EquityMethodologyProfile,
        afterExclusive: LocalDate,
        kind: ReferencePortfolioActionKind?,
    ): EquityMethodologyScheduledAction {
        require(kind == null || kind.isScheduledKind())
        return (afterExclusive.year..afterExclusive.year + 3).asSequence()
            .flatMap { year -> reweightMonths(profile).asSequence().map { month -> year to month } }
            .map { (year, month) ->
                KoreaEquityMethodologyCalendar.scheduledEffectiveDateAfterSecondThursday(year, month)
            }
            .filter { effectiveDate -> effectiveDate > afterExclusive }
            .mapNotNull { effectiveDate -> scheduledActionOn(profile, effectiveDate) }
            .first { action -> kind == null || action.kind == kind }
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

    internal fun reconstitutionMonths(profile: EquityMethodologyProfile): Set<Int> =
        profile.parameters.integerSets.getValue("reconstitutionMonths")

    internal fun reweightMonths(profile: EquityMethodologyProfile): Set<Int> =
        profile.parameters.integerSets.getValue("reweightMonths")

    private fun ReferencePortfolioActionKind.isScheduledKind(): Boolean =
        this == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION ||
            this == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT

    private const val INDEX_SHARE_REFERENCE_LEAD_TRADING_DAYS: Int = 7
}
