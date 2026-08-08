package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.data.StockCatalog
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.EventImpactHorizon
import com.amond.kmpbook.domain.model.EventImpactInsight
import com.amond.kmpbook.domain.model.EventImpactTargetKind
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventRecordKind
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.ReportedFact
import com.amond.kmpbook.domain.model.ScheduledEventEmission
import com.amond.kmpbook.domain.model.ScheduledEventKind
import com.amond.kmpbook.domain.model.ScheduledEventMetric
import com.amond.kmpbook.domain.model.ScheduledEventOccurrence
import com.amond.kmpbook.domain.model.ScheduledEventOutcome
import com.amond.kmpbook.domain.model.ScheduledEventReference
import com.amond.kmpbook.domain.model.ScheduledOutcomeComparison
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class ScheduledEventGenerationResult(
    val emissions: List<ScheduledEventEmission>,
) {
    val newEvents: List<GameEvent> get() = emissions.map(ScheduledEventEmission::newsEvent)
}
