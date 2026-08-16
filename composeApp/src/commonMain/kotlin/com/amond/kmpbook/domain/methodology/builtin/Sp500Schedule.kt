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

/** 최초 구성 뒤에는 분기별 유통주식수/IWF 갱신만 예약하는 S&P 500 v2 일정이다. */
internal object Sp500Schedule : EquityMethodologySchedule {
    override val market: Market = Market.NYSE
    override val exposureRegion: EtfExposureRegion = EtfExposureRegion.UNITED_STATES

    override fun marketDate(at: Instant): LocalDate = GameCalendar.marketLocalDateTime(market, at).date

    override fun initialScheduledAction(profile: EquityMethodologyProfile): EquityMethodologyScheduledAction =
        bootstrapAction(profile)

    override fun scheduledActionOn(
        profile: EquityMethodologyProfile,
        effectiveDate: LocalDate,
    ): EquityMethodologyScheduledAction? {
        if (effectiveDate == profile.effectiveFrom) return bootstrapAction(profile)
        val month = effectiveDate.month.ordinal + 1
        if (month !in quarterlyShareUpdateMonths(profile) ||
            effectiveDate != Sp500Calendar.quarterlyEffectiveDate(effectiveDate.year, month)
        ) return null
        val referenceDate = Sp500Calendar.quarterlyReferenceDate(effectiveDate.year, month)
        return EquityMethodologyScheduledAction(
            kind = ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
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
        require(kind == null || kind.isScheduledKind())
        if ((kind == null || kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) &&
            afterExclusive < profile.effectiveFrom
        ) return bootstrapAction(profile)
        require(kind != ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) {
            "S&P 500 v2 has no recurring scheduled reconstitution."
        }
        return (afterExclusive.year..afterExclusive.year + 3).asSequence()
            .flatMap { year -> quarterlyShareUpdateMonths(profile).sorted().asSequence().map { year to it } }
            .map { (year, month) -> Sp500Calendar.quarterlyEffectiveDate(year, month) }
            .filter { date -> date > afterExclusive && date > profile.effectiveFrom }
            .mapNotNull { date -> scheduledActionOn(profile, date) }
            .first()
    }

    override fun isTradingDate(date: LocalDate): Boolean = Sp500Calendar.isUsTradingDate(date)

    override fun addTradingDays(date: LocalDate, days: Int): LocalDate =
        Sp500Calendar.addUsTradingDays(date, days)

    override fun hasPassedRegularOpen(effectiveDate: LocalDate, at: Instant): Boolean =
        Sp500Calendar.hasPassedUsRegularOpen(effectiveDate, at)

    override fun hasReachedRegularClose(referenceDate: LocalDate, at: Instant): Boolean =
        Sp500Calendar.hasReachedUsRegularClose(referenceDate, at)

    override fun intersectsRegularSession(from: Instant, to: Instant): Boolean =
        Sp500Calendar.intersectsUsRegularSession(from, to)

    override fun reachesRegularClose(from: Instant, to: Instant): Boolean =
        Sp500Calendar.reachesUsRegularClose(from, to)

    internal fun quarterlyShareUpdateMonths(profile: EquityMethodologyProfile): Set<Int> =
        profile.parameters.integerSets.getValue("quarterlyShareUpdateMonths")

    private fun bootstrapAction(profile: EquityMethodologyProfile): EquityMethodologyScheduledAction {
        val month = profile.effectiveFrom.month.ordinal + 1
        require(month in quarterlyShareUpdateMonths(profile))
        require(profile.effectiveFrom == Sp500Calendar.quarterlyEffectiveDate(profile.effectiveFrom.year, month)) {
            "S&P 500 v2 effectiveFrom must be the first US trading date after a quarterly third Friday."
        }
        val referenceDate = Sp500Calendar.quarterlyReferenceDate(profile.effectiveFrom.year, month)
        return EquityMethodologyScheduledAction(
            kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
            selectionDate = referenceDate,
            weightReferenceDate = referenceDate,
            effectiveDate = profile.effectiveFrom,
        )
    }

    private fun ReferencePortfolioActionKind.isScheduledKind(): Boolean =
        this == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION ||
            this == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT
}
