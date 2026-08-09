package com.amond.kmpbook.domain.model.schedule

import com.amond.kmpbook.domain.model.event.EventScope
import com.amond.kmpbook.domain.model.market.Market
import kotlin.time.Instant

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
    }
}
