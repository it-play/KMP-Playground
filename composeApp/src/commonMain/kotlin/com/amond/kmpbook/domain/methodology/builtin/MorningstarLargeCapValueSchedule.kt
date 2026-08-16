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

/** Quarterly ranking and five-close transition schedule for Morningstar US Large Cap Value v2. */
internal object MorningstarLargeCapValueSchedule : EquityMethodologySchedule {
    override val market: Market = Market.NYSE
    override val exposureRegion: EtfExposureRegion = EtfExposureRegion.UNITED_STATES

    override fun marketDate(at: Instant): LocalDate = GameCalendar.marketLocalDateTime(market, at).date

    override fun initialScheduledAction(profile: EquityMethodologyProfile): EquityMethodologyScheduledAction =
        requireNotNull(scheduledActionOn(profile, profile.effectiveFrom)) {
            "Morningstar large-cap value effectiveFrom must be the next-session application of a " +
                "canonical quarterly final transition close."
        }

    override fun scheduledActionOn(
        profile: EquityMethodologyProfile,
        effectiveDate: LocalDate,
    ): EquityMethodologyScheduledAction? {
        if (effectiveDate < profile.effectiveFrom) return null
        val month = effectiveDate.month.ordinal + 1
        if (month !in reconstitutionMonths(profile) ||
            effectiveDate != MorningstarLargeCapValueCalendar.quarterlyFinalApplicationDate(
                effectiveDate.year,
                month,
            )
        ) return null
        val rankingDate = MorningstarLargeCapValueCalendar.quarterlyRankingDate(
            effectiveDate.year,
            month,
        )
        return EquityMethodologyScheduledAction(
            kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
            selectionDate = rankingDate,
            // The licensed random price day is unavailable to the simulation. The versioned
            // proxy freezes both membership inputs and float-market values at this close.
            weightReferenceDate = rankingDate,
            effectiveDate = effectiveDate,
        )
    }

    override fun nextScheduledAction(
        profile: EquityMethodologyProfile,
        afterExclusive: LocalDate,
        kind: ReferencePortfolioActionKind?,
    ): EquityMethodologyScheduledAction {
        require(kind == null || kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) {
            "Morningstar large-cap value has no separate scheduled-reweight lane."
        }
        return (afterExclusive.year..afterExclusive.year + 3).asSequence()
            .flatMap { year ->
                reconstitutionMonths(profile).sorted().asSequence().map { month -> year to month }
            }
            .map { (year, month) ->
                MorningstarLargeCapValueCalendar.quarterlyFinalApplicationDate(year, month)
            }
            .filter { date -> date > afterExclusive && date >= profile.effectiveFrom }
            .mapNotNull { date -> scheduledActionOn(profile, date) }
            .first()
    }

    override fun isTradingDate(date: LocalDate): Boolean =
        MorningstarLargeCapValueCalendar.isUsTradingDate(date)

    override fun addTradingDays(date: LocalDate, days: Int): LocalDate =
        MorningstarLargeCapValueCalendar.addUsTradingDays(date, days)

    override fun hasPassedRegularOpen(effectiveDate: LocalDate, at: Instant): Boolean =
        MorningstarLargeCapValueCalendar.hasPassedUsRegularOpen(effectiveDate, at)

    override fun hasReachedRegularClose(referenceDate: LocalDate, at: Instant): Boolean =
        MorningstarLargeCapValueCalendar.hasReachedUsRegularClose(referenceDate, at)

    override fun intersectsRegularSession(from: Instant, to: Instant): Boolean =
        MorningstarLargeCapValueCalendar.intersectsUsRegularSession(from, to)

    override fun reachesRegularClose(from: Instant, to: Instant): Boolean =
        MorningstarLargeCapValueCalendar.reachesUsRegularClose(from, to)

    internal fun reconstitutionMonths(profile: EquityMethodologyProfile): Set<Int> =
        profile.parameters.integerSets.getValue(RECONSTITUTION_MONTHS)

    private const val RECONSTITUTION_MONTHS: String = "reconstitutionMonths"
}
