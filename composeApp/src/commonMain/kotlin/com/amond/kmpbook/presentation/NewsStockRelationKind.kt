package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.EtfAssetClass
import com.amond.kmpbook.domain.model.EventImpactResolutionSource
import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventImpactTargetKind
import com.amond.kmpbook.domain.model.EventRecordKind
import com.amond.kmpbook.domain.model.CorporateActionNewsReference
import com.amond.kmpbook.domain.model.CorporateActionNewsTransition
import com.amond.kmpbook.domain.model.CorporateActionRecord
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.IndustrySegment
import com.amond.kmpbook.domain.model.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.InstrumentStrategy
import com.amond.kmpbook.domain.model.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.InvestmentAlertStatus
import com.amond.kmpbook.domain.model.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.KrxSidecarPhase
import com.amond.kmpbook.domain.model.KrxViPhase
import com.amond.kmpbook.domain.model.ListingLifecycleState
import com.amond.kmpbook.domain.model.ListingLifecycleEventKind
import com.amond.kmpbook.domain.model.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketActionKind
import com.amond.kmpbook.domain.model.MarketActionReference
import com.amond.kmpbook.domain.model.MarketActionTransition
import com.amond.kmpbook.domain.model.NewsRelevance
import com.amond.kmpbook.domain.model.PendingCorporateAction
import com.amond.kmpbook.domain.model.ReferenceCurrency
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TradingHaltStatus
import com.amond.kmpbook.domain.model.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.UsLuldPhase
import com.amond.kmpbook.domain.model.UsMwcbPhase
import com.amond.kmpbook.domain.model.directionFor
import com.amond.kmpbook.domain.model.blocksOrderlyProductTermination
import com.amond.kmpbook.domain.model.impactCoverageFor
import com.amond.kmpbook.domain.model.relevanceTo
import com.amond.kmpbook.domain.model.resolvedImpactFor
import com.amond.kmpbook.domain.model.resolvePublishedInstrumentTerminationNotice
import com.amond.kmpbook.domain.simulation.TradingProtectionEngine
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.time.Instant

enum class NewsStockRelationKind(val displayName: String) {
    DIRECT_TARGET("직접 대상"),
    UNDERLYING_EXPOSURE("기초자산 연결"),
    CAUSAL_CHAIN("인과 경로"),
    INDUSTRY_SEGMENT("세부 산업 연결"),
    INDUSTRY("산업 연결"),
    MARKET_CONTEXT("시장 연결"),
}
