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

/** Canonical dates and U.S. session clock for the built-in SCHD provider. */
internal object SchdDividend100Schedule : EquityMethodologySchedule {
    override val market: Market = Market.NYSE
    override val exposureRegion: EtfExposureRegion = EtfExposureRegion.UNITED_STATES

    override fun marketDate(at: Instant): LocalDate = GameCalendar.marketLocalDateTime(market, at).date

    override fun initialScheduledAction(profile: EquityMethodologyProfile): EquityMethodologyScheduledAction =
        requireNotNull(scheduledActionOn(profile, profile.effectiveFrom)) {
            "SCHD effectiveFrom must be a canonical annual reconstitution date."
        }

    override fun scheduledActionOn(
        profile: EquityMethodologyProfile,
        effectiveDate: LocalDate,
    ): EquityMethodologyScheduledAction? {
        val month = effectiveDate.month.ordinal + 1
        if (month !in rebalanceMonths(profile) ||
            effectiveDate != SchdDividend100Calendar.scheduledRebalanceDate(effectiveDate.year, month)
        ) return null
        val annual = month == annualReconstitutionMonth(profile)
        val kind = if (annual) {
            ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
        } else {
            ReferencePortfolioActionKind.SCHEDULED_REWEIGHT
        }
        val weightReferenceDate = if (annual) {
            SchdDividend100Calendar.annualWeightReferenceDate(effectiveDate)
        } else {
            SchdDividend100Calendar.quarterlyWeightReferenceDate(effectiveDate)
        }
        return EquityMethodologyScheduledAction(
            kind = kind,
            selectionDate = if (annual) {
                SchdDividend100Calendar.thirdFriday(effectiveDate.year, 2)
            } else {
                weightReferenceDate
            },
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
            .flatMap { year -> rebalanceMonths(profile).asSequence().map { year to it } }
            .map { (year, month) -> SchdDividend100Calendar.scheduledRebalanceDate(year, month) }
            .filter { it > afterExclusive }
            .mapNotNull { scheduledActionOn(profile, it) }
            .first { action -> kind == null || action.kind == kind }
    }

    override fun isTradingDate(date: LocalDate): Boolean = SchdDividend100Calendar.isUsTradingDate(date)
    override fun addTradingDays(date: LocalDate, days: Int): LocalDate =
        SchdDividend100Calendar.addUsTradingDays(date, days)
    override fun hasPassedRegularOpen(effectiveDate: LocalDate, at: Instant): Boolean =
        SchdDividend100Calendar.hasPassedUsRegularOpen(effectiveDate, at)
    override fun hasReachedRegularClose(referenceDate: LocalDate, at: Instant): Boolean =
        SchdDividend100Calendar.hasReachedUsRegularClose(referenceDate, at)
    override fun intersectsRegularSession(from: Instant, to: Instant): Boolean =
        SchdDividend100Calendar.intersectsUsRegularSession(from, to)
    override fun reachesRegularClose(from: Instant, to: Instant): Boolean =
        SchdDividend100Calendar.reachesUsRegularClose(from, to)

    internal fun annualReconstitutionMonth(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("annualReconstitutionMonth")
    internal fun rebalanceMonths(profile: EquityMethodologyProfile): Set<Int> =
        profile.parameters.integerSets.getValue("rebalanceMonths")

    private fun ReferencePortfolioActionKind.isScheduledKind(): Boolean =
        this == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION ||
            this == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT
}
