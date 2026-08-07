package com.amond.kmpbook.domain.model

import kotlin.time.Instant

/** Whether the game is using a published-year date or its long-range recurrence projection. */
enum class ScheduleBasis(val displayName: String) {
    OFFICIAL("공식 일정"),
    PROJECTED("예상 일정"),
}

/** Calendar dates are deterministic; released figures remain explicitly fictional game data. */
enum class ScheduledValueBasis(val displayName: String) {
    GAME_GENERATED("게임 수치"),
}

enum class ScheduledOutcomeComparison(val displayName: String) {
    ABOVE("시장 예상 대비 상회"),
    INLINE("시장 예상 부합"),
    BELOW("시장 예상 대비 하회"),
}

enum class EconomicReleaseVintage(val displayName: String) {
    ADVANCE("속보치"),
    SECOND("잠정치"),
    THIRD("확정치"),
}

enum class ScheduledEventKind(
    val displayName: String,
    val eventType: EventType,
) {
    US_EMPLOYMENT("미국 고용보고서", EventType.ECONOMIC_INDICATOR),
    US_CPI("미국 소비자물가", EventType.ECONOMIC_INDICATOR),
    US_PCE("미국 PCE 물가", EventType.ECONOMIC_INDICATOR),
    US_GDP("미국 GDP", EventType.ECONOMIC_INDICATOR),
    US_FOMC("미국 FOMC", EventType.CENTRAL_BANK),
    US_RETAIL_SALES("미국 소매판매", EventType.ECONOMIC_INDICATOR),
    US_WEEKLY_CLAIMS("미국 주간 신규 실업수당 청구", EventType.ECONOMIC_INDICATOR),
    KR_CPI("한국 소비자물가", EventType.ECONOMIC_INDICATOR),
    KR_EMPLOYMENT("한국 고용동향", EventType.ECONOMIC_INDICATOR),
    KR_BOK("한국은행 금융통화위원회", EventType.CENTRAL_BANK),
    KR_GDP("한국 GDP", EventType.ECONOMIC_INDICATOR),
    EARNINGS("분기 실적", EventType.EARNINGS),
}

/**
 * A seed-independent item on the game calendar. [scheduledAt] is the public release time,
 * not necessarily the time its shock can first reach a regular trading session.
 */
data class ScheduledEventOccurrence(
    val id: String,
    val seriesId: String,
    val kind: ScheduledEventKind,
    val title: String,
    val description: String,
    val scheduledAt: Instant,
    val timeZoneId: String,
    val scheduleBasis: ScheduleBasis,
    val valueBasis: ScheduledValueBasis = ScheduledValueBasis.GAME_GENERATED,
    val referencePeriod: String? = null,
    val vintage: EconomicReleaseVintage? = null,
    val affectedMarkets: Set<Market>,
    val affectedStockIds: Set<String> = emptySet(),
) {
    init {
        require(id.startsWith(ID_PREFIX) && id.length > ID_PREFIX.length) {
            "Scheduled event ids must use the '$ID_PREFIX' namespace"
        }
        require(seriesId.isNotBlank() && title.isNotBlank() && description.isNotBlank())
        require(timeZoneId.isNotBlank())
        require(affectedMarkets.isNotEmpty())
        require(kind == ScheduledEventKind.EARNINGS || affectedStockIds.isEmpty())
        require(kind != ScheduledEventKind.EARNINGS || affectedStockIds.size == 1)
        require(vintage == null || kind == ScheduledEventKind.US_GDP)
    }

    val scope: EventScope
        get() = if (kind == ScheduledEventKind.EARNINGS) EventScope.STOCK else EventScope.COUNTRY

    val labels: List<String>
        get() = listOf(scheduleBasis.displayName, valueBasis.displayName)

    companion object {
        const val ID_PREFIX: String = "scheduled:"

        fun isScheduledId(id: String): Boolean = id.startsWith(ID_PREFIX)
    }
}

data class ScheduledEventMetric(
    val label: String,
    val actual: Double,
    val consensus: Double,
    val unit: String,
    val decimalPlaces: Int,
) {
    init {
        require(label.isNotBlank() && unit.isNotBlank())
        require(actual.isFinite() && consensus.isFinite())
        require(decimalPlaces in 0..4)
    }
}

/** Seeded game data keyed to one immutable occurrence id. */
data class ScheduledEventOutcome(
    val surpriseScore: Double,
    val comparison: ScheduledOutcomeComparison,
    val metrics: List<ScheduledEventMetric>,
    val direction: ImpactDirection,
    val severity: EventSeverity,
    val impact: GameEventImpact,
) {
    init {
        require(surpriseScore in -1.0..1.0)
        require(metrics.isNotEmpty())
        require(impact.direction == direction)
    }
}

/**
 * [newsEvent] keeps the public release time. [impactEvent] can start later so a premarket or
 * closed-session release retains its shock until the next regular session.
 */
data class ScheduledEventEmission(
    val occurrence: ScheduledEventOccurrence,
    val outcome: ScheduledEventOutcome,
    val newsEvent: GameEvent,
    val impactEvent: GameEvent,
) {
    init {
        require(newsEvent.id == occurrence.id && impactEvent.id == occurrence.id)
        require(newsEvent.startsAt == occurrence.scheduledAt)
        require(impactEvent.startsAt >= occurrence.scheduledAt)
    }

    val impactStartsAt: Instant get() = impactEvent.startsAt
}
