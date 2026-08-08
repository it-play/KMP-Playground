package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.CausalMarketRegimeSnapshot
import com.amond.kmpbook.domain.model.CausalSignalSeed
import com.amond.kmpbook.domain.model.EventImpactInsight
import com.amond.kmpbook.domain.model.EventImpactCoveragePolicy
import com.amond.kmpbook.domain.model.EventRecordKind
import com.amond.kmpbook.domain.model.EventTradingHaltDirective
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.InstrumentTerminationTerms
import com.amond.kmpbook.domain.model.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.InstrumentStrategy
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.ListingRecoveryCondition
import com.amond.kmpbook.domain.model.ListingRiskTag
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MIN_CAUSAL_SIGNAL_STRENGTH
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.isDirectProductImpactFor
import com.amond.kmpbook.domain.model.resolvedImpactFor
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class EventGenerationResult(
    val newEvents: List<GameEvent>,
    val activeEvents: List<GameEvent>,
    val snapshot: EventEngineSnapshot,
)
