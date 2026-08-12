package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.data.InstrumentCatalogSnapshot
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionKind
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionMath
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionRecord
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionSource
import com.amond.kmpbook.domain.model.corporateaction.CorporateFundamentalState
import com.amond.kmpbook.domain.model.corporateaction.PendingCorporateAction
import com.amond.kmpbook.domain.model.corporateaction.toAnnouncementNewsReference
import com.amond.kmpbook.domain.model.corporateaction.toAppliedNewsReference
import com.amond.kmpbook.domain.model.corporateaction.toCancellationNewsReference
import com.amond.kmpbook.domain.model.corporateaction.toProductStateCancellationNewsReference
import com.amond.kmpbook.domain.model.event.EventRecordKind
import com.amond.kmpbook.domain.model.event.EventScope
import com.amond.kmpbook.domain.model.event.EventSeverity
import com.amond.kmpbook.domain.model.event.EventType
import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.event.GameEventImpact
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.fund.BenchmarkEngineKind
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.FixedIncomeGeography
import com.amond.kmpbook.domain.model.fund.FundLegalStructure
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioBook
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioBookAdvance
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioRecord
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioState
import com.amond.kmpbook.domain.model.fundproduct.DailyResetCalendar
import com.amond.kmpbook.domain.model.fundproduct.DailyResetLifecycle
import com.amond.kmpbook.domain.model.fundproduct.DailyResetReferenceKind
import com.amond.kmpbook.domain.model.fundproduct.DailyResetState
import com.amond.kmpbook.domain.model.fundproduct.DirectReferenceTerminationPolicy
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadLifecycle
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadState
import com.amond.kmpbook.domain.model.fundproduct.OptionRollCalendar
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyLifecycle
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyState
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundDistribution
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundLedgerEntry
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundState
import com.amond.kmpbook.domain.model.fundstructure.EtnCreditEvent
import com.amond.kmpbook.domain.model.fundstructure.EtnLedgerEntry
import com.amond.kmpbook.domain.model.fundstructure.EtnLedgerKind
import com.amond.kmpbook.domain.model.fundstructure.EtnIssuerCreditModelParameters
import com.amond.kmpbook.domain.model.fundstructure.EtnLifecycle
import com.amond.kmpbook.domain.model.fundstructure.EtnState
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferenceBook
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferenceBookAdvance
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferenceState
import com.amond.kmpbook.domain.model.reference.FixedIncomeRollRecord
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaBook
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaAdvanceInput
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaActionKind
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaBookAdvance
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaRebalanceRecord
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaState
import com.amond.kmpbook.domain.model.reference.CommodityReferenceBook
import com.amond.kmpbook.domain.model.reference.CommodityReferenceBookAdvance
import com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceState
import com.amond.kmpbook.domain.model.reference.CompositeReferenceAdvanceInput
import com.amond.kmpbook.domain.model.reference.CompositeReferenceActionKind
import com.amond.kmpbook.domain.model.reference.CompositeReferenceBook
import com.amond.kmpbook.domain.model.reference.CompositeReferenceBookAdvance
import com.amond.kmpbook.domain.model.reference.CompositeReferenceRebalanceRecord
import com.amond.kmpbook.domain.model.reference.CompositeReferenceState
import com.amond.kmpbook.domain.model.reference.EquityReferenceBook
import com.amond.kmpbook.domain.model.reference.EquityReferenceBookAdvance
import com.amond.kmpbook.domain.model.reference.EquityReferenceRebalanceRecord
import com.amond.kmpbook.domain.model.reference.EquityReferenceState
import com.amond.kmpbook.domain.model.reference.FuturesAllocationRecord
import com.amond.kmpbook.domain.model.reference.FuturesReferenceState
import com.amond.kmpbook.domain.model.reference.FuturesRollRecord
import com.amond.kmpbook.domain.model.reference.FundOfFundsBook
import com.amond.kmpbook.domain.model.reference.FundOfFundsBookAdvance
import com.amond.kmpbook.domain.model.reference.FundOfFundsRebalanceRecord
import com.amond.kmpbook.domain.model.reference.FundOfFundsState
import com.amond.kmpbook.domain.model.reference.ReferenceCurrencyPair
import com.amond.kmpbook.domain.model.reference.ReferenceSourceCatalog
import com.amond.kmpbook.domain.model.reference.ReferenceSourceReturnFrame
import com.amond.kmpbook.domain.model.reference.ReferenceSourceSnapshot
import com.amond.kmpbook.domain.model.game.GameEndReason
import com.amond.kmpbook.domain.model.game.GamePhase
import com.amond.kmpbook.domain.model.game.Screen
import com.amond.kmpbook.domain.model.game.TurnStep
import com.amond.kmpbook.domain.model.index.MarketIndexId
import com.amond.kmpbook.domain.model.index.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.instrument.DistributionFrequency
import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
import com.amond.kmpbook.domain.model.instrument.EtfTaxCategory
import com.amond.kmpbook.domain.model.instrument.FundFinancialState
import com.amond.kmpbook.domain.model.instrument.InstrumentStrategy
import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertDesignation
import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertReleaseRule
import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertStatus
import com.amond.kmpbook.domain.model.listing.lifecycle.DailyListingSurveillanceInput
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingFinalDisposition
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleEventKind
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleReason
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleState
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingNoticeLevel
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRecoveryCondition
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRiskSeverity
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRiskTag
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationTerms
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.listing.termination.resolveInstrumentTerminationAtSessionClose
import com.amond.kmpbook.domain.model.listing.termination.scheduledTerminationOn
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.MarketMoveDirection
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.marketaction.MarketActionKind
import com.amond.kmpbook.domain.model.marketaction.MarketActionReference
import com.amond.kmpbook.domain.model.marketaction.MarketActionTransition
import com.amond.kmpbook.domain.model.marketaction.investmentAlertOccurrenceId
import com.amond.kmpbook.domain.model.marketaction.krxCircuitBreakerOccurrenceId
import com.amond.kmpbook.domain.model.marketaction.krxSidecarOccurrenceId
import com.amond.kmpbook.domain.model.marketaction.krxViOccurrenceId
import com.amond.kmpbook.domain.model.marketaction.usLuldOccurrenceId
import com.amond.kmpbook.domain.model.marketaction.usMwcbOccurrenceId
import com.amond.kmpbook.domain.model.portfolio.Holding
import com.amond.kmpbook.domain.model.portfolio.PortfolioSnapshot
import com.amond.kmpbook.domain.model.pricing.PriceBar
import com.amond.kmpbook.domain.model.pricing.PriceBarInterval
import com.amond.kmpbook.domain.model.pricing.Quote
import com.amond.kmpbook.domain.model.protection.core.InstrumentTradingHalt
import com.amond.kmpbook.domain.model.protection.core.TradingHaltReason
import com.amond.kmpbook.domain.model.protection.core.TradingHaltStatus
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionAction
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionRequest
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerEvent
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerLevel
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerObservation
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerState
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarEvent
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarObservation
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarReleaseReason
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarState
import com.amond.kmpbook.domain.model.protection.krx.KrxViDirection
import com.amond.kmpbook.domain.model.protection.krx.KrxViEvent
import com.amond.kmpbook.domain.model.protection.krx.KrxViKind
import com.amond.kmpbook.domain.model.protection.krx.KrxViObservation
import com.amond.kmpbook.domain.model.protection.krx.KrxViPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxViProductClass
import com.amond.kmpbook.domain.model.protection.krx.KrxViSession
import com.amond.kmpbook.domain.model.protection.krx.KrxViState
import com.amond.kmpbook.domain.model.protection.us.UsLuldEvent
import com.amond.kmpbook.domain.model.protection.us.UsLuldLimitSide
import com.amond.kmpbook.domain.model.protection.us.UsLuldObservation
import com.amond.kmpbook.domain.model.protection.us.UsLuldPhase
import com.amond.kmpbook.domain.model.protection.us.UsLuldState
import com.amond.kmpbook.domain.model.protection.us.UsLuldTier
import com.amond.kmpbook.domain.model.protection.us.UsMwcbEvent
import com.amond.kmpbook.domain.model.protection.us.UsMwcbLevel
import com.amond.kmpbook.domain.model.protection.us.UsMwcbObservation
import com.amond.kmpbook.domain.model.protection.us.UsMwcbPhase
import com.amond.kmpbook.domain.model.protection.us.UsMwcbState
import com.amond.kmpbook.domain.model.protection.us.UsMwcbVenuePhase
import com.amond.kmpbook.domain.model.schedule.ScheduledEventEmission
import com.amond.kmpbook.domain.model.schedule.ScheduledEventKind
import com.amond.kmpbook.domain.model.trading.Order
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.OrderStatus
import com.amond.kmpbook.domain.model.trading.OrderType
import com.amond.kmpbook.domain.model.trading.TimeInForce
import com.amond.kmpbook.domain.model.trading.Trade
import com.amond.kmpbook.domain.model.trading.TradeSettlementKind
import com.amond.kmpbook.domain.model.trading.TradingDayWindow
import com.amond.kmpbook.domain.model.venue.MarketSession
import com.amond.kmpbook.domain.simulation.event.DebugEventGuide
import com.amond.kmpbook.domain.simulation.event.EventEngine
import com.amond.kmpbook.domain.simulation.event.EventGenerationContext
import com.amond.kmpbook.domain.simulation.event.EventGenerationResult
import com.amond.kmpbook.domain.simulation.event.EventShockCalculator
import com.amond.kmpbook.domain.simulation.fund.ReferencePortfolioEngine
import com.amond.kmpbook.domain.simulation.fundproduct.FundProductOverlayEngine
import com.amond.kmpbook.domain.simulation.fundproduct.DailyResetAdvanceInput
import com.amond.kmpbook.domain.simulation.fundproduct.DailyResetEngine
import com.amond.kmpbook.domain.simulation.fundproduct.CashCollateralizedPutSpreadAdvanceInput
import com.amond.kmpbook.domain.simulation.fundproduct.CashCollateralizedPutSpreadEngine
import com.amond.kmpbook.domain.simulation.fundproduct.OptionStrategyAdvanceInput
import com.amond.kmpbook.domain.simulation.fundproduct.OptionStrategyEngine
import com.amond.kmpbook.domain.simulation.fundstructure.ClosedEndFundAdvanceInput
import com.amond.kmpbook.domain.simulation.fundstructure.ClosedEndFundEngine
import com.amond.kmpbook.domain.simulation.fundstructure.EtnAdvanceInput
import com.amond.kmpbook.domain.simulation.fundstructure.EtnEngine
import com.amond.kmpbook.domain.simulation.reference.FixedIncomeReferenceBookEngine
import com.amond.kmpbook.domain.simulation.reference.AlternativeRiskPremiaBookEngine
import com.amond.kmpbook.domain.simulation.reference.CommodityMarketModel
import com.amond.kmpbook.domain.simulation.reference.CommodityReferenceBookEngine
import com.amond.kmpbook.domain.simulation.reference.CompositeReferenceBookEngine
import com.amond.kmpbook.domain.simulation.reference.EquityReferenceBookEngine
import com.amond.kmpbook.domain.simulation.reference.FundOfFundsBookEngine
import com.amond.kmpbook.domain.simulation.listing.ListingLifecycleEngine
import com.amond.kmpbook.domain.simulation.listing.ListingRemediationDecisionStatus
import com.amond.kmpbook.domain.simulation.listing.ListingRemediationPolicy
import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
import com.amond.kmpbook.domain.simulation.market.MarketDynamicsEngine
import com.amond.kmpbook.domain.simulation.market.MarketIndexCalculationInput
import com.amond.kmpbook.domain.simulation.market.MarketIndexEngine
import com.amond.kmpbook.domain.simulation.market.MarketMicrostructure
import com.amond.kmpbook.domain.simulation.order.OrderBookEngine
import com.amond.kmpbook.domain.simulation.order.OrderBookGenerationInput
import com.amond.kmpbook.domain.simulation.order.OrderBookSnapshot
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import com.amond.kmpbook.domain.simulation.price.InstrumentMetricsEngine
import com.amond.kmpbook.domain.simulation.price.PriceAttribution
import com.amond.kmpbook.domain.simulation.price.PriceEngine
import com.amond.kmpbook.domain.simulation.price.PriceGenerationInput
import com.amond.kmpbook.domain.simulation.protection.TradingProtectionEngine
import com.amond.kmpbook.domain.simulation.protection.TradingProtectionRules
import com.amond.kmpbook.domain.simulation.schedule.ScheduledEventEngine
import com.amond.kmpbook.domain.simulation.schedule.DistributionSchedule
import com.amond.kmpbook.domain.tax.core.MoneyAmount
import com.amond.kmpbook.domain.tax.core.CheckedMonetaryArithmetic
import com.amond.kmpbook.domain.tax.core.MoneyRoundingPolicy
import com.amond.kmpbook.domain.tax.core.TaxRate
import com.amond.kmpbook.domain.tax.dividend.DividendTaxCalculator
import com.amond.kmpbook.domain.tax.dividend.DividendTaxClass
import com.amond.kmpbook.domain.tax.dividend.DividendTaxRequest
import com.amond.kmpbook.domain.tax.domestic.DomesticEtfSaleTaxCalculator
import com.amond.kmpbook.domain.tax.domestic.DomesticEtfSaleTaxRequest
import com.amond.kmpbook.domain.tax.domestic.DomesticSaleTaxCalculator
import com.amond.kmpbook.domain.tax.domestic.DomesticSaleTaxRequest
import com.amond.kmpbook.domain.tax.fee.BrokerFeeCalculator
import com.amond.kmpbook.domain.tax.fee.BrokerFeeRequest
import com.amond.kmpbook.domain.tax.fee.BrokerFeeSchedule
import com.amond.kmpbook.domain.tax.foreign.ForeignInstrumentTaxClass
import com.amond.kmpbook.domain.tax.liability.AnnualStockTaxCalculator
import com.amond.kmpbook.domain.tax.liability.AnnualStockTaxRequest
import com.amond.kmpbook.domain.tax.liability.AnnualTaxLedger
import com.amond.kmpbook.domain.tax.liability.StockGainTaxTreatment
import com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus
import com.amond.kmpbook.domain.tax.lot.FifoCostBasisBook
import com.amond.kmpbook.domain.tax.lot.RealizedStockGain
import com.amond.kmpbook.domain.tax.shareholder.MajorShareholderAssessmentRequest
import com.amond.kmpbook.domain.tax.shareholder.MajorShareholderCalculator
import com.amond.kmpbook.domain.tax.shareholder.ShareholderHoldingSnapshot
import com.amond.kmpbook.domain.tax.shareholder.ShareholderRelation
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.presentation.portfolio.BenchmarkPoint
import com.amond.kmpbook.presentation.portfolio.DailyPortfolioStat
import com.amond.kmpbook.presentation.portfolio.DailyTradingSurveillancePoint
import com.amond.kmpbook.presentation.portfolio.DividendLedgerEntry
import com.amond.kmpbook.presentation.portfolio.ForeignExchangeRecord
import com.amond.kmpbook.presentation.portfolio.RealizedGainRecord
import com.amond.kmpbook.presentation.portfolio.TaxPaymentNotice
import com.amond.kmpbook.presentation.portfolio.TransactionCostRecord
import com.amond.kmpbook.presentation.trading.OrderRequest
import com.amond.kmpbook.presentation.trading.RuntimePriceBounds
import com.amond.kmpbook.presentation.trading.RuntimeTradingInterval
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Uses the real persistence deadline when it fits in this turn; otherwise leaves a pending observation at turn-end. */
private fun runtimePersistenceObservationAt(
    conditionSince: Instant,
    turnEnd: Instant,
    persistence: kotlin.time.Duration,
): Instant = minOf(conditionSince + persistence, turnEnd)

/**
 * Campaign-aware exchange closures.
 *
 * The frozen holiday pack ends in 2040, while the last New York session reaches
 * 2041-01-01 06:00 KST and legal T+1/T+2 dates can fall a few days into 2041.
 * Only New Year's Day needs an explicit closure in that short settlement tail.
 */
private fun runtimeClosedDates(market: Market, date: LocalDate): Set<LocalDate> = when {
    date.year in GameCalendar.START_LOCAL_DATE_TIME.year..GameCalendar.CAMPAIGN_END_DATE.year ->
        DefaultMarketHolidays.closedDates(market, date.year)
    date == LocalDate(GameCalendar.CAMPAIGN_END_DATE.year + 1, 1, 1) -> setOf(date)
    else -> emptySet()
}

/** Exact regular-session intersection minus half-open protection intervals. */
private fun runtimeTradableIntervals(
    market: Market,
    from: Instant,
    to: Instant,
    blocked: List<RuntimeTradingInterval> = emptyList(),
): List<RuntimeTradingInterval> {
    require(to >= from)
    if (to == from) return emptyList()
    val localDates = linkedSetOf(
        GameCalendar.marketLocalDateTime(market, from).date,
        GameCalendar.marketLocalDateTime(market, to).date,
    )
    val regular = localDates.mapNotNull { date ->
        GameCalendar.regularSessionWindow(
            market,
            date,
            runtimeClosedDates(market, date),
        )?.let { window ->
            val start = maxOf(from, window.opensAt)
            val end = minOf(to, window.closesAt)
            if (end > start) RuntimeTradingInterval(start, end) else null
        }
    }
    if (regular.isEmpty() || blocked.isEmpty()) return regular

    return blocked.sortedBy(RuntimeTradingInterval::startsAt).fold(regular) { available, exclusion ->
        available.flatMap { interval ->
            if (exclusion.endsAt <= interval.startsAt || exclusion.startsAt >= interval.endsAt) {
                listOf(interval)
            } else {
                buildList {
                    if (exclusion.startsAt > interval.startsAt) {
                        add(RuntimeTradingInterval(interval.startsAt, minOf(exclusion.startsAt, interval.endsAt)))
                    }
                    if (exclusion.endsAt < interval.endsAt) {
                        add(RuntimeTradingInterval(maxOf(exclusion.endsAt, interval.startsAt), interval.endsAt))
                    }
                }
            }
        }
    }
}

private fun runtimeTradingFraction(
    from: Instant,
    to: Instant,
    intervals: List<RuntimeTradingInterval>,
): Double {
    require(to >= from)
    if (to == from) return 0.0
    val available = intervals.sumOf { (it.endsAt - it.startsAt).inWholeNanoseconds }.toDouble()
    return (available / (to - from).inWholeNanoseconds.toDouble()).coerceIn(0.0, 1.0)
}

/**
 * VI triggering quotations and LULD out-of-band quotations are observations, not executable OHLC
 * prices. Protection thresholds can lie between venue ticks, so convert them inward to the nearest
 * executable prices before clamping an already tick-rounded bar.
 */
private fun runtimeClampBarToBounds(
    stock: StockDefinition,
    bar: PriceBar,
    bounds: RuntimePriceBounds,
): PriceBar {
    val executableLower = bounds.lower?.let { lower -> MarketMicrostructure.roundUp(stock, lower) }
    val executableUpper = bounds.upper?.let { upper -> MarketMicrostructure.roundDown(stock, upper) }
    require(
        executableLower == null || executableUpper == null || executableLower <= executableUpper,
    ) {
        "보호 가격 범위 안에 실행 가능한 호가가 없습니다: " +
            "${stock.id}, lower=$executableLower, upper=$executableUpper"
    }
    fun bounded(value: Double): Double = value
        .let { executableLower?.let { lower -> it.coerceAtLeast(lower) } ?: it }
        .let { executableUpper?.let { upper -> it.coerceAtMost(upper) } ?: it }
    val open = bounded(bar.open)
    val close = bounded(bar.close)
    val high = bounded(bar.high).coerceAtLeast(maxOf(open, close))
    val low = bounded(bar.low).coerceAtMost(minOf(open, close))
    return bar.copy(open = open, high = high, low = low, close = close)
}

private fun krxInvestmentAlertReleaseCriteriaCleared(
    designation: InvestmentAlertDesignation,
    points: List<DailyTradingSurveillancePoint>,
): Boolean {
    if (points.isEmpty()) return false
    val close = points.last().close
    fun rise(days: Int): Double? = points.getOrNull(points.lastIndex - days)?.close
        ?.takeIf { it > 0.0 }
        ?.let { close / it - 1.0 }
    val hasFullReleaseWindow = points.size >= 16
    val highest15 = points.size >= 15 && close >= points.takeLast(15)
        .maxOf(DailyTradingSurveillancePoint::close)
    val recentAverageVolume = points.dropLast(1).takeLast(5)
        .takeIf { it.size == 5 }
        ?.map { it.volume.toDouble() }
        ?.average()
        ?: 0.0
    val releaseRule = krxInvestmentAlertReleaseRule(designation)
    return when (releaseRule) {
        InvestmentAlertReleaseRule.CAUTION_PRICE_VOLUME ->
            (rise(5) ?: Double.NEGATIVE_INFINITY) < 0.40 &&
                (rise(15) ?: Double.NEGATIVE_INFINITY) < 0.75 &&
                (recentAverageVolume <= 0.0 || points.last().volume < recentAverageVolume * 5.0)
        InvestmentAlertReleaseRule.WARNING_45_75 -> hasFullReleaseWindow &&
            (rise(5) ?: Double.POSITIVE_INFINITY) < 0.45 &&
            (rise(15) ?: Double.POSITIVE_INFINITY) < 0.75 &&
            !highest15
        InvestmentAlertReleaseRule.WARNING_60_100,
        InvestmentAlertReleaseRule.DANGER_60_100,
        -> hasFullReleaseWindow &&
            (rise(5) ?: Double.POSITIVE_INFINITY) < 0.60 &&
            (rise(15) ?: Double.POSITIVE_INFINITY) < 1.00 &&
            !highest15
    }
}

private fun krxInvestmentAlertReleaseRule(
    designation: InvestmentAlertDesignation,
): InvestmentAlertReleaseRule = designation.releaseRule

private fun krxRelativeMarketRiseMultipleSatisfied(
    points: List<DailyTradingSurveillancePoint>,
    days: Int,
    multiple: Double,
): Boolean {
    require(days > 0 && multiple > 0.0)
    val current = points.lastOrNull() ?: return false
    val base = points.getOrNull(points.lastIndex - days) ?: return false
    val currentMarket = current.marketProxyClose ?: return false
    val baseMarket = base.marketProxyClose ?: return false
    if (base.close <= 0.0 || baseMarket <= 0.0) return false
    val stockRise = current.close / base.close - 1.0
    val marketRise = currentMarket / baseMarket - 1.0
    return stockRise + SimulatorRuntime.PRICE_EPSILON >= marketRise * multiple
}

private fun krxAlertRise(
    points: List<DailyTradingSurveillancePoint>,
    days: Int,
): Double? {
    val current = points.lastOrNull()?.close ?: return null
    return points.getOrNull(points.lastIndex - days)?.close
        ?.takeIf { it > 0.0 }
        ?.let { current / it - 1.0 }
}

private fun krxAlertIsHighest15(points: List<DailyTradingSurveillancePoint>): Boolean =
    points.size >= 15 && points.last().close >= points.takeLast(15)
        .maxOf(DailyTradingSurveillancePoint::close)

private fun krxOutsideTop100AtPreviousClose(
    points: List<DailyTradingSurveillancePoint>,
): Boolean = points.getOrNull(points.lastIndex - 1)
    ?.krxMarketCapRank
    ?.let { it > 100 }
    ?: false

private fun krxWarningNoticeReasonCodes(
    points: List<DailyTradingSurveillancePoint>,
): Set<String> = buildSet {
    // 지정예고는 가격상승률만 적출한다. 최고가·시장대비배수·시총순위는 예고 후
    // 실제 지정 판단일에 함께 확인한다.
    if ((krxAlertRise(points, 3) ?: Double.NEGATIVE_INFINITY) >= 1.00) add("WARNING_NOTICE_3D_100")
    if ((krxAlertRise(points, 5) ?: Double.NEGATIVE_INFINITY) >= 0.60) add("WARNING_NOTICE_5D_60")
    if ((krxAlertRise(points, 15) ?: Double.NEGATIVE_INFINITY) >= 1.00) add("WARNING_NOTICE_15D_100")
}

private fun krxWarningDesignationReasonCodes(
    designation: InvestmentAlertDesignation?,
    points: List<DailyTradingSurveillancePoint>,
    market: Market = Market.KOSDAQ,
): Set<String> = buildSet {
    if (designation?.level != InvestmentAlertLevel.CAUTION ||
        !designation.escalationNoticeReasons.any { it.startsWith("WARNING_NOTICE_") } ||
        !krxEscalationNoticeJudgmentOpen(designation, points) ||
        !krxAlertIsHighest15(points)
    ) {
        return@buildSet
    }
    val outsideTop100 = krxOutsideTop100AtPreviousClose(points)
    if ("WARNING_NOTICE_3D_100" in designation.escalationNoticeReasons &&
        (market == Market.KOSPI || outsideTop100) &&
        (krxAlertRise(points, 3) ?: Double.NEGATIVE_INFINITY) >= 1.00 &&
        krxRelativeMarketRiseMultipleSatisfied(points, 3, 5.0)
    ) {
        add("WARNING_PRICE_INDEX_3D_100")
    }
    if ("WARNING_NOTICE_5D_60" in designation.escalationNoticeReasons &&
        outsideTop100 &&
        (krxAlertRise(points, 5) ?: Double.NEGATIVE_INFINITY) >= 0.60 &&
        krxRelativeMarketRiseMultipleSatisfied(points, 5, 5.0)
    ) {
        add("WARNING_PRICE_INDEX_5D_60")
    }
    if ("WARNING_NOTICE_15D_100" in designation.escalationNoticeReasons &&
        outsideTop100 &&
        (krxAlertRise(points, 15) ?: Double.NEGATIVE_INFINITY) >= 1.00 &&
        krxRelativeMarketRiseMultipleSatisfied(points, 15, 3.0)
    ) {
        add("WARNING_PRICE_INDEX_15D_100")
    }
}

private fun krxDangerNoticeReasonCodes(
    points: List<DailyTradingSurveillancePoint>,
): Set<String> = buildSet {
    // 지정예고는 가격상승률만 적출한다. 최근 15일 최고가와 시장지수 대비 배수는
    // 예고 후 실제 지정 판단일에 함께 확인한다.
    if ((krxAlertRise(points, 3) ?: Double.NEGATIVE_INFINITY) >= 0.45) {
        add("DANGER_NOTICE_3D_45")
    }
    if ((krxAlertRise(points, 5) ?: Double.NEGATIVE_INFINITY) >= 0.60) {
        add("DANGER_NOTICE_5D_60")
    }
    if ((krxAlertRise(points, 15) ?: Double.NEGATIVE_INFINITY) >= 1.00) {
        add("DANGER_NOTICE_15D_100")
    }
}

private fun krxDangerDesignationReasonCodes(
    designation: InvestmentAlertDesignation?,
    points: List<DailyTradingSurveillancePoint>,
): Set<String> = buildSet {
    if (designation?.status != InvestmentAlertStatus.ACTIVE ||
        designation.level != InvestmentAlertLevel.WARNING ||
        !designation.escalationNoticeReasons.any { it.startsWith("DANGER_NOTICE_") } ||
        !krxEscalationNoticeJudgmentOpen(designation, points) ||
        !krxAlertIsHighest15(points)
    ) {
        return@buildSet
    }
    if ("DANGER_NOTICE_3D_45" in designation.escalationNoticeReasons &&
        (krxAlertRise(points, 3) ?: Double.NEGATIVE_INFINITY) >= 0.45 &&
        krxRelativeMarketRiseMultipleSatisfied(points, 3, 5.0)
    ) {
        add("DANGER_PRICE_INDEX_3D_45")
    }
    if ("DANGER_NOTICE_5D_60" in designation.escalationNoticeReasons &&
        (krxAlertRise(points, 5) ?: Double.NEGATIVE_INFINITY) >= 0.60 &&
        krxRelativeMarketRiseMultipleSatisfied(points, 5, 5.0)
    ) {
        add("DANGER_PRICE_INDEX_5D_60")
    }
    if ("DANGER_NOTICE_15D_100" in designation.escalationNoticeReasons &&
        (krxAlertRise(points, 15) ?: Double.NEGATIVE_INFINITY) >= 1.00 &&
        krxRelativeMarketRiseMultipleSatisfied(points, 15, 3.0)
    ) {
        add("DANGER_PRICE_INDEX_15D_100")
    }
}

private fun krxEscalationNoticeJudgmentOpen(
    designation: InvestmentAlertDesignation,
    points: List<DailyTradingSurveillancePoint>,
): Boolean {
    val noticeOn = designation.escalationNoticeOn ?: return false
    val observations = points.filter { it.date >= noticeOn }
    return observations.isNotEmpty() && observations.size <= 10 && points.last().date >= noticeOn
}

private fun krxWarningRedesignationCriteriaSatisfied(
    designation: InvestmentAlertDesignation,
    points: List<DailyTradingSurveillancePoint>,
): Boolean {
    if (designation.level != InvestmentAlertLevel.CAUTION ||
        "WARNING_RELEASE_REDESIGNATION" !in designation.reasonCodes
    ) {
        return false
    }
    val today = points.lastOrNull()?.date ?: return false
    if (designation.redesignationWindow?.contains(today) != true) return false
    val current = points.last()
    val twoDaysBefore = points.getOrNull(points.lastIndex - 2) ?: return false
    val preDesignation = designation.preDesignationClose ?: return false
    val preRelease = designation.preReleaseClose ?: return false
    val wasOutsideTop100 = points.getOrNull(points.lastIndex - 1)?.krxMarketCapRank?.let { it > 100 } ?: false
    return wasOutsideTop100 &&
        current.close > preDesignation &&
        current.close > preRelease &&
        current.close + SimulatorRuntime.PRICE_EPSILON >= twoDaysBefore.close * 1.40
}

private fun krxInvestmentAlertObservedTradingDays(
    designation: InvestmentAlertDesignation,
    points: List<DailyTradingSurveillancePoint>,
): Int = when (designation.level) {
    InvestmentAlertLevel.WARNING -> points.count { it.date >= designation.designatedOn }
    InvestmentAlertLevel.CAUTION,
    InvestmentAlertLevel.DANGER,
    -> points.count { it.date > designation.designatedOn }
}

private fun krxWarningAdditionalRiseEvaluationDate(
    designation: InvestmentAlertDesignation,
    points: List<DailyTradingSurveillancePoint>,
): LocalDate? {
    if (designation.level != InvestmentAlertLevel.WARNING || points.isEmpty()) return null
    val designatedIndex = points.indexOfFirst { it.date == designation.designatedOn }
    if (designatedIndex < 0) return null
    val designatedClose = points[designatedIndex].close
    val preDesignationClose = points.take(designatedIndex).lastOrNull()?.close ?: return null
    val evaluation = points.getOrNull(designatedIndex + 2) ?: return null
    val latest = points.last()
    return evaluation.date.takeIf {
        latest.date == evaluation.date &&
            latest.close + SimulatorRuntime.PRICE_EPSILON >= designatedClose * 1.40 &&
            latest.close > preDesignationClose
    }
}

internal class SimulatorRuntime(
    initialOptions: NewGameOptions,
    private val instrumentCatalog: InstrumentCatalogSnapshot,
    startInSetup: Boolean = false,
) {
    var options: NewGameOptions = initialOptions
        private set
    var phase: GamePhase = if (startInSetup) GamePhase.SETUP else GamePhase.PLAYING
        private set
    var screen: Screen = Screen.HOME
        private set
    var currentTime: Instant = GameCalendar.startInstant
        private set
    var turn: Long = 0L
        private set
    var selectedTurnStep: TurnStep = TurnStep.ONE_HOUR
        private set
    var selectedStockId: String? = null
        private set
    var isAdvancing: Boolean = false
        private set
    var lastMessage: String? = "새 게임을 시작했습니다."
        private set

    private var baseStockDefinitions: List<StockDefinition> = if (options.usFractionalTrading) {
        instrumentCatalog.withUsFractionalTrading()
    } else {
        instrumentCatalog.definitions
    }
    private var baseStockById = baseStockDefinitions.associateBy(StockDefinition::id)
    private val mutableStocks = baseStockDefinitions.toMutableList()
    val stocks: List<StockDefinition> get() = mutableStocks
    private val stockById = mutableStocks.associateByTo(linkedMapOf(), StockDefinition::id)
    private val quotes = linkedMapOf<String, Quote>()
    private val history = linkedMapOf<String, ArrayDeque<PriceBar>>()
    private val chartPriceHistory =
        linkedMapOf<String, MutableMap<PriceBarInterval, ArrayDeque<PriceBar>>>()
    private val pendingEtfReferenceReturns = mutableMapOf<String, Double>()
    /** Event level changes observed while a listing is closed, consumed by its next opening auction. */
    private val pendingClosedEventLogReturns = mutableMapOf<String, Double>()
    private val corporateFundamentals = linkedMapOf<String, CorporateFundamentalState>()
    private val fundFinancialStates = linkedMapOf<String, FundFinancialState>()
    private val referencePortfolioStates = linkedMapOf<String, ReferencePortfolioState>()
    private val referencePortfolioLedger = mutableListOf<ReferencePortfolioRecord>()
    private val equityReferenceStates = linkedMapOf<BenchmarkRef, EquityReferenceState>()
    private val equityReferenceLedger = mutableListOf<EquityReferenceRebalanceRecord>()
    private val fundOfFundsStates = linkedMapOf<BenchmarkRef, FundOfFundsState>()
    private val fundOfFundsRebalanceLedger = mutableListOf<FundOfFundsRebalanceRecord>()
    private val alternativeRiskPremiaStates = linkedMapOf<BenchmarkRef, AlternativeRiskPremiaState>()
    private val alternativeRiskPremiaRebalanceLedger =
        mutableListOf<AlternativeRiskPremiaRebalanceRecord>()
    private val compositeReferenceStates = linkedMapOf<BenchmarkRef, CompositeReferenceState>()
    private val compositeReferenceRebalanceLedger = mutableListOf<CompositeReferenceRebalanceRecord>()
    private val dailyResetStates = linkedMapOf<String, DailyResetState>()
    private val optionStrategyStates = linkedMapOf<String, OptionStrategyState>()
    private val cashCollateralizedPutSpreadStates =
        linkedMapOf<String, CashCollateralizedPutSpreadState>()
    private val etnStates = linkedMapOf<String, EtnState>()
    private val etnLedger = mutableListOf<EtnLedgerEntry>()
    private val closedEndFundStates = linkedMapOf<String, ClosedEndFundState>()
    private val closedEndFundLedger = mutableListOf<ClosedEndFundLedgerEntry>()
    private val fixedIncomeReferenceStates = linkedMapOf<String, FixedIncomeReferenceState>()
    private val fixedIncomeRollLedger = mutableListOf<FixedIncomeRollRecord>()
    private val commoditySpotReferenceStates =
        linkedMapOf<BenchmarkRef, CommoditySpotReferenceState>()
    private val futuresReferenceStates = linkedMapOf<BenchmarkRef, FuturesReferenceState>()
    private val futuresRollLedger = mutableListOf<FuturesRollRecord>()
    private val futuresAllocationLedger = mutableListOf<FuturesAllocationRecord>()
    /** 휴장·거래정지 중에도 유실되지 않고 다음 거래 가능 시간에 한 번 소비되는 설정·환매 신호다. */
    private val pendingFundFlowRates = mutableMapOf<String, Double>()
    private val marketIndices = linkedMapOf<MarketIndexId, MarketIndexSnapshot>()
    private val marketIndexHistory = linkedMapOf<MarketIndexId, ArrayDeque<MarketIndexSnapshot>>()
    private val dailyTrackers = mutableMapOf<String, DailyPriceTracker>()
    private val cash = mutableMapOf(Currency.KRW to options.initialCapitalKrw, Currency.USD to 0.0)
    private val holdings = linkedMapOf<String, Holding>()
    private val orders = mutableListOf<Order>()
    private val trades = mutableListOf<Trade>()
    private val transactionCosts = mutableListOf<TransactionCostRecord>()
    private val realizedGains = mutableListOf<RealizedGainRecord>()
    private var fifoCostBasisBook = FifoCostBasisBook()
    private val taxExchangeRatesByTradeId = linkedMapOf<String, Double>()
    private val pendingTaxSettlementTradeIds = linkedSetOf<String>()
    private val dividends = mutableListOf<DividendLedgerEntry>()
    private val lastEvaluatedDistributionDateByStock = linkedMapOf<String, LocalDate>()
    private val foreignExchanges = mutableListOf<ForeignExchangeRecord>()
    private val activeEvents = mutableListOf<GameEvent>()
    private val newsEvents = mutableListOf<GameEvent>()
    private val readEventIds = mutableSetOf<String>()
    private val readStockNewsEventIds = linkedMapOf<String, MutableSet<String>>()
    private val watchlistedStockIds = linkedSetOf<String>()
    private val pendingCorporateActions = mutableListOf<PendingCorporateAction>()
    private val corporateActionLedger = mutableListOf<CorporateActionRecord>()
    private val listingLifecycleStates = linkedMapOf<String, ListingLifecycleState>()
    private val listingLifecycleLedger = mutableListOf<ListingLifecycleLedgerEvent>()
    private var tradingProtectionSnapshot = TradingProtectionSnapshot()
    private val dailyTradingSurveillance = linkedMapOf<String, ArrayDeque<DailyTradingSurveillancePoint>>()
    private val portfolioSnapshots = mutableListOf<PortfolioSnapshot>()
    private val dailyStatistics = mutableListOf<DailyPortfolioStat>()
    private val benchmarkHistory = mutableListOf<BenchmarkPoint>()
    private val annualTaxLedgers = linkedMapOf<Int, com.amond.kmpbook.domain.tax.liability.AnnualTaxLedger>()
    private val taxPaymentNotices = mutableListOf<TaxPaymentNotice>()

    private var macro = MacroEnvironment(
        usdKrw = options.initialUsdKrw,
        fxRatesToKrw = initialFxRates(options.initialUsdKrw),
        previousFxRatesToKrw = initialFxRates(options.initialUsdKrw),
    )
    private var externalMarketForcesTarget = options.initialExternalMarketForces
    private var macroDate = gameDate(currentTime)
    private var benchmarkValue = BENCHMARK_START
    private var peakAssetsKrw = options.initialCapitalKrw
    private var maximumDrawdown = 0.0
    private var nextSequence = 1L

    private val random = DeterministicRandom(
        DeterministicRandom.mixSeed(options.seed, MACRO_STREAM_ID),
    )
    private val marketDynamicsEngine = MarketDynamicsEngine(
        seed = DeterministicRandom.mixSeed(options.seed, DYNAMICS_STREAM_ID),
        initialForces = options.initialExternalMarketForces,
    )
    private val priceEngine = PriceEngine(DeterministicRandom.mixSeed(options.seed, PRICE_STREAM_ID))
    private val executableBenchmarkDefinitions = instrumentCatalog.benchmarksInEvaluationOrder
        .filter { it.engineKind == BenchmarkEngineKind.EQUITY_METHODOLOGY }
    private val executablePortfolioIdByBenchmarkRef = executableBenchmarkDefinitions.associate { definition ->
        definition.ref to ReferencePortfolioEngine.portfolioIdFor(definition.ref)
    }
    private val equityReferenceBenchmarkDefinitions = instrumentCatalog.benchmarksInEvaluationOrder
        .filter { it.engineKind == BenchmarkEngineKind.EQUITY_REFERENCE }
    private val fixedIncomeBenchmarkDefinitions = instrumentCatalog.benchmarksInEvaluationOrder
        .filter { it.engineKind == BenchmarkEngineKind.FIXED_INCOME_CURVE }
    private val commoditySpotBenchmarkDefinitions = instrumentCatalog.benchmarksInEvaluationOrder
        .filter { it.engineKind == BenchmarkEngineKind.COMMODITY_SPOT }
    private val futuresBenchmarkDefinitions = instrumentCatalog.benchmarksInEvaluationOrder
        .filter { it.engineKind == BenchmarkEngineKind.FUTURES_CURVE }
    private val fundOfFundsBenchmarkDefinitions = instrumentCatalog.benchmarksInEvaluationOrder
        .filter { it.engineKind == BenchmarkEngineKind.FUND_OF_FUNDS_METHODOLOGY }
    private val fundOfFundsProfiles = fundOfFundsBenchmarkDefinitions.associate { definition ->
        definition.ref to requireNotNull(definition.fundOfFundsMethodologyProfile)
    }
    private val alternativeRiskPremiaBenchmarkDefinitions = instrumentCatalog.benchmarksInEvaluationOrder
        .filter { it.engineKind == BenchmarkEngineKind.ALTERNATIVE_RISK_PREMIA }
    private val compositeReferenceBenchmarkDefinitions = instrumentCatalog.benchmarksInEvaluationOrder
        .filter { it.engineKind == BenchmarkEngineKind.COMPOSITE_REFERENCE }
    private val referenceSourceCatalog = ReferenceSourceCatalog(
        benchmarkDefinitions = instrumentCatalog.benchmarks.associateBy { definition -> definition.ref },
        operatingCompanyCurrencies = stocks
            .filter { stock -> stock.behavior.strategy == InstrumentStrategy.OPERATING_COMPANY }
            .associate { stock -> stock.id to ReferenceCurrency.valueOf(stock.currency.name) },
    )
    /** Prices captured before a turn; same-turn quote mutation must never erase a source return. */
    private val directlyReferencedInstrumentIds: Set<String> = buildSet {
        stocks.forEach { stock ->
            val product = stock.fundProductProfile
            listOfNotNull(
                product?.dailyResetTerms?.reference?.instrumentId,
                product?.optionStrategyTerms?.reference?.instrumentId,
                product?.cashCollateralizedPutSpreadTerms?.optionReference?.instrumentId,
            ).forEach(::add)
        }
        alternativeRiskPremiaBenchmarkDefinitions.forEach { definition ->
            addAll(requireNotNull(definition.alternativeRiskPremiaProfile).componentInstrumentIds)
        }
        compositeReferenceBenchmarkDefinitions.forEach { definition ->
            addAll(requireNotNull(definition.compositeReferenceProfile).componentInstrumentIds)
        }
    }
    private val structuredSourceBenchmarkRefs: Set<BenchmarkRef> = buildSet {
        alternativeRiskPremiaBenchmarkDefinitions.forEach { definition ->
            addAll(requireNotNull(definition.alternativeRiskPremiaProfile).componentBenchmarkRefs)
        }
        compositeReferenceBenchmarkDefinitions.forEach { definition ->
            addAll(requireNotNull(definition.compositeReferenceProfile).componentBenchmarkRefs)
        }
    }
    private val referencePortfolioEngine = ReferencePortfolioEngine.forCampaignSeed(options.seed)
    private val equityReferenceBookEngine = EquityReferenceBookEngine.forCampaignSeed(options.seed)
    private val fundProductOverlayEngine = FundProductOverlayEngine.forCampaignSeed(options.seed)
    private val dailyResetEngine = DailyResetEngine()
    private val optionStrategyEngine = OptionStrategyEngine()
    private val cashCollateralizedPutSpreadEngine = CashCollateralizedPutSpreadEngine()
    private val fixedIncomeReferenceBookEngine = FixedIncomeReferenceBookEngine()
    private val commodityMarketModel = CommodityMarketModel.forCampaignSeed(options.seed)
    private val commodityReferenceBookEngine = CommodityReferenceBookEngine()
    private val fundOfFundsBookEngine = FundOfFundsBookEngine.forCampaignSeed(options.seed)
    private val alternativeRiskPremiaBookEngine =
        AlternativeRiskPremiaBookEngine.forCampaignSeed(options.seed)
    private val compositeReferenceBookEngine = CompositeReferenceBookEngine.forCampaignSeed(options.seed)
    private val orderBookEngine = OrderBookEngine(DeterministicRandom.mixSeed(options.seed, BOOK_STREAM_ID))
    private val marketIndexEngine = MarketIndexEngine()
    private val listingLifecycleEngine = ListingLifecycleEngine()
    private val eventEngine = EventEngine(DeterministicRandom.mixSeed(options.seed, EVENT_STREAM_ID))
    private val scheduledEventEngine = ScheduledEventEngine(
        DeterministicRandom.mixSeed(options.seed, ScheduledEventEngine.STREAM_ID),
    )
    private val instrumentMetricsEngine = InstrumentMetricsEngine(
        DeterministicRandom.mixSeed(options.seed, InstrumentMetricsEngine.STREAM_ID),
    )
    private val domesticSaleTaxCalculator = DomesticSaleTaxCalculator()
    private val domesticEtfSaleTaxCalculator = DomesticEtfSaleTaxCalculator()
    private val majorShareholderCalculator = MajorShareholderCalculator()
    private val annualStockTaxCalculator = AnnualStockTaxCalculator()
    private val dividendTaxCalculator = DividendTaxCalculator()
    private val brokerFeeCalculator = BrokerFeeCalculator(
        BrokerFeeSchedule(
            id = "simulator-general-account-2026",
            brokerName = "일반계좌",
            domesticCommissionRate = TaxRate(150L), // 0.015%
            usCommissionRate = TaxRate(700L), // 0.070%
            fxSpreadRate = TaxRate(1_000L), // 0.10%
        ),
    )

    init {
        require(stocks.size >= 24) { "기본 종목 카탈로그가 충분하지 않습니다." }
        val initialDynamics = marketDynamicsEngine.snapshot()
        macro = macro.copy(
            volatilityRegime = initialDynamics.resolvedVolatilityRegime,
            retailOrderFlow = initialDynamics.retailFlow,
            institutionalOrderFlow = initialDynamics.institutionalFlow,
            liquidityStress = initialDynamics.liquidityStress,
            newsIntensity = initialDynamics.newsIntensity,
        )
        selectedStockId = stocks.firstOrNull()?.id
        stocks.associateTo(listingLifecycleStates) { stock -> stock.id to listingLifecycleEngine.initialState(stock) }
        initializeMarketData()
        initializeInstrumentFinancialStates()
        initializeMarketIndices(currentTime)
        initializeTradingProtections(currentTime)
        recalculateAnnualTax(gameDate(currentTime).year)
        recordDailySnapshot(gameDate(currentTime), currentTime)
    }

    fun selectScreen(value: Screen) {
        screen = value
        lastMessage = null
    }

    fun selectStock(stockId: String): Boolean {
        if (stockId !in stockById) return fail("존재하지 않는 종목입니다.")
        selectedStockId = stockId
        lastMessage = null
        return true
    }

    fun selectTurnStep(value: TurnStep) {
        selectedTurnStep = value
        lastMessage = null
    }

    fun setAutoExchange(enabled: Boolean) {
        options = options.copy(autoExchange = enabled)
        lastMessage = if (enabled) "자동 환전을 켰습니다." else "자동 환전을 껐습니다."
    }

    fun setExternalMarketForces(target: ExternalMarketForces) {
        if (options.ironmanMode) {
            lastMessage = "철인 모드에서는 시장 동역학을 변경할 수 없습니다."
            return
        }
        externalMarketForcesTarget = target.copy()
        lastMessage = "시장 환경 목표를 변경했습니다. 실제 시장에는 시간에 따라 반영됩니다."
    }

    internal fun debugSetInstrumentPrice(
        stockId: String,
        amount: Double,
        inputCurrency: DebugPriceCurrency,
    ): DebugRuntimeResult {
        if (phase !in DEBUG_MUTABLE_PHASES) return debugFailure("진행 중이거나 일시 정지된 게임에서만 가격을 바꿀 수 있습니다.")
        val stock = stockById[stockId] ?: return debugFailure("종목 '$stockId'을(를) 찾을 수 없습니다.")
        val lifecycle = listingLifecycleStates.getValue(stock.id)
        if (lifecycle.isTerminal || lifecycle.isSettlementPending) {
            return debugFailure("상장 종료 또는 청산 중인 종목의 가격은 바꿀 수 없습니다.")
        }
        if (!amount.isFinite() || amount <= 0.0 || amount > MAX_DEBUG_PRICE_INPUT) {
            return debugFailure("가격은 0보다 크고 $MAX_DEBUG_PRICE_INPUT 이하여야 합니다.")
        }
        val nativeAmount = when (inputCurrency) {
            DebugPriceCurrency.NATIVE -> amount
            DebugPriceCurrency.KRW -> if (stock.currency == Currency.KRW) amount else amount / macro.usdKrw
            DebugPriceCurrency.USD -> if (stock.currency == Currency.USD) amount else amount * macro.usdKrw
        }
        if (!nativeAmount.isFinite() || nativeAmount <= 0.0 || nativeAmount > MAX_DEBUG_NATIVE_PRICE) {
            return debugFailure("환산된 종목 가격이 허용 범위를 벗어났습니다.")
        }
        val price = MarketMicrostructure.roundNearest(stock, nativeAmount)
        val heldQuantity = holdings[stock.id]?.quantity ?: 0.0
        val openBuyQuantity = orders.asSequence()
            .filter { order -> order.stockId == stock.id && order.isOpen && order.side == OrderSide.BUY }
            .sumOf { order -> order.remainingQuantity }
        val exposedQuantity = heldQuantity + openBuyQuantity
        val exposureKrw = price * exposedQuantity * if (stock.currency == Currency.USD) macro.usdKrw else 1.0
        if (!exposureKrw.isFinite() || exposureKrw > MAX_DEBUG_LEDGER_GROSS_KRW) {
            return debugFailure(
                "변경 가격과 현재 보유·주문 수량의 원화 평가액이 안전한 세무 원장 한도 " +
                    "$MAX_DEBUG_LEDGER_GROSS_KRW 을(를) 넘습니다.",
            )
        }
        val quote = quotes.getValue(stock.id)
        quotes[stock.id] = quote.copy(
            timestamp = currentTime,
            price = price,
            high = maxOf(quote.high, price),
            low = minOf(quote.low, price),
            bidPrice = null,
            askPrice = null,
            bidQuantity = 0.0,
            askQuantity = 0.0,
        )
        replaceLatestBarPrice(history.getValue(stock.id), price)
        chartPriceHistory.getValue(stock.id).values.forEach { bars -> replaceLatestBarPrice(bars, price) }
        dailyTrackers[stock.id]?.let { tracker ->
            tracker.high = maxOf(tracker.high, price)
            tracker.low = minOf(tracker.low, price)
        }
        updateHoldingPrices()
        refreshDebugPortfolioState()
        lastMessage = "${stock.symbol} 가격을 ${stock.currency.symbol}$price(으)로 조정했습니다."
        return DebugRuntimeResult.success(lastMessage.orEmpty(), price.toString())
    }

    internal fun debugChangeInstrumentPrice(stockId: String, percent: Double): DebugRuntimeResult {
        if (!percent.isFinite() || percent <= -100.0 || percent > MAX_DEBUG_PRICE_CHANGE_PERCENT) {
            return debugFailure("가격 변화율은 -100% 초과, $MAX_DEBUG_PRICE_CHANGE_PERCENT% 이하여야 합니다.")
        }
        val current = quotes[stockId]?.price ?: return debugFailure("종목 '$stockId'을(를) 찾을 수 없습니다.")
        return debugSetInstrumentPrice(
            stockId = stockId,
            amount = current * (1.0 + percent / 100.0),
            inputCurrency = DebugPriceCurrency.NATIVE,
        )
    }

    internal fun debugSetCash(currency: Currency, amount: Double): DebugRuntimeResult {
        if (phase !in DEBUG_MUTABLE_PHASES) return debugFailure("현재 게임 단계에서는 현금을 바꿀 수 없습니다.")
        val maximum = when (currency) {
            Currency.KRW -> MAX_DEBUG_CASH_KRW
            Currency.USD -> MAX_DEBUG_CASH_USD
        }
        if (!amount.isFinite() || amount < 0.0 || amount > maximum) {
            return debugFailure("${currency.name} 현금은 0 이상 $maximum 이하여야 합니다.")
        }
        val rounded = roundCurrency(amount, currency)
        cash[currency] = rounded
        refreshDebugPortfolioState()
        lastMessage = "${currency.displayName} 현금을 ${currency.symbol}$rounded(으)로 설정했습니다."
        return DebugRuntimeResult.success(lastMessage.orEmpty(), rounded.toString())
    }

    internal fun debugAddCash(currency: Currency, delta: Double): DebugRuntimeResult {
        if (!delta.isFinite()) return debugFailure("현금 변화량은 유한한 수여야 합니다.")
        val next = cash.getValue(currency) + delta
        return debugSetCash(currency, next)
    }

    internal fun debugSetUsdKrw(rate: Double): DebugRuntimeResult {
        if (phase !in DEBUG_MUTABLE_PHASES) return debugFailure("현재 게임 단계에서는 환율을 바꿀 수 없습니다.")
        if (!rate.isFinite() || rate !in MIN_USD_KRW..MAX_USD_KRW) {
            return debugFailure("USD/KRW는 $MIN_USD_KRW..$MAX_USD_KRW 범위여야 합니다.")
        }
        val ratio = rate / macro.usdKrw
        fun rebased(source: Map<ReferenceCurrency, Double>?): Map<ReferenceCurrency, Double> {
            val base = source ?: initialFxRates(macro.usdKrw)
            return base.mapValues { (currency, value) ->
                when (currency) {
                    ReferenceCurrency.KRW -> 1.0
                    ReferenceCurrency.USD -> rate
                    else -> value * ratio
                }
            }
        }
        val rates = rebased(macro.fxRatesToKrw)
        macro = macro.copy(
            usdKrw = rate,
            previousUsdKrw = rate,
            fxRatesToKrw = rates,
            previousFxRatesToKrw = rates.toMap(),
        )
        options = options.copy(initialUsdKrw = rate)
        refreshDebugPortfolioState()
        lastMessage = "USD/KRW를 $rate(으)로 설정하고 FX 기준선을 재설정했습니다."
        return DebugRuntimeResult.success(lastMessage.orEmpty(), rate.toString())
    }

    internal fun debugSetAutoExchange(enabled: Boolean): DebugRuntimeResult {
        options = options.copy(autoExchange = enabled)
        lastMessage = "자동 환전을 ${if (enabled) "켰습니다" else "껐습니다"}."
        return DebugRuntimeResult.success(lastMessage.orEmpty(), enabled.toString())
    }

    internal fun debugSetIronman(enabled: Boolean): DebugRuntimeResult {
        options = options.copy(ironmanMode = enabled)
        lastMessage = "철인 모드를 ${if (enabled) "켰습니다" else "껐습니다"}."
        return DebugRuntimeResult.success(lastMessage.orEmpty(), enabled.toString())
    }

    internal fun debugSetFractionalTrading(enabled: Boolean): DebugRuntimeResult {
        if (options.usFractionalTrading == enabled) {
            return DebugRuntimeResult.success("미국 종목 소수점 거래가 이미 ${if (enabled) "켜져" else "꺼져"} 있습니다.")
        }
        options = options.copy(usFractionalTrading = enabled)
        baseStockDefinitions = if (enabled) {
            instrumentCatalog.withUsFractionalTrading()
        } else {
            instrumentCatalog.definitions
        }
        baseStockById = baseStockDefinitions.associateBy(StockDefinition::id)
        rebuildDynamicStockDefinitions(corporateActionLedger)
        var cancelled = 0
        if (!enabled) {
            for (index in orders.indices) {
                val order = orders[index]
                val stock = stockById[order.stockId] ?: continue
                if (order.isOpen && !stock.acceptsQuantity(order.remainingQuantity)) {
                    orders[index] = order.copy(
                        status = OrderStatus.CANCELLED,
                        updatedAt = currentTime,
                        rejectionReason = "디버그 규칙 변경으로 주문 수량 단위가 달라졌습니다.",
                    )
                    cancelled++
                }
            }
        }
        lastMessage = "미국 종목 소수점 거래를 ${if (enabled) "켰습니다" else "껐습니다"}. 취소 주문 ${cancelled}건."
        return DebugRuntimeResult.success(lastMessage.orEmpty(), enabled.toString())
    }

    internal fun debugSetExternalMarketForces(target: ExternalMarketForces): DebugRuntimeResult {
        externalMarketForcesTarget = target.copy()
        options = options.copy(initialExternalMarketForces = target.copy())
        lastMessage = "외부 시장 환경 목표를 즉시 변경했습니다. 실제 동역학은 다음 시간부터 수렴합니다."
        return DebugRuntimeResult.success(lastMessage.orEmpty())
    }

    internal fun debugEventGuide(query: String?): List<DebugEventGuide> {
        val guides = eventEngine.debugGuideEntries(eventEligibleStocks())
        val normalizedQuery = query?.trim()?.takeIf(String::isNotEmpty) ?: return guides
        return guides.filter { guide ->
            guide.templateId.contains(normalizedQuery, ignoreCase = true) ||
                guide.title.contains(normalizedQuery, ignoreCase = true) ||
                guide.scope.name.contains(normalizedQuery, ignoreCase = true) ||
                guide.condition.name.contains(normalizedQuery, ignoreCase = true) ||
                guide.argumentName?.contains(normalizedQuery, ignoreCase = true) == true ||
                guide.eligibleTargets.any { target -> target.contains(normalizedQuery, ignoreCase = true) }
        }
    }

    internal fun debugTriggerEvent(templateId: String, target: String?): DebugRuntimeResult {
        if (phase !in DEBUG_MUTABLE_PHASES) {
            return debugFailure("진행 중이거나 일시 정지된 게임에서만 이벤트를 발동할 수 있습니다.")
        }
        val trigger = eventEngine.debugForceTrigger(
            templateId = templateId.trim(),
            targetArgument = target,
            timestamp = currentTime,
            stocks = eventEligibleStocks(),
            macro = macro,
            existingEvents = activeEvents,
            suppressedTemplateIds = stochasticNarrativeSuppressions(currentTime),
        )
        val rejectionMessage = trigger.rejectionMessage
        if (rejectionMessage != null) return debugFailure(rejectionMessage)
        val result = requireNotNull(trigger.generation)
        applyGeneratedEventResult(result)
        marketDynamicsEngine.recordEvents(result.newEvents)
        val event = result.newEvents.single()
        lastMessage = "이벤트 '${event.title}'을(를) 발동했습니다."
        return DebugRuntimeResult.success(lastMessage.orEmpty(), event.id)
    }

    internal fun debugCancelAllOrders(): DebugRuntimeResult {
        var cancelled = 0
        for (index in orders.indices) {
            val order = orders[index]
            if (!order.isOpen) continue
            orders[index] = order.copy(
                status = OrderStatus.CANCELLED,
                updatedAt = currentTime,
                rejectionReason = "디버그 콘솔에서 일괄 취소했습니다.",
            )
            cancelled++
        }
        lastMessage = "미체결 주문 ${cancelled}건을 취소했습니다."
        return DebugRuntimeResult.success(lastMessage.orEmpty(), cancelled.toString())
    }

    private fun replaceLatestBarPrice(bars: ArrayDeque<PriceBar>, price: Double) {
        val previous = bars.removeLastOrNull() ?: return
        bars.addLast(
            previous.copy(
                close = price,
                high = maxOf(previous.high, price),
                low = minOf(previous.low, price),
            ),
        )
    }

    private fun refreshDebugPortfolioState() {
        updateDrawdown()
        recordDailySnapshot(gameDate(currentTime), currentTime)
    }

    private fun debugFailure(message: String): DebugRuntimeResult {
        lastMessage = message
        return DebugRuntimeResult.failure(message)
    }

    fun clearMessage() {
        lastMessage = null
    }

    fun markEventRead(eventId: String) {
        if (newsEvents.any { it.id == eventId }) readEventIds += eventId
    }

    fun markStockNewsListViewed(stockId: String, eventIds: Set<String>) {
        val stock = stockById[stockId] ?: return
        if (eventIds.isEmpty()) return
        val currentEventIds = newsEvents.asSequence()
            .filter { event -> event.affects(stock) }
            .mapTo(linkedSetOf()) { event -> event.id }
        readStockNewsEventIds.getOrPut(stockId, ::linkedSetOf) += eventIds.intersect(currentEventIds)
    }

    fun markAllEventsRead() {
        newsEvents.forEach { event -> readEventIds += event.id }
    }

    fun toggleWatchlist(stockId: String): Boolean {
        if (stockId !in stockById) return fail("존재하지 않는 종목입니다.")
        val added = if (stockId in watchlistedStockIds) {
            watchlistedStockIds.remove(stockId)
            false
        } else {
            watchlistedStockIds.add(stockId)
            true
        }
        lastMessage = if (added) "관심 종목에 추가했습니다." else "관심 종목에서 해제했습니다."
        return added
    }

    fun pause() {
        if (phase == GamePhase.PLAYING) phase = GamePhase.PAUSED
    }

    fun resume() {
        if (phase == GamePhase.PAUSED) phase = GamePhase.PLAYING
    }

    fun finishSettlement() {
        if (phase == GamePhase.SETTLEMENT) {
            phase = GamePhase.FINISHED
            lastMessage = "최종 정산을 완료했습니다."
        }
    }

    fun advance(step: TurnStep) {
        if (phase != GamePhase.PLAYING) {
            fail(if (phase == GamePhase.SETTLEMENT || phase == GamePhase.FINISHED) "종료된 게임은 진행할 수 없습니다." else "게임이 일시 정지되어 있습니다.")
            return
        }
        isAdvancing = true
        lastMessage = null
        var advanced = 0
        repeat(step.hours) {
            if (GameCalendar.isFinished(currentTime)) return@repeat
            advanceOneHour()
            advanced += 1
        }
        if (GameCalendar.isFinished(currentTime)) enterSettlement()
        isAdvancing = false
        if (phase == GamePhase.PLAYING) lastMessage = "${advanced}시간 진행했습니다."
    }

    /** Canonical hourly advance with a cancellation boundary between every simulated hour. */
    internal suspend fun debugAdvance(step: TurnStep) {
        if (phase != GamePhase.PLAYING) {
            fail(if (phase == GamePhase.PAUSED) "게임이 일시 정지되어 있습니다." else "종료된 게임은 진행할 수 없습니다.")
            return
        }
        isAdvancing = true
        lastMessage = null
        var advanced = 0
        try {
            repeat(step.hours) {
                currentCoroutineContext().ensureActive()
                if (GameCalendar.isFinished(currentTime)) return@repeat
                advanceOneHour()
                advanced++
            }
            currentCoroutineContext().ensureActive()
        } finally {
            if (GameCalendar.isFinished(currentTime) && phase == GamePhase.PLAYING) enterSettlement()
            isAdvancing = false
        }
        if (phase == GamePhase.PLAYING) lastMessage = "${advanced}시간 진행했습니다."
    }

    fun placeOrder(request: OrderRequest): Boolean {
        if (phase != GamePhase.PLAYING) return fail("진행 중인 게임에서만 주문할 수 있습니다.")
        val stock = stockById[request.stockId] ?: return fail("존재하지 않는 종목입니다.")
        val listingState = listingLifecycleStates.getValue(stock.id)
        if (!listingState.isOrderAllowed) {
            return fail(
                when (listingState.status) {
                    ListingLifecycleStatus.TRADING_SUSPENDED,
                    ListingLifecycleStatus.UNDER_REVIEW,
                    -> "상장 유지 심사로 거래가 멈춘 종목입니다."
                    ListingLifecycleStatus.DELISTING_SCHEDULED -> "상장폐지 절차로 새 주문을 받지 않습니다."
                    ListingLifecycleStatus.LIQUIDATION_PENDING -> "청산금 지급 절차가 진행 중인 상품입니다."
                    ListingLifecycleStatus.DELISTED,
                    ListingLifecycleStatus.TERMINATED,
                    -> "거래가 종료된 종목입니다."
                    else -> "현재 상장 상태에서는 주문할 수 없습니다."
                },
            )
        }
        val protectionDecision = TradingProtectionEngine.permission(
            tradingProtectionSnapshot,
            TradingProtectionRequest(
                market = stock.market,
                action = TradingProtectionAction.SUBMIT_ORDER,
                stockId = stock.id,
                isAuctionEligibleOrder = request.type == OrderType.LIMIT,
            ),
            currentTime,
        )
        if (!protectionDecision.allowed) {
            return fail(protectionDecision.controllingRestriction?.message ?: "시장 보호장치로 주문할 수 없습니다.")
        }
        if (isInstrumentMatured(stock, currentTime)) {
            return fail("만기상환이 끝난 상품은 더 이상 주문할 수 없습니다.")
        }
        val isFullCorporateActionRemainder = request.side == OrderSide.SELL &&
            holdings[stock.id]?.let { abs(it.quantity - request.quantity) < QUANTITY_EPSILON } == true
        if (!request.quantity.isFinite() ||
            (!stock.acceptsQuantity(request.quantity) && !isFullCorporateActionRemainder)
        ) {
            return fail("주문 수량은 ${stock.quantityStep} 단위의 양수여야 합니다.")
        }
        if (
            request.type == OrderType.LIMIT &&
            (request.limitPrice == null || !request.limitPrice.isFinite() || request.limitPrice <= 0.0)
        ) {
            return fail("지정가 주문에는 유한한 양수 가격이 필요합니다.")
        }
        if (request.limitPrice?.isFinite() == false) {
            return fail("지정가는 유한한 양수여야 합니다.")
        }
        if (request.type != OrderType.LIMIT && request.limitPrice != null) {
            return fail("시장가 주문에는 지정가를 함께 보낼 수 없습니다.")
        }
        if (request.limitPrice != null) {
            val rounded = MarketMicrostructure.roundNearest(stock, request.limitPrice)
            if (abs(rounded - request.limitPrice) > PRICE_EPSILON) {
                return fail("지정가가 ${stock.market.displayName} 호가 단위에 맞지 않습니다.")
            }
        }
        if (!validateOrderResources(stock, request)) return false

        val order = Order(
            id = nextId("order"),
            stockId = stock.id,
            side = request.side,
            type = request.type,
            quantity = request.quantity,
            createdAt = currentTime,
            limitPrice = request.limitPrice,
            status = OrderStatus.ACCEPTED,
            timeInForce = request.timeInForce,
        )
        orders += order
        val index = orders.lastIndex
        tryImmediateExecution(index, stock)
        if (orders[index].status == OrderStatus.ACCEPTED) {
            when (request.timeInForce) {
                TimeInForce.IMMEDIATE_OR_CANCEL -> orders[index] = orders[index].copy(
                    status = OrderStatus.CANCELLED,
                    updatedAt = currentTime,
                    rejectionReason = "즉시 체결되지 않은 잔량을 취소했습니다.",
                )
                TimeInForce.FILL_OR_KILL -> orders[index] = orders[index].copy(
                    status = OrderStatus.REJECTED,
                    updatedAt = currentTime,
                    rejectionReason = "즉시 전량 체결 조건을 충족하지 못했습니다.",
                )
                TimeInForce.DAY,
                TimeInForce.GOOD_TILL_CANCELLED,
                -> Unit
            }
        }
        if (orders[index].status == OrderStatus.REJECTED) {
            return fail(orders[index].rejectionReason ?: "주문이 거부되었습니다.")
        }
        lastMessage = when (orders[index].status) {
            OrderStatus.FILLED -> "주문이 체결되었습니다."
            OrderStatus.CANCELLED -> "즉시 체결되지 않아 주문을 취소했습니다."
            else -> "주문이 접수되었습니다."
        }
        return true
    }

    fun cancelOrder(orderId: String): Boolean {
        val index = orders.indexOfFirst { it.id == orderId }
        if (index < 0) return fail("주문을 찾을 수 없습니다.")
        val order = orders[index]
        if (!order.canCancel) return fail("현재 상태에서는 주문을 취소할 수 없습니다.")
        val stock = stockById.getValue(order.stockId)
        val protectionDecision = TradingProtectionEngine.permission(
            tradingProtectionSnapshot,
            TradingProtectionRequest(
                market = stock.market,
                action = TradingProtectionAction.CANCEL_ORDER,
                stockId = stock.id,
            ),
            currentTime,
        )
        if (!protectionDecision.allowed) {
            return fail(protectionDecision.controllingRestriction?.message ?: "현재 주문을 취소할 수 없습니다.")
        }
        orders[index] = order.copy(status = OrderStatus.CANCELLED, updatedAt = currentTime)
        lastMessage = "주문을 취소했습니다."
        return true
    }

    fun exchange(from: Currency, to: Currency, amount: Double): Boolean {
        if (phase != GamePhase.PLAYING && phase != GamePhase.PAUSED) return fail("현재는 환전할 수 없습니다.")
        if (from == to) return fail("서로 다른 통화를 선택해 주세요.")
        if (!amount.isFinite() || amount <= 0.0) return fail("환전 금액은 0보다 커야 합니다.")
        val result = exchangeInternal(from, to, amount, automatic = false)
        if (result) lastMessage = "환전이 완료되었습니다."
        return result
    }

    fun snapshot(): SimulatorUiState {
        val sessions = Market.entries.associateWith(::marketSessionAtCurrentTime)
        // The save/UI state keeps the original published record. Price-only copies whose
        // startsAt is shifted to the market effect window remain internal to the pricing path.
        val scheduledActiveEvents = newsEvents.filter { event ->
            event.recordKind == EventRecordKind.SCHEDULED_RELEASE &&
                currentTime >= event.effectStartsAt && currentTime < event.effectEndsAt
        }
        val stateQuotes = quotes.mapValues { (stockId, quote) ->
            val stock = stockById.getValue(stockId)
            quote.copy(
                session = if (listingLifecycleStates.getValue(stockId).isTradable) {
                    sessions.getValue(stock.market)
                } else {
                    MarketSession.CLOSED
                },
            )
        }.toMutableMap()
        val selectedBook = selectedStockId?.takeIf { id ->
            val stock = stockById.getValue(id)
            !isInstrumentMatured(stock, currentTime) &&
                listingLifecycleStates.getValue(id).isTradable &&
                TradingProtectionEngine.permission(
                    tradingProtectionSnapshot,
                    TradingProtectionRequest(
                        market = stock.market,
                        action = TradingProtectionAction.CONTINUOUS_TRADING,
                        stockId = id,
                    ),
                    currentTime,
                ).allowed
        }?.let { id ->
            val stock = stockById.getValue(id)
            val quote = stateQuotes.getValue(id)
            orderBook(stock, quote, sessions.getValue(stock.market)).also { book ->
                stateQuotes[id] = book.applyTopOfBook(quote)
            }
        }
        return SimulatorUiState(
            options = options,
            catalogReference = instrumentCatalog.reference,
            phase = phase,
            screen = screen,
            currentTime = currentTime,
            turn = turn,
            selectedTurnStep = selectedTurnStep,
            stocks = stocks.toList(),
            corporateFundamentals = corporateFundamentals.toMap(),
            fundFinancialStates = fundFinancialStates.toMap(),
            referencePortfolioStates = referencePortfolioStates.toMap(),
            referencePortfolioLedger = referencePortfolioLedger.toList(),
            equityReferenceStates = equityReferenceStates.toMap(),
            equityReferenceLedger = equityReferenceLedger.toList(),
            fundOfFundsStates = fundOfFundsStates.toMap(),
            fundOfFundsRebalanceLedger = fundOfFundsRebalanceLedger.toList(),
            alternativeRiskPremiaStates = alternativeRiskPremiaStates.toMap(),
            alternativeRiskPremiaRebalanceLedger = alternativeRiskPremiaRebalanceLedger.toList(),
            compositeReferenceStates = compositeReferenceStates.toMap(),
            compositeReferenceRebalanceLedger = compositeReferenceRebalanceLedger.toList(),
            dailyResetStates = dailyResetStates.toMap(),
            optionStrategyStates = optionStrategyStates.toMap(),
            cashCollateralizedPutSpreadStates = cashCollateralizedPutSpreadStates.toMap(),
            etnStates = etnStates.toMap(),
            etnLedger = etnLedger.toList(),
            closedEndFundStates = closedEndFundStates.toMap(),
            closedEndFundLedger = closedEndFundLedger.toList(),
            fixedIncomeReferenceStates = fixedIncomeReferenceStates.toMap(),
            fixedIncomeRollLedger = fixedIncomeRollLedger.toList(),
            commoditySpotReferenceStates = commoditySpotReferenceStates.toMap(),
            futuresReferenceStates = futuresReferenceStates.toMap(),
            futuresRollLedger = futuresRollLedger.toList(),
            futuresAllocationLedger = futuresAllocationLedger.toList(),
            pendingFundFlowRates = pendingFundFlowRates.toMap(),
            selectedStockId = selectedStockId,
            quotes = stateQuotes.toMap(),
            priceHistory = history.mapValues { (_, bars) -> bars.toList() },
            chartPriceHistory = chartPriceHistory.mapValues { (_, histories) ->
                histories.mapValues { (_, bars) -> bars.toList() }
            },
            cashByCurrency = cash.toMap(),
            holdings = holdings.toMap(),
            orders = orders.toList(),
            trades = trades.toList(),
            selectedOrderBook = selectedBook,
            marketSessions = sessions,
            macro = macro,
            externalMarketForcesTarget = externalMarketForcesTarget,
            marketDynamicsSnapshot = marketDynamicsEngine.snapshot(),
            activeEvents = (activeEvents.filter { it.isActiveAt(currentTime) } + scheduledActiveEvents)
                .distinctBy(GameEvent::id),
            newsEvents = newsEvents.sortedByDescending(GameEvent::startsAt),
            readEventIds = readEventIds.toSet(),
            readStockNewsEventIds = readStockNewsEventIds.mapValues { (_, eventIds) -> eventIds.toSet() },
            portfolioSnapshots = portfolioSnapshots.toList(),
            dailyStatistics = dailyStatistics.toList(),
            currentBenchmarkValue = benchmarkValue,
            benchmarkHistory = benchmarkHistory.toList(),
            transactionCosts = transactionCosts.toList(),
            realizedGains = realizedGains.toList(),
            fifoCostBasisBook = fifoCostBasisBook,
            lastEvaluatedDistributionDateByStock = lastEvaluatedDistributionDateByStock.toMap(),
            dividendLedger = dividends.toList(),
            foreignExchangeLedger = foreignExchanges.toList(),
            annualTaxLedgers = annualTaxLedgers.toMap(),
            taxPaymentNotices = taxPaymentNotices.toList(),
            peakAssetsKrw = peakAssetsKrw,
            maximumDrawdown = maximumDrawdown,
            rngState = random.snapshot(),
            eventEngineSnapshot = eventEngine.snapshot(),
            nextSequence = nextSequence,
            isAdvancing = isAdvancing,
            lastMessage = lastMessage,
            pendingEtfReferenceReturns = pendingEtfReferenceReturns.toMap(),
            pendingClosedEventLogReturns = pendingClosedEventLogReturns.toMap(),
            marketIndices = marketIndices.toMap(),
            marketIndexHistory = marketIndexHistory.mapValues { (_, values) -> values.toList() },
            taxExchangeRatesByTradeId = taxExchangeRatesByTradeId.toMap(),
            pendingTaxSettlementTradeIds = pendingTaxSettlementTradeIds.toSet(),
            watchlistedStockIds = watchlistedStockIds.toSet(),
            pendingCorporateActions = pendingCorporateActions.toList(),
            corporateActionLedger = corporateActionLedger.toList(),
            listingLifecycleStates = listingLifecycleStates.toMap(),
            listingLifecycleLedger = listingLifecycleLedger.toList(),
            tradingProtectionSnapshot = tradingProtectionSnapshot,
            dailyTradingSurveillance = dailyTradingSurveillance.mapValues { (_, values) -> values.toList() },
        )
    }

    private fun restoreFrom(state: SimulatorUiState) {
        require(GameCalendar.isWithinGameRange(state.currentTime)) { "저장 시각이 게임 범위를 벗어났습니다." }
        require(state.turn == GameCalendar.turnAt(state.currentTime)) { "저장 턴과 시각이 일치하지 않습니다." }
        require(state.catalogReference == instrumentCatalog.reference) {
            "저장 종목팩과 현재 설치된 종목팩이 일치하지 않습니다."
        }
        val ids = stockById.keys
        val savedIds = state.stocks.map(StockDefinition::id)
        require(savedIds.distinct().size == savedIds.size && savedIds.toSet() == ids) {
            "저장 종목 카탈로그가 현재 버전과 일치하지 않습니다."
        }
        val expectedCorporateIds = stocks.filter(StockDefinition::hasCorporateEarnings)
            .mapTo(linkedSetOf(), StockDefinition::id)
        val expectedFundIds = stocks.filter {
            it.fundProductProfile?.legalStructure == FundLegalStructure.OPEN_END_ETF
        }
            .mapTo(linkedSetOf(), StockDefinition::id)
        val expectedReferencePortfolioIds = executableBenchmarkDefinitions
            .mapTo(linkedSetOf()) { definition ->
                ReferencePortfolioEngine.portfolioIdFor(definition.ref)
            }
        val expectedEquityReferenceRefs = equityReferenceBenchmarkDefinitions
            .mapTo(linkedSetOf()) { definition -> definition.ref }
        val expectedFundOfFundsRefs = fundOfFundsBenchmarkDefinitions
            .mapTo(linkedSetOf()) { definition -> definition.ref }
        val expectedAlternativeRiskPremiaRefs = alternativeRiskPremiaBenchmarkDefinitions
            .mapTo(linkedSetOf()) { definition -> definition.ref }
        val expectedCompositeReferenceRefs = compositeReferenceBenchmarkDefinitions
            .mapTo(linkedSetOf()) { definition -> definition.ref }
        val expectedDailyResetProductIds = stocks
            .filter { it.fundProductProfile?.dailyResetTerms != null }
            .mapTo(linkedSetOf(), StockDefinition::id)
        val expectedOptionStrategyProductIds = stocks
            .filter { it.fundProductProfile?.optionStrategyTerms != null }
            .mapTo(linkedSetOf(), StockDefinition::id)
        val expectedCashCollateralizedPutSpreadProductIds = stocks
            .filter { it.fundProductProfile?.cashCollateralizedPutSpreadTerms != null }
            .mapTo(linkedSetOf(), StockDefinition::id)
        val expectedEtnIds = stocks.filter {
            it.fundProductProfile?.legalStructure == FundLegalStructure.EXCHANGE_TRADED_NOTE
        }.mapTo(linkedSetOf(), StockDefinition::id)
        val expectedClosedEndFundIds = stocks.filter {
            it.fundProductProfile?.legalStructure == FundLegalStructure.CLOSED_END_FUND
        }.mapTo(linkedSetOf(), StockDefinition::id)
        val expectedFixedIncomeReferenceIds = fixedIncomeBenchmarkDefinitions
            .mapTo(linkedSetOf()) { definition ->
                FixedIncomeReferenceState.referenceIdFor(definition.ref)
            }
        val expectedFixedIncomeRefs = fixedIncomeBenchmarkDefinitions
            .mapTo(linkedSetOf()) { it.ref }
        val expectedCommoditySpotRefs = commoditySpotBenchmarkDefinitions
            .mapTo(linkedSetOf()) { it.ref }
        val expectedFuturesRefs = futuresBenchmarkDefinitions
            .mapTo(linkedSetOf()) { it.ref }
        require(state.corporateFundamentals.keys == expectedCorporateIds) {
            "저장된 기업 재무 상태가 현재 기업 종목과 일치하지 않습니다."
        }
        require(state.fundFinancialStates.keys == expectedFundIds) {
            "저장된 상품 재무 상태가 현재 ETF·ETN·펀드 종목과 일치하지 않습니다."
        }
        require(state.referencePortfolioStates.keys == expectedReferencePortfolioIds &&
            state.referencePortfolioLedger.all { it.portfolioId in expectedReferencePortfolioIds }
        ) {
            "저장된 기준 포트폴리오 상태·재조정 원장이 현재 벤치마크와 일치하지 않습니다."
        }
        require(
            state.equityReferenceStates.keys == expectedEquityReferenceRefs &&
                state.equityReferenceStates.all { (ref, value) -> value.benchmarkRef == ref } &&
                state.equityReferenceLedger.all { record ->
                    record.benchmarkRef in expectedEquityReferenceRefs
                },
        ) { "저장된 일반 주식 기준 바스켓·재조정 원장이 현재 벤치마크와 일치하지 않습니다." }
        require(
            state.fundOfFundsStates.keys == expectedFundOfFundsRefs &&
                state.fundOfFundsStates.all { (ref, value) -> value.benchmarkRef == ref } &&
                state.fundOfFundsRebalanceLedger.all { record ->
                    record.benchmarkRef in expectedFundOfFundsRefs
                },
        ) { "저장된 펀드오브펀드 바스켓·재조정 원장이 현재 벤치마크와 일치하지 않습니다." }
        require(
            state.alternativeRiskPremiaStates.keys == expectedAlternativeRiskPremiaRefs &&
                state.alternativeRiskPremiaStates.all { (ref, value) -> value.benchmarkRef == ref } &&
                state.alternativeRiskPremiaRebalanceLedger.all { record ->
                    record.benchmarkRef in expectedAlternativeRiskPremiaRefs
                },
        ) { "저장된 대체위험 프리미엄 상태·재조정 원장이 현재 벤치마크와 일치하지 않습니다." }
        require(
            state.compositeReferenceStates.keys == expectedCompositeReferenceRefs &&
                state.compositeReferenceStates.all { (ref, value) -> value.benchmarkRef == ref } &&
                state.compositeReferenceRebalanceLedger.all { record ->
                    record.benchmarkRef in expectedCompositeReferenceRefs
                },
        ) { "저장된 복합 기준 상태·재조정 원장이 현재 벤치마크와 일치하지 않습니다." }
        require(state.dailyResetStates.keys == expectedDailyResetProductIds) {
            "저장된 일일 reset 상태가 현재 레버리지·인버스 상품과 일치하지 않습니다."
        }
        require(state.optionStrategyStates.keys == expectedOptionStrategyProductIds) {
            "저장된 옵션 운용 상태가 현재 옵션 상품과 일치하지 않습니다."
        }
        require(
            state.cashCollateralizedPutSpreadStates.keys ==
                expectedCashCollateralizedPutSpreadProductIds,
        ) { "저장된 현금담보 풋스프레드 상태가 현재 옵션 상품과 일치하지 않습니다." }
        require(
            state.etnStates.keys == expectedEtnIds &&
                state.etnLedger.all { it.productId in expectedEtnIds },
        ) { "저장된 ETN 계약 상태·원장이 현재 ETN 상품과 일치하지 않습니다." }
        require(
            state.closedEndFundStates.keys == expectedClosedEndFundIds &&
                state.closedEndFundLedger.all { it.fundId in expectedClosedEndFundIds },
        ) { "저장된 CEF 재무 상태·원장이 현재 폐쇄형 펀드와 일치하지 않습니다." }
        require(
            state.fixedIncomeReferenceStates.keys == expectedFixedIncomeReferenceIds &&
                state.fixedIncomeRollLedger.all { it.benchmarkRef in expectedFixedIncomeRefs },
        ) { "저장된 고정수익 benchmark 상태·만기 교체 원장이 현재 카탈로그와 일치하지 않습니다." }
        require(
            state.commoditySpotReferenceStates.keys == expectedCommoditySpotRefs &&
                state.commoditySpotReferenceStates.all { (ref, value) ->
                    value.benchmarkRef == ref
                },
        ) { "저장된 원자재 현물 benchmark 상태가 현재 카탈로그와 일치하지 않습니다." }
        require(
            state.futuresReferenceStates.keys == expectedFuturesRefs &&
                state.futuresReferenceStates.all { (ref, value) -> value.benchmarkRef == ref } &&
                state.futuresRollLedger.all { it.benchmarkRef in expectedFuturesRefs } &&
                state.futuresAllocationLedger.all { it.benchmarkRef in expectedFuturesRefs },
        ) { "저장된 선물 benchmark 상태·원장이 현재 카탈로그와 일치하지 않습니다." }
        require(
                state.quotes.keys == savedIds.toSet() &&
                state.priceHistory.keys == savedIds.toSet() &&
                state.chartPriceHistory.keys == savedIds.toSet(),
        ) {
            "저장된 모든 상품의 시세와 차트 기록이 필요합니다."
        }
        require(state.cashByCurrency.keys.containsAll(Currency.entries)) { "통화별 현금 잔액이 누락되었습니다." }
        require(state.cashByCurrency.values.all { it >= 0.0 && it.isFinite() }) { "현금 잔액이 올바르지 않습니다." }
        require(state.holdings.keys.all(ids::contains)) { "알 수 없는 보유 종목이 있습니다." }
        require(state.orders.all { it.stockId in ids } && state.trades.all { it.stockId in ids }) {
            "주문·체결 원장에 알 수 없는 종목이 있습니다."
        }
        require(state.pendingEtfReferenceReturns.all { (stockId, value) ->
            stockById[stockId]?.isFundLike == true && value.isFinite()
        }) { "ETF 개장 갭 상태가 올바르지 않습니다." }
        require(state.pendingClosedEventLogReturns.all { (stockId, value) ->
            stockId in stockById && value.isFinite()
        }) { "폐장 중 이벤트 개장 갭 상태가 올바르지 않습니다." }
        require(state.watchlistedStockIds.all(ids::contains)) {
            "관심 종목에 현재 카탈로그가 알 수 없는 ID가 있습니다."
        }
        require(state.pendingCorporateActions.all { it.stockId in ids }) {
            "예정 기업행동에 알 수 없는 종목이 있습니다."
        }
        require(state.corporateActionLedger.all { it.stockId in ids }) {
            "기업행동 원장에 알 수 없는 종목이 있습니다."
        }
        validateCorporateActionState(
            pending = state.pendingCorporateActions,
            applied = state.corporateActionLedger,
            validStockIds = ids,
        )
        rebuildDynamicStockDefinitions(state.corporateActionLedger)
        require(state.stocks == stocks) {
            "저장 종목 정의가 종목팩과 기업행동 원장에서 재구성한 결과와 일치하지 않습니다."
        }
        require(state.listingLifecycleStates.keys == ids && state.listingLifecycleStates.all { (stockId, listing) ->
            stockId in ids && listing.stockId == stockId && stockById[stockId]?.market == listing.market
        }) { "상장 생명주기 상태가 현재 종목 카탈로그와 일치하지 않습니다." }
        require(state.listingLifecycleLedger.all { it.stockId in ids }) {
            "상장 생명주기 원장에 현재 카탈로그가 알 수 없는 ID가 있습니다."
        }
        val krxMarkets = setOf(Market.KOSPI, Market.KOSDAQ)
        val krxStockIds = stocks.filter { stock ->
            stock.market.isKorean && state.listingLifecycleStates.getValue(stock.id).isIndexEligible
        }.mapTo(linkedSetOf(), StockDefinition::id)
        val usStockIds = stocks.filter { stock ->
            stock.market.isUnitedStates && state.listingLifecycleStates.getValue(stock.id).isIndexEligible
        }.mapTo(linkedSetOf(), StockDefinition::id)
        require(state.tradingProtectionSnapshot.let { protection ->
            protection.krxCircuitBreakers.keys == krxMarkets &&
                protection.krxSidecars.keys == krxMarkets &&
                protection.krxVolatilityInterruptions.keys == krxStockIds &&
                protection.instrumentTradingHalts.keys.all { stockId ->
                    stockId in ids && state.listingLifecycleStates.getValue(stockId).isIndexEligible
                } &&
                protection.scheduledInstrumentTradingHalts.values.all { halt ->
                    halt.stockId in ids && state.listingLifecycleStates.getValue(halt.stockId).isIndexEligible
                } &&
                protection.investmentAlerts.keys.all { stockId ->
                    stockId in ids && state.listingLifecycleStates.getValue(stockId).isIndexEligible
                } &&
                protection.usLuldStates.keys == usStockIds &&
                protection.usMarketWideCircuitBreaker?.venueStatuses?.keys ==
                Market.entries.filter(Market::isUnitedStates).toSet()
        }) { "시장 보호장치 저장 상태가 현재 종목 카탈로그와 일치하지 않습니다." }
        require(state.dailyTradingSurveillance.keys == ids && state.dailyTradingSurveillance.all { (stockId, values) ->
            stockId in ids && values.zipWithNext().all { (left, right) -> left.date < right.date }
        }) { "일별 시장감시 이력이 올바르지 않습니다." }
        val savedIndices = state.marketIndices
        require(savedIndices.keys == MarketIndexId.entries.toSet()) {
            "대표 지수 현재값 4종이 모두 필요합니다."
        }
        require(state.marketIndexHistory.keys == MarketIndexId.entries.toSet() && state.marketIndexHistory.all { (id, values) ->
            id in MarketIndexId.entries && values.all { it.id == id } &&
                values.zipWithNext().all { (left, right) -> left.timestamp <= right.timestamp }
        }) { "대표 지수 이력이 올바르지 않습니다." }

        options = state.options
        phase = state.phase
        screen = state.screen
        currentTime = state.currentTime
        turn = state.turn
        selectedTurnStep = state.selectedTurnStep
        selectedStockId = state.selectedStockId?.also { require(it in ids) }
        isAdvancing = false
        lastMessage = "저장 게임을 불러왔습니다."
        val restoredMacro = state.macro.validatedCopy()
        require(restoredMacro.fxRatesToKrw != null && restoredMacro.previousFxRatesToKrw != null) {
            "현재 저장 스키마에는 통화별 환율 상태가 필요합니다."
        }
        require(
            state.options.initialExternalMarketForces.values.all { it.isFinite() && it in 0.0..1.0 } &&
                state.externalMarketForcesTarget.values.all { it.isFinite() && it in 0.0..1.0 },
        ) {
            "외부 시장 환경의 시작값 또는 목표가 올바르지 않습니다."
        }
        val restoredDynamics = state.marketDynamicsSnapshot.validatedCopy()
        require(
            abs(restoredMacro.volatilityRegime - restoredDynamics.resolvedVolatilityRegime) <= 1e-9 &&
                abs(restoredMacro.retailOrderFlow - restoredDynamics.retailFlow) <= 1e-9 &&
                abs(restoredMacro.institutionalOrderFlow -
                    restoredDynamics.institutionalFlow) <= 1e-9 &&
                abs(restoredMacro.liquidityStress - restoredDynamics.liquidityStress) <= 1e-9 &&
                abs(restoredMacro.newsIntensity - restoredDynamics.newsIntensity) <= 1e-9,
        ) { "거시 상태와 시장 동역학 스냅샷이 일치하지 않습니다." }
        macro = restoredMacro
        externalMarketForcesTarget = state.externalMarketForcesTarget
        marketDynamicsEngine.restore(restoredDynamics)
        macroDate = gameDate(currentTime)
        benchmarkValue = state.currentBenchmarkValue
        peakAssetsKrw = state.peakAssetsKrw
        maximumDrawdown = state.maximumDrawdown
        nextSequence = state.nextSequence
        require(nextSequence > 0L) { "저장 시퀀스가 올바르지 않습니다." }
        val accountingSequences = buildList {
            state.trades.mapTo(this) { it.accountingSequence }
            state.dividendLedger.mapTo(this) { it.accountingSequence }
            state.corporateActionLedger.mapTo(this) { it.accountingSequence }
        }
        require(accountingSequences.all { it in 1 until nextSequence } &&
            accountingSequences.distinct().size == accountingSequences.size
        ) { "체결·분배·기업행동의 전역 회계 순번이 올바르지 않습니다." }
        require(state.dividendLedger.map(DividendLedgerEntry::id).distinct().size == state.dividendLedger.size &&
            state.dividendLedger.all { it.stockId in ids && it.accountingSequence > 0L }
        ) { "분배 원장 ID·종목·회계 순번이 올바르지 않습니다." }

        random.restore(state.rngState)
        eventEngine.restore(state.eventEngineSnapshot, stocks)
        quotes.clear()
        quotes.putAll(state.quotes)
        history.clear()
        state.priceHistory.forEach { (stockId, bars) ->
            require(bars.isNotEmpty()) { "차트 기록이 비어 있습니다." }
            require(bars.all { it.step == PriceBarInterval.ONE_HOUR }) {
                "엔진 가격 기록에는 시간봉만 포함되어야 합니다."
            }
            history[stockId] = ArrayDeque(bars.takeLast(MAX_RECENT_BARS))
        }
        chartPriceHistory.clear()
        state.chartPriceHistory.forEach { (stockId, savedHistories) ->
            require(savedHistories.keys == CHART_INTERVALS) {
                "차트 주기별 가격 기록이 모두 필요합니다."
            }
            chartPriceHistory[stockId] = CHART_INTERVALS.associateWithTo(linkedMapOf()) { interval ->
                val bars = savedHistories.getValue(interval)
                require(bars.all { it.step == interval }) {
                    "차트 가격 기록의 봉 주기가 일치하지 않습니다."
                }
                ArrayDeque(bars.takeLast(MAX_CHART_BARS_PER_INTERVAL))
            }
        }
        pendingEtfReferenceReturns.clear()
        pendingEtfReferenceReturns.putAll(state.pendingEtfReferenceReturns)
        pendingClosedEventLogReturns.clear()
        pendingClosedEventLogReturns.putAll(state.pendingClosedEventLogReturns)
        marketIndices.clear()
        marketIndexHistory.clear()
        marketIndices.putAll(savedIndices)
        for (id in MarketIndexId.entries) {
            val values = state.marketIndexHistory.getValue(id).takeLast(MAX_INDEX_BARS)
            require(values.isNotEmpty()) { "대표 지수 이력이 비어 있습니다." }
            marketIndexHistory[id] = ArrayDeque<MarketIndexSnapshot>().apply {
                addAll(values)
            }
        }
        cash.clear()
        cash.putAll(state.cashByCurrency)
        holdings.clear()
        holdings.putAll(state.holdings)
        orders.clear()
        orders += state.orders
        trades.clear()
        trades += state.trades
        transactionCosts.clear()
        transactionCosts += state.transactionCosts
        realizedGains.clear()
        realizedGains += state.realizedGains
        dividends.clear()
        dividends += state.dividendLedger
        lastEvaluatedDistributionDateByStock.clear()
        lastEvaluatedDistributionDateByStock.putAll(state.lastEvaluatedDistributionDateByStock)
        corporateActionLedger.clear()
        corporateActionLedger += state.corporateActionLedger
        corporateFundamentals.clear()
        corporateFundamentals.putAll(state.corporateFundamentals)
        fundFinancialStates.clear()
        fundFinancialStates.putAll(state.fundFinancialStates)
        referencePortfolioStates.clear()
        referencePortfolioStates.putAll(state.referencePortfolioStates)
        referencePortfolioLedger.clear()
        referencePortfolioLedger += state.referencePortfolioLedger
        equityReferenceStates.clear()
        equityReferenceStates.putAll(state.equityReferenceStates)
        equityReferenceLedger.clear()
        equityReferenceLedger += state.equityReferenceLedger
        fundOfFundsStates.clear()
        fundOfFundsStates.putAll(state.fundOfFundsStates)
        fundOfFundsRebalanceLedger.clear()
        fundOfFundsRebalanceLedger += state.fundOfFundsRebalanceLedger
        alternativeRiskPremiaStates.clear()
        alternativeRiskPremiaStates.putAll(state.alternativeRiskPremiaStates)
        alternativeRiskPremiaRebalanceLedger.clear()
        alternativeRiskPremiaRebalanceLedger += state.alternativeRiskPremiaRebalanceLedger
        compositeReferenceStates.clear()
        compositeReferenceStates.putAll(state.compositeReferenceStates)
        compositeReferenceRebalanceLedger.clear()
        compositeReferenceRebalanceLedger += state.compositeReferenceRebalanceLedger
        dailyResetStates.clear()
        dailyResetStates.putAll(state.dailyResetStates)
        optionStrategyStates.clear()
        optionStrategyStates.putAll(state.optionStrategyStates)
        cashCollateralizedPutSpreadStates.clear()
        cashCollateralizedPutSpreadStates.putAll(state.cashCollateralizedPutSpreadStates)
        etnStates.clear()
        etnStates.putAll(state.etnStates)
        etnLedger.clear()
        etnLedger += state.etnLedger
        closedEndFundStates.clear()
        closedEndFundStates.putAll(state.closedEndFundStates)
        closedEndFundLedger.clear()
        closedEndFundLedger += state.closedEndFundLedger
        fixedIncomeReferenceStates.clear()
        fixedIncomeReferenceStates.putAll(state.fixedIncomeReferenceStates)
        fixedIncomeRollLedger.clear()
        fixedIncomeRollLedger += state.fixedIncomeRollLedger
        commoditySpotReferenceStates.clear()
        commoditySpotReferenceStates.putAll(state.commoditySpotReferenceStates)
        futuresReferenceStates.clear()
        futuresReferenceStates.putAll(state.futuresReferenceStates)
        futuresRollLedger.clear()
        futuresRollLedger += state.futuresRollLedger
        futuresAllocationLedger.clear()
        futuresAllocationLedger += state.futuresAllocationLedger
        pendingFundFlowRates.clear()
        pendingFundFlowRates.putAll(state.pendingFundFlowRates)
        listingLifecycleStates.clear()
        listingLifecycleStates.putAll(state.listingLifecycleStates)
        require(listingLifecycleStates.values.all { lifecycle ->
            lifecycle.status != ListingLifecycleStatus.LIQUIDATION_PENDING ||
                lifecycle.finalDisposition?.type != ListingFinalDispositionType.CASH_LIQUIDATION ||
                lifecycle.finalDisposition.entitledQuantity != null &&
                lifecycle.finalDisposition.entitledCostBasis != null
        }) { "청산 대기 상태에는 확정된 수량과 원가가 필요합니다." }
        updateHoldingPrices()
        listingLifecycleLedger.clear()
        listingLifecycleLedger += state.listingLifecycleLedger
        tradingProtectionSnapshot = state.tradingProtectionSnapshot
        dailyTradingSurveillance.clear()
        stocks.forEach { stock ->
            dailyTradingSurveillance[stock.id] = ArrayDeque(
                state.dailyTradingSurveillance.getValue(stock.id)
                    .takeLast(MAX_DAILY_SURVEILLANCE_POINTS),
            )
        }
        restoreTaxExchangeRateLedger(state)
        val replayedTaxYears = replayTaxAccountingLedger()
        require(fifoCostBasisBook.lots.all { it.stockId in ids } && fifoBookMatchesHoldings()) {
            "FIFO 세무원장과 보유 수량이 일치하지 않습니다."
        }
        foreignExchanges.clear()
        foreignExchanges += state.foreignExchangeLedger
        activeEvents.clear()
        activeEvents += state.eventEngineSnapshot.activeEvents
        newsEvents.clear()
        newsEvents += state.newsEvents.sortedBy(GameEvent::startsAt)
        readEventIds.clear()
        readEventIds += state.readEventIds
        readStockNewsEventIds.clear()
        state.readStockNewsEventIds.forEach { (stockId, eventIds) ->
            readStockNewsEventIds[stockId] = eventIds.toMutableSet()
        }
        watchlistedStockIds.clear()
        watchlistedStockIds += state.watchlistedStockIds
        pendingCorporateActions.clear()
        pendingCorporateActions += state.pendingCorporateActions
        portfolioSnapshots.clear()
        portfolioSnapshots += state.portfolioSnapshots
        dailyStatistics.clear()
        dailyStatistics += state.dailyStatistics
        benchmarkHistory.clear()
        benchmarkHistory += state.benchmarkHistory
        annualTaxLedgers.clear()
        annualTaxLedgers.putAll(state.annualTaxLedgers)
        taxPaymentNotices.clear()
        taxPaymentNotices += state.taxPaymentNotices
        replayedTaxYears.forEach(::recalculateAnnualTax)

        dailyTrackers.clear()
        for (stock in stocks) {
            val quote = quotes.getValue(stock.id)
            val trackerDate = marketDate(stock.market, currentTime)
            dailyTrackers[stock.id] = DailyPriceTracker(
                date = trackerDate,
                basePrice = quote.previousClose,
                open = quote.open,
                high = quote.high,
                low = quote.low,
                hasRegularTrading = history.getValue(stock.id).any { bar ->
                    bar.volume > 0L && marketDate(stock.market, bar.startTime) == trackerDate
                },
            )
        }
    }

    private fun initializeMarketData() {
        for (stock in stocks) {
            initializeInstrumentMarketData(stock, currentTime)
        }
    }

    private fun initializeInstrumentFinancialStates() {
        corporateFundamentals.clear()
        fundFinancialStates.clear()
        referencePortfolioStates.clear()
        referencePortfolioLedger.clear()
        equityReferenceStates.clear()
        equityReferenceLedger.clear()
        fundOfFundsStates.clear()
        fundOfFundsRebalanceLedger.clear()
        alternativeRiskPremiaStates.clear()
        alternativeRiskPremiaRebalanceLedger.clear()
        compositeReferenceStates.clear()
        compositeReferenceRebalanceLedger.clear()
        dailyResetStates.clear()
        optionStrategyStates.clear()
        cashCollateralizedPutSpreadStates.clear()
        etnStates.clear()
        etnLedger.clear()
        closedEndFundStates.clear()
        closedEndFundLedger.clear()
        fixedIncomeReferenceStates.clear()
        fixedIncomeRollLedger.clear()
        commoditySpotReferenceStates.clear()
        futuresReferenceStates.clear()
        futuresRollLedger.clear()
        futuresAllocationLedger.clear()
        for (stock in stocks) {
            if (stock.hasCorporateEarnings) {
                corporateFundamentals[stock.id] =
                    instrumentMetricsEngine.initialCorporateState(stock, currentTime)
            }
            when (stock.fundProductProfile?.legalStructure) {
                FundLegalStructure.OPEN_END_ETF -> fundFinancialStates[stock.id] =
                    instrumentMetricsEngine.initialFundState(stock, currentTime)
                FundLegalStructure.EXCHANGE_TRADED_NOTE -> etnStates[stock.id] =
                    initialEtnState(stock)
                FundLegalStructure.CLOSED_END_FUND -> closedEndFundStates[stock.id] =
                    initialClosedEndFundState(stock)
                null -> Unit
            }
            stock.fundProductProfile?.dailyResetTerms?.let { terms ->
                val referenceMarket = dailyResetReferenceMarket(terms.resetCalendar)
                dailyResetStates[stock.id] = dailyResetEngine.initialState(
                    productId = stock.id,
                    referenceLevel = DAILY_RESET_INITIAL_REFERENCE_LEVEL,
                    nav = stock.initialPrice,
                    tradingDate = lastCompletedRegularTradingDate(referenceMarket, currentTime),
                    at = currentTime,
                    targetLeverage = terms.targetLeverage,
                )
            }
            stock.fundProductProfile?.optionStrategyTerms?.let { terms ->
                val referenceMarket = optionReferenceMarket(terms.rollCalendar)
                optionStrategyStates[stock.id] = optionStrategyEngine.initialState(
                    terms = terms,
                    referenceLevel = OPTION_INITIAL_REFERENCE_LEVEL,
                    nav = stock.initialPrice,
                    cashRateAnnual = macro.policyRate.coerceIn(-0.10, 1.0),
                    annualizedImpliedVolatility = optionImpliedVolatility(stock),
                    tradingDate = lastCompletedRegularTradingDate(referenceMarket, currentTime),
                    at = currentTime,
                )
            }
            stock.fundProductProfile?.cashCollateralizedPutSpreadTerms?.let { terms ->
                val referenceMarket = optionReferenceMarket(terms.rollCalendar)
                cashCollateralizedPutSpreadStates[stock.id] =
                    cashCollateralizedPutSpreadEngine.initialState(
                        terms = terms,
                        cashReferenceLevel = OPTION_INITIAL_REFERENCE_LEVEL,
                        optionReferenceLevel = OPTION_INITIAL_REFERENCE_LEVEL,
                        nav = stock.initialPrice,
                        optionDiscountRateAnnual = macro.policyRate.coerceIn(-0.10, 1.0),
                        annualizedImpliedVolatility = optionImpliedVolatility(stock),
                        tradingDate = lastCompletedRegularTradingDate(referenceMarket, currentTime),
                        at = currentTime,
                    )
            }
        }
        if (executableBenchmarkDefinitions.isNotEmpty()) {
            referencePortfolioStates.putAll(
                referencePortfolioEngine.initialBook(
                    definitions = executableBenchmarkDefinitions,
                    atDate = marketDate(Market.NYSE, currentTime),
                    at = currentTime,
                ).states,
            )
        }
        if (equityReferenceBenchmarkDefinitions.isNotEmpty()) {
            equityReferenceStates.putAll(
                equityReferenceBookEngine.initialBook(
                    definitions = equityReferenceBenchmarkDefinitions,
                    atDate = gameDate(currentTime),
                    at = currentTime,
                ).states,
            )
        }
        if (fundOfFundsProfiles.isNotEmpty()) {
            fundOfFundsStates.putAll(
                fundOfFundsBookEngine.initialBook(
                    profiles = fundOfFundsProfiles,
                    atDate = marketDate(Market.NYSE, currentTime),
                    at = currentTime,
                ).states,
            )
        }
        if (fixedIncomeBenchmarkDefinitions.isNotEmpty()) {
            val fixedIncomeBook = fixedIncomeReferenceBookEngine.initialBook(
                definitions = fixedIncomeBenchmarkDefinitions,
                macro = macro,
                at = currentTime,
            )
            fixedIncomeBook.states.values.forEach { state ->
                fixedIncomeReferenceStates[state.referenceId] = state
            }
        }
        if (commoditySpotBenchmarkDefinitions.isNotEmpty() || futuresBenchmarkDefinitions.isNotEmpty()) {
            val frame = commodityMarketModel.initialFrame(
                spotTerms = commoditySpotBenchmarkDefinitions.map { definition ->
                    requireNotNull(definition.commoditySpotTerms)
                },
                futuresTerms = futuresBenchmarkDefinitions.map { definition ->
                    requireNotNull(definition.futuresReferenceTerms)
                },
                macro = macro,
                at = currentTime,
            )
            val book = commodityReferenceBookEngine.initialBook(frame)
            commoditySpotReferenceStates.putAll(book.spotStates)
            futuresReferenceStates.putAll(book.futuresStates)
        }
        if (alternativeRiskPremiaBenchmarkDefinitions.isNotEmpty()) {
            alternativeRiskPremiaStates.putAll(
                alternativeRiskPremiaBookEngine.initialBook(
                    definitions = alternativeRiskPremiaBenchmarkDefinitions,
                    sourceCatalog = referenceSourceCatalog,
                    sourceSnapshot = currentReferenceSourceSnapshot(),
                    atDate = gameDate(currentTime),
                    at = currentTime,
                ).states,
            )
        }
        if (compositeReferenceBenchmarkDefinitions.isNotEmpty()) {
            compositeReferenceStates.putAll(
                compositeReferenceBookEngine.initialBook(
                    definitions = compositeReferenceBenchmarkDefinitions,
                    sourceCatalog = referenceSourceCatalog,
                    sourceSnapshot = currentReferenceSourceSnapshot(),
                    atDate = gameDate(currentTime),
                    at = currentTime,
                ).states,
            )
        }
    }

    /** Current typed source income/duration frame, shared by ALT and composite bootstraps. */
    private fun currentReferenceSourceSnapshot(): ReferenceSourceSnapshot {
        val benchmarkIncome = linkedMapOf<BenchmarkRef, Double>()
        executablePortfolioIdByBenchmarkRef.forEach { (ref, portfolioId) ->
            benchmarkIncome[ref] = referencePortfolioStates.getValue(portfolioId)
                .estimatedAnnualIncomeYield
        }
        equityReferenceStates.forEach { (ref, state) ->
            benchmarkIncome[ref] = state.estimatedAnnualIncomeYield
        }
        fixedIncomeReferenceStates.values.forEach { state ->
            benchmarkIncome[state.benchmarkRef] = state.estimatedAnnualIncomeYield
        }
        commoditySpotReferenceStates.keys.forEach { ref -> benchmarkIncome[ref] = 0.0 }
        futuresReferenceStates.keys.forEach { ref -> benchmarkIncome[ref] = 0.0 }
        fundOfFundsStates.forEach { (ref, state) ->
            benchmarkIncome[ref] = state.estimatedAnnualIncomeYield
        }
        alternativeRiskPremiaStates.forEach { (ref, state) ->
            benchmarkIncome[ref] = state.estimatedAnnualIncomeYield
        }

        val benchmarkDurations = fixedIncomeReferenceStates.values.associate { state ->
            state.benchmarkRef to state.positions.sumOf { position ->
                position.currentWeight * position.modifiedDurationYears
            }
        }.toMutableMap()
        alternativeRiskPremiaStates.forEach { (ref, state) ->
            benchmarkDurations[ref] = state.effectiveDurationYears
        }
        val instrumentIncome = directlyReferencedInstrumentIds.associateWith { stockId ->
            stockById.getValue(stockId).dividendYield
        }
        return ReferenceSourceSnapshot(
            benchmarkAnnualIncomeYields = benchmarkIncome,
            benchmarkDurationsYears = benchmarkDurations,
            instrumentAnnualIncomeYields = instrumentIncome,
            instrumentDurationsYears = directlyReferencedInstrumentIds.associateWith { 0.0 },
            instrumentAvailability = directlyReferencedInstrumentIds.associateWith { stockId ->
                listingLifecycleStates.getValue(stockId).isIndexEligible
            },
            mortgageRateAnnual = currentMortgageRateAnnual(),
        )
    }

    private fun currentMortgageRateAnnual(
        fixedIncomeStates: Collection<FixedIncomeReferenceState> = fixedIncomeReferenceStates.values,
    ): Double {
        val tenYearTreasuryRate = fixedIncomeStates.asSequence()
            .mapNotNull { state -> state.nominalCurves[ReferenceCurrency.USD] }
            .firstOrNull()
            ?.rateAtYears(10.0)
            ?: macro.policyRate
        return (tenYearTreasuryRate + MODEL_MORTGAGE_SPREAD_ANNUAL).coerceIn(0.0, 1.0)
    }

    private fun initialEtnState(stock: StockDefinition): EtnState {
        val profile = requireNotNull(stock.fundProductProfile)
        val terms = requireNotNull(profile.etnProductTerms)
        val credit = requireNotNull(profile.etnIssuerCreditModelParameters)
        require(terms.productId == stock.id && credit.issuerId == terms.issuerId)
        return EtnState(
            productId = stock.id,
            referenceLevel = OPTION_INITIAL_REFERENCE_LEVEL,
            feeAdjustedIndicativeValuePerNote = stock.initialPrice,
            notesOutstanding = stock.sharesOutstanding,
            accruedCouponPerNote = 0.0,
            issuerCreditSpread = credit.initialCreditSpread,
            issuerHazardRate = credit.initialHazardRate,
            issuerRecoveryRate = credit.recoveryRate,
            indicativeValueObservationWindow = emptyList(),
            lifecycle = EtnLifecycle.ACTIVE,
            terminalCreditEvent = null,
            asOf = currentTime,
            revision = 0L,
        )
    }

    private fun initialClosedEndFundState(stock: StockDefinition): ClosedEndFundState {
        val profile = requireNotNull(stock.fundProductProfile)
        val terms = requireNotNull(profile.closedEndFundTerms)
        val parameters = requireNotNull(profile.closedEndFundMarketModelParameters)
        require(terms.fundId == stock.id && parameters.fundId == stock.id)
        val initialNav = stock.initialPrice / (1.0 + parameters.targetMarketDiscountRate)
        val commonNetAssets = initialNav * stock.sharesOutstanding.toDouble()
        val netAssetFraction = 1.0 - parameters.initialDebtToGrossAssets -
            parameters.initialPreferredToGrossAssets
        require(netAssetFraction > 0.0)
        val grossAssets = commonNetAssets / netAssetFraction
        return ClosedEndFundState(
            fundId = stock.id,
            grossAssets = grossAssets,
            commonSharesOutstanding = stock.sharesOutstanding.toDouble(),
            debtLiability = grossAssets * parameters.initialDebtToGrossAssets,
            preferredShareLiability = grossAssets * parameters.initialPreferredToGrossAssets,
            navPerCommonShare = initialNav,
            undistributedNetInvestmentIncome = 0.0,
            distributionReserve = 0.0,
            marketDiscountRate = parameters.targetMarketDiscountRate,
            asOf = currentTime,
            revision = 0L,
        )
    }

    private fun dailyResetReferenceMarket(calendar: DailyResetCalendar): Market = when (calendar) {
        DailyResetCalendar.KRX_EQUITY -> Market.KOSPI
        DailyResetCalendar.US_EQUITY -> Market.NYSE
    }

    private fun optionReferenceMarket(calendar: OptionRollCalendar): Market = when (calendar) {
        OptionRollCalendar.KRX_EQUITY -> Market.KOSPI
        OptionRollCalendar.US_EQUITY -> Market.NYSE
    }

    private fun optionImpliedVolatility(stock: StockDefinition): Double =
        (stock.volatility * sqrt(macro.volatilityRegime.coerceAtLeast(0.0))).coerceIn(0.0, 5.0)

    private fun lastCompletedRegularTradingDate(market: Market, at: Instant): LocalDate {
        var candidate = marketDate(market, at)
        while (true) {
            val window = GameCalendar.regularSessionWindow(
                market,
                candidate,
                runtimeClosedDates(market, candidate),
            )
            if (window != null && at >= window.closesAt) return candidate
            candidate = candidate.minus(1, DateTimeUnit.DAY)
        }
    }

    private fun reachesDailyResetClose(
        calendar: DailyResetCalendar,
        from: Instant,
        to: Instant,
    ): Boolean = reachesMarketClose(dailyResetReferenceMarket(calendar), from, to)

    private fun reachesMarketClose(
        market: Market,
        from: Instant,
        to: Instant,
    ): Boolean {
        val dates = linkedSetOf(marketDate(market, from), marketDate(market, to))
        return dates.any { date ->
            GameCalendar.regularSessionWindow(
                market,
                date,
                runtimeClosedDates(market, date),
            )?.let { window -> from < window.closesAt && to >= window.closesAt } == true
        }
    }

    private fun reachesOptionClose(
        calendar: OptionRollCalendar,
        from: Instant,
        to: Instant,
    ): Boolean = reachesMarketClose(optionReferenceMarket(calendar), from, to)

    private fun etnCreditShocks(
        stock: StockDefinition,
        previous: EtnState,
        parameters: EtnIssuerCreditModelParameters,
        elapsedYearFraction: Double,
        from: Instant,
    ): Pair<Double, Double> {
        val stressMultiplier = (
            1.0 + macro.liquidityStress * 3.0 - macro.growthSurprise * 8.0 -
                macro.riskSentiment * 0.35
            ).coerceIn(0.25, 8.0)
        val targetSpread = (parameters.initialCreditSpread * stressMultiplier).coerceIn(0.0, 1.0)
        val random = DeterministicRandom.keyed(
            options.seed,
            "etn-issuer-credit:${parameters.issuerId}:${stock.id}:${from.epochSeconds}",
        )
        val spreadInnovation = parameters.spreadShockAnnualVolatility *
            sqrt(elapsedYearFraction) * random.nextGaussian()
        val spreadDrift = parameters.annualSpreadMeanReversionRate *
            (targetSpread - previous.issuerCreditSpread) * elapsedYearFraction
        val nextSpread = (previous.issuerCreditSpread + spreadDrift + spreadInnovation)
            .coerceIn(0.0, 1.0)
        val impliedHazard = (nextSpread / (1.0 - previous.issuerRecoveryRate).coerceAtLeast(0.05))
            .coerceIn(0.0, 1.0)
        val hazardAdjustment = parameters.annualSpreadMeanReversionRate *
            (impliedHazard - previous.issuerHazardRate) * elapsedYearFraction
        val nextHazard = (previous.issuerHazardRate + hazardAdjustment).coerceIn(0.0, 1.0)
        return (nextSpread - previous.issuerCreditSpread) to
            (nextHazard - previous.issuerHazardRate)
    }

    private fun etnCreditMarkedValue(
        state: EtnState,
        maturityDate: LocalDate,
        valuationDate: LocalDate,
    ): Double {
        val remainingYears = (
            (maturityDate.toEpochDays() - valuationDate.toEpochDays()).coerceAtLeast(0) / 365.25
            ).coerceAtMost(ETN_MAX_CREDIT_DURATION_YEARS)
        // The noteholder owns both the fee-adjusted indicative claim and an already accrued,
        // unpaid coupon claim. Keeping the latter in the market mark makes the later cash payment
        // an ex-coupon transfer instead of counting the same option premium as an earlier loss.
        val unsecuredClaim = (
            state.feeAdjustedIndicativeValuePerNote + state.accruedCouponPerNote
            ).coerceAtLeast(ETN_MIN_MARKED_VALUE)
        return unsecuredClaim * exp(-state.issuerCreditSpread * remainingYears)
    }

    private fun closedEndFundDiscountShock(
        stock: StockDefinition,
        state: ClosedEndFundState,
        elapsedYearFraction: Double,
        from: Instant,
        target: Double,
        meanReversion: Double,
        annualVolatility: Double,
    ): Double {
        val random = DeterministicRandom.keyed(
            options.seed,
            "closed-end-fund-discount:${stock.id}:${from.epochSeconds}",
        )
        val systematic = (
            macro.liquidityStress * 0.08 - macro.riskSentiment * 0.04
            ) * elapsedYearFraction
        val innovation = annualVolatility * sqrt(elapsedYearFraction) * random.nextGaussian()
        val shock = systematic + innovation
        val beforeShock = state.marketDiscountRate +
            meanReversion * (target - state.marketDiscountRate) * elapsedYearFraction
        return shock.coerceIn(-0.98 - beforeShock, 1.0 - beforeShock)
    }

    private fun initializeMarketIndices(at: Instant) {
        val initial = marketIndexEngine.initialSnapshots(at)
        marketIndices.putAll(initial)
        for ((id, snapshot) in initial) {
            marketIndexHistory[id] = ArrayDeque<MarketIndexSnapshot>().apply { addLast(snapshot) }
        }
    }

    private fun initializeTradingProtections(at: Instant) {
        val krxMarkets = listOf(Market.KOSPI, Market.KOSDAQ)
        val krxCircuitBreakers = krxMarkets.associateWith { market ->
            TradingProtectionEngine.initialKrxCircuitBreaker(market, marketDate(market, at))
        }
        val krxSidecars = krxMarkets.associateWith { market ->
            TradingProtectionEngine.initialKrxSidecar(market, marketDate(market, at))
        }
        val krxVis = stocks.asSequence().filter { it.market.isKorean }.associate { stock ->
            stock.id to TradingProtectionEngine.initialKrxVi(stock.id, stock.market)
        }
        val usDate = marketDate(Market.NYSE, at)
        val usLuld = stocks.asSequence().filter { it.market.isUnitedStates }.associate { stock ->
            val quote = quotes.getValue(stock.id)
            val easternTime = GameCalendar.marketLocalDateTime(stock.market, at).time
            stock.id to TradingProtectionEngine.initialUsLuld(
                stockId = stock.id,
                primaryMarket = stock.market,
                tradingDate = usDate,
                tier = usLuldTier(stock),
                previousClose = quote.previousClose,
                referencePrice = quote.price,
                referencePriceEffectiveAt = at,
                easternTime = easternTime,
            )
        }
        tradingProtectionSnapshot = TradingProtectionSnapshot(
            krxCircuitBreakers = krxCircuitBreakers,
            krxSidecars = krxSidecars,
            krxVolatilityInterruptions = krxVis,
            usMarketWideCircuitBreaker = TradingProtectionEngine.initialUsMwcb(usDate, at),
            usLuldStates = usLuld,
        )
        dailyTradingSurveillance.clear()
        stocks.forEach { stock -> dailyTradingSurveillance[stock.id] = ArrayDeque() }
    }

    private fun usLuldTier(stock: StockDefinition): UsLuldTier = when {
        stock.isFundLike -> UsLuldTier.TIER_1
        stock.marketCap >= 10_000_000_000.0 -> UsLuldTier.TIER_1
        else -> UsLuldTier.TIER_2
    }

    private fun initializeInstrumentMarketData(stock: StockDefinition, at: Instant) {
        val session = marketSession(stock.market, at)
        quotes[stock.id] = Quote(
            stockId = stock.id,
            timestamp = at,
            price = stock.initialPrice,
            previousClose = stock.initialPrice,
            session = session,
        )
        history[stock.id] = ArrayDeque<PriceBar>().apply {
            addLast(
                PriceBar(
                    stockId = stock.id,
                    startTime = at - 1.hours,
                    endTime = at,
                    step = PriceBarInterval.ONE_HOUR,
                    open = stock.initialPrice,
                    high = stock.initialPrice,
                    low = stock.initialPrice,
                    close = stock.initialPrice,
                    volume = 0L,
                ),
            )
        }
        chartPriceHistory[stock.id] = CHART_INTERVALS.associateWithTo(linkedMapOf()) { ArrayDeque() }
        dailyTrackers[stock.id] = DailyPriceTracker(
            date = marketDate(stock.market, at),
            basePrice = stock.initialPrice,
            open = stock.initialPrice,
            high = stock.initialPrice,
            low = stock.initialPrice,
            hasRegularTrading = false,
        )
    }

    private fun advanceOneHour() {
        val from = currentTime
        val to = GameCalendar.advanceHours(from, 1)
        if (to <= from) return
        val fromGameDate = gameDate(from)

        processInstrumentLifecycle(from)
        advanceProtectionClock(from)
        applyDueCorporateActions(from, to)
        updateMacro(from)
        generateEvents(from, to)
        syncEventDrivenTradingHalts(from, to)
        announceDueDirectUnderlyingFundLiquidations(from, to)
        val activeStocks = stocks.filterNot { stock ->
            listingLifecycleStates.getValue(stock.id).let { state ->
                state.isTerminal || state.isSettlementPending
            }
        }
        val scheduledImpactEvents = scheduledEventEngine.impactEventsBetween(from, to, activeStocks)

        val previousClosesByStockId = quotes.mapValues { (_, quote) -> quote.price }
        val turnEvents = activeEvents + scheduledImpactEvents
        val ordinaryTradingFractions = Market.entries.associateWith { market ->
            regularTradingFraction(market, from, to)
        }
        val provisionalMarketFractions = Market.entries.associateWith { market ->
            marketProtectionTradingFraction(
                market,
                from,
                to,
                ordinaryTradingFractions.getValue(market),
            )
        }
        val protectionBeforeObservation = tradingProtectionSnapshot
        val provisional = generateTurnBars(
            from = from,
            to = to,
            turnEvents = turnEvents,
            ordinaryTradingFractions = ordinaryTradingFractions,
            effectiveMarketFractions = provisionalMarketFractions,
            commit = false,
        )
        val provisionalIndices = calculateMarketIndices(
            timestamp = to,
            bars = provisional.bars,
            previousClosesByStockId = previousClosesByStockId,
            fractions = provisionalMarketFractions,
        )
        val protectionImpact = evaluateTradingProtections(
            from = from,
            to = to,
            bars = provisional.bars,
            previousPrices = previousClosesByStockId,
            provisionalIndices = provisionalIndices,
            ordinaryTradingFractions = ordinaryTradingFractions,
        )
        val tradingFractions = Market.entries.associateWith { market ->
            marketProtectionTradingFraction(
                market,
                from,
                to,
                ordinaryTradingFractions.getValue(market),
                protectionImpact.marketBlocks[market].orEmpty(),
            )
        }
        val finalized = generateTurnBars(
            from = from,
            to = to,
            turnEvents = turnEvents,
            ordinaryTradingFractions = ordinaryTradingFractions,
            effectiveMarketFractions = tradingFractions,
            additionalInstrumentBlocks = protectionImpact.instrumentBlocks,
            priceBounds = protectionImpact.priceBounds,
            temporaryProtectionMarkets = protectionImpact.temporaryProtectionMarkets,
            commit = true,
        )
        advanceFundFinancialStates(to, finalized)

        updateMarketIndices(to, finalized.bars, previousClosesByStockId, tradingFractions)
        updateMarketChange(finalized.bars, tradingFractions)
        marketDynamicsEngine.observeMarketReturn(
            realizedSystemicReturn(
                attributions = finalized.priceAttributions,
                tradingFractions = finalized.stockTradingFractions,
            ),
        )
        processOpenOrders(
            finalized.bars,
            finalized.stockTradingFractions,
            finalized.stockFirstExecutionTimes,
            protectionBeforeObservation,
            protectionImpact,
        )
        processDailyListingSurveillance(from, to)
        reconcileStructuredSourceAvailability(to)
        processInstrumentLifecycle(to)
        processScheduledDividends(from, to)
        maybeAnnounceCorporateActions(from, to)
        applyDueCorporateActionsAtBoundary(to)
        updateHoldingPrices()
        expireDayOrders(to)
        updateBenchmark(finalized.bars, tradingFractions)

        currentTime = to
        turn = GameCalendar.turnAt(to)
        activeEvents.clear()
        activeEvents += eventEngine.advanceTo(to)
        // Make restrictions whose legal boundary is exactly the turn end visible before the user
        // can place the next order (for example, a KRX full-session halt beginning at 09:00).
        advanceProtectionClock(to)
        processTaxExchangeRateSettlements(to)
        processDueTaxPayments(gameDate(to))
        updateDrawdown()

        val toGameDate = gameDate(to)
        if (toGameDate != fromGameDate) {
            recalculateAnnualTax(fromGameDate.year)
            if (toGameDate.year != fromGameDate.year && toGameDate.year <= 2040) {
                recalculateAnnualTax(toGameDate.year)
            }
            recordDailySnapshot(fromGameDate, to)
            trimMarketActionNewsArchive()
        }
    }

    private fun realizedSystemicReturn(
        attributions: Map<String, PriceAttribution>,
        tradingFractions: Map<String, Double>,
    ): Double? {
        var weightedLogReturn = 0.0
        var totalWeight = 0.0
        for (stock in stocks) {
            if (stock.isFundLike || listingLifecycleStates.getValue(stock.id).isIndexEligible.not()) continue
            val tradingFraction = tradingFractions[stock.id] ?: 0.0
            if (tradingFraction <= 0.0) continue
            val attribution = attributions[stock.id] ?: continue
            val weight = stock.marketCap.coerceAtLeast(1.0)
            val fullHourEquivalent =
                attribution.systemicDiffusionLogReturn / sqrt(tradingFraction) +
                    attribution.systemicContinuousLogReturn / tradingFraction +
                    attribution.systemicJumpLogReturn
            weightedLogReturn += fullHourEquivalent * weight
            totalWeight += weight
        }
        return if (totalWeight == 0.0) null else {
            (exp(weightedLogReturn / totalWeight) - 1.0).coerceIn(-1.0, 1.0)
        }
    }

    /**
     * Price generation is keyed by stock and hour, so this provisional/final two-pass use is
     * deterministic. The provisional pass is read-only; only the finalized pass consumes carry,
     * advances daily trackers, writes quotes, and appends one history bar.
     */
    private fun generateTurnBars(
        from: Instant,
        to: Instant,
        turnEvents: List<GameEvent>,
        ordinaryTradingFractions: Map<Market, Double>,
        effectiveMarketFractions: Map<Market, Double>,
        additionalInstrumentBlocks: Map<String, List<RuntimeTradingInterval>> = emptyMap(),
        priceBounds: Map<String, RuntimePriceBounds> = emptyMap(),
        temporaryProtectionMarkets: Set<Market> = emptySet(),
        commit: Boolean,
    ): TurnGenerationResult {
        val generatedBars = linkedMapOf<String, PriceBar>()
        val stockTradingFractions = mutableMapOf<String, Double>()
        val stockFirstExecutionTimes = mutableMapOf<String, Instant>()
        val priceAttributions = mutableMapOf<String, PriceAttribution>()
        val openingReferencedInstrumentPrices = directlyReferencedInstrumentIds.associateWith { stockId ->
            quotes.getValue(stockId).price
        }
        fun referencedInstrumentPriceLogReturn(stockId: String): Double {
            if (!listingLifecycleStates.getValue(stockId).isIndexEligible) return 0.0
            val bar = requireNotNull(generatedBars[stockId]) {
                "참조 사업회사 기초 봉이 먼저 생성되지 않았습니다: $stockId"
            }
            val openingPrice = openingReferencedInstrumentPrices.getValue(stockId)
            require(openingPrice.isFinite() && openingPrice > 0.0 && bar.close.isFinite() && bar.close > 0.0) {
                "참조 사업회사 가격이 유효하지 않습니다: " +
                    "$stockId open=$openingPrice close=${bar.close}"
            }
            return ln(bar.close / openingPrice)
        }
        val referencePortfolioAdvance = if (referencePortfolioStates.isEmpty()) {
            null
        } else {
            val referenceDate = marketDate(Market.NYSE, from)
            val referenceFraction = regionalTradingFraction(
                EtfExposureRegion.UNITED_STATES,
                from,
                effectiveMarketFractions,
            )
            val references = executableBenchmarkDefinitions.map { it.ref }
            referencePortfolioEngine.advanceHour(
                book = ReferencePortfolioBook(referencePortfolioStates.toMap()),
                definitions = executableBenchmarkDefinitions,
                referenceDates = references.associateWith { referenceDate },
                referenceTradingFractions = references.associateWith { referenceFraction },
                from = from,
                to = to,
                macro = macro,
            )
        }
        val equityReferenceAdvance = if (equityReferenceStates.isEmpty()) {
            null
        } else {
            equityReferenceBookEngine.advanceHour(
                book = EquityReferenceBook(equityReferenceStates.toMap()),
                definitions = equityReferenceBenchmarkDefinitions,
                macro = macro,
                marketTradingFractions = effectiveMarketFractions,
                from = from,
                to = to,
            )
        }
        val fixedIncomeReferenceAdvance = if (fixedIncomeReferenceStates.isEmpty()) {
            null
        } else {
            val statesByRef = fixedIncomeReferenceStates.values.associateBy(
                FixedIncomeReferenceState::benchmarkRef,
            )
            val fractions = fixedIncomeBenchmarkDefinitions.associate { definition ->
                val profile = requireNotNull(definition.fixedIncomeProfile)
                val region = when (profile.geography) {
                    FixedIncomeGeography.KOREA -> EtfExposureRegion.KOREA
                    FixedIncomeGeography.UNITED_STATES -> EtfExposureRegion.UNITED_STATES
                    FixedIncomeGeography.GLOBAL -> EtfExposureRegion.GLOBAL
                    FixedIncomeGeography.DEVELOPED_EX_US -> EtfExposureRegion.DEVELOPED_EX_US
                    FixedIncomeGeography.EMERGING_MARKETS -> EtfExposureRegion.EMERGING_MARKETS
                }
                definition.ref to (
                    regionalTradingFraction(region, from, effectiveMarketFractions) /
                        REFERENCE_TRADING_HOURS_PER_YEAR
                    )
            }
            fixedIncomeReferenceBookEngine.advance(
                book = FixedIncomeReferenceBook(statesByRef),
                definitions = fixedIncomeBenchmarkDefinitions,
                macro = macro,
                elapsedYearFractions = fractions,
                to = to,
            )
        }
        val commodityReferenceAdvance = if (
            commoditySpotReferenceStates.isEmpty() && futuresReferenceStates.isEmpty()
        ) {
            null
        } else {
            val book = CommodityReferenceBook(
                spotStates = commoditySpotReferenceStates.toMap(),
                futuresStates = futuresReferenceStates.toMap(),
            )
            val frame = commodityMarketModel.advanceFrame(
                book = book,
                spotTerms = commoditySpotBenchmarkDefinitions.map { definition ->
                    requireNotNull(definition.commoditySpotTerms)
                },
                futuresTerms = futuresBenchmarkDefinitions.map { definition ->
                    requireNotNull(definition.futuresReferenceTerms)
                },
                macro = macro,
                from = from,
                to = to,
            )
            commodityReferenceBookEngine.advance(book, frame)
        }
        fun baseBenchmarkGrossLogReturn(ref: BenchmarkRef): Double? =
            referencePortfolioAdvance?.grossReferenceLogReturns?.get(ref)
                ?: equityReferenceAdvance?.grossReferenceLogReturns?.get(ref)
                ?: fixedIncomeReferenceAdvance?.grossReferenceLogReturns?.get(ref)
                ?: commodityReferenceAdvance?.grossReferenceLogReturns?.get(ref)

        fun baseBenchmarkAnnualIncomeYield(ref: BenchmarkRef): Double? {
            val portfolioId = executablePortfolioIdByBenchmarkRef[ref]
            return portfolioId?.let { id -> referencePortfolioAdvance?.book?.states?.get(id) }
                ?.estimatedAnnualIncomeYield
                ?: equityReferenceAdvance?.estimatedAnnualIncomeYields?.get(ref)
                ?: fixedIncomeReferenceAdvance?.annualIncomeYields?.get(ref)
                ?: 0.0.takeIf {
                    commodityReferenceAdvance?.grossReferenceLogReturns?.containsKey(ref) == true
                }
        }
        val fundOfFundsAdvance = if (fundOfFundsStates.isEmpty()) {
            null
        } else {
            val componentRefs = fundOfFundsProfiles.values.flatMapTo(linkedSetOf()) { profile ->
                profile.componentBenchmarkRefs
            }
            fundOfFundsBookEngine.advanceHour(
                book = FundOfFundsBook(fundOfFundsStates.toMap()),
                profiles = fundOfFundsProfiles,
                componentGrossLogReturns = componentRefs.associateWith { ref ->
                    requireNotNull(baseBenchmarkGrossLogReturn(ref)) {
                        "펀드오브펀드 구성 benchmark 수익률이 먼저 계산되지 않았습니다: $ref"
                    }
                },
                componentAnnualIncomeYields = componentRefs.associateWith { ref ->
                    requireNotNull(baseBenchmarkAnnualIncomeYield(ref)) {
                        "펀드오브펀드 구성 benchmark 소득률이 먼저 계산되지 않았습니다: $ref"
                    }
                },
                macro = macro,
                referenceTradingDate = marketDate(Market.NYSE, from),
                referenceTradingFraction = regionalTradingFraction(
                    EtfExposureRegion.UNITED_STATES,
                    from,
                    effectiveMarketFractions,
                ),
                reachesReferenceClose = reachesMarketClose(Market.NYSE, from, to),
                from = from,
                to = to,
            )
        }

        fun preStructuredBenchmarkGrossLogReturn(ref: BenchmarkRef): Double? =
            fundOfFundsAdvance?.grossReferenceLogReturns?.get(ref)
                ?: baseBenchmarkGrossLogReturn(ref)

        fun preStructuredBenchmarkAnnualIncomeYield(ref: BenchmarkRef): Double? =
            fundOfFundsAdvance?.estimatedAnnualIncomeYields?.get(ref)
                ?: baseBenchmarkAnnualIncomeYield(ref)

        fun preStructuredBenchmarkDurationYears(ref: BenchmarkRef): Double? =
            fixedIncomeReferenceAdvance?.book?.states?.get(ref)?.positions?.sumOf { position ->
                position.currentWeight * position.modifiedDurationYears
            }

        val instrumentSourceIncomeYields = directlyReferencedInstrumentIds.associateWith { stockId ->
            stockById.getValue(stockId).dividendYield
        }
        val instrumentSourceDurations = directlyReferencedInstrumentIds.associateWith { 0.0 }
        val instrumentSourceAvailability = directlyReferencedInstrumentIds.associateWith { stockId ->
            listingLifecycleStates.getValue(stockId).isIndexEligible
        }
        val sourceFxLogReturns = buildMap {
            for (sourceCurrency in ReferenceCurrency.entries) {
                for (targetCurrency in ReferenceCurrency.entries) {
                    if (sourceCurrency == targetCurrency) continue
                    val sourceReturn = ln(
                        macro.rateToKrw(sourceCurrency) /
                            macro.rateToKrw(sourceCurrency, previous = true),
                    )
                    val targetReturn = ln(
                        macro.rateToKrw(targetCurrency) /
                            macro.rateToKrw(targetCurrency, previous = true),
                    )
                    put(ReferenceCurrencyPair(sourceCurrency, targetCurrency), sourceReturn - targetReturn)
                }
            }
        }
        var alternativeRiskPremiaAdvance: AlternativeRiskPremiaBookAdvance? = null
        var compositeReferenceAdvance: CompositeReferenceBookAdvance? = null
        var structuredReferenceAdvancesResolved = false

        fun resolveStructuredReferenceAdvances() {
            if (structuredReferenceAdvancesResolved) return
            val instrumentReturns = directlyReferencedInstrumentIds.associateWith { stockId ->
                val result = referencedInstrumentPriceLogReturn(stockId)
                require(result.isFinite()) {
                    "복합 기준의 사업회사 기초 수익률이 유한하지 않습니다: $stockId=$result"
                }
                result
            }
            val benchmarkReturns = structuredSourceBenchmarkRefs.mapNotNull { ref ->
                preStructuredBenchmarkGrossLogReturn(ref)?.let { value -> ref to value }
            }.toMap()
            val benchmarkIncomeYields = structuredSourceBenchmarkRefs.mapNotNull { ref ->
                preStructuredBenchmarkAnnualIncomeYield(ref)?.let { value -> ref to value }
            }.toMap()
            val benchmarkDurations = structuredSourceBenchmarkRefs.mapNotNull { ref ->
                preStructuredBenchmarkDurationYears(ref)?.let { value -> ref to value }
            }.toMap()
            val preAlternativeFrame = ReferenceSourceReturnFrame(
                benchmarkLogReturns = benchmarkReturns,
                benchmarkAnnualIncomeYields = benchmarkIncomeYields,
                benchmarkDurationsYears = benchmarkDurations,
                instrumentLogReturns = instrumentReturns,
                instrumentAnnualIncomeYields = instrumentSourceIncomeYields,
                instrumentDurationsYears = instrumentSourceDurations,
                instrumentAvailability = instrumentSourceAvailability,
                fxLogReturns = sourceFxLogReturns,
            )
            if (alternativeRiskPremiaStates.isNotEmpty()) {
                alternativeRiskPremiaAdvance = alternativeRiskPremiaBookEngine.advanceHour(
                    book = AlternativeRiskPremiaBook(alternativeRiskPremiaStates.toMap()),
                    definitions = alternativeRiskPremiaBenchmarkDefinitions,
                    sourceCatalog = referenceSourceCatalog,
                    input = AlternativeRiskPremiaAdvanceInput(
                        sourceFrame = preAlternativeFrame,
                        annualRiskFreeRate = macro.policyRate.coerceIn(-0.25, 1.0),
                    ),
                    from = from,
                    to = to,
                )
            }
            if (compositeReferenceStates.isNotEmpty()) {
                val alternative = alternativeRiskPremiaAdvance
                compositeReferenceAdvance = compositeReferenceBookEngine.advanceHour(
                    book = CompositeReferenceBook(compositeReferenceStates.toMap()),
                    definitions = compositeReferenceBenchmarkDefinitions,
                    sourceCatalog = referenceSourceCatalog,
                    input = CompositeReferenceAdvanceInput(
                        sourceFrame = ReferenceSourceReturnFrame(
                            benchmarkLogReturns = benchmarkReturns +
                                alternative?.referenceLogReturns.orEmpty(),
                            benchmarkAnnualIncomeYields = benchmarkIncomeYields +
                                alternative?.estimatedAnnualIncomeYields.orEmpty(),
                            benchmarkDurationsYears = benchmarkDurations +
                                alternative?.effectiveDurationsYears.orEmpty(),
                            instrumentLogReturns = instrumentReturns,
                            instrumentAnnualIncomeYields = instrumentSourceIncomeYields,
                            instrumentDurationsYears = instrumentSourceDurations,
                            instrumentAvailability = instrumentSourceAvailability,
                            fxLogReturns = sourceFxLogReturns,
                        ),
                        annualRiskFreeRate = macro.policyRate.coerceIn(-0.25, 1.0),
                        mortgageRateAnnual = currentMortgageRateAnnual(
                            fixedIncomeReferenceAdvance?.book?.states?.values
                                ?: fixedIncomeReferenceStates.values,
                        ),
                    ),
                    from = from,
                    to = to,
                )
            }
            structuredReferenceAdvancesResolved = true
        }

        fun explicitBenchmarkGrossLogReturn(ref: BenchmarkRef): Double? =
            compositeReferenceAdvance?.referenceLogReturns?.get(ref)
                ?: alternativeRiskPremiaAdvance?.referenceLogReturns?.get(ref)
                ?: preStructuredBenchmarkGrossLogReturn(ref)

        fun explicitBenchmarkAnnualIncomeYield(ref: BenchmarkRef): Double? =
            compositeReferenceAdvance?.estimatedAnnualIncomeYields?.get(ref)
                ?: alternativeRiskPremiaAdvance?.estimatedAnnualIncomeYields?.get(ref)
                ?: preStructuredBenchmarkAnnualIncomeYield(ref)

        val pricingStocks = stocks.sortedBy { stock ->
            val profile = stock.fundProductProfile
            val hasInstrumentReference = listOfNotNull(
                profile?.dailyResetTerms?.reference,
                profile?.optionStrategyTerms?.reference,
                profile?.cashCollateralizedPutSpreadTerms?.optionReference,
            ).any { it.kind == DailyResetReferenceKind.INSTRUMENT }
            when {
                !stock.isFundLike -> 0
                hasInstrumentReference -> 2
                else -> 1
            }
        }
        for (stock in pricingStocks) {
            if (stock.isFundLike) resolveStructuredReferenceAdvances()
            val previousQuote = quotes.getValue(stock.id)
            val listingState = listingLifecycleStates.getValue(stock.id)
            val matured = isInstrumentMatured(stock, from)
            val fundReferenceFraction = stock.etfProfile?.let { profile ->
                regionalTradingFraction(profile.exposureRegion, from, effectiveMarketFractions)
            }
            val productProfile = stock.fundProductProfile
            val benchmarkGrossLogReturn = productProfile?.benchmarkRef
                ?.let(::explicitBenchmarkGrossLogReturn)
            val benchmarkAnnualIncomeYield = productProfile?.benchmarkRef
                ?.let(::explicitBenchmarkAnnualIncomeYield)
            val basketGrossLogReturn = benchmarkGrossLogReturn?.let { benchmarkReturn ->
                benchmarkReturn + fundProductOverlayEngine.trackingErrorLogReturn(
                    productId = stock.id,
                    profile = requireNotNull(productProfile),
                    from = from,
                    referenceTradingFraction = requireNotNull(fundReferenceFraction),
                )
            }
            val dailyResetTerms = productProfile?.dailyResetTerms
            val dailyResetAdvance = dailyResetTerms
                ?.takeIf { !matured && !listingState.isSettlementPending && !listingState.isTerminal }
                ?.let { terms ->
                    val referenceMarket = dailyResetReferenceMarket(terms.resetCalendar)
                    val referenceFraction = when (terms.reference.kind) {
                        DailyResetReferenceKind.BENCHMARK -> regionalTradingFraction(
                            if (terms.resetCalendar == DailyResetCalendar.KRX_EQUITY) {
                                EtfExposureRegion.KOREA
                            } else {
                                EtfExposureRegion.UNITED_STATES
                            },
                            from,
                            effectiveMarketFractions,
                        )
                        DailyResetReferenceKind.INSTRUMENT -> {
                            val underlyingId = requireNotNull(terms.reference.instrumentId)
                            requireNotNull(stockTradingFractions[underlyingId]) {
                                "일일 reset 기초 종목은 상품보다 먼저 가격이 계산되어야 합니다: $underlyingId"
                            }
                        }
                    }
                    val referenceReturn = when (terms.reference.kind) {
                        DailyResetReferenceKind.BENCHMARK -> {
                            val explicitReturn = requireNotNull(terms.reference.benchmarkRef)
                                .let(::explicitBenchmarkGrossLogReturn)
                            explicitReturn ?: priceEngine.coarseUnderlyingReferenceLogReturn(
                                stock = stock,
                                macro = macro,
                                referenceTradingFraction = referenceFraction,
                            )
                        }
                        DailyResetReferenceKind.INSTRUMENT -> {
                            val underlyingId = requireNotNull(terms.reference.instrumentId)
                            referencedInstrumentPriceLogReturn(underlyingId)
                        }
                    }
                    require(referenceReturn.isFinite()) {
                        "일일 reset 기초 수익률이 유한하지 않습니다: " +
                            "product=${stock.id}, reference=${terms.reference}, value=$referenceReturn, " +
                            "from=$from, to=$to, fraction=$referenceFraction"
                    }
                    dailyResetEngine.advance(
                        DailyResetAdvanceInput(
                            state = dailyResetStates.getValue(stock.id),
                            terms = terms,
                            referenceLogReturn = referenceReturn,
                            elapsedYearFraction = referenceFraction / REFERENCE_TRADING_HOURS_PER_YEAR,
                            cashRateAnnual = macro.policyRate.coerceIn(-0.10, 1.0),
                            shortBorrowRateAnnual = (
                                DAILY_RESET_BASE_SHORT_BORROW_RATE +
                                    macro.liquidityStress * DAILY_RESET_STRESS_BORROW_SPREAD
                                ).coerceIn(0.0, 2.0),
                            productExpenseRateAnnual = requireNotNull(stock.etfProfile).annualExpenseRatio,
                            referenceTradingDate = marketDate(referenceMarket, from),
                            resetAtEnd = reachesDailyResetClose(terms.resetCalendar, from, to),
                            to = to,
                        ),
                    )
                }
            val optionStrategyTerms = productProfile?.optionStrategyTerms
            val optionStrategyAdvance = optionStrategyTerms
                ?.takeIf { !matured && !listingState.isSettlementPending && !listingState.isTerminal }
                ?.let { terms ->
                    val referenceMarket = optionReferenceMarket(terms.rollCalendar)
                    val optionCloseAtEnd = reachesOptionClose(terms.rollCalendar, from, to)
                    val allowOpeningNewCycle = !hasPublishedDirectUnderlyingLiquidation(stock.id)
                    val referenceFraction = when (terms.reference.kind) {
                        DailyResetReferenceKind.BENCHMARK -> regionalTradingFraction(
                            if (terms.rollCalendar == OptionRollCalendar.KRX_EQUITY) {
                                EtfExposureRegion.KOREA
                            } else {
                                EtfExposureRegion.UNITED_STATES
                            },
                            from,
                            effectiveMarketFractions,
                        )
                        DailyResetReferenceKind.INSTRUMENT -> {
                            val underlyingId = requireNotNull(terms.reference.instrumentId)
                            requireNotNull(stockTradingFractions[underlyingId]) {
                                "옵션 전략 기초 종목은 상품보다 먼저 가격이 계산되어야 합니다: $underlyingId"
                            }
                        }
                    }
                    val referencePriceReturn = when (terms.reference.kind) {
                        DailyResetReferenceKind.BENCHMARK -> {
                            val ref = requireNotNull(terms.reference.benchmarkRef)
                            explicitBenchmarkGrossLogReturn(ref)
                                ?: priceEngine.coarseUnderlyingReferenceLogReturn(
                                    stock = stock,
                                    macro = macro,
                                    referenceTradingFraction = referenceFraction,
                                )
                        }
                        DailyResetReferenceKind.INSTRUMENT -> {
                            val underlyingId = requireNotNull(terms.reference.instrumentId)
                            referencedInstrumentPriceLogReturn(underlyingId)
                        }
                    }
                    val annualIncomeYield = terms.reference.benchmarkRef
                        ?.let(::explicitBenchmarkAnnualIncomeYield) ?: 0.0
                    optionStrategyEngine.advance(
                        OptionStrategyAdvanceInput(
                            state = optionStrategyStates.getValue(stock.id),
                            terms = terms,
                            underlyingTotalLogReturn = referencePriceReturn +
                                annualIncomeYield / REFERENCE_TRADING_HOURS_PER_YEAR * referenceFraction,
                            cashRateAnnual = macro.policyRate.coerceIn(-0.10, 1.0),
                            annualizedImpliedVolatility = optionImpliedVolatility(stock),
                            elapsedYearFraction = ((to - from).inWholeNanoseconds.toDouble() /
                                NANOSECONDS_PER_YEAR).coerceIn(0.0, 1.0),
                            referenceTradingDate = marketDate(referenceMarket, from),
                            tradingCloseAtEnd = optionCloseAtEnd,
                            forceRollAtEnd = !allowOpeningNewCycle && optionCloseAtEnd,
                            allowOpeningNewCycle = allowOpeningNewCycle,
                            to = to,
                        ),
                    )
                }
            val optionProductLogReturn = optionStrategyAdvance?.productLogReturn
                ?.takeIf { stock.instrumentType != InstrumentType.ETN }
                ?.minus(
                    requireNotNull(stock.etfProfile).annualExpenseRatio /
                        REFERENCE_TRADING_HOURS_PER_YEAR * requireNotNull(fundReferenceFraction),
                )
            val elapsedWallYearFraction = ((to - from).inWholeNanoseconds.toDouble() /
                NANOSECONDS_PER_YEAR).coerceIn(0.0, 1.0)
            val cashCollateralizedPutSpreadTerms =
                productProfile?.cashCollateralizedPutSpreadTerms
            val cashCollateralizedPutSpreadAdvance = cashCollateralizedPutSpreadTerms
                ?.takeIf { !matured && !listingState.isSettlementPending && !listingState.isTerminal }
                ?.let { terms ->
                    val referenceMarket = optionReferenceMarket(terms.rollCalendar)
                    val optionCloseAtEnd = reachesOptionClose(terms.rollCalendar, from, to)
                    val allowOpeningNewCycle = !hasPublishedDirectUnderlyingLiquidation(stock.id)
                    val referenceFraction = regionalTradingFraction(
                        if (terms.rollCalendar == OptionRollCalendar.KRX_EQUITY) {
                            EtfExposureRegion.KOREA
                        } else {
                            EtfExposureRegion.UNITED_STATES
                        },
                        from,
                        effectiveMarketFractions,
                    )
                    val cashPriceReturn = requireNotNull(
                        explicitBenchmarkGrossLogReturn(terms.cashBenchmarkRef),
                    ) { "현금담보 풋스프레드의 현금 benchmark 수익률이 필요합니다: ${stock.id}" }
                    val cashIncomeYield = requireNotNull(
                        explicitBenchmarkAnnualIncomeYield(terms.cashBenchmarkRef),
                    ) { "현금담보 풋스프레드의 현금 benchmark 소득률이 필요합니다: ${stock.id}" }
                    val optionPriceReturn = when (terms.optionReference.kind) {
                        DailyResetReferenceKind.BENCHMARK -> {
                            val ref = requireNotNull(terms.optionReference.benchmarkRef)
                            requireNotNull(explicitBenchmarkGrossLogReturn(ref)) {
                                "현금담보 풋스프레드의 옵션 기초 benchmark 수익률이 필요합니다: ${stock.id}"
                            }
                        }
                        DailyResetReferenceKind.INSTRUMENT -> {
                            val underlyingId = requireNotNull(terms.optionReference.instrumentId)
                            referencedInstrumentPriceLogReturn(underlyingId)
                        }
                    }
                    val optionIncomeYield = terms.optionReference.benchmarkRef
                        ?.let(::explicitBenchmarkAnnualIncomeYield) ?: 0.0
                    cashCollateralizedPutSpreadEngine.advance(
                        CashCollateralizedPutSpreadAdvanceInput(
                            state = cashCollateralizedPutSpreadStates.getValue(stock.id),
                            terms = terms,
                            cashBenchmarkTotalLogReturn = cashPriceReturn +
                                cashIncomeYield / REFERENCE_TRADING_HOURS_PER_YEAR * referenceFraction,
                            optionUnderlyingTotalLogReturn = optionPriceReturn +
                                optionIncomeYield / REFERENCE_TRADING_HOURS_PER_YEAR * referenceFraction,
                            optionDiscountRateAnnual = macro.policyRate.coerceIn(-0.10, 1.0),
                            annualizedImpliedVolatility = optionImpliedVolatility(stock),
                            elapsedYearFraction = elapsedWallYearFraction,
                            referenceTradingDate = marketDate(referenceMarket, from),
                            tradingCloseAtEnd = optionCloseAtEnd,
                            forceRollAtEnd = !allowOpeningNewCycle && optionCloseAtEnd,
                            allowOpeningNewCycle = allowOpeningNewCycle,
                            to = to,
                        ),
                    )
                }
            val cashCollateralizedPutSpreadProductLogReturn =
                cashCollateralizedPutSpreadAdvance?.productLogReturn?.minus(
                    requireNotNull(stock.etfProfile).annualExpenseRatio /
                        REFERENCE_TRADING_HOURS_PER_YEAR * requireNotNull(fundReferenceFraction),
                )
            val etnAdvance = productProfile?.etnProductTerms
                ?.takeIf {
                    !matured && !listingState.isSettlementPending && !listingState.isTerminal &&
                        etnStates.getValue(stock.id).lifecycle == EtnLifecycle.ACTIVE
                }
                ?.let { terms ->
                    val previous = etnStates.getValue(stock.id)
                    val effectiveDate = marketDate(stock.market, to)
                    val venueCloseReached = reachesMarketClose(stock.market, from, to)
                    val contractualSettlementDate = nextTradingDateOnOrAfter(
                        stock.market,
                        terms.maturityDate,
                    )
                    val contractualMaturityReached = venueCloseReached &&
                        effectiveDate == contractualSettlementDate
                    val dueTerminationTerms = if (venueCloseReached) {
                        resolveInstrumentTerminationAtSessionClose(
                            stock = stock,
                            events = newsEvents,
                            evaluatedOn = effectiveDate,
                            incumbentOccurrenceId =
                                listingState.controllingTerminationOccurrenceId,
                        )?.takeIf { decision ->
                            decision.scheduledTerminationOn == effectiveDate
                        }?.notice?.terms
                    } else {
                        null
                    }
                    val contractEvent = if (contractualMaturityReached) {
                        EtnCreditEvent.CONTRACTUAL_MATURITY
                    } else {
                        when (dueTerminationTerms?.kind) {
                            null -> EtnCreditEvent.NONE
                            InstrumentTerminationKind.CONTRACTUAL_MATURITY ->
                                EtnCreditEvent.CONTRACTUAL_MATURITY
                            InstrumentTerminationKind.CREDIT_DEFAULT ->
                                EtnCreditEvent.CREDIT_DEFAULT
                            InstrumentTerminationKind.ISSUER_ACCELERATION ->
                                EtnCreditEvent.ISSUER_ACCELERATION
                            InstrumentTerminationKind.OPTIONAL_CALL ->
                                EtnCreditEvent.ISSUER_CALL
                            InstrumentTerminationKind.FUND_LIQUIDATION ->
                                error("ETN에는 펀드 청산 계약 이벤트를 적용할 수 없습니다: ${stock.id}")
                        }
                    }
                    val creditParameters = requireNotNull(productProfile.etnIssuerCreditModelParameters)
                    val (spreadShock, hazardShock) = etnCreditShocks(
                        stock = stock,
                        previous = previous,
                        parameters = creditParameters,
                        elapsedYearFraction = elapsedWallYearFraction,
                        from = from,
                    )
                    EtnEngine(terms).advance(
                        state = previous,
                        input = EtnAdvanceInput(
                            effectiveAt = to,
                            effectiveDate = effectiveDate,
                            elapsedYearFraction = elapsedWallYearFraction,
                            referenceLogReturn = optionStrategyAdvance?.productLogReturn
                                ?: requireNotNull(benchmarkGrossLogReturn) {
                                    "ETN에는 실행 가능한 옵션 또는 벤치마크 기준수익률이 필요합니다: ${stock.id}"
                                },
                            referenceCouponAccrualPerNote =
                                optionStrategyAdvance?.grossPremiumReceived ?: 0.0,
                            issuerCreditSpreadShock = spreadShock,
                            issuerHazardRateShock = hazardShock,
                            contractEvent = contractEvent,
                            contractSettlementNotes = if (contractEvent != EtnCreditEvent.NONE) {
                                previous.notesOutstanding
                            } else {
                                0L
                            },
                            contractualSettlementDeadlineReached = contractualMaturityReached,
                            recordIndicativeValueObservation = venueCloseReached,
                            creditEventRecoveryRate = dueTerminationTerms
                                ?.accelerationRecoveryRate
                                ?.takeIf { contractEvent == EtnCreditEvent.CREDIT_DEFAULT },
                        ),
                    )
                }
            val etnProductLogReturn = etnAdvance?.let { advance ->
                val previous = etnStates.getValue(stock.id)
                val terms = requireNotNull(productProfile.etnProductTerms)
                val previousMarkedValue = etnCreditMarkedValue(
                    state = previous,
                    maturityDate = terms.maturityDate,
                    valuationDate = marketDate(stock.market, from),
                )
                val nextMarkedValue = advance.ledgerEntries
                    .lastOrNull { entry ->
                        entry.kind == EtnLedgerKind.CONTRACT_SETTLEMENT &&
                            entry.notesSettled > 0L
                    }
                    ?.let { entry ->
                        entry.cashPaidToNoteholders / entry.notesSettled.toDouble()
                    }
                    ?: etnCreditMarkedValue(
                        state = advance.state,
                        maturityDate = terms.maturityDate,
                        valuationDate = marketDate(stock.market, to),
                    )
                ln(nextMarkedValue.coerceAtLeast(ETN_MIN_MARKED_VALUE) / previousMarkedValue)
            }
            val closedEndFundAdvance = productProfile?.closedEndFundTerms
                ?.takeIf { !matured && !listingState.isSettlementPending && !listingState.isTerminal }
                ?.let { terms ->
                    val previous = closedEndFundStates.getValue(stock.id)
                    val parameters = requireNotNull(productProfile.closedEndFundMarketModelParameters)
                    val referenceFraction = requireNotNull(fundReferenceFraction)
                    val assetPriceLogReturn = benchmarkGrossLogReturn
                        ?: priceEngine.coarseUnderlyingReferenceLogReturn(
                            stock = stock,
                            macro = macro,
                            referenceTradingFraction = referenceFraction,
                        )
                    val annualIncomeYield = benchmarkAnnualIncomeYield ?: stock.dividendYield
                    val incomeLogReturn = annualIncomeYield /
                        REFERENCE_TRADING_HOURS_PER_YEAR * referenceFraction
                    val grossInvestmentIncome = previous.grossAssets * incomeLogReturn
                    ClosedEndFundEngine(terms, parameters).advance(
                        state = previous,
                        input = ClosedEndFundAdvanceInput(
                            effectiveAt = to,
                            elapsedYearFraction = elapsedWallYearFraction,
                            assetTotalLogReturn = assetPriceLogReturn + incomeLogReturn,
                            grossInvestmentIncome = grossInvestmentIncome,
                            annualBorrowingRate = (
                                macro.policyRate + parameters.annualBorrowingSpread
                                ).coerceIn(0.0, 100.0),
                            annualPreferredDistributionRate = (
                                macro.policyRate + parameters.annualPreferredDistributionSpread
                                ).coerceIn(0.0, 100.0),
                            operatingExpenses = previous.grossAssets *
                                requireNotNull(stock.etfProfile).annualExpenseRatio *
                                elapsedWallYearFraction,
                            realizedGainReserveChange = 0.0,
                            marketDiscountShock = closedEndFundDiscountShock(
                                stock = stock,
                                state = previous,
                                elapsedYearFraction = elapsedWallYearFraction,
                                from = from,
                                target = parameters.targetMarketDiscountRate,
                                meanReversion = parameters.annualDiscountMeanReversionRate,
                                annualVolatility = parameters.discountShockAnnualVolatility,
                            ),
                        ),
                    )
                }
            val closedEndFundProductLogReturn = closedEndFundAdvance?.let { advance ->
                ln(
                    advance.state.marketPricePerCommonShare /
                        closedEndFundStates.getValue(stock.id).marketPricePerCommonShare,
                )
            }
            val productFairValueLogReturn = dailyResetAdvance?.productLogReturn
                ?: etnProductLogReturn
                ?: closedEndFundProductLogReturn
                ?: cashCollateralizedPutSpreadProductLogReturn
                ?: optionProductLogReturn
            if (commit && dailyResetAdvance != null) {
                dailyResetStates[stock.id] = dailyResetAdvance.state
            }
            if (commit && optionStrategyAdvance != null) {
                optionStrategyStates[stock.id] = optionStrategyAdvance.state
            }
            if (commit && cashCollateralizedPutSpreadAdvance != null) {
                cashCollateralizedPutSpreadStates[stock.id] =
                    cashCollateralizedPutSpreadAdvance.state
            }
            if (commit && etnAdvance != null) {
                val previous = etnStates.getValue(stock.id)
                require(etnAdvance.previousRevision == previous.revision)
                require(etnAdvance.ledgerEntries.none { next ->
                    etnLedger.any { existing -> existing.id == next.id }
                })
                etnStates[stock.id] = etnAdvance.state
                etnLedger += etnAdvance.ledgerEntries
            }
            if (commit && closedEndFundAdvance != null) {
                val previous = closedEndFundStates.getValue(stock.id)
                require(closedEndFundAdvance.previousRevision == previous.revision)
                require(closedEndFundAdvance.ledgerEntries.none { next ->
                    closedEndFundLedger.any { existing -> existing.id == next.id }
                })
                closedEndFundStates[stock.id] = closedEndFundAdvance.state
                closedEndFundLedger += closedEndFundAdvance.ledgerEntries
            }
            if (matured || !listingState.isTradable) {
                stockTradingFractions[stock.id] = 0.0
                if (commit && !matured && !listingState.isSettlementPending && !listingState.isTerminal) {
                    val haltedImpulse = EventShockCalculator.aggregate(turnEvents, stock, from, to)
                    val haltedReferenceReturn = if (stock.isFundLike) {
                        priceEngine.referenceLogReturn(
                            stock = stock,
                            macro = macro,
                            referenceTradingFraction = requireNotNull(fundReferenceFraction),
                            fxTradingFraction = 1.0,
                            basketGrossLogReturn = productFairValueLogReturn ?: basketGrossLogReturn,
                        ) + priceEngine.referenceEventLogReturn(stock, haltedImpulse) +
                            if (benchmarkGrossLogReturn == null && productFairValueLogReturn == null) {
                                priceEngine.referenceResidualLogReturn(
                                    stock = stock,
                                    startTime = from,
                                    macro = macro,
                                    eventImpulse = haltedImpulse,
                                    tradingFraction = effectiveMarketFractions.getValue(stock.market),
                                )
                            } else {
                                0.0
                            } +
                            if (productFairValueLogReturn == null) {
                                priceEngine.fundAccrualLogReturn(
                                    stock,
                                    ordinaryTradingFractions.getValue(stock.market),
                                    benchmarkAnnualIncomeYield,
                                )
                            } else {
                                0.0
                            }
                    } else {
                        priceEngine.referenceEventLogReturn(stock, haltedImpulse)
                    }
                    val haltedDirectReturn = priceEngine.directProductEventLogReturn(haltedImpulse)
                    if (stock.isFundLike && haltedReferenceReturn != 0.0) {
                        pendingEtfReferenceReturns[stock.id] = (
                            (pendingEtfReferenceReturns[stock.id] ?: 0.0) + haltedReferenceReturn
                            ).coerceIn(-2.5, 2.5)
                    }
                    val haltedPriceReturn = haltedDirectReturn +
                        if (stock.isFundLike) 0.0 else haltedReferenceReturn
                    if (haltedPriceReturn != 0.0) {
                        pendingClosedEventLogReturns[stock.id] = (
                            (pendingClosedEventLogReturns[stock.id] ?: 0.0) + haltedPriceReturn
                            ).coerceIn(-2.5, 2.5)
                    }
                }
                val flatBar = PriceBar(
                    stockId = stock.id,
                    startTime = from,
                    endTime = to,
                    step = PriceBarInterval.ONE_HOUR,
                    open = previousQuote.price,
                    high = previousQuote.price,
                    low = previousQuote.price,
                    close = previousQuote.price,
                    volume = 0L,
                )
                generatedBars[stock.id] = flatBar
                if (commit) {
                    quotes[stock.id] = previousQuote.copy(
                        timestamp = to,
                        volume = 0L,
                        bidPrice = null,
                        askPrice = null,
                        bidQuantity = 0.0,
                        askQuantity = 0.0,
                        session = MarketSession.CLOSED,
                    )
                    appendHistory(stock.id, flatBar)
                }
                continue
            }

            val tracker = if (commit) {
                trackerFor(stock, from, previousQuote.price)
            } else {
                dailyTrackerSnapshot(stock, from, previousQuote.price)
            }
            val tradingIntervals = instrumentProtectionTradingIntervals(
                stock = stock,
                from = from,
                to = to,
                additionalBlocks = additionalInstrumentBlocks[stock.id].orEmpty(),
            )
            val fraction = runtimeTradingFraction(from, to, tradingIntervals)
            stockTradingFractions[stock.id] = fraction
            tradingIntervals.firstOrNull()?.startsAt?.let { stockFirstExecutionTimes[stock.id] = it }
            val session = if (fraction > 0.0) MarketSession.REGULAR else marketSession(stock.market, from)
            val impulse = EventShockCalculator.aggregate(turnEvents, stock, from, to)
            val ordinaryFraction = ordinaryTradingFractions.getValue(stock.market)
            val fairValueFraction = if (
                stock.market.isUnitedStates &&
                (macro.usCircuitBreakerLevel in 1..2 || stock.market in temporaryProtectionMarkets)
            ) {
                ordinaryFraction
            } else {
                fraction
            }
            val totalReferenceFraction = fundReferenceFraction ?: fraction
            val activeReferenceFraction = minOf(totalReferenceFraction, fairValueFraction)
            val closedReferenceFraction = (totalReferenceFraction - activeReferenceFraction).coerceIn(0.0, 1.0)
            val managedReferenceLogReturn = productFairValueLogReturn ?: basketGrossLogReturn
            val activeManagedReturn = managedReferenceLogReturn?.let { totalReturn ->
                if (
                    optionProductLogReturn != null ||
                        cashCollateralizedPutSpreadProductLogReturn != null ||
                        etnProductLogReturn != null ||
                        closedEndFundProductLogReturn != null
                ) {
                    totalReturn * fairValueFraction
                } else if (totalReferenceFraction == 0.0) {
                    // Explicit reference engines may still accrue financing, borrow, FX or
                    // implementation costs while the underlying exchange is closed.
                    totalReturn * fairValueFraction
                } else {
                    totalReturn * activeReferenceFraction / totalReferenceFraction
                }
            }
            val closedManagedReturn = managedReferenceLogReturn?.let { totalReturn ->
                if (
                    optionProductLogReturn != null ||
                        cashCollateralizedPutSpreadProductLogReturn != null ||
                        etnProductLogReturn != null ||
                        closedEndFundProductLogReturn != null
                ) {
                    totalReturn * (1.0 - fairValueFraction)
                } else if (totalReferenceFraction == 0.0) {
                    totalReturn * (1.0 - fairValueFraction)
                } else {
                    totalReturn * closedReferenceFraction / totalReferenceFraction
                }
            }
            val closedFxFraction = (1.0 - fairValueFraction).coerceIn(0.0, 1.0)
            val previousCarry = if (fraction > 0.0) pendingEtfReferenceReturns[stock.id] ?: 0.0 else 0.0
            val previousEventCarry = if (fraction > 0.0) pendingClosedEventLogReturns[stock.id] ?: 0.0 else 0.0
            if (commit && fraction > 0.0) {
                pendingEtfReferenceReturns.remove(stock.id)
                pendingClosedEventLogReturns.remove(stock.id)
            }
            val closedFairValueReturn = if (
                stock.isFundLike && (closedReferenceFraction > 0.0 || closedFxFraction > 0.0)
            ) {
                priceEngine.referenceLogReturn(
                    stock = stock,
                    macro = macro,
                    referenceTradingFraction = closedReferenceFraction,
                    fxTradingFraction = closedFxFraction,
                    basketGrossLogReturn = closedManagedReturn,
                )
            } else {
                0.0
            }
            val closedEventFraction = (1.0 - fairValueFraction).coerceIn(0.0, 1.0)
            val closedReferenceEventReturn = if (closedEventFraction > 0.0) {
                priceEngine.referenceEventLogReturn(
                    stock = stock,
                    eventImpulse = impulse,
                    referenceFraction = closedEventFraction,
                )
            } else {
                0.0
            }
            val closedDirectProductEventReturn = if (closedEventFraction > 0.0) {
                priceEngine.directProductEventLogReturn(
                    eventImpulse = impulse,
                    fairValueFraction = closedEventFraction,
                )
            } else {
                0.0
            }
            val closedFundAccrualReturn = if (stock.isFundLike && productFairValueLogReturn == null) {
                priceEngine.fundAccrualLogReturn(
                    stock,
                    (ordinaryFraction - fairValueFraction).coerceIn(0.0, 1.0),
                    benchmarkAnnualIncomeYield,
                )
            } else {
                0.0
            }
            val closedReferenceCarry = if (stock.isFundLike) {
                closedFairValueReturn + closedReferenceEventReturn + closedFundAccrualReturn
            } else {
                0.0
            }
            val closedPriceCarry = closedDirectProductEventReturn +
                if (stock.isFundLike) 0.0 else closedReferenceEventReturn
            val hasLeadingClosedComplement = fraction > 0.0 &&
                tradingIntervals.firstOrNull()?.startsAt?.let { it > from } == true
            val carriedReference = previousCarry + if (hasLeadingClosedComplement) closedReferenceCarry else 0.0
            val carriedPriceDislocation = previousEventCarry +
                if (hasLeadingClosedComplement) closedPriceCarry else 0.0
            if (commit && !hasLeadingClosedComplement && closedReferenceCarry != 0.0) {
                pendingEtfReferenceReturns[stock.id] =
                    ((pendingEtfReferenceReturns[stock.id] ?: 0.0) + closedReferenceCarry)
                        .coerceIn(-2.5, 2.5)
            }
            if (commit && !hasLeadingClosedComplement && closedPriceCarry != 0.0) {
                pendingClosedEventLogReturns[stock.id] = (
                    (pendingClosedEventLogReturns[stock.id] ?: 0.0) + closedPriceCarry
                    ).coerceIn(-2.5, 2.5)
            }
            val firstRegularBar = fraction > 0.0 && !tracker.hasRegularTrading
            val currentReferenceValue = fundFinancialStates[stock.id]?.navPerUnit
                ?: etnStates[stock.id]?.let { state ->
                    val terms = requireNotNull(stock.fundProductProfile?.etnProductTerms)
                    etnCreditMarkedValue(
                        state = state,
                        maturityDate = terms.maturityDate,
                        valuationDate = marketDate(stock.market, from),
                    )
                }
                ?: closedEndFundStates[stock.id]?.marketPricePerCommonShare
            val priceToReferenceLogGap = currentReferenceValue?.let { referenceValue ->
                ln(previousQuote.price / referenceValue)
            } ?: 0.0
            val result = priceEngine.generateHour(
                PriceGenerationInput(
                    stock = stock,
                    startTime = from,
                    previousPrice = previousQuote.price,
                    dailyBasePrice = tracker.basePrice,
                    session = session,
                    macro = macro,
                    eventImpulse = impulse,
                    dayOpen = tracker.open,
                    dayHigh = tracker.high,
                    dayLow = tracker.low,
                    regularTradingFraction = fraction,
                    fairValueTradingFraction = fairValueFraction,
                    referenceTradingFraction = activeReferenceFraction,
                    basketGrossLogReturn = activeManagedReturn.takeIf { productFairValueLogReturn == null },
                    productFairValueLogReturn = activeManagedReturn.takeIf { productFairValueLogReturn != null },
                    basketAnnualIncomeYield = benchmarkAnnualIncomeYield,
                    carriedReferenceLogReturn = carriedReference,
                    carriedPriceDislocationLogReturn = carriedPriceDislocation,
                    priceToReferenceLogGap = priceToReferenceLogGap,
                    isFirstRegularBarOfDay = firstRegularBar,
                ),
            )
            priceAttributions[stock.id] = result.attribution
            // VI/LULD bounds constrain executable quotations. Applying them to a zero-fraction
            // closed-session fair-value bar distorts an indicative value even though no trade can
            // occur (notably sub-cent ETN values), so only clamp an actually executable interval.
            val effectiveBounds = if (commit && fraction > 0.0) {
                effectiveProtectionPriceBounds(stock, priceBounds[stock.id])
            } else {
                null
            }
            val bar = effectiveBounds?.let { runtimeClampBarToBounds(stock, result.bar, it) } ?: result.bar
            generatedBars[stock.id] = bar
            if (commit) {
                val baseQuote = if (bar == result.bar) {
                    result.quote
                } else {
                    result.quote.copy(
                        price = bar.close,
                        volume = bar.volume,
                    )
                }
                if (firstRegularBar) {
                    tracker.open = bar.open
                    tracker.hasRegularTrading = true
                }
                // Product fair value can move while the listing is closed. The persisted quote,
                // bar and next hour's PriceGenerationInput must still share one containing daily
                // range; only the executed-session open flag is conditional on actual trading.
                tracker.high = maxOf(
                    tracker.high,
                    tracker.open,
                    bar.high,
                    baseQuote.price,
                )
                tracker.low = minOf(
                    tracker.low,
                    tracker.open,
                    bar.low,
                    baseQuote.price,
                )
                val quote = baseQuote.copy(
                    price = bar.close,
                    open = tracker.open,
                    high = tracker.high,
                    low = tracker.low,
                    volume = bar.volume,
                )
                quotes[stock.id] = quote
                appendHistory(stock.id, bar)
            }
        }
        if (commit && referencePortfolioAdvance != null) {
            commitReferencePortfolioAdvance(referencePortfolioAdvance)
        }
        if (commit && equityReferenceAdvance != null) {
            commitEquityReferenceAdvance(equityReferenceAdvance)
        }
        if (commit && fundOfFundsAdvance != null) {
            commitFundOfFundsAdvance(fundOfFundsAdvance)
        }
        if (commit && alternativeRiskPremiaAdvance != null) {
            commitAlternativeRiskPremiaAdvance(alternativeRiskPremiaAdvance)
        }
        if (commit && compositeReferenceAdvance != null) {
            commitCompositeReferenceAdvance(compositeReferenceAdvance)
        }
        if (commit && fixedIncomeReferenceAdvance != null) {
            commitFixedIncomeReferenceAdvance(fixedIncomeReferenceAdvance)
        }
        if (commit && commodityReferenceAdvance != null) {
            commitCommodityReferenceAdvance(commodityReferenceAdvance)
        }
        return TurnGenerationResult(
            bars = generatedBars,
            stockTradingFractions = stockTradingFractions,
            stockFirstExecutionTimes = stockFirstExecutionTimes,
            priceAttributions = priceAttributions,
        )
    }

    private fun commitReferencePortfolioAdvance(advance: ReferencePortfolioBookAdvance) {
        require(advance.book.states.keys == referencePortfolioStates.keys)
        val recordsByPortfolioId = advance.records.associateBy(ReferencePortfolioRecord::portfolioId)
        for ((portfolioId, next) in advance.book.states) {
            val previous = referencePortfolioStates.getValue(portfolioId)
            val record = recordsByPortfolioId[portfolioId]
            require(next.asOf >= previous.asOf) { "기준 포트폴리오 시각은 뒤로 이동할 수 없습니다." }
            if (record == null) {
                require(next.revision == previous.revision) {
                    "재조정 원장 없이 기준 포트폴리오 revision을 변경할 수 없습니다."
                }
            } else {
                require(next.revision == previous.revision + 1L && record.revision == next.revision) {
                    "기준 포트폴리오 revision과 재조정 원장이 연속되지 않습니다."
                }
                require(
                    record.portfolioId == next.portfolioId &&
                        record.benchmarkRef == next.benchmarkRef &&
                        record.effectiveDate == next.lastRebalanceDate &&
                        record.resultingConstituentCount == next.positions.size &&
                        abs(record.turnoverRate - next.lastTurnoverRate) <=
                        ReferencePortfolioState.WEIGHT_EPSILON,
                ) { "기준 포트폴리오 상태와 재조정 원장의 계보가 일치하지 않습니다." }
                require(referencePortfolioLedger.none { it.id == record.id }) {
                    "같은 기준 포트폴리오 재조정 원장을 두 번 기록할 수 없습니다."
                }
            }
        }
        referencePortfolioStates.clear()
        referencePortfolioStates.putAll(advance.book.states)
        referencePortfolioLedger += advance.records
    }

    private fun commitEquityReferenceAdvance(advance: EquityReferenceBookAdvance) {
        require(advance.book.states.keys == equityReferenceStates.keys)
        val recordsByRef = advance.rebalanceRecords.associateBy(EquityReferenceRebalanceRecord::benchmarkRef)
        require(recordsByRef.size == advance.rebalanceRecords.size) {
            "같은 시간에 하나의 일반 주식 benchmark를 두 번 재조정할 수 없습니다."
        }
        for ((benchmarkRef, next) in advance.book.states) {
            val previous = equityReferenceStates.getValue(benchmarkRef)
            val record = recordsByRef[benchmarkRef]
            require(next.asOf == advance.book.asOf && next.asOf > previous.asOf) {
                "일반 주식 기준 바스켓 시각은 매 tick 앞으로 이동해야 합니다."
            }
            if (record == null) {
                require(next.revision == previous.revision) {
                    "재조정 원장 없이 일반 주식 기준 revision을 변경할 수 없습니다."
                }
            } else {
                val actionDate = when (record.kind) {
                    com.amond.kmpbook.domain.model.reference.EquityReferenceActionKind.RECONSTITUTION ->
                        next.lastSelectionDate
                    com.amond.kmpbook.domain.model.reference.EquityReferenceActionKind.REWEIGHT ->
                        next.lastReweightDate
                }
                require(
                    next.revision == previous.revision + 1L &&
                        record.revision == next.revision &&
                        record.benchmarkRef == benchmarkRef &&
                        record.selectionDate == actionDate &&
                        record.effectiveAt == next.asOf &&
                        record.compositionHashAfter == next.compositionHash &&
                        record.resultingPositionCount == next.positions.size &&
                        record.representedConstituentCount ==
                        next.positions.sumOf { position -> position.representedConstituentCount },
                ) { "일반 주식 기준 상태와 재조정 원장의 계보가 일치하지 않습니다." }
                require(equityReferenceLedger.none { existing -> existing.id == record.id }) {
                    "같은 일반 주식 기준 재조정 원장을 두 번 기록할 수 없습니다."
                }
            }
        }
        equityReferenceStates.clear()
        equityReferenceStates.putAll(advance.book.states)
        equityReferenceLedger += advance.rebalanceRecords
    }

    private fun commitFundOfFundsAdvance(advance: FundOfFundsBookAdvance) {
        require(advance.book.states.keys == fundOfFundsStates.keys)
        val recordsByRef = advance.rebalanceRecords.associateBy(FundOfFundsRebalanceRecord::benchmarkRef)
        require(recordsByRef.size == advance.rebalanceRecords.size) {
            "같은 시간에 하나의 펀드오브펀드 benchmark를 두 번 재조정할 수 없습니다."
        }
        for ((benchmarkRef, next) in advance.book.states) {
            val previous = fundOfFundsStates.getValue(benchmarkRef)
            val record = recordsByRef[benchmarkRef]
            require(next.asOf == advance.book.asOf && next.asOf > previous.asOf) {
                "펀드오브펀드 기준 바스켓 시각은 매 tick 앞으로 이동해야 합니다."
            }
            require(next.positions.all { position ->
                fundOfFundsBookEngine.hasCanonicalCandidate(next.universe, position)
            }) { "펀드오브펀드 상태에 canonical 후보군 밖의 종목이 포함되었습니다." }
            if (record == null) {
                require(next.revision == previous.revision) {
                    "재조정 원장 없이 펀드오브펀드 revision을 변경할 수 없습니다."
                }
            } else {
                val actionDate = when (record.kind) {
                    com.amond.kmpbook.domain.model.reference.FundOfFundsActionKind.RECONSTITUTION ->
                        next.lastSelectionDate
                    com.amond.kmpbook.domain.model.reference.FundOfFundsActionKind.REWEIGHT ->
                        next.lastReweightDate
                }
                require(
                    next.revision == previous.revision + 1L &&
                        record.revision == next.revision &&
                        record.benchmarkRef == benchmarkRef &&
                        record.effectiveDate == actionDate &&
                        record.effectiveAt == next.asOf &&
                        record.compositionHashAfter == next.compositionHash &&
                        record.resultingFundCount == next.positions.size,
                ) { "펀드오브펀드 상태와 재조정 원장의 계보가 일치하지 않습니다." }
                require(fundOfFundsRebalanceLedger.none { existing -> existing.id == record.id }) {
                    "같은 펀드오브펀드 재조정 원장을 두 번 기록할 수 없습니다."
                }
            }
        }
        fundOfFundsStates.clear()
        fundOfFundsStates.putAll(advance.book.states)
        fundOfFundsRebalanceLedger += advance.rebalanceRecords
    }

    private fun reconcileStructuredSourceAvailability(at: Instant) {
        if (alternativeRiskPremiaStates.isNotEmpty()) {
            commitAlternativeRiskPremiaAdvance(
                advance = alternativeRiskPremiaBookEngine.reconcileAvailability(
                    book = AlternativeRiskPremiaBook(alternativeRiskPremiaStates.toMap()),
                    definitions = alternativeRiskPremiaBenchmarkDefinitions,
                    sourceSnapshot = currentReferenceSourceSnapshot(),
                    at = at,
                ),
                sameAsOfReconciliation = true,
            )
        }
        if (compositeReferenceStates.isNotEmpty()) {
            // ALT가 같은 시각에 직접 기초종목을 현금으로 치환했다면 그 결과의 소득률·듀레이션을
            // 다시 읽어 ALT를 기초로 쓰는 복합 기준에도 한 tick 지연 없이 전파한다.
            commitCompositeReferenceAdvance(
                advance = compositeReferenceBookEngine.reconcileAvailability(
                    book = CompositeReferenceBook(compositeReferenceStates.toMap()),
                    definitions = compositeReferenceBenchmarkDefinitions,
                    sourceSnapshot = currentReferenceSourceSnapshot(),
                    at = at,
                ),
                sameAsOfReconciliation = true,
            )
        }
    }

    private fun commitAlternativeRiskPremiaAdvance(
        advance: AlternativeRiskPremiaBookAdvance,
        sameAsOfReconciliation: Boolean = false,
    ) {
        require(advance.book.states.keys == alternativeRiskPremiaStates.keys)
        val recordsByRef = advance.rebalanceRecords.associateBy(
            AlternativeRiskPremiaRebalanceRecord::benchmarkRef,
        )
        require(recordsByRef.size == advance.rebalanceRecords.size) {
            "같은 시간에 하나의 대체위험 프리미엄 기준을 두 번 재조정할 수 없습니다."
        }
        for ((benchmarkRef, next) in advance.book.states) {
            val previous = alternativeRiskPremiaStates.getValue(benchmarkRef)
            val record = recordsByRef[benchmarkRef]
            require(
                next.asOf == advance.book.asOf &&
                    if (sameAsOfReconciliation) next.asOf == previous.asOf else next.asOf > previous.asOf,
            )
            if (sameAsOfReconciliation && record != null) {
                require(record.kind == AlternativeRiskPremiaActionKind.EXTRAORDINARY_SOURCE_TO_CASH)
            }
            if (record == null) {
                require(next.revision == previous.revision &&
                    next.compositionHash == previous.compositionHash
                ) { "원장 없이 대체위험 프리미엄 목표 노출을 변경할 수 없습니다." }
            } else {
                val definition = requireNotNull(instrumentCatalog.findBenchmark(benchmarkRef))
                val expectedActionDate = when (record.kind) {
                    AlternativeRiskPremiaActionKind.REWEIGHT -> next.lastReweightDate
                    AlternativeRiskPremiaActionKind.EXTRAORDINARY_SOURCE_TO_CASH ->
                        marketDate(
                            if (definition.baseCurrency == ReferenceCurrency.KRW) {
                                Market.KOSPI
                            } else {
                                Market.NYSE
                            },
                            record.effectiveAt,
                        )
                }
                val effectiveAtIsValid = when (record.kind) {
                    AlternativeRiskPremiaActionKind.REWEIGHT ->
                        record.effectiveAt > previous.asOf && record.effectiveAt <= next.asOf
                    AlternativeRiskPremiaActionKind.EXTRAORDINARY_SOURCE_TO_CASH ->
                        record.effectiveAt == previous.asOf
                }
                require(
                    next.revision == previous.revision + 1L &&
                        record.revision == next.revision &&
                        record.benchmarkRef == benchmarkRef &&
                        record.effectiveDate == expectedActionDate &&
                        effectiveAtIsValid &&
                        record.compositionHashBefore == previous.compositionHash &&
                        record.compositionHashAfter == next.compositionHash &&
                        abs(record.resultingGrossExposure - next.grossExposure) <= PRICE_EPSILON &&
                        abs(record.resultingNetExposure - next.netExposure) <= PRICE_EPSILON &&
                        abs(record.resultingDurationYears - next.effectiveDurationYears) <= PRICE_EPSILON,
                ) { "대체위험 프리미엄 상태와 재조정 원장의 계보가 일치하지 않습니다." }
                require(alternativeRiskPremiaRebalanceLedger.none { existing -> existing.id == record.id })
            }
        }
        alternativeRiskPremiaStates.clear()
        alternativeRiskPremiaStates.putAll(advance.book.states)
        alternativeRiskPremiaRebalanceLedger += advance.rebalanceRecords
        if (sameAsOfReconciliation && advance.rebalanceRecords.isNotEmpty()) {
            alternativeRiskPremiaRebalanceLedger.sortWith(
                compareBy<AlternativeRiskPremiaRebalanceRecord>(
                    AlternativeRiskPremiaRebalanceRecord::effectiveAt,
                ).thenBy(AlternativeRiskPremiaRebalanceRecord::benchmarkRef)
                    .thenBy(AlternativeRiskPremiaRebalanceRecord::revision),
            )
        }
    }

    private fun commitCompositeReferenceAdvance(
        advance: CompositeReferenceBookAdvance,
        sameAsOfReconciliation: Boolean = false,
    ) {
        require(advance.book.states.keys == compositeReferenceStates.keys)
        val recordsByRef = advance.rebalanceRecords.associateBy(
            CompositeReferenceRebalanceRecord::benchmarkRef,
        )
        require(recordsByRef.size == advance.rebalanceRecords.size) {
            "같은 시간에 하나의 복합 기준을 두 번 재조정할 수 없습니다."
        }
        for ((benchmarkRef, next) in advance.book.states) {
            val previous = compositeReferenceStates.getValue(benchmarkRef)
            val record = recordsByRef[benchmarkRef]
            require(
                next.asOf == advance.book.asOf &&
                    if (sameAsOfReconciliation) next.asOf == previous.asOf else next.asOf > previous.asOf,
            )
            if (sameAsOfReconciliation && record != null) {
                require(record.kind == CompositeReferenceActionKind.EXTRAORDINARY_SOURCE_TO_CASH)
            }
            if (record == null) {
                require(next.revision == previous.revision &&
                    next.compositionHash == previous.compositionHash
                ) { "원장 없이 복합 기준 목표 비중을 변경할 수 없습니다." }
            } else {
                val actionDate = when (record.kind) {
                    CompositeReferenceActionKind.SELECTION ->
                        next.lastSelectionDate
                    CompositeReferenceActionKind.REWEIGHT ->
                        next.lastReweightDate
                    CompositeReferenceActionKind.EXTRAORDINARY_SOURCE_TO_CASH -> {
                        val definition = requireNotNull(instrumentCatalog.findBenchmark(benchmarkRef))
                        marketDate(
                            if (definition.baseCurrency == ReferenceCurrency.KRW) {
                                Market.KOSPI
                            } else {
                                Market.NYSE
                            },
                            record.effectiveAt,
                        )
                    }
                }
                val effectiveAtIsValid = when (record.kind) {
                    CompositeReferenceActionKind.SELECTION,
                    CompositeReferenceActionKind.REWEIGHT,
                    -> record.effectiveAt > previous.asOf && record.effectiveAt <= next.asOf
                    CompositeReferenceActionKind.EXTRAORDINARY_SOURCE_TO_CASH ->
                        record.effectiveAt == previous.asOf
                }
                require(
                    next.revision == previous.revision + 1L &&
                        record.revision == next.revision &&
                        record.benchmarkRef == benchmarkRef &&
                        record.effectiveDate == actionDate &&
                        effectiveAtIsValid &&
                        record.compositionHashBefore == previous.compositionHash &&
                        record.compositionHashAfter == next.compositionHash &&
                        abs(record.resultingGrossExposure - next.grossExposure) <= PRICE_EPSILON &&
                        abs(record.resultingNetExposure - next.netExposure) <= PRICE_EPSILON &&
                        abs(record.resultingDurationYears - next.effectiveDurationYears) <= PRICE_EPSILON,
                ) { "복합 기준 상태와 재조정 원장의 계보가 일치하지 않습니다." }
                require(compositeReferenceRebalanceLedger.none { existing -> existing.id == record.id })
            }
        }
        compositeReferenceStates.clear()
        compositeReferenceStates.putAll(advance.book.states)
        compositeReferenceRebalanceLedger += advance.rebalanceRecords
        if (sameAsOfReconciliation && advance.rebalanceRecords.isNotEmpty()) {
            compositeReferenceRebalanceLedger.sortWith(
                compareBy<CompositeReferenceRebalanceRecord>(
                    CompositeReferenceRebalanceRecord::effectiveAt,
                ).thenBy(CompositeReferenceRebalanceRecord::benchmarkRef)
                    .thenBy(CompositeReferenceRebalanceRecord::revision),
            )
        }
    }

    private fun commitFixedIncomeReferenceAdvance(advance: FixedIncomeReferenceBookAdvance) {
        val previousByRef = fixedIncomeReferenceStates.values.associateBy(
            FixedIncomeReferenceState::benchmarkRef,
        )
        require(advance.book.states.keys == previousByRef.keys)
        val recordsByRef = advance.rollRecords.associateBy(FixedIncomeRollRecord::benchmarkRef)
        for ((benchmarkRef, next) in advance.book.states) {
            val previous = previousByRef.getValue(benchmarkRef)
            val record = recordsByRef[benchmarkRef]
            require(next.asOf >= previous.asOf) {
                "고정수익 기준 포트폴리오 시각은 뒤로 이동할 수 없습니다."
            }
            if (record == null) {
                require(next.revision == previous.revision) {
                    "만기 교체 원장 없이 고정수익 revision을 변경할 수 없습니다."
                }
            } else {
                require(next.revision == previous.revision + 1L && record.revision == next.revision) {
                    "고정수익 revision과 만기 교체 원장이 연속되지 않습니다."
                }
                val previousIds = previous.positions.mapTo(linkedSetOf()) { it.assetId }
                val nextIds = next.positions.mapTo(linkedSetOf()) { it.assetId }
                require(
                    record.benchmarkRef == benchmarkRef &&
                        record.effectiveAt == next.asOf &&
                        record.removedAssetIds.all(previousIds::contains) &&
                        record.addedAssetIds.all(nextIds::contains) &&
                        nextIds == previousIds - record.removedAssetIds.toSet() +
                        record.addedAssetIds.toSet(),
                ) { "고정수익 상태와 만기 교체 원장의 계보가 일치하지 않습니다." }
                require(fixedIncomeRollLedger.none { it.id == record.id }) {
                    "같은 고정수익 만기 교체 원장을 두 번 기록할 수 없습니다."
                }
            }
        }
        fixedIncomeReferenceStates.clear()
        advance.book.states.values.forEach { next ->
            fixedIncomeReferenceStates[next.referenceId] = next
        }
        fixedIncomeRollLedger += advance.rollRecords
    }

    private fun commitCommodityReferenceAdvance(advance: CommodityReferenceBookAdvance) {
        require(advance.book.spotStates.keys == commoditySpotReferenceStates.keys)
        require(advance.book.futuresStates.keys == futuresReferenceStates.keys)
        val rollRecordsByRef = advance.futuresRollRecords.groupBy(FuturesRollRecord::benchmarkRef)
        val allocationRecordsByRef =
            advance.futuresAllocationRecords.groupBy(FuturesAllocationRecord::benchmarkRef)
        advance.book.spotStates.forEach { (benchmarkRef, next) ->
            val previous = commoditySpotReferenceStates.getValue(benchmarkRef)
            require(next.asOf > previous.asOf) {
                "원자재 현물 benchmark 시각은 매 tick 앞으로 이동해야 합니다."
            }
        }
        advance.book.futuresStates.forEach { (benchmarkRef, next) ->
            val previous = futuresReferenceStates.getValue(benchmarkRef)
            require(next.asOf > previous.asOf) {
                "선물 benchmark 시각은 매 tick 앞으로 이동해야 합니다."
            }
            val newRolls = rollRecordsByRef[benchmarkRef].orEmpty()
            val newAllocations = allocationRecordsByRef[benchmarkRef].orEmpty()
            val revisions = (newRolls.map(FuturesRollRecord::revision) +
                newAllocations.map(FuturesAllocationRecord::revision)).distinct().sorted()
            if (revisions.isEmpty()) {
                require(next.revision == previous.revision) {
                    "선물 원장 없이 benchmark revision을 변경할 수 없습니다."
                }
            } else {
                require(revisions.first() == previous.revision + 1L)
                require(revisions.zipWithNext().all { (before, after) -> after == before + 1L })
                require(next.revision == revisions.last()) {
                    "선물 상태 revision과 roll·배분 원장의 마지막 revision이 다릅니다."
                }
            }
        }
        require(advance.futuresRollRecords.none { next ->
            futuresRollLedger.any { existing -> existing.id == next.id }
        }) { "같은 선물 roll 원장을 두 번 기록할 수 없습니다." }
        require(advance.futuresAllocationRecords.none { next ->
            futuresAllocationLedger.any { existing -> existing.id == next.id }
        }) { "같은 선물 배분 원장을 두 번 기록할 수 없습니다." }
        commoditySpotReferenceStates.clear()
        commoditySpotReferenceStates.putAll(advance.book.spotStates)
        futuresReferenceStates.clear()
        futuresReferenceStates.putAll(advance.book.futuresStates)
        futuresRollLedger += advance.futuresRollRecords
        futuresAllocationLedger += advance.futuresAllocationRecords
    }

    private fun advanceFundFinancialStates(
        at: Instant,
        turn: TurnGenerationResult,
    ) {
        for ((stockId, previousState) in fundFinancialStates.toMap()) {
            val stock = stockById.getValue(stockId)
            val listingState = listingLifecycleStates.getValue(stockId)
            val attribution = turn.priceAttributions[stockId]
            if (listingState.isTerminal || listingState.isSettlementPending) {
                pendingFundFlowRates.remove(stockId)
                continue
            }
            if (attribution == null) continue
            val tradingFraction = turn.stockTradingFractions[stockId] ?: 0.0
            val externalFlowRate = if (tradingFraction > 0.0) {
                pendingFundFlowRates[stockId] ?: 0.0
            } else {
                0.0
            }
            fundFinancialStates[stockId] = instrumentMetricsEngine.advanceFund(
                state = previousState,
                stock = stock,
                fairValueLogReturn = attribution.fairValueLogReturn - attribution.carriedReference,
                carriedFairValueLogReturn = attribution.carriedReference,
                tradingFraction = tradingFraction,
                riskSentiment = macro.riskSentiment,
                externalFlowRate = externalFlowRate,
                at = at,
            )
            if (tradingFraction > 0.0) pendingFundFlowRates.remove(stockId)
        }
    }

    private fun dailyTrackerSnapshot(
        stock: StockDefinition,
        time: Instant,
        previousPrice: Double,
    ): DailyPriceTracker {
        val existing = dailyTrackers.getValue(stock.id)
        val date = marketDate(stock.market, time)
        return if (existing.date == date) {
            existing.copy()
        } else {
            DailyPriceTracker(date, previousPrice, previousPrice, previousPrice, previousPrice, false)
        }
    }

    private fun effectiveProtectionPriceBounds(
        stock: StockDefinition,
        additional: RuntimePriceBounds?,
    ): RuntimePriceBounds? {
        var result = additional
        tradingProtectionSnapshot.krxVolatilityInterruptions[stock.id]?.takeIf {
            it.phase == KrxViPhase.CALL_AUCTION
        }?.let { vi ->
            val reference = requireNotNull(vi.referencePrice)
            val rate = requireNotNull(vi.triggerRate)
            val viBounds = if (vi.direction == com.amond.kmpbook.domain.model.protection.krx.KrxViDirection.UPPER) {
                RuntimePriceBounds(upper = reference * (1.0 + rate))
            } else {
                RuntimePriceBounds(lower = reference * (1.0 - rate))
            }
            result = result?.merge(viBounds) ?: viBounds
        }
        tradingProtectionSnapshot.usLuldStates[stock.id]?.bands?.let { bands ->
            val luldBounds = RuntimePriceBounds(
                lower = bands.lower,
                upper = bands.upper,
            )
            result = result?.merge(luldBounds) ?: luldBounds
        }
        return result
    }

    private fun applyDueCorporateActions(from: Instant, to: Instant) {
        val due = pendingCorporateActions.filter { action ->
            action.effectiveNotBefore <= from &&
                listingLifecycleStates[action.stockId]?.isTerminal != true &&
                listingLifecycleStates[action.stockId]?.isSettlementPending != true &&
                stockById[action.stockId]?.let { regularTradingFraction(it.market, from, to) > 0.0 } == true
        }
        for (action in due) {
            val stock = stockById.getValue(action.stockId)
            if (isCorporateActionProductStateEligible(stock)) {
                applyCorporateAction(action, from)
            } else {
                cancelPendingCorporateActionForProductState(action, stock, from)
            }
            pendingCorporateActions.removeAll { it.id == action.id }
        }
    }

    /** 다음 정규장 시작 경계에 들어가기 전에 가격·수량을 조정해 오래된 호가 체결 창을 없앤다. */
    private fun applyDueCorporateActionsAtBoundary(at: Instant) {
        val next = at + 1.hours
        val due = pendingCorporateActions.filter { action ->
            action.effectiveNotBefore <= at &&
                listingLifecycleStates[action.stockId]?.isTerminal != true &&
                listingLifecycleStates[action.stockId]?.isSettlementPending != true &&
                stockById[action.stockId]?.let { regularTradingFraction(it.market, at, next) > 0.0 } == true
        }
        for (action in due) {
            val stock = stockById.getValue(action.stockId)
            if (isCorporateActionProductStateEligible(stock)) {
                applyCorporateAction(action, at)
            } else {
                cancelPendingCorporateActionForProductState(action, stock, at)
            }
            pendingCorporateActions.removeAll { it.id == action.id }
        }
    }

    /** Runs once at each listing venue's local close and turns news/price data into exchange state. */
    private fun processDailyListingSurveillance(from: Instant, to: Instant) {
        val eligibleKrxStocks = stocks.filter { stock ->
            stock.market.isKorean && stock.hasCorporateEarnings &&
                listingLifecycleStates.getValue(stock.id).isIndexEligible
        }
        val krxMarketProxyClose = listOf(Market.KOSPI, Market.KOSDAQ).associateWith { market ->
            eligibleKrxStocks.asSequence()
                .filter { stock -> stock.market == market }
                .sumOf { stock -> stock.sharesOutstanding.toDouble() * quotes.getValue(stock.id).price }
                .takeIf { it > 0.0 }
        }
        // KOSPI 공시의 상대상승 비교는 업종지수 기준이다. 카탈로그 섹터에 비교 종목이
        // 둘 이상 있을 때 섹터 시총 프록시를 쓰고, 단독 섹터는 자기 자신과 비교하지
        // 않도록 KOSPI 종합 프록시로 물러난다. KOSDAQ은 공시대로 종합지수 프록시다.
        val krxSectorProxyClose = eligibleKrxStocks
            .groupBy { stock -> stock.market to stock.sector }
            .mapValues { (_, members) ->
                members.takeIf { it.size >= 2 }
                    ?.sumOf { stock -> stock.sharesOutstanding.toDouble() * quotes.getValue(stock.id).price }
            }
        val krxMarketCaps = stocks.asSequence()
            .filter { stock ->
                stock.market.isKorean && stock.hasCorporateEarnings &&
                    listingLifecycleStates.getValue(stock.id).isIndexEligible
            }
            .map { stock ->
                stock.id to stock.sharesOutstanding.toDouble() * quotes.getValue(stock.id).price
            }
            .sortedByDescending(Pair<String, Double>::second)
            .toList()
        // 카탈로그가 KRX 전 종목을 담지는 않으므로 단순 1..N 순위는 모든 종목을 상위100으로
        // 오인한다. 기준일 시총 경계 프록시를 먼저 적용하고, 경계 밖은 101위부터 매긴다.
        val top100Proxy = krxMarketCaps.filter { it.second >= KRX_TOP_100_MARKET_CAP_PROXY_KRW }
        val outsideTop100Proxy = krxMarketCaps.filter { it.second < KRX_TOP_100_MARKET_CAP_PROXY_KRW }
        val krxMarketCapRanks = buildMap {
            top100Proxy.forEachIndexed { index, (stockId, _) -> put(stockId, index + 1) }
            outsideTop100Proxy.forEachIndexed { index, (stockId, _) -> put(stockId, 101 + index) }
        }
        for (stock in stocks) {
            val previous = listingLifecycleStates.getValue(stock.id)
            if (previous.isTerminal) continue
            val tradingDate = marketDate(stock.market, from)
            val venueCloseAt = GameCalendar.regularSessionWindow(
                stock.market,
                tradingDate,
                runtimeClosedDates(stock.market, tradingDate),
            )?.closesAt ?: continue
            if (venueCloseAt <= from || venueCloseAt > to) continue
            // The regular session is half-open; sample its final representable instant so an
            // event that starts during this hour is included, while a post-close event is not.
            val surveillanceAt = venueCloseAt - 1.nanoseconds
            // Price exposure may legitimately propagate through an ETF's underlying holdings, but
            // exchange listing actions must only affect the security named in the notice.
            val directListingRiskEvents = newsEvents.filter { event ->
                event.isActiveAt(surveillanceAt) && stock.id in event.affectedStockIds &&
                    event.instrumentTermination == null &&
                    listingRiskTagsFor(event, stock.id).isNotEmpty()
            }
            // 최종처분 공시는 가격충격 duration이 끝나도 거래소 생명주기 판단 근거로 남는다.
            val resolvedTerminationDecision = resolveInstrumentTerminationAtSessionClose(
                stock = stock,
                events = newsEvents,
                evaluatedOn = tradingDate,
                incumbentOccurrenceId = previous.controllingTerminationOccurrenceId,
            )
            val terminationDecision = if (previous.controllingTerminationOccurrenceId == null) {
                resolvedTerminationDecision?.takeUnless { candidate ->
                    previous.status == ListingLifecycleStatus.DELISTING_SCHEDULED &&
                        candidate.rawEffectiveOn >= requireNotNull(previous.scheduledDelistingOn)
                }
            } else {
                resolvedTerminationDecision
            }
            val terminationNotice = terminationDecision?.notice
            val terminationEvent = terminationNotice?.event
            val listingRiskEventsForInput = (directListingRiskEvents + listOfNotNull(terminationEvent))
                .distinctBy(GameEvent::id)
            val riskTags = listingRiskEventsForInput
                .flatMapTo(linkedSetOf()) { event -> listingRiskTagsFor(event, stock.id) }
            val resolutionKey = listingRemediationEventId(stock, previous)
            val explicitRecoveryConditions = newsEvents.asSequence()
                .filter { event ->
                    val remediation = event.marketAction?.takeIf { action ->
                        action.kind == MarketActionKind.LISTING_REMEDIATION
                    }
                    event.isActiveAt(surveillanceAt) && stock.id in event.affectedStockIds &&
                        (remediation == null || remediation.occurrenceId == resolutionKey)
                }
                .flatMap { event -> event.directListingRecoveryConditions(stock.id).asSequence() }
                .toCollection(linkedSetOf())
            val recoveryConditions = buildSet {
                addAll(explicitRecoveryConditions)
                evaluateListingRemediation(
                    stock = stock,
                    state = previous,
                    tradingDate = tradingDate,
                    at = venueCloseAt,
                    activeRiskEvents = directListingRiskEvents,
                )?.let(::add)
            }
            val dayVolume = history.getValue(stock.id)
                .asSequence()
                .filter { bar -> marketDate(stock.market, bar.endTime) == tradingDate }
                .sumOf(PriceBar::volume)
            val hadTradableObservation = history.getValue(stock.id).any { bar ->
                bar.volume > 0L && marketDate(stock.market, bar.endTime) == tradingDate
            }
            val quote = quotes.getValue(stock.id)
            val surveillance = dailyTradingSurveillance.getOrPut(stock.id) { ArrayDeque() }
            if (hadTradableObservation && surveillance.lastOrNull()?.date != tradingDate) {
                surveillance.addLast(
                    DailyTradingSurveillancePoint(
                        date = tradingDate,
                        close = quote.price,
                        volume = dayVolume,
                        turnoverRate = dayVolume.toDouble() / stock.sharesOutstanding.toDouble(),
                        marketProxyClose = if (stock.market == Market.KOSPI) {
                            krxSectorProxyClose[stock.market to stock.sector]
                                ?: krxMarketProxyClose[stock.market]
                        } else {
                            krxMarketProxyClose[stock.market]
                        },
                        krxMarketCapRank = krxMarketCapRanks[stock.id],
                    ),
                )
                while (surveillance.size > MAX_DAILY_SURVEILLANCE_POINTS) surveillance.removeFirst()
            }
            if (stock.market.isKorean) {
                refreshInvestmentAlertNoticeEnd(stock, venueCloseAt, surveillance.toList())
            }
            if (stock.market.isKorean && hadTradableObservation) {
                evaluateKrxInvestmentAlert(stock, venueCloseAt, surveillance.toList())
            }
            val riskSeverityByTag = buildMap {
                listingRiskEventsForInput.forEach { event ->
                    val severity = when (event.severity) {
                    EventSeverity.MINOR -> ListingRiskSeverity.LOW
                    EventSeverity.MODERATE -> ListingRiskSeverity.MODERATE
                    EventSeverity.MAJOR -> ListingRiskSeverity.HIGH
                    EventSeverity.CRITICAL -> ListingRiskSeverity.CRITICAL
                    }
                    listingRiskTagsFor(event, stock.id).forEach { tag ->
                        val previousSeverity = get(tag) ?: ListingRiskSeverity.NONE
                        if (severity.level > previousSeverity.level) put(tag, severity)
                    }
                }
            }
            val dispositionHint = terminationNotice?.terms?.finalDisposition
            // The pure close decision resolves the winner, raw date, contractual maturity hard cap,
            // and evaluated-date clamp together so payout terms cannot come from another notice.
            val scheduledDelistingOn = terminationDecision?.scheduledTerminationOn
            val otcTransferAvailable = stock.market.isUnitedStates &&
                previous.activeReason !in setOf(
                    ListingLifecycleReason.BANKRUPTCY_OR_INSOLVENCY,
                    ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION,
                    ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION,
                )
            val input = DailyListingSurveillanceInput(
                stockId = stock.id,
                tradingDate = tradingDate,
                close = quote.price,
                marketCapitalization = stock.sharesOutstanding.toDouble() * quote.price,
                tradedVolume = dayVolume.takeIf { hadTradableObservation },
                turnoverRate = dayVolume.toDouble().div(stock.sharesOutstanding.toDouble())
                    .takeIf { hadTradableObservation },
                riskTags = riskTags,
                riskSeverityByTag = riskSeverityByTag,
                recoveryConditions = recoveryConditions,
                scheduledDelistingOn = scheduledDelistingOn,
                finalDispositionHint = dispositionHint,
                otcTransferAvailable = otcTransferAvailable,
                liquidationCashPerUnit = terminationNotice?.takeIf {
                    dispositionHint == ListingFinalDispositionType.CASH_LIQUIDATION
                }?.let { notice -> listingLiquidationUnitPrice(stock, notice.terms) },
                controllingTerminationOccurrenceId = terminationNotice?.event?.id,
                controllingTerminationNoticePriority = terminationNotice?.terms?.kind?.noticePriority,
                controllingTerminationRawEffectiveOn = terminationDecision?.rawEffectiveOn,
            )
            val result = listingLifecycleEngine.evaluate(previous, input)
            var nextState = result.state
            var ledgerEvents = result.ledgerEvents
            if (previous.status != ListingLifecycleStatus.LIQUIDATION_PENDING &&
                nextState.status == ListingLifecycleStatus.LIQUIDATION_PENDING
            ) {
                val disposition = requireNotNull(nextState.finalDisposition)
                val holding = holdings[stock.id]
                val frozen = disposition.copy(
                    entitledQuantity = holding?.quantity ?: 0.0,
                    entitledCostBasis = holding?.costBasis ?: 0.0,
                )
                nextState = nextState.copy(finalDisposition = frozen)
                ledgerEvents = ledgerEvents.map { event ->
                    if (event.kind == ListingLifecycleEventKind.LIQUIDATION_STARTED) {
                        event.copy(disposition = frozen)
                    } else {
                        event
                    }
                }
                // 청산 권리는 이미 확정됐으므로 이후 분할·병합과 배당 권리에서 분리한다.
                // 제거되는 기업행동은 이를 취소한 상장 원장 전이와 연결된 뉴스로 계보를 닫는다.
                val liquidationEvent = requireNotNull(
                    ledgerEvents.singleOrNull { event ->
                        event.stockId == stock.id &&
                            event.toStatus == ListingLifecycleStatus.LIQUIDATION_PENDING
                    },
                ) { "청산 대기 전이에 대응하는 상장 원장 이벤트가 없습니다." }
                cancelPendingCorporateActions(stock, venueCloseAt, liquidationEvent)
                pendingEtfReferenceReturns.remove(stock.id)
                pendingClosedEventLogReturns.remove(stock.id)
                pendingFundFlowRates.remove(stock.id)
            }
            listingLifecycleStates[stock.id] = nextState
            if (ledgerEvents.isEmpty()) continue

            listingLifecycleLedger += ledgerEvents
            ledgerEvents.forEach { event -> addListingLifecycleNews(stock, event, venueCloseAt) }
            if (previous.isTradable && !nextState.isTradable) {
                cancelOpenOrdersForStock(
                    stock.id,
                    venueCloseAt,
                    "${nextState.status.displayName} 조치로 미체결 주문을 취소했습니다.",
                )
            }
            syncListingTradingProtections(stock, previous, nextState, venueCloseAt)
            if (!previous.isTerminal && nextState.isTerminal) {
                applyListingFinalDisposition(stock, nextState, venueCloseAt)
            }
        }
    }

    private fun listingRiskTagsFor(event: GameEvent, stockId: String): Set<ListingRiskTag> {
        if (stockId !in event.affectedStockIds) return emptySet()
        val stock = stockById[stockId] ?: return emptySet()
        if (event.instrumentTermination?.isEligibleFor(stock) == false) {
            return emptySet()
        }
        return event.directListingRiskTags(stockId)
    }

    private fun listingLiquidationUnitPrice(
        stock: StockDefinition,
        terms: InstrumentTerminationTerms,
    ): Double = when (terms.valuationMethod) {
        InstrumentTerminationValuationMethod.ETN_CONTRACT_SETTLEMENT,
        InstrumentTerminationValuationMethod.ETN_CREDIT_DEFAULT_RECOVERY,
        -> {
            val expectedEvent = when (terms.kind) {
                InstrumentTerminationKind.CONTRACTUAL_MATURITY ->
                    EtnCreditEvent.CONTRACTUAL_MATURITY
                InstrumentTerminationKind.CREDIT_DEFAULT -> EtnCreditEvent.CREDIT_DEFAULT
                InstrumentTerminationKind.ISSUER_ACCELERATION ->
                    EtnCreditEvent.ISSUER_ACCELERATION
                InstrumentTerminationKind.OPTIONAL_CALL -> EtnCreditEvent.ISSUER_CALL
                InstrumentTerminationKind.FUND_LIQUIDATION ->
                    error("펀드 청산에는 ETN 평가 방식을 사용할 수 없습니다.")
            }
            val contractualSettlement = etnLedger.lastOrNull { entry ->
                entry.productId == stock.id &&
                    entry.kind == EtnLedgerKind.CONTRACT_SETTLEMENT &&
                    entry.notesSettled > 0L &&
                    entry.contractEvent == expectedEvent
            }
            contractualSettlement?.let { entry ->
                entry.cashPaidToNoteholders / entry.notesSettled.toDouble()
            } ?: requireNotNull(etnStates[stock.id]) {
                "ETN 최종 지표가치에는 계약 상태가 필요합니다: ${stock.id}"
            }.let { state ->
                val contractualClaim =
                    state.feeAdjustedIndicativeValuePerNote + state.accruedCouponPerNote
                if (terms.valuationMethod ==
                    InstrumentTerminationValuationMethod.ETN_CREDIT_DEFAULT_RECOVERY
                ) {
                    contractualClaim * requireNotNull(terms.accelerationRecoveryRate)
                } else {
                    contractualClaim
                }
            }
        }
        InstrumentTerminationValuationMethod.FINAL_NET_ASSET_VALUE ->
            closedEndFundStates[stock.id]?.navPerCommonShare
                ?: requireNotNull(fundFinancialStates[stock.id]) {
                    "펀드 최종 순자산가치에는 NAV 상태가 필요합니다: ${stock.id}"
                }.navPerUnit
    }.coerceAtLeast(0.0)

    private fun evaluateListingRemediation(
        stock: StockDefinition,
        state: ListingLifecycleState,
        tradingDate: LocalDate,
        at: Instant,
        activeRiskEvents: List<GameEvent>,
    ): ListingRecoveryCondition? {
        if (state.status == ListingLifecycleStatus.LISTED || state.isTerminal) return null
        val stageStartedOn = listingLifecycleLedger
            .lastOrNull { event -> event.stockId == stock.id && event.toStatus == state.status }
            ?.tradingDate
            ?: state.designatedOn
            ?: tradingDate
        val eventId = listingRemediationEventId(stock, state, stageStartedOn)
        val alreadyDecided = newsEvents.any { event -> event.id == eventId }
        val sourceRiskActive = activeRiskEvents.any { event ->
            listingRiskTagsFor(event, stock.id).any { tag -> tag.matchesListingReason(state.activeReason) }
        }
        val decision = ListingRemediationPolicy.evaluate(
            state = state,
            tradingDate = tradingDate,
            stageStartedOn = stageStartedOn,
            campaignSeed = options.seed,
            sourceRiskActive = sourceRiskActive,
            alreadyDecidedForStage = alreadyDecided,
        )
        if (decision.status !in setOf(
                ListingRemediationDecisionStatus.CURED,
                ListingRemediationDecisionStatus.NOT_CURED,
            )
        ) {
            return null
        }
        if (!alreadyDecided) {
            val cured = decision.status == ListingRemediationDecisionStatus.CURED
            newsEvents += GameEvent(
                id = eventId,
                title = if (cured) {
                    "${stock.name} 개선 심사를 통과했어요"
                } else {
                    "${stock.name} 개선 심사가 계속돼요"
                },
                description = if (cured) {
                    "거래소가 개선 자료를 확인해 ${state.status.displayName} 조치를 해제할 수 있다고 판단했습니다."
                } else {
                    "거래소 심사에서 개선이 충분히 확인되지 않았습니다. 다음 상장 절차가 예정대로 진행됩니다."
                },
                scope = EventScope.STOCK,
                type = EventType.REGULATION_POLICY,
                severity = if (cured) EventSeverity.MINOR else EventSeverity.MAJOR,
                impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
                startsAt = at,
                durationHours = 720,
                recordKind = EventRecordKind.MARKET_ACTION,
                affectedMarkets = setOf(stock.market),
                affectedSectors = setOf(stock.sector),
                affectedStockIds = setOf(stock.id),
                sourceLabel = "거래소 공개 규칙 · 게임 개선심사",
                marketAction = MarketActionReference(
                    kind = MarketActionKind.LISTING_REMEDIATION,
                    occurrenceId = eventId,
                    transition = MarketActionTransition.REMEDIATION_RECORDED,
                    announcedAt = at,
                    stockId = stock.id,
                    markets = setOf(stock.market),
                    listingStatus = state.status,
                ),
                listingRecoveryConditions = decision.recoveryCondition?.let(::setOf).orEmpty(),
            )
        }
        return decision.recoveryCondition
    }

    private fun listingRemediationEventId(
        stock: StockDefinition,
        state: ListingLifecycleState,
        stageStartedOn: LocalDate = listingLifecycleLedger
            .lastOrNull { event -> event.stockId == stock.id && event.toStatus == state.status }
            ?.tradingDate
            ?: state.designatedOn
            ?: marketDate(stock.market, currentTime),
    ): String = "$LISTING_RESOLUTION_EVENT_PREFIX${stock.id}:${state.designatedOn}:${state.status}:$stageStartedOn"

    private fun ListingRiskTag.matchesListingReason(reason: ListingLifecycleReason?): Boolean = when (reason) {
        ListingLifecycleReason.KRX_LISTING_MAINTENANCE,
        ListingLifecycleReason.US_LISTING_MAINTENANCE,
        -> this in setOf(
            ListingRiskTag.LISTING_MAINTENANCE_DEFICIENCY,
            ListingRiskTag.QUALITATIVE_LISTING_REVIEW,
        )
        ListingLifecycleReason.KRX_ADMINISTRATIVE_ISSUE -> this == ListingRiskTag.ADMINISTRATIVE_ISSUE
        ListingLifecycleReason.US_MINIMUM_BID_PRICE -> this == ListingRiskTag.LOW_BID_PRICE
        ListingLifecycleReason.US_MARKET_CAPITALIZATION -> this == ListingRiskTag.LOW_MARKET_CAPITALIZATION
        ListingLifecycleReason.LOW_TRADING_LIQUIDITY -> this == ListingRiskTag.LOW_TRADING_LIQUIDITY
        ListingLifecycleReason.AUDIT_OR_DISCLOSURE_FAILURE ->
            this == ListingRiskTag.AUDIT_OPINION_FAILURE || this == ListingRiskTag.DISCLOSURE_VIOLATION
        ListingLifecycleReason.SERIOUS_COMPLIANCE_EVENT -> this == ListingRiskTag.SERIOUS_COMPLIANCE_EVENT
        ListingLifecycleReason.BANKRUPTCY_OR_INSOLVENCY -> this == ListingRiskTag.BANKRUPTCY_OR_INSOLVENCY
        ListingLifecycleReason.CORE_BUSINESS_SUSPENSION -> this == ListingRiskTag.CORE_BUSINESS_SUSPENSION
        ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION -> this == ListingRiskTag.ETF_LIQUIDATION_APPROVED
        ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION ->
            this == ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION
        ListingLifecycleReason.ISSUER_ELIGIBILITY_FAILURE -> this == ListingRiskTag.ISSUER_ELIGIBILITY_FAILURE
        ListingLifecycleReason.UNDERLYING_INDEX_UNAVAILABLE -> this == ListingRiskTag.UNDERLYING_INDEX_UNAVAILABLE
        ListingLifecycleReason.LIQUIDITY_PROVIDER_FAILURE -> this == ListingRiskTag.LIQUIDITY_PROVIDER_FAILURE
        null -> false
    }

    private fun addListingLifecycleNews(
        stock: StockDefinition,
        ledgerEvent: ListingLifecycleLedgerEvent,
        at: Instant,
    ) {
        val id = "listing-lifecycle:${ledgerEvent.id}"
        if (newsEvents.any { it.id == id }) return
        newsEvents += GameEvent(
            id = id,
            title = "${stock.name} ${ledgerEvent.title}",
            description = ledgerEvent.summary,
            scope = EventScope.STOCK,
            type = EventType.REGULATION_POLICY,
            severity = when (ledgerEvent.level) {
                ListingNoticeLevel.INFO -> EventSeverity.MINOR
                ListingNoticeLevel.CAUTION -> EventSeverity.MODERATE
                ListingNoticeLevel.WARNING -> EventSeverity.MAJOR
                ListingNoticeLevel.CRITICAL -> EventSeverity.CRITICAL
            },
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = at,
            durationHours = 720,
            recordKind = EventRecordKind.MARKET_ACTION,
            affectedMarkets = setOf(stock.market),
            affectedSectors = setOf(stock.sector),
            affectedStockIds = setOf(stock.id),
            sourceLabel = "거래소 공개 규칙 · 게임 감시",
            marketAction = MarketActionReference(
                kind = MarketActionKind.LISTING_LIFECYCLE,
                occurrenceId = ledgerEvent.id,
                transition = MarketActionTransition.LIFECYCLE_CHANGED,
                announcedAt = at,
                stockId = stock.id,
                markets = setOf(stock.market),
                listingLedgerSequence = ledgerEvent.sequence,
                listingStatus = ledgerEvent.toStatus,
            ),
        )
    }

    /**
     * KRX price-surge alert ladder using the public 3/5/15-day price conditions. Account-
     * concentration criteria cannot exist without simulated participant accounts and are omitted.
     */
    private fun evaluateKrxInvestmentAlert(
        stock: StockDefinition,
        at: Instant,
        points: List<DailyTradingSurveillancePoint>,
    ) {
        if (points.isEmpty()) return
        val today = points.last().date
        val close = points.last().close
        val previous = tradingProtectionSnapshot.investmentAlerts[stock.id]
        val warningNoticeReasons = krxWarningNoticeReasonCodes(points)
        val warningDesignationReasons = krxWarningDesignationReasonCodes(previous, points, stock.market)
        val dangerNoticeReasons = if (previous?.status == InvestmentAlertStatus.ACTIVE &&
            previous.level == InvestmentAlertLevel.WARNING
        ) {
            krxDangerNoticeReasonCodes(points)
        } else {
            emptySet()
        }
        val dangerDesignationReasons = krxDangerDesignationReasonCodes(previous, points)

        if (previous?.status == InvestmentAlertStatus.RELEASED &&
            previous.level == InvestmentAlertLevel.CAUTION
        ) {
            val redesignationReasons = when {
                krxWarningRedesignationCriteriaSatisfied(previous, points) ->
                    setOf("WARNING_REDESIGNATION_2D_40")
                warningDesignationReasons.isNotEmpty() -> warningDesignationReasons
                else -> emptySet()
            }
            if (redesignationReasons.isNotEmpty()) {
                designateInvestmentAlert(
                    stock = stock,
                    at = at,
                    onDate = nthTradingDate(stock.market, today, 1),
                    level = InvestmentAlertLevel.WARNING,
                    reasonCodes = redesignationReasons,
                    previous = previous,
                    releaseRule = previous.redesignationReleaseRule
                        ?: InvestmentAlertReleaseRule.WARNING_60_100,
                    preDesignationClose = previous.preDesignationClose ?: close,
                    priorLevelUntilEffective = InvestmentAlertLevel.CAUTION,
                    isRedesignation = "WARNING_REDESIGNATION_2D_40" in redesignationReasons,
                )
                return
            }
            val initialNoticeWindowOpen = previous.escalationNoticeReasons
                .any { it.startsWith("WARNING_NOTICE_") } &&
                krxEscalationNoticeJudgmentOpen(previous, points)
            val redesignationWindowOpen = previous.redesignationWindow?.contains(today) == true
            if (initialNoticeWindowOpen || redesignationWindowOpen) return
        }

        if (previous?.status == InvestmentAlertStatus.ACTIVE) {
            // A warning's two-day follow-up condition and an initial danger designation can
            // schedule a future full-session halt even when today's closing review also changes
            // the alert level.
            maybeStartInvestmentAlertHalt(stock, previous, points, at)
            if (krxWarningRedesignationCriteriaSatisfied(previous, points)) {
                designateInvestmentAlert(
                    stock = stock,
                    at = at,
                    onDate = nthTradingDate(stock.market, today, 1),
                    level = InvestmentAlertLevel.WARNING,
                    reasonCodes = setOf("WARNING_REDESIGNATION_2D_40"),
                    previous = previous,
                    releaseRule = previous.redesignationReleaseRule
                        ?: InvestmentAlertReleaseRule.WARNING_60_100,
                    preDesignationClose = previous.preDesignationClose,
                    priorLevelUntilEffective = InvestmentAlertLevel.CAUTION,
                    isRedesignation = true,
                )
                return
            }
            val escalation = when (previous.level) {
                InvestmentAlertLevel.CAUTION -> warningDesignationReasons
                    .takeIf { it.isNotEmpty() }
                    ?.let { InvestmentAlertLevel.WARNING to it }
                InvestmentAlertLevel.WARNING -> dangerDesignationReasons
                    .takeIf { it.isNotEmpty() }
                    ?.let { InvestmentAlertLevel.DANGER to it }
                InvestmentAlertLevel.DANGER -> null
            }
            if (escalation != null) {
                designateInvestmentAlert(
                    stock = stock,
                    at = at,
                    onDate = nthTradingDate(stock.market, today, 1),
                    level = escalation.first,
                    reasonCodes = escalation.second,
                    previous = previous,
                    releaseRule = if (escalation.first == InvestmentAlertLevel.WARNING) {
                        InvestmentAlertReleaseRule.WARNING_60_100
                    } else {
                        InvestmentAlertReleaseRule.DANGER_60_100
                    },
                    preDesignationClose = close,
                    priorLevelUntilEffective = previous.level,
                )
                return
            }
            val noticeReasons = when (previous.level) {
                InvestmentAlertLevel.CAUTION -> warningNoticeReasons
                InvestmentAlertLevel.WARNING -> dangerNoticeReasons
                InvestmentAlertLevel.DANGER -> emptySet()
            }
            val noticeStillOpen = krxEscalationNoticeJudgmentOpen(previous, points)
            if (noticeReasons.isNotEmpty() && !noticeStillOpen) {
                recordInvestmentAlertEscalationNotice(stock, previous, noticeReasons, today, at)
                return
            }
            if (previous.level == InvestmentAlertLevel.CAUTION) {
                if (today >= previous.designatedOn) {
                    val released = TradingProtectionEngine.releaseInvestmentAlert(
                        state = previous,
                        releasedAt = at,
                        releasedOn = today,
                        reason = "지정예고에 따른 투자주의 1일 적용이 끝났습니다.",
                        criteriaCleared = true,
                        force = true,
                        releaseEffectiveOn = nthTradingDate(stock.market, today, 1),
                    )
                    tradingProtectionSnapshot = tradingProtectionSnapshot.copy(
                        investmentAlerts = tradingProtectionSnapshot.investmentAlerts + (stock.id to released),
                    )
                    addInvestmentAlertReleaseNews(stock, released, at)
                }
                return
            }
            val observedTradingDays = krxInvestmentAlertObservedTradingDays(previous, points)
            val requiredTradingDays = 10
            val reviewOpen = today >= previous.releaseReviewWindow.startsOn &&
                observedTradingDays >= requiredTradingDays
            val criteriaCleared = krxInvestmentAlertReleaseCriteriaCleared(previous, points)
            if (reviewOpen && criteriaCleared) {
                val released = TradingProtectionEngine.releaseInvestmentAlert(
                    state = previous,
                    releasedAt = at,
                    releasedOn = today,
                    reason = "가격 급등 기준이 해소됐습니다.",
                    criteriaCleared = true,
                    releaseEffectiveOn = nthTradingDate(stock.market, today, 1),
                )
                tradingProtectionSnapshot = tradingProtectionSnapshot.copy(
                    investmentAlerts = tradingProtectionSnapshot.investmentAlerts + (stock.id to released),
                )
                addInvestmentAlertReleaseNews(stock, released, at)
                if (previous.level == InvestmentAlertLevel.DANGER) {
                    designateInvestmentAlert(
                        stock = stock,
                        at = at,
                        onDate = nthTradingDate(stock.market, today, 1),
                        level = InvestmentAlertLevel.WARNING,
                        reasonCodes = setOf("DANGER_RELEASE_DOWNGRADE"),
                        previous = released,
                        releaseRule = InvestmentAlertReleaseRule.WARNING_60_100,
                        preDesignationClose = close,
                        priorLevelUntilEffective = InvestmentAlertLevel.DANGER,
                    )
                } else if (previous.level == InvestmentAlertLevel.WARNING) {
                    val cautionOn = nthTradingDate(stock.market, today, 1)
                    designateInvestmentAlert(
                        stock = stock,
                        at = at,
                        onDate = cautionOn,
                        level = InvestmentAlertLevel.CAUTION,
                        reasonCodes = setOf("WARNING_RELEASE_REDESIGNATION"),
                        previous = released,
                        releaseRule = InvestmentAlertReleaseRule.CAUTION_PRICE_VOLUME,
                        preDesignationClose = previous.preDesignationClose,
                        preReleaseClose = close,
                        redesignationReleaseRule = krxInvestmentAlertReleaseRule(previous),
                        redesignationWindow = TradingDayWindow(
                            nthTradingDate(stock.market, cautionOn, 1),
                            nthTradingDate(stock.market, cautionOn, 9),
                        ),
                        priorLevelUntilEffective = InvestmentAlertLevel.WARNING,
                    )
                }
            }
            return
        }
        if (warningNoticeReasons.isNotEmpty()) {
            val cautionOn = nthTradingDate(stock.market, today, 1)
            designateInvestmentAlert(
                stock = stock,
                at = at,
                onDate = cautionOn,
                level = InvestmentAlertLevel.CAUTION,
                reasonCodes = warningNoticeReasons,
                previous = previous,
                releaseRule = InvestmentAlertReleaseRule.CAUTION_PRICE_VOLUME,
                escalationNoticeOn = cautionOn,
                escalationNoticeReasons = warningNoticeReasons,
            )
        }
    }

    private fun recordInvestmentAlertEscalationNotice(
        stock: StockDefinition,
        previous: InvestmentAlertDesignation,
        reasons: Set<String>,
        judgmentDate: LocalDate,
        at: Instant,
    ) {
        val noticeOn = nthTradingDate(stock.market, judgmentDate, 1)
        val target = if (previous.level == InvestmentAlertLevel.CAUTION) {
            InvestmentAlertLevel.WARNING
        } else {
            InvestmentAlertLevel.DANGER
        }
        val updated = previous.copy(
            escalationNoticeOn = noticeOn,
            escalationNoticeReasons = reasons,
        )
        tradingProtectionSnapshot = tradingProtectionSnapshot.copy(
            investmentAlerts = tradingProtectionSnapshot.investmentAlerts + (stock.id to updated),
        )
        val noticeSession = regularSessionAt(stock.market, noticeOn)
        val noticeEndsOn = nthTradingDate(stock.market, noticeOn, 9)
        addProtectionNews(
            id = "investment-alert-notice:${stock.id}:${target.name}:$noticeOn",
            title = "${stock.name} ${investmentAlertLabel(target)} 지정예고 종목이에요",
            description = "지정예고일부터 종목의 실제 거래일 기준 10일 동안 가격·시장지수 상대 상승 요건을 확인합니다.",
            at = at,
            stock = stock,
            severity = if (target == InvestmentAlertLevel.DANGER) EventSeverity.MAJOR else EventSeverity.MODERATE,
            marketAction = MarketActionReference(
                kind = MarketActionKind.INVESTMENT_ALERT,
                occurrenceId = "investment-alert-notice:${stock.id}:${previous.designatedAt}:$noticeOn",
                transition = MarketActionTransition.DESIGNATION_NOTICE,
                announcedAt = at,
                effectiveAt = noticeSession.opensAt,
                endsAt = regularSessionAt(stock.market, noticeEndsOn).closesAt,
                stockId = stock.id,
                markets = setOf(stock.market),
                alertLevel = target,
                effectiveOn = noticeOn,
            ),
        )
    }

    /** A notice observes the security's trading days, so a stock-specific halt extends its end. */
    private fun refreshInvestmentAlertNoticeEnd(
        stock: StockDefinition,
        venueCloseAt: Instant,
        points: List<DailyTradingSurveillancePoint>,
    ) {
        val designation = tradingProtectionSnapshot.investmentAlerts[stock.id] ?: return
        if (designation.status != InvestmentAlertStatus.ACTIVE) return
        val noticeOn = designation.escalationNoticeOn ?: return
        val noticeIndex = newsEvents.indexOfLast { event ->
            event.marketAction?.let { action ->
                action.kind == MarketActionKind.INVESTMENT_ALERT &&
                    action.transition == MarketActionTransition.DESIGNATION_NOTICE &&
                    action.stockId == stock.id &&
                    action.effectiveOn == noticeOn
            } == true
        }
        if (noticeIndex < 0) return
        val event = newsEvents[noticeIndex]
        val action = requireNotNull(event.marketAction)
        val noticeObservations = points.filter { point -> point.date >= noticeOn }
        val exactEndsAt = when {
            noticeObservations.size >= INVESTMENT_ALERT_NOTICE_TRADING_DAYS -> regularSessionAt(
                stock.market,
                noticeObservations[INVESTMENT_ALERT_NOTICE_TRADING_DAYS - 1].date,
            ).closesAt
            action.endsAt?.let { venueCloseAt >= it } == true -> {
                val nextTradingDate = nthTradingDate(stock.market, marketDate(stock.market, venueCloseAt), 1)
                regularSessionAt(stock.market, nextTradingDate).closesAt
            }
            else -> return
        }
        if (action.endsAt == exactEndsAt) return
        newsEvents[noticeIndex] = event.copy(marketAction = action.copy(endsAt = exactEndsAt))
    }

    private fun designateInvestmentAlert(
        stock: StockDefinition,
        at: Instant,
        onDate: LocalDate,
        level: InvestmentAlertLevel,
        reasonCodes: Set<String>,
        previous: InvestmentAlertDesignation?,
        releaseRule: InvestmentAlertReleaseRule = when (level) {
            InvestmentAlertLevel.CAUTION -> InvestmentAlertReleaseRule.CAUTION_PRICE_VOLUME
            InvestmentAlertLevel.WARNING -> InvestmentAlertReleaseRule.WARNING_60_100
            InvestmentAlertLevel.DANGER -> InvestmentAlertReleaseRule.DANGER_60_100
        },
        preDesignationClose: Double? = null,
        preReleaseClose: Double? = null,
        redesignationReleaseRule: InvestmentAlertReleaseRule? = null,
        redesignationWindow: TradingDayWindow? = null,
        escalationNoticeOn: LocalDate? = null,
        escalationNoticeReasons: Set<String> = emptySet(),
        priorLevelUntilEffective: InvestmentAlertLevel? = null,
        isRedesignation: Boolean = false,
    ) {
        val releaseAfterTradingDays = when (level) {
            InvestmentAlertLevel.CAUTION -> 0
            // 투자경고는 지정일을 포함한 10거래일째, 투자위험은 지정일 다음부터
            // 10거래일을 관찰한다. 종목정지로 관측일이 밀리면 evaluate 쪽에서 더 기다린다.
            InvestmentAlertLevel.WARNING -> 9
            InvestmentAlertLevel.DANGER -> 10
        }
        val reviewStart = nthTradingDate(stock.market, onDate, releaseAfterTradingDays)
        val candidate = InvestmentAlertDesignation(
            stockId = stock.id,
            level = level,
            reasonCodes = reasonCodes,
            summary = when (level) {
                InvestmentAlertLevel.CAUTION -> "최근 거래가 과열돼 주의가 필요해요"
                InvestmentAlertLevel.WARNING -> "주가가 급등해 투자경고로 지정됐어요"
                InvestmentAlertLevel.DANGER -> "가격 변동이 매우 커 투자위험으로 지정됐어요"
            },
            designatedAt = at,
            designatedOn = onDate,
            releaseReviewWindow = TradingDayWindow(
                reviewStart,
                nthTradingDate(stock.market, reviewStart, 260),
            ),
            redesignationWindow = redesignationWindow,
            releaseRule = releaseRule,
            preDesignationClose = preDesignationClose,
            preReleaseClose = preReleaseClose,
            redesignationReleaseRule = redesignationReleaseRule,
            escalationNoticeOn = escalationNoticeOn,
            escalationNoticeReasons = escalationNoticeReasons,
            priorLevelUntilEffective = priorLevelUntilEffective,
            isRedesignation = isRedesignation,
        )
        val designation = TradingProtectionEngine.designateInvestmentAlert(candidate, previous)
        tradingProtectionSnapshot = tradingProtectionSnapshot.copy(
            investmentAlerts = tradingProtectionSnapshot.investmentAlerts + (stock.id to designation),
        )
        val effectiveSession = regularSessionAt(stock.market, onDate)
        addProtectionNews(
            id = "investment-alert:${stock.id}:${level.name}:$onDate",
            title = if (onDate > marketDate(stock.market, at)) {
                "${stock.name} 내일부터 ${investmentAlertLabel(level)} 종목이에요"
            } else {
                "${stock.name} ${investmentAlertLabel(level)} 종목이에요"
            },
            description = "효력일은 $onDate 입니다. " + designation.summary + if (level == InvestmentAlertLevel.WARNING) {
                " 지정일부터 2거래일 뒤 종가가 지정일보다 40% 이상 높고 지정 전일 종가도 웃돌면 다음 거래일 하루 동안 거래가 멈춥니다."
            } else if (level == InvestmentAlertLevel.DANGER) {
                " 최초 투자위험 지정에 따라 지정 효력일의 정규장 거래가 하루 동안 멈춥니다."
            } else {
                ""
            },
            at = at,
            stock = stock,
            severity = when (level) {
                InvestmentAlertLevel.CAUTION -> EventSeverity.MODERATE
                InvestmentAlertLevel.WARNING -> EventSeverity.MAJOR
                InvestmentAlertLevel.DANGER -> EventSeverity.CRITICAL
            },
            marketAction = MarketActionReference(
                kind = MarketActionKind.INVESTMENT_ALERT,
                occurrenceId = investmentAlertOccurrenceId(stock.id, designation.designatedAt),
                transition = MarketActionTransition.DESIGNATED,
                announcedAt = at,
                effectiveAt = effectiveSession.opensAt,
                stockId = stock.id,
                markets = setOf(stock.market),
                alertLevel = level,
                effectiveOn = onDate,
            ),
        )
        maybeStartInvestmentAlertHalt(stock, designation, dailyTradingSurveillance.getValue(stock.id).toList(), at)
    }

    private fun addInvestmentAlertReleaseNews(
        stock: StockDefinition,
        released: InvestmentAlertDesignation,
        announcedAt: Instant,
    ) {
        val effectiveOn = requireNotNull(released.releaseEffectiveOn)
        val effectiveAt = regularSessionAt(stock.market, effectiveOn).opensAt
        addProtectionNews(
            id = "investment-alert-release:${stock.id}:${released.designatedAt}:$effectiveOn",
            title = "${stock.name} ${investmentAlertLabel(released.level)} 지정이 다음 거래일부터 해제돼요",
            description = "해제 심사에서 가격 급등 기준을 다시 충족하지 않아 $effectiveOn 정규장 개장부터 시장경보를 해제합니다.",
            at = announcedAt,
            stock = stock,
            severity = EventSeverity.MINOR,
            marketAction = MarketActionReference(
                kind = MarketActionKind.INVESTMENT_ALERT,
                occurrenceId = investmentAlertOccurrenceId(stock.id, released.designatedAt),
                transition = MarketActionTransition.RELEASE_ANNOUNCED,
                announcedAt = announcedAt,
                effectiveAt = effectiveAt,
                endsAt = effectiveAt,
                stockId = stock.id,
                markets = setOf(stock.market),
                alertLevel = released.level,
                effectiveOn = effectiveOn,
            ),
        )
    }

    private fun maybeStartInvestmentAlertHalt(
        stock: StockDefinition,
        designation: InvestmentAlertDesignation,
        points: List<DailyTradingSurveillancePoint>,
        at: Instant,
    ) {
        if (designation.level == InvestmentAlertLevel.CAUTION) return
        if (designation.level == InvestmentAlertLevel.WARNING &&
            "DANGER_RELEASE_DOWNGRADE" in designation.reasonCodes
        ) {
            return
        }
        val latest = points.lastOrNull()
        val haltDate: LocalDate
        val eventKey: String
        val detail: String
        val description: String
        when (designation.level) {
            InvestmentAlertLevel.WARNING -> {
                val evaluationDate = krxWarningAdditionalRiseEvaluationDate(designation, points) ?: return
                haltDate = nthTradingDate(stock.market, evaluationDate, 1)
                eventKey = "investment-warning-additional-rise-halt:${stock.id}:${designation.designatedOn}"
                detail = "투자경고 지정 후 2거래일간 추가 급등"
                description =
                    "투자경고 지정일부터 2거래일 뒤 종가가 지정일 종가보다 40% 이상 높고 지정 전일 종가도 웃돌아 다음 거래일 정규장 거래를 정지합니다."
            }

            InvestmentAlertLevel.DANGER -> {
                // The designation is announced after today's close and becomes effective on the
                // stored next trading date. If another halt overlaps, retry on a later review day
                // instead of overwriting that independent regulatory state.
                val nextTradingDate = nthTradingDate(
                    stock.market,
                    latest?.date ?: marketDate(stock.market, at),
                    1,
                )
                haltDate = maxOf(designation.designatedOn, nextTradingDate)
                eventKey = "investment-danger-initial-halt:${stock.id}:${designation.designatedOn}"
                detail = "최초 투자위험 지정"
                description = "최초 투자위험 지정에 따라 지정 효력일의 정규장 거래를 하루 동안 정지합니다."
            }

            InvestmentAlertLevel.CAUTION -> return
        }
        if (newsEvents.any { event -> event.marketAction?.occurrenceId == eventKey }) return
        val session = GameCalendar.regularSessionWindow(
            stock.market,
            haltDate,
            runtimeClosedDates(stock.market, haltDate),
        ) ?: return
        val halt = TradingProtectionEngine.startInstrumentTradingHalt(
            occurrenceId = eventKey,
            stockId = stock.id,
            reason = TradingHaltReason.REGULATORY_ACTION,
            detail = detail,
            startedAt = session.opensAt,
            scheduledReleaseAt = session.closesAt,
        )
        tradingProtectionSnapshot = tradingProtectionSnapshot.copy(
            scheduledInstrumentTradingHalts =
                tradingProtectionSnapshot.scheduledInstrumentTradingHalts + (eventKey to halt),
        )
        addProtectionNews(
            id = eventKey,
            title = "${stock.name} 다음 거래일 거래가 멈춰요",
            description = description,
            at = at,
            stock = stock,
            severity = EventSeverity.CRITICAL,
            marketAction = MarketActionReference(
                kind = MarketActionKind.INSTRUMENT_TRADING_HALT,
                occurrenceId = eventKey,
                transition = MarketActionTransition.HALT_SCHEDULED,
                announcedAt = at,
                effectiveAt = session.opensAt,
                endsAt = session.closesAt,
                stockId = stock.id,
                markets = setOf(stock.market),
            ),
        )
    }

    private fun nthTradingDate(market: Market, start: LocalDate, count: Int): LocalDate {
        var date = start
        var remaining = count
        while (remaining > 0 && date < LocalDate(2040, 12, 31)) {
            date = date.plus(1, DateTimeUnit.DAY)
            if (isTradingDate(market, date)) remaining -= 1
        }
        return date
    }

    private fun investmentAlertLabel(level: InvestmentAlertLevel): String = when (level) {
        InvestmentAlertLevel.CAUTION -> "투자주의"
        InvestmentAlertLevel.WARNING -> "투자경고"
        InvestmentAlertLevel.DANGER -> "투자위험"
    }

    private fun cancelOpenOrdersForStock(stockId: String, at: Instant, reason: String) {
        for (index in orders.indices) {
            val order = orders[index]
            if (order.stockId == stockId && order.isOpen) {
                orders[index] = order.copy(
                    status = OrderStatus.CANCELLED,
                    updatedAt = at,
                    rejectionReason = reason,
                )
            }
        }
    }

    private fun syncListingTradingProtections(
        stock: StockDefinition,
        previous: ListingLifecycleState,
        current: ListingLifecycleState,
        at: Instant,
    ) {
        val volatilityInterruptions = tradingProtectionSnapshot.krxVolatilityInterruptions.toMutableMap()
        val halts = tradingProtectionSnapshot.instrumentTradingHalts.toMutableMap()
        val scheduledHalts = tradingProtectionSnapshot.scheduledInstrumentTradingHalts.toMutableMap()
        val investmentAlerts = tradingProtectionSnapshot.investmentAlerts.toMutableMap()
        val luldStates = tradingProtectionSnapshot.usLuldStates.toMutableMap()
        val existing = halts[stock.id]
        val existingIsListingHalt = existing?.reason in LISTING_TRADING_HALT_REASONS
        val desiredListingHaltReason = if (current.status == ListingLifecycleStatus.DELISTING_SCHEDULED) {
            TradingHaltReason.DELISTING_PROCESS
        } else {
            TradingHaltReason.LISTING_MAINTENANCE_REVIEW
        }
        if (!current.isIndexEligible) {
            // Once settlement or final disposition starts, the listing lifecycle is the sole source
            // of truth. Instrument-scoped market protections must not survive or be recreated.
            volatilityInterruptions.remove(stock.id)
            halts.remove(stock.id)
            scheduledHalts.entries.removeAll { (_, halt) -> halt.stockId == stock.id }
            investmentAlerts.remove(stock.id)
            luldStates.remove(stock.id)
        } else if (!current.isTradable &&
            (
                existing == null || existing.status == TradingHaltStatus.RELEASED ||
                    existingIsListingHalt && existing.reason != desiredListingHaltReason
                )
        ) {
            halts[stock.id] = TradingProtectionEngine.startInstrumentTradingHalt(
                occurrenceId = "listing-halt:${stock.id}:${current.ledgerSequence}",
                stockId = stock.id,
                reason = desiredListingHaltReason,
                detail = current.activeReason?.displayName ?: current.status.displayName,
                startedAt = at,
            )
        } else if (!previous.isTradable && current.isTradable && existingIsListingHalt &&
            existing?.status == TradingHaltStatus.ACTIVE
        ) {
            halts[stock.id] = TradingProtectionEngine.releaseInstrumentTradingHalt(
                existing,
                at,
                "상장 유지 조치가 해제됐습니다.",
            )
        }
        tradingProtectionSnapshot = tradingProtectionSnapshot.copy(
            krxVolatilityInterruptions = volatilityInterruptions,
            instrumentTradingHalts = halts,
            scheduledInstrumentTradingHalts = scheduledHalts,
            investmentAlerts = investmentAlerts,
            usLuldStates = luldStates,
        )
    }

    private fun syncEventDrivenTradingHalts(from: Instant, to: Instant) {
        val newDisclosureEvents = newsEvents.filter { event ->
            event.startsAt >= from && event.startsAt < to &&
                event.affectedStockIds.isNotEmpty() && event.tradingHaltDirective != null
        }
        if (newDisclosureEvents.isEmpty()) return
        val halts = tradingProtectionSnapshot.instrumentTradingHalts.toMutableMap()
        val scheduledHalts = tradingProtectionSnapshot.scheduledInstrumentTradingHalts.toMutableMap()
        for (event in newDisclosureEvents) {
            val directive = requireNotNull(event.tradingHaltDirective)
            for (stockId in event.affectedStockIds) {
                val stock = stockById[stockId] ?: continue
                if (stock.market !in directive.eligibleMarkets) continue
                val releaseAt = event.startsAt + directive.durationMinutes.minutes
                val occurrenceId = "event-halt:$stockId:${event.id}"
                val halt = TradingProtectionEngine.startInstrumentTradingHalt(
                    occurrenceId = occurrenceId,
                    stockId = stockId,
                    reason = directive.reason,
                    detail = directive.detail,
                    startedAt = event.startsAt,
                    scheduledReleaseAt = releaseAt,
                )
                val current = halts[stockId]
                if (current == null || !TradingProtectionEngine.isInstrumentHaltActive(current, event.startsAt)) {
                    halts[stockId] = halt
                } else {
                    // 원인별 정지를 보존해 첫 정지가 끝난 뒤에도 두 번째 공시의 남은 구간을
                    // permission·가격봉·주문 체결이 동일하게 차감하도록 한다.
                    scheduledHalts[occurrenceId] = halt
                }
                addProtectionNews(
                    id = occurrenceId,
                    title = "${stock.name} 거래가 잠시 멈췄어요",
                    description = "${directive.detail}을 위해 ${directive.durationMinutes}분간 거래를 정지합니다. 기존 주문은 취소할 수 있습니다.",
                    at = event.startsAt,
                    stock = stock,
                    severity = EventSeverity.MAJOR,
                    marketAction = MarketActionReference(
                        kind = MarketActionKind.INSTRUMENT_TRADING_HALT,
                        occurrenceId = occurrenceId,
                        transition = MarketActionTransition.HALT_STARTED,
                        announcedAt = event.startsAt,
                        effectiveAt = halt.startedAt,
                        endsAt = halt.scheduledReleaseAt,
                        stockId = stock.id,
                        markets = setOf(stock.market),
                    ),
                )
            }
        }
        tradingProtectionSnapshot = tradingProtectionSnapshot.copy(
            instrumentTradingHalts = halts,
            scheduledInstrumentTradingHalts = scheduledHalts,
        )
    }

    private fun applyListingFinalDisposition(
        stock: StockDefinition,
        state: ListingLifecycleState,
        at: Instant,
    ) {
        val disposition = state.finalDisposition ?: return
        if (pendingCorporateActions.any { it.stockId == stock.id }) {
            val terminalEvent = requireNotNull(
                listingLifecycleLedger.singleOrNull { event ->
                    event.stockId == stock.id && event.sequence == state.ledgerSequence &&
                        event.toStatus == state.status
                },
            ) { "최종 상장 상태에 대응하는 원장 이벤트가 없습니다." }
            cancelPendingCorporateActions(stock, at, terminalEvent)
        }
        pendingEtfReferenceReturns.remove(stock.id)
        pendingClosedEventLogReturns.remove(stock.id)
        pendingFundFlowRates.remove(stock.id)

        when (disposition.type) {
            ListingFinalDispositionType.CASH_LIQUIDATION -> settleListingCashDisposition(
                stock = stock,
                at = at,
                disposition = disposition,
                reason = disposition.type.displayName,
            )

            ListingFinalDispositionType.MARKET_SALE -> settleListingMarketSale(
                stock = stock,
                at = at,
                unitPrice = quotes.getValue(stock.id).price,
                reason = disposition.type.displayName,
            )

            ListingFinalDispositionType.WORTHLESS_DISPOSITION -> {
                val quote = quotes.getValue(stock.id)
                quotes[stock.id] = quote.copy(
                    timestamp = at,
                    price = 0.0,
                    open = 0.0,
                    high = 0.0,
                    low = 0.0,
                    volume = 0L,
                    bidPrice = null,
                    askPrice = null,
                    bidQuantity = 0.0,
                    askQuantity = 0.0,
                    session = MarketSession.CLOSED,
                )
                holdings[stock.id]?.let { holding ->
                    // Keep the legal right and FIFO lot at zero value. A tax loss is not invented
                    // until a later legally recognizable disposal exists.
                    holdings[stock.id] = holding.copy(currentPrice = 0.0)
                }
            }

            ListingFinalDispositionType.OTC_TRANSFER -> {
                // This simulator has no OTC order venue. The frozen holding remains visible as a
                // non-tradable right. Until an OTC valuation engine exists it has zero portfolio
                // value, rather than silently retaining the last exchange quote forever.
                quotes[stock.id] = quotes.getValue(stock.id).copy(
                    timestamp = at,
                    price = 0.0,
                    open = 0.0,
                    high = 0.0,
                    low = 0.0,
                    volume = 0L,
                    bidPrice = null,
                    askPrice = null,
                    bidQuantity = 0.0,
                    askQuantity = 0.0,
                    session = MarketSession.CLOSED,
                )
                holdings[stock.id]?.let { holding ->
                    holdings[stock.id] = holding.copy(currentPrice = 0.0)
                }
            }
        }
    }

    private fun settleListingCashDisposition(
        stock: StockDefinition,
        at: Instant,
        disposition: ListingFinalDisposition,
        reason: String,
    ) {
        val holding = holdings.remove(stock.id) ?: return
        val quantity = requireNotNull(disposition.entitledQuantity)
        require(kotlin.math.abs(quantity - holding.quantity) <= QUANTITY_EPSILON) {
            "청산 효력일에 확정된 권리 수량과 보유 수량이 일치하지 않습니다."
        }
        // A contractual recovery can legally be zero. A minimum exchange tick only applies to an
        // order-book trade and must never manufacture liquidation proceeds.
        val price = requireNotNull(disposition.cashPerUnit).coerceAtLeast(0.0)
        val gross = roundCurrency(price * quantity, stock.currency)
        val paidOn = marketDate(stock.market, at)
        val taxBreakdown = if (stock.market.isKorean && stock.etfProfile != null) {
            val acquisitionValueKrw = round(requireNotNull(disposition.entitledCostBasis)).toLong()
            val positiveGain = (gross.toLong() - acquisitionValueKrw).coerceAtLeast(0L)
            domesticEtfSaleTaxCalculator.calculate(
                DomesticEtfSaleTaxRequest(
                    taxCategory = stock.etfProfile.taxCategory,
                    grossProceedsKrw = gross.toLong(),
                    acquisitionValueKrw = acquisitionValueKrw,
                    taxableStandardGainKrw = round(
                        positiveGain * stock.etfProfile.taxablePriceGainRatio,
                    ).toLong(),
                    soldOn = paidOn,
                ),
            )
        } else {
            null
        }
        val saleTax = taxBreakdown?.totalTax?.amount ?: 0.0
        cash[stock.currency] = roundCurrency(
            cash.getValue(stock.currency) + gross - saleTax,
            stock.currency,
        )
        val exchangeRateToKrw = if (stock.currency == Currency.USD) macro.usdKrw else 1.0
        val fifoSale = fifoCostBasisBook.sell(
            stockId = stock.id,
            soldOn = paidOn,
            quantity = quantity,
            grossProceedsKrw = round(gross * exchangeRateToKrw).toLong(),
            directSellingCostsKrw = round(saleTax * exchangeRateToKrw).toLong(),
        )
        fifoCostBasisBook = fifoSale.updatedBook
        val (taxTreatment, assessmentNotes) = assessStockGainTreatment(stock, paidOn, holding)
        val orderId = nextId("listing-disposition-order")
        val tradeId = nextId("trade")
        val accountingSequence = nextSequence++
        orders += Order(
            id = orderId,
            stockId = stock.id,
            side = OrderSide.SELL,
            type = OrderType.MARKET,
            quantity = quantity,
            createdAt = at,
            status = OrderStatus.FILLED,
            filledQuantity = quantity,
            averageFilledPrice = price,
            updatedAt = at,
            timeInForce = TimeInForce.DAY,
            rejectionReason = reason,
            isNonMarketDisposition = true,
        )
        trades += Trade(
            id = tradeId,
            orderId = orderId,
            stockId = stock.id,
            side = OrderSide.SELL,
            quantity = quantity,
            price = price,
            currency = stock.currency,
            executedAt = at,
            tax = saleTax,
            settlementKind = TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT,
            settlementDateOverride = paidOn,
            accountingSequence = accountingSequence,
        )
        transactionCosts += TransactionCostRecord(
            tradeId = tradeId,
            stockId = stock.id,
            market = stock.market,
            paidAt = at,
            currency = stock.currency,
            commission = 0.0,
            saleTax = saleTax,
            exchangeRateToKrw = exchangeRateToKrw,
            taxBreakdown = taxBreakdown,
        )
        realizedGains += RealizedGainRecord(
            tradeId = tradeId,
            stockId = stock.id,
            market = stock.market,
            soldAt = at,
            settlementDate = paidOn,
            quantity = quantity,
            proceeds = gross,
            costBasis = requireNotNull(disposition.entitledCostBasis),
            commission = 0.0,
            saleTax = saleTax,
            currency = stock.currency,
            exchangeRateToKrw = exchangeRateToKrw,
            taxTreatment = taxTreatment,
            assessmentNotes = assessmentNotes + "$reason 지급일의 환율과 원천징수를 처분 원장에 반영했습니다.",
            taxGrossProceedsKrw = fifoSale.grossProceedsKrw,
            taxCostBasisKrw = fifoSale.allocatedCostBasisKrw,
            taxDirectSellingCostsKrw = fifoSale.directSellingCostsKrw,
            taxGainKrw = fifoSale.realizedGainKrw,
            taxableFinancialIncomeKrw = if (
                stock.etfProfile?.taxCategory == EtfTaxCategory.KOREAN_OTHER
            ) {
                taxBreakdown?.taxableBase?.minorUnits ?: 0L
            } else {
                0L
            },
        )
        taxExchangeRatesByTradeId[tradeId] = exchangeRateToKrw
        // This record is created on the contractual cash-payment date, so the settlement FX is
        // already known and must not enter the pending T+ settlement queue.
        recalculateAnnualTax(paidOn.year)
    }

    /** A closing-auction/cleanup-period sale uses the same fees and Korean sale taxes as an order. */
    private fun settleListingMarketSale(
        stock: StockDefinition,
        at: Instant,
        unitPrice: Double,
        reason: String,
    ) {
        val holding = holdings.remove(stock.id) ?: return
        val price = MarketMicrostructure.roundNearest(
            stock,
            unitPrice.coerceAtLeast(MarketMicrostructure.minimumPrice(stock.market)),
        )
        val gross = roundCurrency(price * holding.quantity, stock.currency)
        val tradedOn = marketDate(stock.market, at)
        val settledOn = settlementDate(stock.market, tradedOn)
        val feeBreakdown = brokerFeeCalculator.calculate(
            BrokerFeeRequest(
                market = stock.market,
                side = OrderSide.SELL,
                grossAmount = money(gross, stock.currency),
                quantity = holding.quantity,
                tradedOn = tradedOn,
            ),
        )
        val commission = feeBreakdown.totalFees.amount
        val taxBreakdown = when {
            !stock.market.isKorean -> null
            stock.etfProfile != null -> {
                val acquisitionValueKrw = round(holding.averagePrice * holding.quantity).toLong()
                val positiveGain = (gross.toLong() - acquisitionValueKrw).coerceAtLeast(0L)
                domesticEtfSaleTaxCalculator.calculate(
                    DomesticEtfSaleTaxRequest(
                        taxCategory = stock.etfProfile.taxCategory,
                        grossProceedsKrw = gross.toLong(),
                        acquisitionValueKrw = acquisitionValueKrw,
                        taxableStandardGainKrw = round(
                            positiveGain * stock.etfProfile.taxablePriceGainRatio,
                        ).toLong(),
                        soldOn = tradedOn,
                    ),
                )
            }
            else -> domesticSaleTaxCalculator.calculate(
                DomesticSaleTaxRequest(
                    market = stock.market,
                    grossProceedsKrw = gross.toLong(),
                    soldOn = tradedOn,
                ),
            )
        }
        val saleTax = taxBreakdown?.totalTax?.amount ?: 0.0
        cash[stock.currency] = roundCurrency(
            cash.getValue(stock.currency) + gross - commission - saleTax,
            stock.currency,
        )
        val exchangeRateToKrw = if (stock.currency == Currency.USD) macro.usdKrw else 1.0
        val fifoSale = fifoCostBasisBook.sell(
            stockId = stock.id,
            soldOn = settledOn,
            quantity = holding.quantity,
            grossProceedsKrw = round(gross * exchangeRateToKrw).toLong(),
            directSellingCostsKrw = round((commission + saleTax) * exchangeRateToKrw).toLong(),
        )
        fifoCostBasisBook = fifoSale.updatedBook
        val (taxTreatment, assessmentNotes) = assessStockGainTreatment(stock, tradedOn, holding)
        val orderId = nextId("listing-market-sale-order")
        val tradeId = nextId("trade")
        val accountingSequence = nextSequence++
        orders += Order(
            id = orderId,
            stockId = stock.id,
            side = OrderSide.SELL,
            type = OrderType.MARKET,
            quantity = holding.quantity,
            createdAt = at,
            status = OrderStatus.FILLED,
            filledQuantity = holding.quantity,
            averageFilledPrice = price,
            updatedAt = at,
            timeInForce = TimeInForce.DAY,
            rejectionReason = reason,
        )
        trades += Trade(
            id = tradeId,
            orderId = orderId,
            stockId = stock.id,
            side = OrderSide.SELL,
            quantity = holding.quantity,
            price = price,
            currency = stock.currency,
            executedAt = at,
            commission = commission,
            tax = saleTax,
            accountingSequence = accountingSequence,
        )
        transactionCosts += TransactionCostRecord(
            tradeId = tradeId,
            stockId = stock.id,
            market = stock.market,
            paidAt = at,
            currency = stock.currency,
            commission = commission,
            saleTax = saleTax,
            exchangeRateToKrw = exchangeRateToKrw,
            feeBreakdown = feeBreakdown,
            taxBreakdown = taxBreakdown,
        )
        realizedGains += RealizedGainRecord(
            tradeId = tradeId,
            stockId = stock.id,
            market = stock.market,
            soldAt = at,
            settlementDate = settledOn,
            quantity = holding.quantity,
            proceeds = gross,
            costBasis = holding.averagePrice * holding.quantity,
            commission = commission,
            saleTax = saleTax,
            currency = stock.currency,
            exchangeRateToKrw = exchangeRateToKrw,
            taxTreatment = taxTreatment,
            assessmentNotes = assessmentNotes + "$reason 처분을 일반 매도 원장에 반영했습니다.",
            taxGrossProceedsKrw = fifoSale.grossProceedsKrw,
            taxCostBasisKrw = fifoSale.allocatedCostBasisKrw,
            taxDirectSellingCostsKrw = fifoSale.directSellingCostsKrw,
            taxGainKrw = fifoSale.realizedGainKrw,
            taxableFinancialIncomeKrw = if (
                stock.etfProfile?.taxCategory == EtfTaxCategory.KOREAN_OTHER
            ) {
                taxBreakdown?.taxableBase?.minorUnits ?: 0L
            } else {
                0L
            },
        )
        taxExchangeRatesByTradeId[tradeId] = exchangeRateToKrw
        if (stock.market.isUnitedStates) pendingTaxSettlementTradeIds += tradeId
        recalculateAnnualTax(settledOn.year)
    }

    /** ETN처럼 계약상 만기가 있는 상품은 사전 알림과 실제 상환을 캠페인 원장에 남긴다. */
    private fun processInstrumentLifecycle(at: Instant) {
        announceDirectUnderlyingFundLiquidations(at)
        for (stock in stocks) {
            if (stock.instrumentType != InstrumentType.ETN) continue
            if (listingLifecycleStates.getValue(stock.id).isTerminal) continue
            val maturity = instrumentMaturityDate(stock) ?: continue
            val localDate = marketDate(stock.market, at)
            val effectiveMaturityDate = nextTradingDateOnOrAfter(stock.market, maturity)
            val milestone = when (localDate) {
                maturity.minus(5, DateTimeUnit.YEAR) -> "5년"
                maturity.minus(1, DateTimeUnit.YEAR) -> "1년"
                maturity.minus(90, DateTimeUnit.DAY) -> "90일"
                maturity.minus(30, DateTimeUnit.DAY) -> "30일"
                else -> null
            }
            if (milestone != null) announceMaturityMilestone(stock, maturity, milestone, at)
            if (localDate != effectiveMaturityDate) continue
            val eventId = "instrument-maturity-effective:${stock.id}:$maturity"
            if (newsEvents.any { it.id == eventId }) continue
            val session = GameCalendar.regularSessionWindow(
                stock.market,
                effectiveMaturityDate,
                runtimeClosedDates(stock.market, effectiveMaturityDate),
            ) ?: continue
            if (at >= session.closesAt) continue
            // Announce during the maturity date, then let the same listing engine used by ETF
            // liquidations move through scheduled delisting -> cash pending -> payment. This
            // preserves a final valuation session and prevents midnight auto-payment.
            val event = GameEvent(
                id = eventId,
                title = "${stock.name} 계약상 만기일이에요",
                description = "$maturity 계약 만기${if (effectiveMaturityDate != maturity) "의 다음 거래일인 $effectiveMaturityDate" else ""} 정규장 종료 뒤 최종 지표가치를 확정하고 현금 상환 절차를 시작합니다.",
                scope = EventScope.STOCK,
                type = EventType.FUND_OPERATION,
                severity = EventSeverity.MAJOR,
                impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
                startsAt = at,
                durationHours = 24,
                recordKind = EventRecordKind.INSTRUMENT_LIFECYCLE,
                affectedMarkets = setOf(stock.market),
                affectedSectors = setOf(stock.sector),
                affectedStockIds = setOf(stock.id),
                sourceLabel = "공식 상품조건 기반 캠페인 일정",
                instrumentTermination = InstrumentTerminationTerms(
                    kind = InstrumentTerminationKind.CONTRACTUAL_MATURITY,
                    contractualDate = maturity,
                    valuationMethod =
                        InstrumentTerminationValuationMethod.ETN_CONTRACT_SETTLEMENT,
                ),
            )
            newsEvents += event
        }
    }

    /**
     * 단일 기업을 직접 기초로 삼는 상품은 그 기업의 청산·상장 종료 뒤에도 유령 가격으로
     * 계속 운용될 수 없다. 기초기업이 지수 적격성을 잃은 첫 경계에서 상품 청산 공시를
     * 확정해, 기존 상장 생명주기 엔진이 다음 거래소 종가의 최종 NAV와 현금 분배를 맡는다.
     *
     * 복합 기준의 일부 직접 편입 종목은 별도 source-to-cash 원장으로 처리하므로 여기에는
     * 상품 수익 전체를 직접 지배하는 daily-reset/option reference만 포함한다.
     */
    private fun announceDirectUnderlyingFundLiquidations(at: Instant) {
        for (stock in stocks) {
            val listingState = listingLifecycleStates.getValue(stock.id)
            if (listingState.isSettlementPending || listingState.isTerminal) continue
            val directUnderlyingIds = directLiquidatingUnderlyingIds(stock)
            if (directUnderlyingIds.isEmpty()) continue
            val unavailableUnderlyingIds = directUnderlyingIds.filter { underlyingId ->
                listingLifecycleStates[underlyingId]?.isIndexEligible == false
            }
            if (unavailableUnderlyingIds.isEmpty()) continue
            announceDirectUnderlyingFundLiquidation(
                stock = stock,
                underlyingIds = unavailableUnderlyingIds,
                announcedAt = at,
                effectiveNotBefore = at,
            )
        }
    }

    /**
     * 같은 거래소 종가에서 기초기업과 단일종목 상품을 함께 종료할 수 있도록, 이미 발표된
     * 기초기업 종료 공시 또는 확정된 상장폐지 일정을 가격 계산 전에 상품 공시로 전파한다.
     * 이 경계가 있어 옵션형 상품도 마지막 NAV를 확정하는 종가에 새 주기를 열지 않는다.
     */
    private fun announceDueDirectUnderlyingFundLiquidations(from: Instant, to: Instant) {
        val dueUnderlyingCloseById = directlyReferencedInstrumentIds.mapNotNull { underlyingId ->
            val underlying = stockById.getValue(underlyingId)
            val lifecycle = listingLifecycleStates.getValue(underlyingId)
            if (!lifecycle.isIndexEligible) return@mapNotNull underlyingId to from
            val date = marketDate(underlying.market, from)
            val closeAt = GameCalendar.regularSessionWindow(
                underlying.market,
                date,
                runtimeClosedDates(underlying.market, date),
            )?.closesAt ?: return@mapNotNull null
            if (closeAt <= from || closeAt > to) return@mapNotNull null
            val scheduledByListing =
                lifecycle.status == ListingLifecycleStatus.DELISTING_SCHEDULED &&
                    lifecycle.scheduledDelistingOn?.let { scheduledOn ->
                        nextTradingDateOnOrAfter(underlying.market, scheduledOn) == date
                    } == true
            val scheduledByTerminationOn = resolveInstrumentTerminationAtSessionClose(
                stock = underlying,
                events = newsEvents,
                evaluatedOn = date,
                incumbentOccurrenceId = lifecycle.controllingTerminationOccurrenceId,
            )?.scheduledTerminationOn
            val scheduledByTermination = scheduledByTerminationOn?.let { scheduledOn ->
                nextTradingDateOnOrAfter(underlying.market, scheduledOn) == date
            } == true
            underlyingId.takeIf { scheduledByListing || scheduledByTermination }?.let { it to closeAt }
        }.toMap()
        if (dueUnderlyingCloseById.isEmpty()) return

        for (stock in stocks) {
            val listingState = listingLifecycleStates.getValue(stock.id)
            if (listingState.isSettlementPending || listingState.isTerminal) continue
            val directIds = directLiquidatingUnderlyingIds(stock)
            val dueIds = directIds.filter(dueUnderlyingCloseById::containsKey)
            if (dueIds.isEmpty()) continue
            announceDirectUnderlyingFundLiquidation(
                stock = stock,
                underlyingIds = dueIds,
                announcedAt = from,
                effectiveNotBefore = dueIds.maxOf(dueUnderlyingCloseById::getValue),
            )
        }
    }

    private fun directLiquidatingUnderlyingIds(stock: StockDefinition): List<String> {
        val product = stock.fundProductProfile ?: return emptyList()
        return buildSet {
            listOfNotNull(
                product.dailyResetTerms?.let { terms ->
                    terms.reference to terms.directReferenceTerminationRule
                },
                product.optionStrategyTerms?.let { terms ->
                    terms.reference to terms.directReferenceTerminationRule
                },
                product.cashCollateralizedPutSpreadTerms?.let { terms ->
                    terms.optionReference to terms.directReferenceTerminationRule
                },
            ).forEach { (reference, rule) ->
                if (reference.kind != DailyResetReferenceKind.INSTRUMENT) return@forEach
                when (requireNotNull(rule).policy) {
                    DirectReferenceTerminationPolicy.LIQUIDATE_AT_NEXT_VENUE_CLOSE ->
                        add(requireNotNull(reference.instrumentId))
                }
            }
        }.toList().sorted()
    }

    private fun announceDirectUnderlyingFundLiquidation(
        stock: StockDefinition,
        underlyingIds: List<String>,
        announcedAt: Instant,
        effectiveNotBefore: Instant,
    ) {
        val sortedIds = underlyingIds.sorted().distinct()
        val eventId = "direct-underlying-liquidation:${stock.id}:${sortedIds.joinToString("+")}"
        if (newsEvents.any { event -> event.id == eventId }) return
        val unavailableNames = sortedIds.joinToString(", ") { underlyingId ->
            stockById.getValue(underlyingId).name
        }
        newsEvents += GameEvent(
            id = eventId,
            title = "${stock.name} 기초자산 종료에 따른 청산 절차",
            description =
                "$unavailableNames 종목이 청산·상장 종료 단계에 들어가 더 이상 직접 " +
                    "기초수익률을 산출할 수 없습니다. 다음 거래소 종가에 최종 순자산가치를 " +
                    "확정하고 현금 분배 절차를 시작합니다.",
            scope = EventScope.STOCK,
            type = EventType.FUND_OPERATION,
            severity = EventSeverity.CRITICAL,
            impact = GameEventImpact(direction = ImpactDirection.NEGATIVE),
            startsAt = announcedAt,
            durationHours = 24,
            recordKind = EventRecordKind.INSTRUMENT_LIFECYCLE,
            affectedMarkets = setOf(stock.market),
            affectedSectors = setOf(stock.sector),
            affectedStockIds = setOf(stock.id),
            sourceLabel = "직접 기초자산 생명주기 연동",
            instrumentTermination = InstrumentTerminationTerms(
                kind = InstrumentTerminationKind.FUND_LIQUIDATION,
                effectiveNotBefore = effectiveNotBefore,
                valuationMethod = InstrumentTerminationValuationMethod.FINAL_NET_ASSET_VALUE,
            ),
        )
    }

    private fun hasPublishedDirectUnderlyingLiquidation(productId: String): Boolean =
        newsEvents.any { event ->
            event.id.startsWith("direct-underlying-liquidation:$productId:") &&
                event.instrumentTermination?.kind == InstrumentTerminationKind.FUND_LIQUIDATION
        }

    private fun announceMaturityMilestone(
        stock: StockDefinition,
        maturity: LocalDate,
        milestone: String,
        at: Instant,
    ) {
        val eventId = "instrument-maturity-notice:${stock.id}:$milestone"
        if (newsEvents.any { it.id == eventId }) return
        newsEvents += GameEvent(
            id = eventId,
            title = "${stock.name} 만기 $milestone 전",
            description = "계약상 만기일 $maturity 전 사전 안내입니다. ETN은 ETF와 달리 발행사의 무담보 채무이며 만기상환·조기상환·발행사 신용 위험이 있습니다.",
            scope = EventScope.STOCK,
            type = EventType.FUND_OPERATION,
            severity = if (milestone == "30일") EventSeverity.MAJOR else EventSeverity.MODERATE,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = at,
            durationHours = if (milestone == "30일") 720 else 168,
            recordKind = EventRecordKind.INSTRUMENT_LIFECYCLE,
            affectedMarkets = setOf(stock.market),
            affectedSectors = setOf(stock.sector),
            affectedStockIds = setOf(stock.id),
            sourceLabel = "공식 상품조건 기반 캠페인 일정",
        )
    }

    private fun instrumentMaturityDate(stock: StockDefinition): LocalDate? =
        stock.identityProfile?.maturityDate?.let(LocalDate::parse)

    private fun nextTradingDateOnOrAfter(market: Market, date: LocalDate): LocalDate {
        var result = date
        while (!isTradingDate(market, result) && result < LocalDate(2040, 12, 31)) {
            result = result.plus(1, DateTimeUnit.DAY)
        }
        return result
    }

    private fun isInstrumentMatured(stock: StockDefinition, _at: Instant): Boolean =
        listingLifecycleStates[stock.id]?.isTerminal == true

    private fun validateCorporateActionState(
        pending: List<PendingCorporateAction>,
        applied: List<CorporateActionRecord>,
        validStockIds: Set<String>,
    ) {
        val pendingIds = pending.map(PendingCorporateAction::id)
        val appliedIds = applied.map(CorporateActionRecord::id)
        require(pendingIds.distinct().size == pendingIds.size) { "예정 기업행동 ID가 중복되었습니다." }
        require(appliedIds.distinct().size == appliedIds.size) { "적용 기업행동 ID가 중복되었습니다." }
        require(pendingIds.none(appliedIds.toSet()::contains)) { "같은 기업행동이 예정·적용 원장에 동시에 있습니다." }
        require(pending.all { action ->
            action.id.isNotBlank() && action.stockId in validStockIds && action.rationale.isNotBlank() &&
                action.effectiveNotBefore > action.announcedAt && action.quantityMultiplier.isFinite() &&
                ((action.kind == CorporateActionKind.FORWARD_SPLIT && action.quantityMultiplier > 1.0) ||
                    (action.kind == CorporateActionKind.REVERSE_SPLIT && action.quantityMultiplier in 0.0..<1.0))
        }) { "예정 기업행동 값 또는 시간 순서가 올바르지 않습니다." }
        require(applied.all { action ->
            action.id.isNotBlank() && action.stockId in validStockIds && action.rationale.isNotBlank() &&
                action.effectiveNotBefore > action.announcedAt &&
                action.effectiveAt >= action.effectiveNotBefore && action.quantityMultiplier.isFinite() &&
                action.preActionPrice > 0.0 && action.postActionPrice > 0.0 &&
                abs(action.preActionPrice / action.quantityMultiplier - action.postActionPrice) <=
                maxOf(0.02, action.postActionPrice * 0.02) &&
                ((action.kind == CorporateActionKind.FORWARD_SPLIT && action.quantityMultiplier > 1.0) ||
                    (action.kind == CorporateActionKind.REVERSE_SPLIT && action.quantityMultiplier in 0.0..<1.0)) &&
                action.accountingSequence > 0L
        }) { "적용 기업행동 값 또는 시간 순서가 올바르지 않습니다." }
        require(applied.zipWithNext().all { (left, right) -> left.effectiveAt <= right.effectiveAt }) {
            "기업행동 원장의 시간 순서가 올바르지 않습니다."
        }
    }

    private fun rebuildDynamicStockDefinitions(actions: List<CorporateActionRecord>) {
        mutableStocks.clear()
        mutableStocks += baseStockDefinitions
        stockById.clear()
        stockById.putAll(mutableStocks.associateBy(StockDefinition::id))
        actions.forEach { action -> applyDynamicShareMultiplier(action.stockId, action.quantityMultiplier) }
    }

    private fun applyDynamicShareMultiplier(stockId: String, multiplier: Double) {
        val current = stockById.getValue(stockId)
        val adjustedShares = round(current.sharesOutstanding.toDouble() * multiplier)
            .toLong()
            .coerceAtLeast(1L)
        val adjusted = current.copy(sharesOutstanding = adjustedShares)
        val index = mutableStocks.indexOfFirst { it.id == stockId }
        require(index >= 0) { "기업행동 대상 종목이 카탈로그에 없습니다." }
        mutableStocks[index] = adjusted
        stockById[stockId] = adjusted
    }

    private fun sharesOutstandingAt(stockId: String, at: Instant): Long {
        var shares = baseStockById.getValue(stockId).sharesOutstanding
        corporateActionLedger.asSequence()
            .filter { it.stockId == stockId && it.effectiveAt <= at }
            .sortedBy(CorporateActionRecord::effectiveAt)
            .forEach { action ->
                shares = round(shares.toDouble() * action.quantityMultiplier).toLong().coerceAtLeast(1L)
            }
        return shares
    }

    private fun applyCorporateAction(action: PendingCorporateAction, effectiveAt: Instant) {
        val stock = stockById.getValue(action.stockId)
        require(isCorporateActionProductStateEligible(stock)) {
            "Corporate action is not executable for the product's current legal/strategy state: ${stock.id}"
        }
        val multiplier = action.quantityMultiplier
        val actionAccountingSequence = nextSequence
        val adjustedFundFinancialState = fundFinancialStates[stock.id]?.let { state ->
            instrumentMetricsEngine.applyFundUnitAdjustment(
                state = state,
                quantityMultiplier = multiplier,
                corporateActionAccountingSequence = actionAccountingSequence,
                at = effectiveAt,
            )
        }
        val adjustedDailyResetState = dailyResetStates[stock.id]?.let { state ->
            dailyResetEngine.applyProductUnitAdjustment(
                state = state,
                quantityMultiplier = multiplier,
                corporateActionAccountingSequence = actionAccountingSequence,
                at = effectiveAt,
            )
        }
        val adjustedOptionStrategyState = optionStrategyStates[stock.id]?.let { state ->
            optionStrategyEngine.applyProductUnitAdjustment(
                state = state,
                quantityMultiplier = multiplier,
                corporateActionAccountingSequence = actionAccountingSequence,
                at = effectiveAt,
            )
        }
        val adjustedCashPutSpreadState = cashCollateralizedPutSpreadStates[stock.id]?.let { state ->
            cashCollateralizedPutSpreadEngine.applyProductUnitAdjustment(
                state = state,
                quantityMultiplier = multiplier,
                corporateActionAccountingSequence = actionAccountingSequence,
                at = effectiveAt,
            )
        }
        val adjustedClosedEndFundState = closedEndFundStates[stock.id]?.let { state ->
            val profile = requireNotNull(stock.fundProductProfile)
            ClosedEndFundEngine(
                terms = requireNotNull(profile.closedEndFundTerms),
                marketModelParameters = requireNotNull(profile.closedEndFundMarketModelParameters),
            ).applyProductUnitAdjustment(
                state = state,
                quantityMultiplier = multiplier,
                corporateActionAccountingSequence = actionAccountingSequence,
                at = effectiveAt,
            )
        }
        val before = quotes.getValue(stock.id)
        fun adjustedPrice(value: Double): Double = MarketMicrostructure.roundNearest(
            stock,
            (value / multiplier).coerceAtLeast(MarketMicrostructure.minimumPrice(stock.market)),
        )
        val postPrice = adjustedPrice(before.price)
        val adjustedQuote = before.copy(
            timestamp = effectiveAt,
            price = postPrice,
            previousClose = adjustedPrice(before.previousClose),
            open = adjustedPrice(before.open),
            high = adjustedPrice(before.high),
            low = adjustedPrice(before.low),
            bidPrice = before.bidPrice?.let(::adjustedPrice),
            askPrice = before.askPrice?.let(::adjustedPrice),
            bidQuantity = before.bidQuantity * multiplier,
            askQuantity = before.askQuantity * multiplier,
        )
        quotes[stock.id] = adjustedQuote
        if (stock.market.isUnitedStates) {
            // A split changes every price-domain input atomically. Reusing the old LULD state
            // would clamp the first post-action bar to pre-split bands (and would retain stale
            // limit/pause deadlines), so establish a fresh NORMAL regime at the adjusted mark.
            val local = GameCalendar.marketLocalDateTime(stock.market, effectiveAt)
            val adjustedLuld = TradingProtectionEngine.initialUsLuld(
                stockId = stock.id,
                primaryMarket = stock.market,
                tradingDate = local.date,
                tier = usLuldTier(stock),
                previousClose = adjustedQuote.previousClose,
                referencePrice = adjustedQuote.price,
                referencePriceEffectiveAt = effectiveAt,
                easternTime = local.time,
            )
            tradingProtectionSnapshot = tradingProtectionSnapshot.copy(
                usLuldStates = tradingProtectionSnapshot.usLuldStates + (stock.id to adjustedLuld),
            )
        }
        dailyTrackers[stock.id]?.let { tracker ->
            tracker.basePrice = adjustedPrice(tracker.basePrice)
            tracker.open = adjustedPrice(tracker.open)
            tracker.high = adjustedPrice(tracker.high)
            tracker.low = adjustedPrice(tracker.low)
        }
        history[stock.id]?.let { bars ->
            val adjustedBars = bars.map { bar ->
                bar.copy(
                    open = adjustedPrice(bar.open),
                    high = adjustedPrice(bar.high),
                    low = adjustedPrice(bar.low),
                    close = adjustedPrice(bar.close),
                    volume = round(bar.volume.toDouble() * multiplier).toLong().coerceAtLeast(0L),
                )
            }
            bars.clear()
            bars.addAll(adjustedBars)
        }
        chartPriceHistory[stock.id]?.values?.forEach { bars ->
            val adjustedBars = bars.map { bar ->
                bar.copy(
                    open = adjustedPrice(bar.open),
                    high = adjustedPrice(bar.high),
                    low = adjustedPrice(bar.low),
                    close = adjustedPrice(bar.close),
                    volume = round(bar.volume.toDouble() * multiplier).toLong().coerceAtLeast(0L),
                )
            }
            bars.clear()
            bars.addAll(adjustedBars)
        }
        dailyTradingSurveillance[stock.id]?.let { points ->
            val adjustedPoints = points.map { point ->
                point.copy(
                    close = adjustedPrice(point.close),
                    volume = round(point.volume.toDouble() * multiplier).toLong().coerceAtLeast(0L),
                )
            }
            points.clear()
            points.addAll(adjustedPoints)
        }
        tradingProtectionSnapshot.investmentAlerts[stock.id]?.let { alert ->
            val adjustedAlert = alert.copy(
                preDesignationClose = alert.preDesignationClose?.let(::adjustedPrice),
                preReleaseClose = alert.preReleaseClose?.let(::adjustedPrice),
            )
            tradingProtectionSnapshot = tradingProtectionSnapshot.copy(
                investmentAlerts = tradingProtectionSnapshot.investmentAlerts + (stock.id to adjustedAlert),
            )
        }
        holdings[stock.id]?.let { holding ->
            holdings[stock.id] = holding.copy(
                quantity = holding.quantity * multiplier,
                averagePrice = holding.averagePrice / multiplier,
                currentPrice = postPrice,
            )
            fifoCostBasisBook = fifoCostBasisBook.applyQuantityMultiplier(stock.id, multiplier)
        }
        for (index in orders.indices) {
            val order = orders[index]
            if (order.stockId != stock.id || !order.isOpen) continue
            val adjustedQuantity = order.quantity * multiplier
            orders[index] = if (!stock.acceptsQuantity(adjustedQuantity)) {
                order.copy(
                    status = OrderStatus.CANCELLED,
                    updatedAt = effectiveAt,
                    rejectionReason = "분할·병합 후 주문 수량 단위가 맞지 않아 자동 취소했습니다.",
                )
            } else {
                order.copy(
                    quantity = adjustedQuantity,
                    filledQuantity = order.filledQuantity * multiplier,
                    limitPrice = order.limitPrice?.let(::adjustedPrice),
                    averageFilledPrice = order.averageFilledPrice?.let(::adjustedPrice),
                    updatedAt = effectiveAt,
                )
            }
        }
        applyDynamicShareMultiplier(stock.id, multiplier)
        adjustedFundFinancialState?.let { fundFinancialStates[stock.id] = it }
        adjustedDailyResetState?.let { dailyResetStates[stock.id] = it }
        adjustedOptionStrategyState?.let { optionStrategyStates[stock.id] = it }
        adjustedCashPutSpreadState?.let { cashCollateralizedPutSpreadStates[stock.id] = it }
        adjustedClosedEndFundState?.let { closedEndFundStates[stock.id] = it }
        nextSequence += 1L
        val settledFraction = action.kind == CorporateActionKind.REVERSE_SPLIT &&
            !stock.supportsFractional && settleCorporateActionFraction(stock, postPrice, effectiveAt)
        val record = CorporateActionRecord(
            id = action.id,
            stockId = stock.id,
            kind = action.kind,
            announcedAt = action.announcedAt,
            effectiveNotBefore = action.effectiveNotBefore,
            effectiveAt = effectiveAt,
            quantityMultiplier = multiplier,
            preActionPrice = before.price,
            postActionPrice = postPrice,
            source = action.source,
            rationale = action.rationale,
            accountingSequence = actionAccountingSequence,
        )
        corporateActionLedger += record
        val ratioLabel = corporateActionRatioLabel(action)
        newsEvents += GameEvent(
            id = "${action.id}:effective",
            title = "${stock.name} ${action.kind.displayName} 효력 발생",
            description = if (settledFraction) {
                "${ratioLabel}이 반영됐습니다. 정수 거래단위 미만 단주는 조정가격으로 현금정산하고 FIFO 원가와 양도손익 원장에 기록했습니다."
            } else {
                "${ratioLabel}이 반영됐습니다. 보유 수량과 주당원가를 서로 반대 비율로 조정해 총 평가액과 FIFO 총원가는 보존했습니다."
            },
            scope = EventScope.STOCK,
            type = EventType.CORPORATE_ACTION,
            severity = EventSeverity.MINOR,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = effectiveAt,
            durationHours = 24,
            recordKind = EventRecordKind.CORPORATE_ACTION,
            corporateActionReference = record.toAppliedNewsReference(),
            affectedMarkets = setOf(stock.market),
            affectedSectors = setOf(stock.sector),
            affectedStockIds = setOf(stock.id),
            sourceLabel = action.source.displayName,
        )
        lastMessage = "${stock.name} ${action.kind.displayName}($ratioLabel)을 반영했습니다."
    }

    /** 상장 종료가 아직 적용되지 않은 분할·병합을 무효화한 사실을 구조화된 전이로 남긴다. */
    private fun cancelPendingCorporateActions(
        stock: StockDefinition,
        cancelledAt: Instant,
        listingEvent: ListingLifecycleLedgerEvent,
    ) {
        val cancelled = pendingCorporateActions.filter { action -> action.stockId == stock.id }
        cancelled.forEach { action ->
            newsEvents += GameEvent(
                id = "${action.id}:cancelled:${listingEvent.sequence}",
                title = "${stock.name} ${action.kind.displayName} 일정 취소",
                description = "${listingEvent.title} 조치가 효력을 가져 앞서 공시한 ${action.kind.displayName} 일정을 종료했습니다.",
                scope = EventScope.STOCK,
                type = EventType.CORPORATE_ACTION,
                severity = EventSeverity.MODERATE,
                impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
                startsAt = cancelledAt,
                durationHours = 24,
                recordKind = EventRecordKind.CORPORATE_ACTION,
                corporateActionReference = action.toCancellationNewsReference(cancelledAt, listingEvent),
                affectedMarkets = setOf(stock.market),
                affectedSectors = setOf(stock.sector),
                affectedStockIds = setOf(stock.id),
                sourceLabel = action.source.displayName,
            )
        }
        pendingCorporateActions.removeAll { action -> action.stockId == stock.id }
    }

    /** Closes an announced action whose product contract became non-executable during notice. */
    private fun cancelPendingCorporateActionForProductState(
        action: PendingCorporateAction,
        stock: StockDefinition,
        cancelledAt: Instant,
    ) {
        newsEvents += GameEvent(
            id = "${action.id}:cancelled:product-state",
            title = "${stock.name} ${action.kind.displayName} 일정 취소",
            description =
                "공시 후 상품의 계약·운용 상태가 바뀌어 기존 단위로 분할·병합을 실행할 수 없어 일정을 종료했습니다.",
            scope = EventScope.STOCK,
            type = EventType.CORPORATE_ACTION,
            severity = EventSeverity.MODERATE,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = cancelledAt,
            durationHours = 24,
            recordKind = EventRecordKind.CORPORATE_ACTION,
            corporateActionReference = action.toProductStateCancellationNewsReference(cancelledAt),
            affectedMarkets = setOf(stock.market),
            affectedSectors = setOf(stock.sector),
            affectedStockIds = setOf(stock.id),
            sourceLabel = action.source.displayName,
        )
    }

    private fun isCorporateActionProductStateEligible(stock: StockDefinition): Boolean {
        val product = stock.fundProductProfile ?: return true
        if (product.legalStructure == FundLegalStructure.EXCHANGE_TRADED_NOTE) return false
        if (product.dailyResetTerms != null &&
            dailyResetStates[stock.id]?.lifecycle != DailyResetLifecycle.ACTIVE
        ) {
            return false
        }
        if (product.optionStrategyTerms != null &&
            optionStrategyStates[stock.id]?.lifecycle != OptionStrategyLifecycle.ACTIVE
        ) {
            return false
        }
        if (product.cashCollateralizedPutSpreadTerms != null &&
            cashCollateralizedPutSpreadStates[stock.id]?.lifecycle !=
            CashCollateralizedPutSpreadLifecycle.ACTIVE
        ) {
            return false
        }
        return true
    }

    /** 정수 수량 시장의 병합 단주는 자동 현금정산하고 FIFO 원가·양도손익을 함께 기록한다. */
    private fun settleCorporateActionFraction(
        stock: StockDefinition,
        postActionPrice: Double,
        effectiveAt: Instant,
    ): Boolean {
        val holding = holdings[stock.id] ?: return false
        val tradableQuantity = floor((holding.quantity + QUANTITY_EPSILON) / stock.quantityStep) *
            stock.quantityStep
        val remainder = (holding.quantity - tradableQuantity).coerceAtLeast(0.0)
        if (remainder < QUANTITY_EPSILON) return false

        val gross = maxOf(
            if (stock.currency == Currency.KRW) 1.0 else 0.01,
            roundCurrency(postActionPrice * remainder, stock.currency),
        )
        val tradedOn = marketDate(stock.market, effectiveAt)
        val settledOn = settlementDate(stock.market, tradedOn)
        val taxBreakdown = when {
            !stock.market.isKorean -> null
            stock.etfProfile != null -> {
                val acquisitionValueKrw = round(holding.averagePrice * remainder).toLong()
                val positiveTradingGain = (gross.toLong() - acquisitionValueKrw).coerceAtLeast(0L)
                domesticEtfSaleTaxCalculator.calculate(
                    DomesticEtfSaleTaxRequest(
                        taxCategory = stock.etfProfile.taxCategory,
                        grossProceedsKrw = gross.toLong(),
                        acquisitionValueKrw = acquisitionValueKrw,
                        taxableStandardGainKrw = round(
                            positiveTradingGain * stock.etfProfile.taxablePriceGainRatio,
                        ).toLong(),
                        soldOn = tradedOn,
                    ),
                )
            }

            else -> domesticSaleTaxCalculator.calculate(
                DomesticSaleTaxRequest(
                    market = stock.market,
                    grossProceedsKrw = gross.toLong(),
                    soldOn = tradedOn,
                ),
            )
        }
        val saleTax = taxBreakdown?.totalTax?.amount ?: 0.0
        val exchangeRateToKrw = if (stock.currency == Currency.USD) macro.usdKrw else 1.0
        val fifoSale = fifoCostBasisBook.sell(
            stockId = stock.id,
            soldOn = settledOn,
            quantity = remainder,
            grossProceedsKrw = round(gross * exchangeRateToKrw).toLong(),
            directSellingCostsKrw = round(saleTax * exchangeRateToKrw).toLong(),
        )
        fifoCostBasisBook = fifoSale.updatedBook
        val (taxTreatment, assessmentNotes) = assessStockGainTreatment(stock, tradedOn, holding)
        val realized = gross - holding.averagePrice * remainder - saleTax
        if (tradableQuantity < stock.quantityStep / 2.0) {
            holdings.remove(stock.id)
        } else {
            holdings[stock.id] = holding.copy(
                quantity = tradableQuantity,
                currentPrice = postActionPrice,
                realizedProfit = holding.realizedProfit + realized,
            )
        }
        cash[stock.currency] = roundCurrency(
            cash.getValue(stock.currency) + gross - saleTax,
            stock.currency,
        )

        val orderId = nextId("cash-in-lieu-order")
        val tradeId = nextId("trade")
        val accountingSequence = nextSequence++
        orders += Order(
            id = orderId,
            stockId = stock.id,
            side = OrderSide.SELL,
            type = OrderType.MARKET,
            quantity = remainder,
            createdAt = effectiveAt,
            status = OrderStatus.FILLED,
            filledQuantity = remainder,
            averageFilledPrice = postActionPrice,
            updatedAt = effectiveAt,
            timeInForce = TimeInForce.DAY,
            rejectionReason = "주식병합 단주 현금정산",
        )
        trades += Trade(
            id = tradeId,
            orderId = orderId,
            stockId = stock.id,
            side = OrderSide.SELL,
            quantity = remainder,
            price = postActionPrice,
            currency = stock.currency,
            executedAt = effectiveAt,
            tax = saleTax,
            accountingSequence = accountingSequence,
        )
        transactionCosts += TransactionCostRecord(
            tradeId = tradeId,
            stockId = stock.id,
            market = stock.market,
            paidAt = effectiveAt,
            currency = stock.currency,
            commission = 0.0,
            saleTax = saleTax,
            exchangeRateToKrw = exchangeRateToKrw,
            taxBreakdown = taxBreakdown,
        )
        realizedGains += RealizedGainRecord(
            tradeId = tradeId,
            stockId = stock.id,
            market = stock.market,
            soldAt = effectiveAt,
            settlementDate = settledOn,
            quantity = remainder,
            proceeds = gross,
            costBasis = holding.averagePrice * remainder,
            commission = 0.0,
            saleTax = saleTax,
            currency = stock.currency,
            exchangeRateToKrw = exchangeRateToKrw,
            taxTreatment = taxTreatment,
            assessmentNotes = assessmentNotes + "주식병합 단주를 현금정산 처분으로 반영했습니다.",
            taxGrossProceedsKrw = fifoSale.grossProceedsKrw,
            taxCostBasisKrw = fifoSale.allocatedCostBasisKrw,
            taxDirectSellingCostsKrw = fifoSale.directSellingCostsKrw,
            taxGainKrw = fifoSale.realizedGainKrw,
            taxableFinancialIncomeKrw = if (
                stock.etfProfile?.taxCategory == EtfTaxCategory.KOREAN_OTHER
            ) {
                taxBreakdown?.taxableBase?.minorUnits ?: 0L
            } else {
                0L
            },
        )
        taxExchangeRatesByTradeId[tradeId] = exchangeRateToKrw
        if (stock.market.isUnitedStates) pendingTaxSettlementTradeIds += tradeId
        recalculateAnnualTax(settledOn.year)
        return true
    }

    private fun maybeAnnounceCorporateActions(from: Instant, to: Instant) {
        for (stock in stocks) {
            if (isInstrumentMatured(stock, to)) continue
            if (listingLifecycleStates[stock.id]?.isSettlementPending == true) continue
            // ETN note denominations are part of stated principal, coupon, and minimum redemption
            // terms. This campaign model has no versioned amendment/cash-in-lieu contract for
            // those terms, so it must not manufacture an equity-style split for an ETN.
            if (!isCorporateActionProductStateEligible(stock)) continue
            val crossedVenueClose = regularTradingFraction(stock.market, from, to) > 0.0 &&
                regularTradingFraction(stock.market, to, to + 1.hours) == 0.0
            if (!crossedVenueClose) continue
            val campaignDate = marketDate(stock.market, to)
            if (pendingCorporateActions.any { it.stockId == stock.id }) continue
            val lastApplied = corporateActionLedger.lastOrNull { it.stockId == stock.id }
            if (lastApplied != null && to - lastApplied.effectiveAt < CORPORATE_ACTION_COOLDOWN_HOURS.hours) continue
            val closes = history.getValue(stock.id)
                .filter { it.volume > 0L }
                .groupBy { marketDate(stock.market, it.endTime) }
                .toSortedMap()
                .values
                .map { it.last().close }
            if (closes.isEmpty()) continue
            val price = closes.last()
            val lowTrigger = if (stock.currency == Currency.KRW) 1_500.0 else 5.0
            val highTrigger = if (stock.currency == Currency.KRW) 1_000_000.0 else 750.0
            val requiredLowDays = if (
                stock.behavior.strategy in setOf(
                    com.amond.kmpbook.domain.model.instrument.InstrumentStrategy.DAILY_LEVERAGED,
                    com.amond.kmpbook.domain.model.instrument.InstrumentStrategy.DAILY_INVERSE,
                    com.amond.kmpbook.domain.model.instrument.InstrumentStrategy.COVERED_CALL,
                )
            ) 7 else 15
            val reverse = closes.takeLast(requiredLowDays).size == requiredLowDays &&
                closes.takeLast(requiredLowDays).all { it < lowTrigger }
            val forward = closes.takeLast(FORWARD_SPLIT_STREAK_DAYS).size == FORWARD_SPLIT_STREAK_DAYS &&
                closes.takeLast(FORWARD_SPLIT_STREAK_DAYS).all { it > highTrigger }
            if (!reverse && !forward) continue
            // 임계값만으로 즉시 발동하지 않고, 종목·날짜별 결정론적 이사회 게이트를 거친다.
            val gate = (PriceEngine.stableHash64("${stock.id}:$campaignDate:corporate-action") and Long.MAX_VALUE) %
                CORPORATE_ACTION_BOARD_GATE
            if (gate != 0L) continue

            val kind = if (reverse) CorporateActionKind.REVERSE_SPLIT else CorporateActionKind.FORWARD_SPLIT
            val target = when {
                reverse && stock.currency == Currency.KRW -> 10_000.0
                reverse -> 25.0
                stock.currency == Currency.KRW -> 100_000.0
                else -> 150.0
            }
            val multiplier = if (reverse) {
                CorporateActionMath.reverseMultiplier(price, target)
            } else {
                CorporateActionMath.forwardMultiplier(price, target)
            }
            val rationale = if (reverse) {
                "${requiredLowDays}거래일 이상 저가 구간이 지속됐고 ${stock.behavior.principalRisk.displayName} 구조를 반영한 가상 이사회가 유통가격 조정을 승인했습니다."
            } else {
                "${FORWARD_SPLIT_STREAK_DAYS}거래일 이상 고가 구간이 지속돼 거래 접근성을 높이기 위한 가상 이사회 분할을 승인했습니다."
            }
            val action = PendingCorporateAction(
                id = nextId("corporate-action"),
                stockId = stock.id,
                kind = kind,
                announcedAt = to,
                effectiveNotBefore = to + CORPORATE_ACTION_NOTICE_HOURS.hours,
                quantityMultiplier = multiplier,
                rationale = rationale,
            )
            pendingCorporateActions += action
            val ratioLabel = corporateActionRatioLabel(action)
            newsEvents += GameEvent(
                id = "${action.id}:announcement",
                title = "${stock.name} ${kind.displayName} 결의",
                description = "$ratioLabel 조정이 ${CORPORATE_ACTION_NOTICE_HOURS / 24}일 후 첫 정규장에서 반영됩니다. $rationale 이 공시는 실제 기업 공시가 아닌 캠페인 규칙상 가상 사건입니다.",
                scope = EventScope.STOCK,
                type = EventType.CORPORATE_ACTION,
                severity = EventSeverity.MODERATE,
                impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
                startsAt = to,
                durationHours = CORPORATE_ACTION_NOTICE_HOURS,
                recordKind = EventRecordKind.CORPORATE_ACTION,
                corporateActionReference = action.toAnnouncementNewsReference(),
                affectedMarkets = setOf(stock.market),
                affectedSectors = setOf(stock.sector),
                affectedStockIds = setOf(stock.id),
                sourceLabel = CorporateActionSource.CAMPAIGN_RULE.displayName,
            )
        }
    }

    private fun corporateActionRatioLabel(action: PendingCorporateAction): String =
        if (action.quantityMultiplier > 1.0) {
            "1:${action.quantityMultiplier.toInt()} 분할"
        } else {
            "${kotlin.math.round(1.0 / action.quantityMultiplier).toInt()}:1 병합"
        }

    private fun updateMacro(time: Instant) {
        // Exactly one path-dependent frame is resolved for this hour. The provisional and final
        // pricing passes below both consume this immutable MacroEnvironment and never advance the
        // dynamics RNG themselves.
        val dynamics = marketDynamicsEngine.advance(externalMarketForcesTarget)
        val effectiveForces = dynamics.effectiveForces
        val date = gameDate(time)
        val resetMarketChange = date != macroDate
        macroDate = date
        val previousUsdKrw = macro.usdKrw
        val previousFxRates = macro.fxRatesToKrw ?: initialFxRates(previousUsdKrw)
        val dynamicFxTarget = options.initialUsdKrw * exp(
            0.22 * (effectiveForces.worldTension - 0.5) +
                0.12 * (effectiveForces.chaos - 0.5) -
                0.10 * (effectiveForces.institutionalBuyingPower - 0.5) +
                0.10 * dynamics.liquidityStress,
        )
        val meanReversion = ln(dynamicFxTarget / previousUsdKrw) * FX_MEAN_REVERSION
        val fxVolatility = FX_HOURLY_VOLATILITY * (0.72 + dynamics.volatilityRegime * 0.28)
        val usdKrw = (previousUsdKrw * exp(meanReversion + random.nextGaussian() * fxVolatility))
            .coerceIn(MIN_USD_KRW, MAX_USD_KRW)
        val usdLogReturn = ln(usdKrw / previousUsdKrw)
        val fxRates = ReferenceCurrency.entries.associateWith { currency ->
            when (currency) {
                ReferenceCurrency.KRW -> 1.0
                ReferenceCurrency.USD -> usdKrw
                else -> {
                    val previous = previousFxRates[currency] ?: initialFxRates(previousUsdKrw).getValue(currency)
                    val target = initialFxRates(options.initialUsdKrw).getValue(currency)
                    val crossMeanReversion = ln(target / previous) * FX_CROSS_MEAN_REVERSION
                    (previous * exp(
                        crossMeanReversion + usdLogReturn * FX_GLOBAL_KRW_LOADING +
                            random.nextGaussian() * FX_CROSS_HOURLY_VOLATILITY,
                    )).coerceIn(target * 0.35, target * 2.75)
                }
            }
        }
        // Monetary-policy decisions are owned by the scheduled FOMC/BOK calendar. Economic
        // levels move slowly; release surprises are transient innovations, never reconstructed
        // every hour from the distance between a level and an arbitrary 2% reference.
        val policyChange = 0.0
        val policyRate = macro.policyRate
        val inflationTarget = (
            0.02 + 0.010 * (effectiveForces.worldTension - 0.5) +
                0.006 * (effectiveForces.chaos - 0.5) +
                0.004 * maxOf(effectiveForces.economicMomentum - 0.5, 0.0)
            ).coerceIn(-0.02, 0.15)
        val growthTarget = (
            0.02 + 0.050 * (effectiveForces.economicMomentum - 0.5) -
                0.012 * maxOf(effectiveForces.worldTension - 0.5, 0.0) -
                0.008 * dynamics.liquidityStress
            ).coerceIn(-0.10, 0.15)
        val inflation = (
            macro.inflationRate + (inflationTarget - macro.inflationRate) * MACRO_LEVEL_REVERSION +
                random.nextGaussian() * MACRO_LEVEL_INNOVATION * dynamics.volatilityRegime
            ).coerceIn(-0.02, 0.15)
        val growth = (
            macro.growthRate + (growthTarget - macro.growthRate) * MACRO_LEVEL_REVERSION +
                random.nextGaussian() * MACRO_LEVEL_INNOVATION * dynamics.volatilityRegime
            ).coerceIn(-0.10, 0.15)
        val sentimentMemory = marketDynamicsEngine.snapshot().eventSentimentMemory
        val riskTarget = kotlin.math.tanh(
            0.42 * dynamics.retailFlow +
                0.64 * dynamics.institutionalFlow +
                0.44 * (effectiveForces.economicMomentum - 0.5) -
                0.62 * dynamics.liquidityStress +
                0.72 * sentimentMemory,
        )
        val riskSentiment = (
            macro.riskSentiment + (riskTarget - macro.riskSentiment) * RISK_SENTIMENT_REVERSION +
                random.nextGaussian() * RISK_SENTIMENT_INNOVATION
            ).coerceIn(-1.0, 1.0)
        val marketReturns = Market.entries.associateWith { market ->
            val fraction = regularTradingFraction(market, time, time + 1.hours)
            if (fraction == 0.0) 0.0 else dynamics.marketReturns.getValue(market)
        }
        val sectorReturns = dynamics.sectorReturns
        val regionalReturns = linkedMapOf<EtfExposureRegion, Double>()
        regionalReturns[EtfExposureRegion.KOREA] = marketReturns
            .filterKeys(Market::isKorean).values.filter { it != 0.0 }.averageOrZero()
        regionalReturns[EtfExposureRegion.UNITED_STATES] = marketReturns
            .filterKeys(Market::isUnitedStates).values.filter { it != 0.0 }.averageOrZero()
        regionalReturns[EtfExposureRegion.DEVELOPED_EX_US] = if (
            regionalTradingFraction(EtfExposureRegion.DEVELOPED_EX_US, time) > 0.0
        ) {
            marketReturns.values.filter { it != 0.0 }.averageOrZero() +
                random.nextGaussian() * MARKET_FACTOR_VOLATILITY * 0.65 * dynamics.volatilityRegime
        } else 0.0
        regionalReturns[EtfExposureRegion.EMERGING_MARKETS] = if (
            regionalTradingFraction(EtfExposureRegion.EMERGING_MARKETS, time) > 0.0
        ) {
            marketReturns.values.filter { it != 0.0 }.averageOrZero() +
                random.nextGaussian() * MARKET_FACTOR_VOLATILITY * 0.78 * dynamics.volatilityRegime
        } else 0.0
        regionalReturns[EtfExposureRegion.GLOBAL] = regionalReturns.values
            .filter { it != 0.0 }.averageOrZero()
        macro = MacroEnvironment(
            policyRate = policyRate,
            policyRateChange = policyChange,
            inflationRate = inflation,
            inflationSurprise = macro.inflationSurprise * MACRO_SURPRISE_DECAY,
            growthRate = growth,
            growthSurprise = macro.growthSurprise * MACRO_SURPRISE_DECAY,
            usdKrw = usdKrw,
            previousUsdKrw = previousUsdKrw,
            fxRatesToKrw = fxRates,
            previousFxRatesToKrw = previousFxRates,
            riskSentiment = riskSentiment,
            volatilityRegime = dynamics.volatilityRegime,
            retailOrderFlow = dynamics.retailFlow,
            institutionalOrderFlow = dynamics.institutionalFlow,
            liquidityStress = dynamics.liquidityStress,
            newsIntensity = dynamics.newsHazardMultiplier,
            marketHourlyReturns = marketReturns,
            sectorHourlyReturns = sectorReturns,
            regionalEtfHourlyReturns = regionalReturns,
            marketChangeFromPreviousClose = if (resetMarketChange) emptyMap() else macro.marketChangeFromPreviousClose,
            usCircuitBreakerLevel = tradingProtectionSnapshot.usMarketWideCircuitBreaker?.let { state ->
                if (state.phase == UsMwcbPhase.NORMAL) 0 else (state.activeLevel?.ordinal?.plus(1) ?: 0)
            } ?: 0,
        )
    }

    private fun generateEvents(from: Instant, to: Instant) {
        val eligibleStocks = eventEligibleStocks()

        // Calendar releases own their reported fact and direct repricing. Emit them first so a
        // stochastic template cannot immediately restate the same CPI/GDP/rate narrative.
        val scheduled = scheduledEventEngine.generate(from, to, eligibleStocks)
        if (scheduled.emissions.isNotEmpty()) {
            val existingIds = newsEvents.mapTo(mutableSetOf(), GameEvent::id)
            newsEvents += scheduled.newEvents.filter { existingIds.add(it.id) }
            applyScheduledCorporateFundamentals(scheduled.emissions)
            applyScheduledMacro(scheduled.emissions)
        }

        // Do not expose this hour's not-yet-realized factor innovation to the event selector or
        // freeze it into a causal snapshot. Hazard decisions only see pre-step state and the last
        // finalized market observations.
        val eventContextMacro = macro.copy(
            marketHourlyReturns = emptyMap(),
            sectorHourlyReturns = emptyMap(),
            regionalEtfHourlyReturns = emptyMap(),
        )
        val dynamicsSnapshot = marketDynamicsEngine.snapshot()
        val result = eventEngine.generate(
            EventGenerationContext(
                timestamp = from,
                stocks = eligibleStocks,
                macro = eventContextMacro,
                externalForces = dynamicsSnapshot.effectiveForces,
                newsHazardMultiplier = dynamicsSnapshot.newsIntensity,
                elapsedHours = 1,
                existingEvents = activeEvents,
                suppressedTemplateIds = stochasticNarrativeSuppressions(from),
                maxNewEvents = 1,
            ),
        )
        applyGeneratedEventResult(result)

        val allNewEvents = result.newEvents + scheduled.newEvents
        // Direct repricing is consumed this hour by EventShockCalculator. Sentiment and news
        // clustering enter only the next dynamics frame, preventing same-hour double counting.
        marketDynamicsEngine.recordEvents(allNewEvents)
    }

    private fun eventEligibleStocks(): List<StockDefinition> = stocks.filterNot { stock ->
        listingLifecycleStates.getValue(stock.id).let { state ->
            state.isTerminal || state.isSettlementPending
        }
    }

    private fun applyGeneratedEventResult(result: EventGenerationResult) {
        activeEvents.clear()
        activeEvents += result.activeEvents
        if (result.newEvents.isEmpty()) return
        newsEvents += result.newEvents
        recordFundFlowSignals(result.newEvents)
        trimStochasticNews()
    }

    private fun stochasticNarrativeSuppressions(at: Instant): Set<String> {
        val recentKinds = newsEvents.asSequence()
            .filter { event ->
                event.recordKind == EventRecordKind.SCHEDULED_RELEASE &&
                    event.startsAt <= at && event.startsAt + NARRATIVE_FAMILY_COOLDOWN_HOURS.hours > at
            }
            .mapNotNull { it.scheduledEventReference?.kind }
            .toSet()
        return buildSet {
            if (recentKinds.any { it in INFLATION_RELEASE_KINDS }) {
                add("inflation_hot")
                add("inflation_cools")
            }
            if (recentKinds.any { it in GROWTH_RELEASE_KINDS }) {
                add("growth_recession")
                add("growth_rebound")
            }
            if (recentKinds.any { it in POLICY_RELEASE_KINDS }) {
                add("surprise_rate_hike")
                add("surprise_rate_cut")
            }
        }
    }

    private fun recordFundFlowSignals(events: List<GameEvent>) {
        for (event in events) {
            val flowRate = when (event.generatorTemplateId) {
                "etf_inflow" -> abs(event.impact.shockReturn).coerceIn(0.003, 0.030)
                "etf_outflow" -> -abs(event.impact.shockReturn).coerceIn(0.004, 0.035)
                else -> continue
            }
            val stockId = event.affectedStockIds.singleOrNull()
                ?.takeIf { candidateId ->
                    candidateId in fundFinancialStates &&
                        listingLifecycleStates[candidateId]?.let { state ->
                            !state.isTerminal && !state.isSettlementPending
                        } == true
                }
                ?: continue
            pendingFundFlowRates[stockId] = (
                (pendingFundFlowRates[stockId] ?: 0.0) + flowRate
                ).coerceIn(-0.20, 0.20)
        }
    }

    private fun applyScheduledCorporateFundamentals(emissions: List<ScheduledEventEmission>) {
        for (emission in emissions) {
            if (emission.occurrence.kind != ScheduledEventKind.EARNINGS) continue
            val stockId = emission.occurrence.affectedStockIds.single()
            val stock = stockById[stockId] ?: continue
            val current = corporateFundamentals[stockId] ?: continue
            corporateFundamentals[stockId] = instrumentMetricsEngine.applyEarnings(
                state = current,
                stock = stock,
                emission = emission,
            )
        }
    }

    private fun trimStochasticNews() {
        val activeIds = activeEvents.mapTo(hashSetOf(), GameEvent::id)
        var historicalStochasticCount = newsEvents.count { event ->
            !isProtectedLedgerNews(event) && event.id !in activeIds
        }
        if (historicalStochasticCount <= MAX_NEWS_EVENTS) return
        val iterator = newsEvents.listIterator()
        while (iterator.hasNext() && historicalStochasticCount > MAX_NEWS_EVENTS) {
            val event = iterator.next()
            if (!isProtectedLedgerNews(event) && event.id !in activeIds) {
                iterator.remove()
                historicalStochasticCount -= 1
            }
        }
    }

    /**
     * 시장조치 전이는 같은 발생 ID의 선행 조치와 함께 검증되므로 개별 뉴스가 아닌
     * `(종류, 발생 ID)` 그룹 전체를 보존·제거한다. 최근 그룹과 현재 상태가 참조하거나
     * 아직 효력이 남은 그룹만 유지해 2040년 캠페인 저장 JSON의 크기에 상한을 둔다.
     */
    private fun trimMarketActionNewsArchive() {
        val eventsByOccurrence = linkedMapOf<Pair<MarketActionKind, String>, MutableList<GameEvent>>()
        for (event in newsEvents) {
            val action = event.marketAction ?: continue
            eventsByOccurrence.getOrPut(action.kind to action.occurrenceId, ::mutableListOf) += event
        }
        if (eventsByOccurrence.size <= MAX_MARKET_ACTION_OCCURRENCE_GROUPS) return

        val retainedOccurrences = currentRequiredMarketActionOccurrences().toMutableSet()
        eventsByOccurrence.forEach { (occurrence, events) ->
            if (events.any { event ->
                    val action = requireNotNull(event.marketAction)
                    event.isActiveAt(currentTime) || action.effectiveAt > currentTime ||
                        action.endsAt?.let { endsAt -> endsAt > currentTime } == true
                }
            ) {
                retainedOccurrences += occurrence
            }
        }
        eventsByOccurrence.entries
            .sortedWith(
                compareBy<Map.Entry<Pair<MarketActionKind, String>, MutableList<GameEvent>>>(
                    { entry -> entry.value.maxOf(GameEvent::startsAt) },
                    { entry -> entry.key.first.ordinal },
                    { entry -> entry.key.second },
                ),
            )
            .takeLast(MAX_MARKET_ACTION_OCCURRENCE_GROUPS)
            .mapTo(retainedOccurrences) { entry -> entry.key }

        val removedEventIds = mutableSetOf<String>()
        newsEvents.removeAll { event ->
            val action = event.marketAction ?: return@removeAll false
            val remove = (action.kind to action.occurrenceId) !in retainedOccurrences
            if (remove) removedEventIds += event.id
            remove
        }
        readEventIds.removeAll(removedEventIds)
        readStockNewsEventIds.values.forEach { eventIds -> eventIds.removeAll(removedEventIds) }
        readStockNewsEventIds.entries.removeAll { (_, eventIds) -> eventIds.isEmpty() }
    }

    private fun currentRequiredMarketActionOccurrences(): Set<Pair<MarketActionKind, String>> = buildSet {
        val protection = tradingProtectionSnapshot
        protection.krxCircuitBreakers.values.forEach { state ->
            val level = state.activeLevel
            val triggeredAt = state.triggeredAt
            if (state.phase != KrxCircuitBreakerPhase.NORMAL && level != null && triggeredAt != null) {
                add(MarketActionKind.KRX_CIRCUIT_BREAKER to krxCircuitBreakerOccurrenceId(
                    state.market,
                    level,
                    triggeredAt,
                ))
            }
        }
        protection.krxSidecars.values.forEach { state ->
            val triggeredAt = state.triggeredAt
            if (state.phase == KrxSidecarPhase.PROGRAM_FLOW_SUSPENDED && triggeredAt != null) {
                add(MarketActionKind.KRX_SIDECAR to krxSidecarOccurrenceId(state.market, triggeredAt))
            }
        }
        protection.krxVolatilityInterruptions.values.forEach { state ->
            val triggeredAt = state.triggeredAt
            if (state.phase == KrxViPhase.CALL_AUCTION && triggeredAt != null) {
                add(MarketActionKind.KRX_VOLATILITY_INTERRUPTION to krxViOccurrenceId(
                    state.stockId,
                    state.triggerCount,
                    triggeredAt,
                ))
            }
        }
        protection.usMarketWideCircuitBreaker?.let { state ->
            val level = state.activeLevel
            val triggeredAt = state.triggeredAt
            if (state.phase != UsMwcbPhase.NORMAL && level != null && triggeredAt != null) {
                add(MarketActionKind.US_MARKET_WIDE_CIRCUIT_BREAKER to usMwcbOccurrenceId(level, triggeredAt))
            }
        }
        protection.usLuldStates.values.forEach { state ->
            val pauseStartedAt = state.pauseStartedAt
            if (state.phase in setOf(
                    UsLuldPhase.TRADING_PAUSE,
                    UsLuldPhase.REOPENING_AUCTION,
                    UsLuldPhase.CLOSING_AUCTION_ONLY,
                ) && pauseStartedAt != null
            ) {
                add(MarketActionKind.US_LIMIT_UP_LIMIT_DOWN to usLuldOccurrenceId(
                    state.stockId,
                    pauseStartedAt,
                ))
            }
        }
        (protection.instrumentTradingHalts.values + protection.scheduledInstrumentTradingHalts.values)
            .forEach { halt ->
                add(MarketActionKind.INSTRUMENT_TRADING_HALT to halt.occurrenceId)
            }
        protection.investmentAlerts.values.forEach { designation ->
            add(MarketActionKind.INVESTMENT_ALERT to investmentAlertOccurrenceId(
                designation.stockId,
                designation.designatedAt,
            ))
            if (designation.status == InvestmentAlertStatus.ACTIVE) {
                val haltOccurrenceId = when (designation.level) {
                    InvestmentAlertLevel.CAUTION -> null
                    InvestmentAlertLevel.WARNING ->
                        "investment-warning-additional-rise-halt:${designation.stockId}:${designation.designatedOn}"
                    InvestmentAlertLevel.DANGER ->
                        "investment-danger-initial-halt:${designation.stockId}:${designation.designatedOn}"
                }
                haltOccurrenceId?.let { occurrenceId ->
                    add(MarketActionKind.INSTRUMENT_TRADING_HALT to occurrenceId)
                }
            }
        }
        listingLifecycleStates.values.forEach { state ->
            if (state.status == ListingLifecycleStatus.LISTED || state.isTerminal) return@forEach
            val stock = stockById.getValue(state.stockId)
            add(MarketActionKind.LISTING_REMEDIATION to listingRemediationEventId(stock, state))
        }
    }

    private fun isProtectedLedgerNews(event: GameEvent): Boolean =
        event.recordKind != EventRecordKind.NEWS ||
            event.marketAction != null ||
            event.listingRiskTags.isNotEmpty() ||
            event.listingRecoveryConditions.isNotEmpty() ||
            event.listingFinalDispositionHint != null

    private fun applyScheduledMacro(emissions: List<ScheduledEventEmission>) {
        for (emission in emissions) {
            val outcome = emission.outcome
            val actual = outcome.metrics.first().actual
            macro = when (emission.occurrence.kind) {
                ScheduledEventKind.US_CPI,
                ScheduledEventKind.US_PCE,
                ScheduledEventKind.KR_CPI,
                -> {
                    val nextInflation = actual / 100.0
                    macro.copy(
                        inflationRate = nextInflation,
                        inflationSurprise = (nextInflation - macro.inflationRate)
                            .coerceIn(-0.10, 0.10),
                    )
                }

                ScheduledEventKind.US_GDP,
                ScheduledEventKind.KR_GDP,
                -> {
                    val nextGrowth = actual / 100.0
                    macro.copy(
                        growthRate = nextGrowth,
                        growthSurprise = (nextGrowth - macro.growthRate)
                            .coerceIn(-0.15, 0.15),
                    )
                }

                ScheduledEventKind.US_FOMC,
                ScheduledEventKind.KR_BOK,
                -> {
                    val nextRate = actual / 100.0
                    macro.copy(
                        policyRate = nextRate,
                        policyRateChange = nextRate - macro.policyRate,
                    )
                }

                ScheduledEventKind.US_WEEKLY_CLAIMS -> macro.copy(
                    growthRate = (macro.growthRate - outcome.surpriseScore * 0.0006)
                        .coerceIn(-0.10, 0.15),
                    growthSurprise = 0.0,
                )

                ScheduledEventKind.US_EMPLOYMENT,
                ScheduledEventKind.KR_EMPLOYMENT,
                ScheduledEventKind.US_RETAIL_SALES,
                -> macro.copy(
                    growthRate = (macro.growthRate + outcome.surpriseScore * 0.0007)
                        .coerceIn(-0.10, 0.15),
                    growthSurprise = 0.0,
                )

                ScheduledEventKind.EARNINGS -> macro
            }
        }
    }

    private fun trackerFor(stock: StockDefinition, time: Instant, previousPrice: Double): DailyPriceTracker {
        val localDate = marketDate(stock.market, time)
        val tracker = dailyTrackers.getValue(stock.id)
        if (tracker.date != localDate) {
            tracker.date = localDate
            tracker.basePrice = previousPrice
            tracker.open = previousPrice
            tracker.high = previousPrice
            tracker.low = previousPrice
            tracker.hasRegularTrading = false
        }
        return tracker
    }

    private fun appendHistory(stockId: String, bar: PriceBar) {
        if (bar.volume == 0L) return
        val bars = history.getValue(stockId)
        if (bars.size == 1 && bars.first().volume == 0L) bars.clear()
        bars.addLast(bar)
        while (bars.size > MAX_RECENT_BARS) bars.removeFirst()
        val stock = stockById.getValue(stockId)
        CHART_INTERVALS.forEach { interval -> appendChartPriceHistory(stock, bar, interval) }
    }

    private fun appendChartPriceHistory(
        stock: StockDefinition,
        bar: PriceBar,
        interval: PriceBarInterval,
    ) {
        val bars = chartPriceHistory.getValue(stock.id).getValue(interval)
        val previous = bars.lastOrNull()
        if (previous != null && belongsToSameChartPeriod(stock.market, previous, bar, interval)) {
            bars.removeLast()
            bars.addLast(
                previous.copy(
                    endTime = maxOf(previous.endTime, bar.endTime),
                    high = maxOf(previous.high, bar.high),
                    low = minOf(previous.low, bar.low),
                    close = bar.close,
                    volume = previous.volume + bar.volume,
                ),
            )
        } else {
            bars.addLast(
                PriceBar(
                    stockId = stock.id,
                    startTime = bar.startTime,
                    endTime = bar.endTime,
                    step = interval,
                    open = bar.open,
                    high = bar.high,
                    low = bar.low,
                    close = bar.close,
                    volume = bar.volume,
                ),
            )
        }
        while (bars.size > MAX_CHART_BARS_PER_INTERVAL) bars.removeFirst()
    }

    private fun belongsToSameChartPeriod(
        market: Market,
        previous: PriceBar,
        next: PriceBar,
        interval: PriceBarInterval,
    ): Boolean {
        val previousDate = marketDate(market, previous.startTime)
        val nextDate = marketDate(market, next.startTime)
        return when (interval) {
            PriceBarInterval.ONE_HOUR -> false
            PriceBarInterval.ONE_DAY -> previousDate == nextDate
            PriceBarInterval.ONE_WEEK -> chartWeekStart(previousDate) == chartWeekStart(nextDate)
            PriceBarInterval.ONE_MONTH ->
                previousDate.year == nextDate.year && previousDate.month == nextDate.month

            PriceBarInterval.THREE_MONTHS ->
                previousDate.year == nextDate.year && previousDate.month.ordinal / 3 == nextDate.month.ordinal / 3
        }
    }

    private fun chartWeekStart(date: LocalDate): LocalDate {
        val daysSinceMonday = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
        }
        return date.minus(daysSinceMonday, DateTimeUnit.DAY)
    }

    private fun processOpenOrders(
        bars: Map<String, PriceBar>,
        fractions: Map<String, Double>,
        firstExecutionTimes: Map<String, Instant>,
        protectionBeforeObservation: TradingProtectionSnapshot,
        protectionImpact: TurnProtectionImpact,
    ) {
        for (index in orders.indices) {
            val order = orders[index]
            if (!order.isOpen || order.status == OrderStatus.PENDING) continue
            val stock = stockById.getValue(order.stockId)
            if (!listingLifecycleStates.getValue(stock.id).isTradable) continue
            if ((fractions[stock.id] ?: 0.0) <= 0.0) continue
            val bar = bars.getValue(stock.id)
            if (bar.volume <= 0L) continue
            val fillPrice = when (order.type) {
                OrderType.MARKET -> bar.open
                OrderType.LIMIT -> limitFillPrice(order, bar)
            } ?: continue
            val executionAt = firstExecutionTimes[stock.id] ?: bar.startTime
            val newlyRestrictedAt = protectionImpact.firstNewRestrictionAt(stock)
            val executionSnapshot = if (newlyRestrictedAt != null && executionAt < newlyRestrictedAt) {
                protectionBeforeObservation
            } else {
                tradingProtectionSnapshot
            }
            executeOrder(index, fillPrice, executionAt, executionSnapshot)
        }
    }

    private fun limitFillPrice(order: Order, bar: PriceBar): Double? {
        val limit = requireNotNull(order.limitPrice)
        return when (order.side) {
            OrderSide.BUY -> if (bar.low <= limit) minOf(bar.open, limit) else null
            OrderSide.SELL -> if (bar.high >= limit) maxOf(bar.open, limit) else null
        }
    }

    private fun tryImmediateExecution(index: Int, stock: StockDefinition) {
        if (!listingLifecycleStates.getValue(stock.id).isTradable) return
        val session = marketSessionAtCurrentTime(stock.market)
        if (session != MarketSession.REGULAR) return
        // At an hourly opening boundary the ETF quote still represents yesterday's
        // close until the opening bar consumes its fair-value carry. Queue the order so
        // processOpenOrders fills it at that bar's gap open instead of the stale quote.
        if (stock.isFundLike && stock.id in pendingEtfReferenceReturns) return
        val quote = quotes.getValue(stock.id)
        val book = orderBook(stock, quote, session)
        val order = orders[index]
        val price = when (order.side) {
            OrderSide.BUY -> book.bestAsk?.price ?: quote.price
            OrderSide.SELL -> book.bestBid?.price ?: quote.price
        }
        val executable = when (order.type) {
            OrderType.MARKET -> true
            OrderType.LIMIT -> when (order.side) {
                OrderSide.BUY -> price <= requireNotNull(order.limitPrice)
                OrderSide.SELL -> price >= requireNotNull(order.limitPrice)
            }
        }
        if (executable) executeOrder(index, price, currentTime)
    }

    private fun executeOrder(
        index: Int,
        price: Double,
        executedAt: Instant,
        protectionSnapshot: TradingProtectionSnapshot = tradingProtectionSnapshot,
    ) {
        val order = orders[index]
        if (!order.isOpen) return
        val stock = stockById.getValue(order.stockId)
        val protectionDecision = TradingProtectionEngine.permission(
            protectionSnapshot,
            TradingProtectionRequest(
                market = stock.market,
                action = TradingProtectionAction.EXECUTE_TRADE,
                stockId = stock.id,
                proposedExecutionPrice = price,
            ),
            executedAt,
        )
        if (!protectionDecision.allowed || !listingLifecycleStates.getValue(stock.id).isTradable) return
        val quantity = order.remainingQuantity
        val gross = roundCurrency(price * quantity, stock.currency)
        val tradedOn = marketDate(stock.market, executedAt)
        val feeBreakdown = brokerFeeCalculator.calculate(
            BrokerFeeRequest(
                market = stock.market,
                side = order.side,
                grossAmount = money(gross, stock.currency),
                quantity = quantity,
                tradedOn = tradedOn,
            ),
        )
        val commission = feeBreakdown.totalFees.amount
        val preSaleHolding = if (order.side == OrderSide.SELL) holdings[stock.id] else null
        val taxBreakdown = when {
            order.side != OrderSide.SELL || !stock.market.isKorean -> null
            stock.etfProfile != null -> {
                val acquisitionValueKrw = round((preSaleHolding?.averagePrice ?: price) * quantity).toLong()
                val positiveTradingGain = (gross.toLong() - acquisitionValueKrw).coerceAtLeast(0L)
                domesticEtfSaleTaxCalculator.calculate(
                    DomesticEtfSaleTaxRequest(
                        taxCategory = stock.etfProfile.taxCategory,
                        grossProceedsKrw = gross.toLong(),
                        acquisitionValueKrw = acquisitionValueKrw,
                        taxableStandardGainKrw = round(
                            positiveTradingGain * stock.etfProfile.taxablePriceGainRatio,
                        ).toLong(),
                        soldOn = tradedOn,
                    ),
                )
            }
            else -> domesticSaleTaxCalculator.calculate(
                DomesticSaleTaxRequest(
                    market = stock.market,
                    grossProceedsKrw = gross.toLong(),
                    soldOn = tradedOn,
                ),
            )
        }
        val saleTax = taxBreakdown?.totalTax?.amount ?: 0.0
        val exchangeRateToKrw = if (stock.currency == Currency.USD) macro.usdKrw else 1.0
        val settledOn = settlementDate(stock.market, tradedOn)
        val tradeId = nextId("trade")

        if (order.side == OrderSide.BUY) {
            val required = gross + commission
            if (!ensureCash(stock.currency, required)) {
                orders[index] = order.copy(
                    status = OrderStatus.REJECTED,
                    updatedAt = executedAt,
                    rejectionReason = "주문 체결에 필요한 잔고가 부족합니다.",
                )
                lastMessage = "잔고 부족으로 주문이 거부되었습니다."
                return
            }
            cash[stock.currency] = roundCurrency(cash.getValue(stock.currency) - required, stock.currency)
            val previous = holdings[stock.id]
            val newQuantity = (previous?.quantity ?: 0.0) + quantity
            val totalCost = (previous?.costBasis ?: 0.0) + gross + commission
            holdings[stock.id] = Holding(
                stockId = stock.id,
                quantity = newQuantity,
                averagePrice = totalCost / newQuantity,
                // Execution price belongs to the trade/cost basis. Portfolio valuation stays on
                // the canonical quote mark even when an immediate fill occurs at best ask.
                currentPrice = quotes.getValue(stock.id).price,
                currency = stock.currency,
                realizedProfit = previous?.realizedProfit ?: 0.0,
            )
            fifoCostBasisBook = fifoCostBasisBook.addPurchase(
                lotId = tradeId,
                stockId = stock.id,
                acquiredOn = settledOn,
                quantity = quantity,
                purchasePriceKrw = round(gross * exchangeRateToKrw).toLong(),
                directPurchaseCostsKrw = round(commission * exchangeRateToKrw).toLong(),
            )
        } else {
            val previous = holdings[stock.id]
            if (previous == null || previous.quantity + QUANTITY_EPSILON < quantity) {
                orders[index] = order.copy(
                    status = OrderStatus.REJECTED,
                    updatedAt = executedAt,
                    rejectionReason = "매도 가능한 수량이 부족합니다.",
                )
                lastMessage = "보유 수량 부족으로 주문이 거부되었습니다."
                return
            }
            val proceeds = gross - commission - saleTax
            cash[stock.currency] = roundCurrency(cash.getValue(stock.currency) + proceeds, stock.currency)
            val costBasis = previous.averagePrice * quantity
            val realized = gross - costBasis - commission - saleTax
            val (taxTreatment, assessmentNotes) = assessStockGainTreatment(stock, tradedOn, previous)
            val fifoSale = fifoCostBasisBook.sell(
                stockId = stock.id,
                soldOn = settledOn,
                quantity = quantity,
                grossProceedsKrw = round(gross * exchangeRateToKrw).toLong(),
                directSellingCostsKrw = round((commission + saleTax) * exchangeRateToKrw).toLong(),
            )
            fifoCostBasisBook = fifoSale.updatedBook
            val remaining = (previous.quantity - quantity).coerceAtLeast(0.0)
            if (remaining < stock.quantityStep / 2.0) {
                holdings.remove(stock.id)
            } else {
                holdings[stock.id] = previous.copy(
                    quantity = remaining,
                    // A best-bid fill must not replace the independently persisted quote mark.
                    currentPrice = quotes.getValue(stock.id).price,
                    realizedProfit = previous.realizedProfit + realized,
                )
            }
            realizedGains += RealizedGainRecord(
                tradeId = tradeId,
                stockId = stock.id,
                market = stock.market,
                soldAt = executedAt,
                settlementDate = settledOn,
                quantity = quantity,
                proceeds = gross,
                costBasis = costBasis,
                commission = commission,
                saleTax = saleTax,
                currency = stock.currency,
                exchangeRateToKrw = exchangeRateToKrw,
                taxTreatment = taxTreatment,
                assessmentNotes = assessmentNotes,
                taxGrossProceedsKrw = fifoSale.grossProceedsKrw,
                taxCostBasisKrw = fifoSale.allocatedCostBasisKrw,
                taxDirectSellingCostsKrw = fifoSale.directSellingCostsKrw,
                taxGainKrw = fifoSale.realizedGainKrw,
                taxableFinancialIncomeKrw = if (
                    stock.etfProfile?.taxCategory == EtfTaxCategory.KOREAN_OTHER
                ) {
                    taxBreakdown?.taxableBase?.minorUnits ?: 0L
                } else {
                    0L
                },
            )
        }

        val accountingSequence = nextSequence++
        val trade = Trade(
            id = tradeId,
            orderId = order.id,
            stockId = stock.id,
            side = order.side,
            quantity = quantity,
            price = price,
            currency = stock.currency,
            executedAt = executedAt,
            commission = commission,
            tax = saleTax,
            accountingSequence = accountingSequence,
        )
        trades += trade
        transactionCosts += TransactionCostRecord(
            tradeId = tradeId,
            stockId = stock.id,
            market = stock.market,
            paidAt = executedAt,
            currency = stock.currency,
            commission = commission,
            saleTax = saleTax,
            exchangeRateToKrw = exchangeRateToKrw,
            feeBreakdown = feeBreakdown,
            taxBreakdown = taxBreakdown,
        )
        taxExchangeRatesByTradeId[tradeId] = exchangeRateToKrw
        if (stock.market.isUnitedStates) pendingTaxSettlementTradeIds += tradeId
        orders[index] = order.copy(
            status = OrderStatus.FILLED,
            filledQuantity = order.quantity,
            averageFilledPrice = price,
            updatedAt = executedAt,
        )
        if (order.side == OrderSide.SELL) recalculateAnnualTax(settledOn.year)
    }

    private fun validateOrderResources(stock: StockDefinition, request: OrderRequest): Boolean {
        if (request.side == OrderSide.SELL) {
            val owned = holdings[stock.id]?.quantity ?: 0.0
            val reserved = orders.filter {
                it.stockId == stock.id && it.side == OrderSide.SELL && it.isOpen
            }.sumOf(Order::remainingQuantity)
            if (owned - reserved + QUANTITY_EPSILON < request.quantity) {
                return fail("주문 가능한 보유 수량이 부족합니다.")
            }
            return true
        }

        val referencePrice = request.limitPrice ?: quotes.getValue(stock.id).askPrice ?: quotes.getValue(stock.id).price
        val estimated = referencePrice * request.quantity * BUY_RESERVE_MULTIPLIER
        val existingReservation = orders.filter {
            it.side == OrderSide.BUY && it.stockId.let(stockById::get)?.currency == stock.currency && it.isOpen
        }.sumOf { open ->
            val openStock = stockById.getValue(open.stockId)
            val openPrice = open.limitPrice ?: quotes.getValue(open.stockId).price
            openPrice * open.remainingQuantity * BUY_RESERVE_MULTIPLIER
        }
        val nativeAvailable = cash.getValue(stock.currency) - existingReservation
        if (nativeAvailable + CASH_EPSILON >= estimated) return true
        if (stock.currency == Currency.USD && options.autoExchange) {
            val usdFromKrw = cash.getValue(Currency.KRW) / macro.usdKrw * (1.0 - FX_SPREAD_RATE)
            if (nativeAvailable + usdFromKrw + CASH_EPSILON >= estimated) return true
        }
        return fail("주문 가능 현금이 부족합니다.")
    }

    private fun ensureCash(currency: Currency, required: Double): Boolean {
        if (cash.getValue(currency) + CASH_EPSILON >= required) return true
        if (currency != Currency.USD || !options.autoExchange) return false
        val shortage = required - cash.getValue(Currency.USD)
        val krwRequired = shortage * macro.usdKrw / (1.0 - FX_SPREAD_RATE) + 1.0
        if (!exchangeInternal(Currency.KRW, Currency.USD, krwRequired, automatic = true)) return false
        return cash.getValue(Currency.USD) + CASH_EPSILON >= required
    }

    private fun exchangeInternal(
        from: Currency,
        to: Currency,
        sourceAmount: Double,
        automatic: Boolean,
    ): Boolean {
        val roundedSource = roundCurrency(sourceAmount, from)
        if (roundedSource <= 0.0 || cash.getValue(from) + CASH_EPSILON < roundedSource) {
            return fail("환전할 잔고가 부족합니다.")
        }
        val received = when {
            from == Currency.KRW && to == Currency.USD ->
                roundCurrency(roundedSource / macro.usdKrw * (1.0 - FX_SPREAD_RATE), Currency.USD)
            from == Currency.USD && to == Currency.KRW ->
                roundCurrency(roundedSource * macro.usdKrw * (1.0 - FX_SPREAD_RATE), Currency.KRW)
            else -> return fail("지원하지 않는 환전 통화입니다.")
        }
        if (received <= 0.0) return fail("환전 후 수령 금액이 최소 통화 단위보다 작습니다.")
        cash[from] = roundCurrency(cash.getValue(from) - roundedSource, from)
        cash[to] = roundCurrency(cash.getValue(to) + received, to)
        val spreadCostKrw = when (from) {
            Currency.KRW -> roundedSource * FX_SPREAD_RATE
            Currency.USD -> roundedSource * macro.usdKrw * FX_SPREAD_RATE
        }
        foreignExchanges += ForeignExchangeRecord(
            id = nextId("fx"),
            executedAt = currentTime,
            fromCurrency = from,
            toCurrency = to,
            sourceAmount = roundedSource,
            receivedAmount = received,
            usdKrwRate = macro.usdKrw,
            spreadCostKrw = spreadCostKrw,
            automatic = automatic,
        )
        return true
    }

    private fun processScheduledDividends(from: Instant, to: Instant) {
        // Build every structural, market and holder-cash transition against the unchanged opening
        // snapshot first. A later product must not leave earlier CEF/ETN state, quotes or cash
        // partially committed if its legal accounting rejects the proposed distribution.
        val commits = mutableListOf<() -> Unit>()
        val plannedLastEvaluatedDistributionDateByStock =
            lastEvaluatedDistributionDateByStock.toMutableMap()
        val plannedDividends = mutableListOf<DividendLedgerEntry>()
        val plannedCash = cash.toMutableMap()
        var plannedFifoCostBasisBook = fifoCostBasisBook
        var plannedNextSequence = nextSequence
        val affectedTaxYears = linkedSetOf<Int>()

        for (stock in stocks) {
            if (isInstrumentMatured(stock, to)) continue
            if (listingLifecycleStates[stock.id]?.isSettlementPending == true) continue
            val fromDate = marketDate(stock.market, from)
            val payDate = marketDate(stock.market, to)
            val frequency = stock.behavior.distributionFrequency
            if (
                payDate == fromDate ||
                !DistributionSchedule.isDistributionDate(payDate, frequency)
            ) continue
            val lastEvaluatedDate = lastEvaluatedDistributionDateByStock[stock.id]
            require(lastEvaluatedDate == null || lastEvaluatedDate <= payDate) {
                "Distribution evaluation date moved backwards for ${stock.id}: " +
                    "last=$lastEvaluatedDate, next=$payDate"
            }
            if (lastEvaluatedDate == payDate) continue
            // Mark the cadence boundary as evaluated before yield/cap calculation. A zero payout is
            // still a completed decision and must not be recalculated differently on turn retry.
            plannedLastEvaluatedDistributionDateByStock[stock.id] = payDate

            val annualIncomeYield = currentProductAnnualDistributionYield(stock)

            // The game combines ex-date and payment date. Removing the gross per-unit
            // distribution from the quote prevents a buy-before-payment free-cash exploit.
            val quoteBeforeDistribution = quotes.getValue(stock.id)
            val periodsPerYear = frequency.periodsPerYear
            if (periodsPerYear <= 0) continue
            val etnStateBeforeDistribution = etnStates[stock.id]
                ?.takeIf { state -> state.lifecycle == EtnLifecycle.ACTIVE }
            var grossPerUnit = if (stock.instrumentType == InstrumentType.ETN) {
                etnStateBeforeDistribution?.accruedCouponPerNote ?: 0.0
            } else {
                if (annualIncomeYield <= 0.0) continue
                quoteBeforeDistribution.price * annualIncomeYield / periodsPerYear
            }
            if (stock.instrumentType == InstrumentType.CLOSED_END_FUND) {
                val previous = closedEndFundStates.getValue(stock.id)
                val product = requireNotNull(stock.fundProductProfile)
                val engine = ClosedEndFundEngine(
                    terms = requireNotNull(product.closedEndFundTerms),
                    marketModelParameters = requireNotNull(product.closedEndFundMarketModelParameters),
                )
                grossPerUnit = minOf(
                    grossPerUnit,
                    engine.maximumPermittedCommonDistributionPerShare(previous),
                )
            }
            if (grossPerUnit <= 0.0) continue

            var classifiedReturnOfCapitalPerUnit: Double? = null
            val structuralPriceAfterDistribution = when (stock.instrumentType) {
                InstrumentType.ETN -> {
                    val previous = requireNotNull(etnStateBeforeDistribution)
                    val terms = requireNotNull(stock.fundProductProfile?.etnProductTerms)
                    val advance = EtnEngine(terms).advance(
                        state = previous,
                        input = EtnAdvanceInput(
                            effectiveAt = to,
                            effectiveDate = payDate,
                            elapsedYearFraction = 0.0,
                            referenceLogReturn = 0.0,
                            couponPaymentPerNote = grossPerUnit,
                        ),
                    )
                    require(advance.previousRevision == previous.revision)
                    require(advance.ledgerEntries.none { next ->
                        etnLedger.any { existing -> existing.id == next.id }
                    })
                    val nextState = advance.state
                    val nextLedger = advance.ledgerEntries.toList()
                    commits += {
                        etnStates[stock.id] = nextState
                        etnLedger += nextLedger
                    }
                    val markedBefore = etnCreditMarkedValue(
                        state = previous,
                        maturityDate = terms.maturityDate,
                        valuationDate = payDate,
                    )
                    val markedAfter = etnCreditMarkedValue(
                        state = advance.state,
                        maturityDate = terms.maturityDate,
                        valuationDate = payDate,
                    )
                    quoteBeforeDistribution.price * markedAfter / markedBefore
                }
                InstrumentType.CLOSED_END_FUND -> {
                    val previous = closedEndFundStates.getValue(stock.id)
                    val terms = requireNotNull(stock.fundProductProfile?.closedEndFundTerms)
                    val parameters = requireNotNull(
                        stock.fundProductProfile.closedEndFundMarketModelParameters,
                    )
                    val incomePerShare = minOf(
                        grossPerUnit,
                        previous.undistributedNetInvestmentIncome
                            .coerceAtLeast(0.0) / previous.commonSharesOutstanding,
                    )
                    val afterIncome = grossPerUnit - incomePerShare
                    val realizedGainPerShare = minOf(
                        afterIncome,
                        previous.distributionReserve / previous.commonSharesOutstanding,
                    )
                    val returnOfCapitalPerShare = afterIncome - realizedGainPerShare
                    val distribution = ClosedEndFundDistribution(
                        netInvestmentIncomePerShare = incomePerShare,
                        realizedGainPerShare = realizedGainPerShare,
                        returnOfCapitalPerShare = returnOfCapitalPerShare,
                    )
                    val advance = ClosedEndFundEngine(terms, parameters).advance(
                        state = previous,
                        input = ClosedEndFundAdvanceInput(
                            effectiveAt = to,
                            elapsedYearFraction = 0.0,
                            assetTotalLogReturn = 0.0,
                            grossInvestmentIncome = 0.0,
                            annualBorrowingRate = 0.0,
                            annualPreferredDistributionRate = 0.0,
                            operatingExpenses = 0.0,
                            realizedGainReserveChange = 0.0,
                            marketDiscountShock = 0.0,
                            distribution = distribution,
                        ),
                    )
                    require(advance.previousRevision == previous.revision)
                    require(advance.ledgerEntries.none { next ->
                        closedEndFundLedger.any { existing -> existing.id == next.id }
                    })
                    val nextState = advance.state
                    val nextLedger = advance.ledgerEntries.toList()
                    commits += {
                        closedEndFundStates[stock.id] = nextState
                        closedEndFundLedger += nextLedger
                    }
                    classifiedReturnOfCapitalPerUnit = returnOfCapitalPerShare
                    quoteBeforeDistribution.price *
                        advance.state.marketPricePerCommonShare /
                        previous.marketPricePerCommonShare
                }
                else -> {
                    fundFinancialStates[stock.id]?.let { financialState ->
                        val nextState = instrumentMetricsEngine.applyFundDistribution(
                            state = financialState,
                            grossPerUnit = grossPerUnit,
                            at = to,
                        )
                        commits += { fundFinancialStates[stock.id] = nextState }
                    }
                    quoteBeforeDistribution.price - grossPerUnit
                }
            }
            val adjustedPrice = MarketMicrostructure.roundNearest(
                stock,
                structuralPriceAfterDistribution
                    .coerceAtLeast(MarketMicrostructure.tickSize(stock, quoteBeforeDistribution.price)),
            )
            val adjustedQuote = quoteBeforeDistribution.copy(
                price = adjustedPrice,
                high = maxOf(quoteBeforeDistribution.high, adjustedPrice),
                low = minOf(quoteBeforeDistribution.low, adjustedPrice),
                bidPrice = null,
                askPrice = null,
                bidQuantity = 0.0,
                askQuantity = 0.0,
            )
            commits += {
                quotes[stock.id] = adjustedQuote
                dailyTrackers[stock.id]?.let { tracker ->
                    // The post-bar distribution adjustment becomes the next interval's previous
                    // price. Keep date/base/open, extending only the observed price range.
                    tracker.high = maxOf(tracker.high, adjustedPrice)
                    tracker.low = minOf(tracker.low, adjustedPrice)
                }
                holdings[stock.id]?.let { holding ->
                    holdings[stock.id] = holding.copy(currentPrice = adjustedPrice)
                }
            }

            val holding = holdings[stock.id] ?: continue
            val ledgerId = "dividend:${stock.id}:$payDate"
            if (dividends.any { it.id == ledgerId } || plannedDividends.any { it.id == ledgerId }) continue

            val gross = holding.quantity * grossPerUnit
            val rocEligible = stock.market.isUnitedStates && stock.instrumentType in setOf(
                InstrumentType.ETF,
                InstrumentType.CLOSED_END_FUND,
            )
            val taxableCoverage = classifiedReturnOfCapitalPerUnit?.let { returnOfCapitalPerUnit ->
                ((grossPerUnit - returnOfCapitalPerUnit) / grossPerUnit).coerceIn(0.0, 1.0)
            } ?: if (rocEligible) {
                stock.behavior.distributionCoverageRatio.coerceIn(0.0, 1.0)
            } else {
                1.0
            }
            val taxableGross = gross * taxableCoverage
            val result = dividendTaxCalculator.calculate(
                DividendTaxRequest(
                    taxClass = when {
                        stock.market.isKorean && stock.isFundLike -> DividendTaxClass.KOREAN_ETF_DISTRIBUTION
                        stock.market.isKorean -> DividendTaxClass.KOREAN_ORDINARY_CASH
                        else -> when (stock.instrumentType) {
                            InstrumentType.ETF -> DividendTaxClass.US_RIC_ETF_DISTRIBUTION
                            InstrumentType.CLOSED_END_FUND -> DividendTaxClass.US_RIC_CLOSED_END_DISTRIBUTION
                            InstrumentType.ETN -> DividendTaxClass.US_ETN_CONTINGENT_COUPON
                            InstrumentType.REIT -> DividendTaxClass.US_REIT_DISTRIBUTION
                            InstrumentType.ADR -> DividendTaxClass.FOREIGN_ADR_DISTRIBUTION
                            InstrumentType.STOCK -> DividendTaxClass.US_ORDINARY_CORPORATION
                        }
                    },
                    grossAmount = money(taxableGross, stock.currency),
                    paidOn = payDate,
                    taxExchangeRateToKrw = if (stock.currency == Currency.USD) macro.usdKrw else 1.0,
                    w8BenValid = true,
                    otherFinancialIncomeGrossKrw = CheckedMonetaryArithmetic.sum(
                        (dividends.asSequence() + plannedDividends.asSequence())
                            .filter { gameDate(it.paidAt).year == payDate.year }
                            .map { entry ->
                                CheckedMonetaryArithmetic.roundedToLong(
                                    entry.financialIncomeAmountKrw,
                                    "Dividend financial income",
                                )
                            },
                        "Annual dividend financial income",
                    ),
                ),
            )
            val roundedTaxableGross = result.breakdown.taxableBase.amount
            val roundedGross = roundCurrency(gross, stock.currency)
            val returnOfCapital = (roundedGross - roundedTaxableGross).coerceAtLeast(0.0)
            val tax = result.breakdown.totalTax.amount
            val net = roundCurrency(result.netCash.amount + returnOfCapital, stock.currency)
            val exchangeRate = if (stock.currency == Currency.USD) macro.usdKrw else 1.0
            val (updatedBasis, excessRocGainKrw) = plannedFifoCostBasisBook.applyReturnOfCapital(
                stockId = stock.id,
                amountKrw = round(returnOfCapital * exchangeRate).toLong(),
            )
            plannedFifoCostBasisBook = updatedBasis
            plannedCash[stock.currency] = roundCurrency(
                plannedCash.getValue(stock.currency) + net,
                stock.currency,
            )
            plannedDividends += DividendLedgerEntry(
                id = ledgerId,
                stockId = stock.id,
                paidAt = to,
                currency = stock.currency,
                grossAmount = roundedGross,
                withholdingTax = tax,
                netAmount = net,
                exchangeRateToKrw = exchangeRate,
                taxBreakdown = result.breakdown,
                taxableIncomeAmount = roundedTaxableGross,
                returnOfCapitalAmount = returnOfCapital,
                excessReturnOfCapitalGainKrw = excessRocGainKrw,
                accountingSequence = plannedNextSequence++,
            )
            affectedTaxYears += payDate.year
        }

        // Annual tax construction is validating and therefore belongs to the immutable planning
        // phase. No product state or holder cash has changed if a monetary bound is exceeded.
        val plannedDividendSnapshot = dividends + plannedDividends
        val plannedTaxProjections = affectedTaxYears.associateWith { year ->
            requireNotNull(calculateAnnualTaxProjection(year, plannedDividendSnapshot, realizedGains))
        }

        // The planning phase above has completed without mutating Runtime state. From here every
        // operation is a non-validating assignment/list append, so the batch is committed once.
        commits.forEach { commit -> commit() }
        lastEvaluatedDistributionDateByStock.clear()
        lastEvaluatedDistributionDateByStock.putAll(plannedLastEvaluatedDistributionDateByStock)
        fifoCostBasisBook = plannedFifoCostBasisBook
        cash.clear()
        cash.putAll(plannedCash)
        dividends += plannedDividends
        nextSequence = plannedNextSequence
        plannedTaxProjections.forEach { (year, projection) ->
            applyAnnualTaxProjection(year, projection)
        }
    }

    /**
     * Returns the latest income yield produced by the same benchmark state that drives NAV.
     * Keeping this lookup shared with cash distributions prevents a reconstitution from changing
     * price carry while leaving the next payment pinned to stale catalog metadata.
     */
    private fun currentBenchmarkAnnualIncomeYield(benchmarkRef: BenchmarkRef): Double? {
        val methodologyPortfolioId = executablePortfolioIdByBenchmarkRef[benchmarkRef]
        return methodologyPortfolioId
            ?.let(referencePortfolioStates::get)
            ?.estimatedAnnualIncomeYield
            ?: equityReferenceStates[benchmarkRef]?.estimatedAnnualIncomeYield
            ?: fundOfFundsStates[benchmarkRef]?.estimatedAnnualIncomeYield
            ?: alternativeRiskPremiaStates[benchmarkRef]?.estimatedAnnualIncomeYield
            ?: compositeReferenceStates[benchmarkRef]?.estimatedAnnualIncomeYield
            ?: fixedIncomeReferenceStates[
                FixedIncomeReferenceState.referenceIdFor(benchmarkRef)
            ]?.estimatedAnnualIncomeYield
            ?: 0.0.takeIf {
                benchmarkRef in commoditySpotReferenceStates ||
                    benchmarkRef in futuresReferenceStates
            }
    }

    /**
     * Plain and fund-of-funds products pass through the income generated by their current
     * benchmark composition. Managed-distribution CEFs and derivative-income overlays instead
     * follow a product payout policy: their benchmark yield is only one input and must not replace
     * the declared product-level distribution rate. ETN cash coupons are settled from their
     * contract state separately in [processScheduledDividends].
     */
    private fun currentProductAnnualDistributionYield(stock: StockDefinition): Double {
        val profile = stock.fundProductProfile ?: return stock.dividendYield
        val hasProductPayoutOverlay = profile.dailyResetTerms != null ||
            profile.optionStrategyTerms != null ||
            profile.cashCollateralizedPutSpreadTerms != null ||
            profile.legalStructure == FundLegalStructure.CLOSED_END_FUND
        return if (hasProductPayoutOverlay) {
            stock.dividendYield
        } else {
            currentBenchmarkAnnualIncomeYield(profile.benchmarkRef) ?: stock.dividendYield
        }
    }

    private fun expireDayOrders(time: Instant) {
        for (index in orders.indices) {
            val order = orders[index]
            if (!order.isOpen || order.timeInForce != TimeInForce.DAY) continue
            val stock = stockById.getValue(order.stockId)
            val created = GameCalendar.marketLocalDateTime(stock.market, order.createdAt)
            val now = GameCalendar.marketLocalDateTime(stock.market, time)
            val close = if (stock.market.isKorean) LocalTime(15, 30) else LocalTime(16, 0)
            val targetTradingDate = dayOrderTargetTradingDate(stock.market, created.date, created.time, close)
            if (now.date > targetTradingDate || (now.date == targetTradingDate && now.time >= close)) {
                orders[index] = order.copy(status = OrderStatus.EXPIRED, updatedAt = time)
            }
        }
    }

    private fun dayOrderTargetTradingDate(
        market: Market,
        createdDate: LocalDate,
        createdTime: LocalTime,
        close: LocalTime,
    ): LocalDate {
        val createdIsTradingDay = isTradingDate(market, createdDate)
        if (createdIsTradingDay && createdTime < close) return createdDate
        var candidate = createdDate.plus(1, DateTimeUnit.DAY)
        while (!isTradingDate(market, candidate) && candidate <= LocalDate(2040, 12, 31)) {
            candidate = candidate.plus(1, DateTimeUnit.DAY)
        }
        return candidate
    }

    private fun isTradingDate(market: Market, date: LocalDate): Boolean {
        if (date.dayOfWeek in WEEKEND) return false
        return date !in runtimeClosedDates(market, date)
    }

    private fun updateHoldingPrices() {
        for ((stockId, holding) in holdings.toMap()) {
            val pendingCashDisposition = listingLifecycleStates[stockId]
                ?.takeIf { it.status == ListingLifecycleStatus.LIQUIDATION_PENDING }
                ?.finalDisposition
                ?.takeIf { it.type == ListingFinalDispositionType.CASH_LIQUIDATION }
            val contractualUnitValue = pendingCashDisposition?.cashPerUnit
            holdings[stockId] = holding.copy(
                currentPrice = contractualUnitValue ?: quotes.getValue(stockId).price,
            )
        }
    }

    private fun updateMarketChange(
        bars: Map<String, PriceBar>,
        fractions: Map<Market, Double>,
    ) {
        val updated = macro.marketChangeFromPreviousClose.toMutableMap()
        for (market in Market.entries) {
            if (fractions.getValue(market) <= 0.0) continue
            val marketBars = stocks.filter { stock ->
                listingLifecycleStates.getValue(stock.id).isIndexEligible &&
                    stock.hasCorporateEarnings && if (market.isUnitedStates) {
                    stock.market.isUnitedStates
                } else {
                    stock.market == market
                }
            }.map { bars.getValue(it.id) }
            if (marketBars.isEmpty()) continue
            val hourly = marketBars.map { if (it.open == 0.0) 0.0 else it.close / it.open - 1.0 }.average()
            updated[market] = (1.0 + (updated[market] ?: 0.0)) * (1.0 + hourly) - 1.0
        }
        macro = macro.copy(marketChangeFromPreviousClose = updated)
    }

    private fun updateMarketIndices(
        timestamp: Instant,
        bars: Map<String, PriceBar>,
        previousClosesByStockId: Map<String, Double>,
        fractions: Map<Market, Double>,
    ) {
        val calculated = calculateMarketIndices(timestamp, bars, previousClosesByStockId, fractions)
        marketIndices.clear()
        marketIndices.putAll(calculated)
        for ((id, snapshot) in calculated) {
            val values = marketIndexHistory.getOrPut(id) { ArrayDeque() }
            values.addLast(snapshot)
            while (values.size > MAX_INDEX_BARS) values.removeFirst()
        }
    }

    private fun calculateMarketIndices(
        timestamp: Instant,
        bars: Map<String, PriceBar>,
        previousClosesByStockId: Map<String, Double>,
        fractions: Map<Market, Double>,
    ): Map<MarketIndexId, MarketIndexSnapshot> {
        val indexStocks = stocks.filter { stock ->
            stock.market.isUnitedStates && stock.hasCorporateEarnings &&
                listingLifecycleStates.getValue(stock.id).isIndexEligible
        }
        val indexStockIds = indexStocks.mapTo(linkedSetOf(), StockDefinition::id)
        val hasUsTrading = fractions.any { (market, fraction) -> market.isUnitedStates && fraction > 0.0 }
        return marketIndexEngine.calculate(
            MarketIndexCalculationInput(
                timestamp = timestamp,
                stocks = indexStocks,
                barsByStockId = bars.filterKeys(indexStockIds::contains),
                previousCloseByStockId = previousClosesByStockId.filterKeys(indexStockIds::contains),
                previousIndices = marketIndices,
                macro = macro,
                // Price bars already contain the 09:30 half-hour fraction, so do not scale twice.
                usTradingFraction = if (hasUsTrading) 1.0 else 0.0,
            ),
        )
    }

    private fun marketProtectionTradingFraction(
        market: Market,
        from: Instant,
        to: Instant,
        ordinaryFraction: Double,
        additionalBlocks: List<RuntimeTradingInterval> = emptyList(),
    ): Double {
        if (ordinaryFraction <= 0.0) return 0.0
        return runtimeTradingFraction(
            from,
            to,
            runtimeTradableIntervals(
                market,
                from,
                to,
                marketProtectionBlocks(market, from, to) + additionalBlocks,
            ),
        )
    }

    private fun instrumentProtectionTradingIntervals(
        stock: StockDefinition,
        from: Instant,
        to: Instant,
        additionalBlocks: List<RuntimeTradingInterval> = emptyList(),
    ): List<RuntimeTradingInterval> {
        if (!listingLifecycleStates.getValue(stock.id).isTradable) return emptyList()
        val blocks = marketProtectionBlocks(stock.market, from, to).toMutableList()
        tradingProtectionSnapshot.instrumentTradingHalts[stock.id]?.let { halt ->
            if (halt.status == TradingHaltStatus.ACTIVE) {
                runtimeTradingBlock(halt.startedAt, halt.scheduledReleaseAt ?: to, from, to)?.let(blocks::add)
            }
        }
        tradingProtectionSnapshot.scheduledInstrumentTradingHalts.values
            .asSequence()
            .filter { halt -> halt.stockId == stock.id && halt.status == TradingHaltStatus.ACTIVE }
            .forEach { halt ->
                runtimeTradingBlock(
                    halt.startedAt,
                    halt.scheduledReleaseAt ?: to,
                    from,
                    to,
                )?.let(blocks::add)
            }
        tradingProtectionSnapshot.krxVolatilityInterruptions[stock.id]?.let { vi ->
            if (vi.phase == KrxViPhase.CALL_AUCTION) {
                runtimeTradingBlock(
                    vi.triggeredAt ?: from,
                    requireNotNull(vi.auctionEndsAt),
                    from,
                    to,
                )?.let(blocks::add)
            }
        }
        tradingProtectionSnapshot.usLuldStates[stock.id]?.let { luld ->
            val luldBlock = when (luld.phase) {
                UsLuldPhase.NORMAL -> null
                UsLuldPhase.LIMIT_STATE -> {
                    val startsAt = requireNotNull(luld.limitStateDeadline)
                    runtimeTradingBlock(
                        startsAt,
                        startsAt + TradingProtectionRules.US_LULD_PAUSE + US_REOPENING_AUCTION_MINUTES.minutes,
                        from,
                        to,
                    )
                }
                UsLuldPhase.TRADING_PAUSE -> runtimeTradingBlock(
                    luld.pauseStartedAt ?: from,
                    requireNotNull(luld.pauseEndsAt) + US_REOPENING_AUCTION_MINUTES.minutes,
                    from,
                    to,
                )
                UsLuldPhase.REOPENING_AUCTION -> runtimeTradingBlock(
                    luld.pauseStartedAt ?: luld.reopeningStartedAt ?: from,
                    requireNotNull(luld.reopeningStartedAt) + US_REOPENING_AUCTION_MINUTES.minutes,
                    from,
                    to,
                )
                UsLuldPhase.CLOSING_AUCTION_ONLY,
                UsLuldPhase.CLOSED_FOR_DAY,
                -> runtimeTradingBlock(luld.pauseStartedAt ?: from, to, from, to)
            }
            luldBlock?.let(blocks::add)
        }
        blocks += additionalBlocks
        return runtimeTradableIntervals(stock.market, from, to, blocks)
    }

    private fun marketProtectionBlocks(
        market: Market,
        from: Instant,
        to: Instant,
    ): List<RuntimeTradingInterval> {
        if (market.isKorean) {
            val state = tradingProtectionSnapshot.krxCircuitBreakers[market] ?: return emptyList()
            if (state.tradingDate != marketDate(market, from)) return emptyList()
            val end = when (state.phase) {
                KrxCircuitBreakerPhase.NORMAL -> return emptyList()
                KrxCircuitBreakerPhase.HALTED,
                KrxCircuitBreakerPhase.REOPENING_CALL_AUCTION,
                -> requireNotNull(state.reopeningEndsAt)
                KrxCircuitBreakerPhase.CLOSED_FOR_DAY -> to
            }
            return listOfNotNull(runtimeTradingBlock(state.triggeredAt ?: from, end, from, to))
        }

        val state = tradingProtectionSnapshot.usMarketWideCircuitBreaker ?: return emptyList()
        if (state.tradingDate != marketDate(market, from) || state.phase == UsMwcbPhase.NORMAL) return emptyList()
        val venue = state.venueStatuses[market] ?: return emptyList()
        if (venue.phase == UsMwcbVenuePhase.REOPENED) return emptyList()
        val end = if (state.phase == UsMwcbPhase.CLOSED_FOR_DAY) {
            to
        } else {
            requireNotNull(state.haltEndsAt) + usVenueReopeningDelayMinutes(market).minutes
        }
        return listOfNotNull(runtimeTradingBlock(state.triggeredAt ?: from, end, from, to))
    }

    private fun runtimeTradingBlock(
        startsAt: Instant,
        endsAt: Instant,
        from: Instant,
        to: Instant,
    ): RuntimeTradingInterval? {
        val start = maxOf(startsAt, from)
        val end = minOf(endsAt, to)
        return if (end > start) RuntimeTradingInterval(start, end) else null
    }

    private fun fractionAfter(
        from: Instant,
        to: Instant,
        resumesAt: Instant,
        maximum: Double,
    ): Double {
        if (resumesAt <= from) return maximum
        if (resumesAt >= to) return 0.0
        val wallFraction = (to - resumesAt).inWholeNanoseconds / 3_600_000_000_000.0
        return minOf(maximum, wallFraction.coerceIn(0.0, 1.0))
    }

    private fun usVenueReopeningDelayMinutes(market: Market): Int = when (market) {
        Market.NASDAQ, Market.NYSE_ARCA, Market.CBOE_BZX -> 1
        Market.NYSE, Market.NYSE_AMERICAN -> 2
        else -> 1
    }

    private fun advanceProtectionClock(at: Instant) {
        val krxCircuitBreakers = tradingProtectionSnapshot.krxCircuitBreakers.toMutableMap()
        for (market in listOf(Market.KOSPI, Market.KOSDAQ)) {
            val date = marketDate(market, at)
            val previous = krxCircuitBreakers[market]
                ?: TradingProtectionEngine.initialKrxCircuitBreaker(market, date)
            val transition = if (previous.tradingDate != date) {
                null
            } else {
                TradingProtectionEngine.advanceKrxCircuitBreaker(previous, at)
            }
            val next = transition?.state ?: TradingProtectionEngine.initialKrxCircuitBreaker(market, date)
            transition?.let { change ->
                recordKrxCircuitBreakerTransition(
                    market = market,
                    event = change.event,
                    state = change.state,
                    previous = previous,
                )
            }
            krxCircuitBreakers[market] = next
        }

        val savedUsMwcb = tradingProtectionSnapshot.usMarketWideCircuitBreaker
        val usDate = marketDate(Market.NYSE, at)
        val usAdvance = if (savedUsMwcb == null || savedUsMwcb.tradingDate != usDate) {
            null
        } else {
            TradingProtectionEngine.advanceUsMwcb(savedUsMwcb, at)
        }
        var usMwcb = if (usAdvance == null) {
            TradingProtectionEngine.initialUsMwcb(usDate, at)
        } else {
            usAdvance.state
        }
        if (usMwcb.phase == UsMwcbPhase.REOPENING_AUCTIONS) {
            val haltEnd = requireNotNull(usMwcb.haltEndsAt)
            for (market in usMwcb.venueStatuses.keys.sortedWith(usMwcbReopeningOrder())) {
                if (usMwcb.venueStatuses.getValue(market).phase != UsMwcbVenuePhase.REOPENED &&
                    at >= haltEnd + usVenueReopeningDelayMinutes(market).minutes
                ) {
                    val completesAt = haltEnd + usVenueReopeningDelayMinutes(market).minutes
                    val beforeCompletion = usMwcb
                    val completion = TradingProtectionEngine.completeUsMwcbVenueReopening(
                        usMwcb,
                        market,
                        completesAt,
                    )
                    usMwcb = completion.state
                    recordUsMwcbTransition(completion.event, usMwcb, completesAt, beforeCompletion)
                }
            }
        }

        val halts = tradingProtectionSnapshot.instrumentTradingHalts.toMutableMap()
        val scheduledHalts = tradingProtectionSnapshot.scheduledInstrumentTradingHalts.toMutableMap()
        val releasedHalts = mutableListOf<com.amond.kmpbook.domain.model.protection.core.InstrumentTradingHalt>()
        for ((stockId, halt) in halts.toMap()) {
            val legalReleaseAt = halt.scheduledReleaseAt
            if (halt.status == TradingHaltStatus.ACTIVE && legalReleaseAt != null && at >= legalReleaseAt) {
                val released = TradingProtectionEngine.releaseInstrumentTradingHalt(
                    halt,
                    legalReleaseAt,
                    "예정된 거래정지 시간이 끝났습니다.",
                )
                halts[stockId] = released
                releasedHalts += released
            }
        }
        // A future KRX alert halt is stored separately so a disclosure/listing halt can remain the
        // current source of truth. When intervals overlap, the pending restriction takes over only
        // for its remaining interval; there is no false resume transition between them.
        for ((scheduleId, scheduled) in scheduledHalts.toMap().toSortedMap()) {
            if (listingLifecycleStates[scheduled.stockId]?.isTerminal == true ||
                scheduled.scheduledReleaseAt?.let { at >= it } == true
            ) {
                scheduledHalts.remove(scheduleId)
                continue
            }
            if (at < scheduled.startedAt) continue
            val current = halts[scheduled.stockId]
            if (current != null && TradingProtectionEngine.isInstrumentHaltActive(current, at)) continue
            halts[scheduled.stockId] = scheduled
            scheduledHalts.remove(scheduleId)
        }
        for (releasedHalt in releasedHalts) {
            val stockId = releasedHalt.stockId
            val stock = stockById.getValue(stockId)
            val legalReleaseAt = requireNotNull(releasedHalt.releasedAt)
            val continuingHalt = halts[stockId]?.takeIf { candidate ->
                candidate.occurrenceId != releasedHalt.occurrenceId &&
                    TradingProtectionEngine.isInstrumentHaltActive(candidate, legalReleaseAt)
            }
            val regularTradingResumed = continuingHalt == null && GameCalendar.isRegularMarketOpen(
                stock.market,
                legalReleaseAt,
                runtimeClosedDates(stock.market, marketDate(stock.market, legalReleaseAt)),
            )
            val copy = when {
                continuingHalt != null -> "${stock.name} 거래정지 사유 하나가 끝났어요" to
                    "이 거래정지 사유는 해소됐지만 다른 거래정지 조치가 이어져 주문 체결은 아직 재개되지 않습니다."
                regularTradingResumed -> "${stock.name} 거래가 다시 시작됐어요" to
                    "거래정지 사유가 해소돼 정규장 주문과 체결이 재개됩니다."
                else -> "${stock.name} 거래정지 일정이 끝났어요" to
                    "거래정지 조치는 끝났지만 현재 정규장이 닫혀 있어 다음 정규장 개장 때 거래할 수 있습니다."
            }
            addProtectionNews(
                id = "halt-release:${releasedHalt.occurrenceId}",
                title = copy.first,
                description = copy.second,
                at = legalReleaseAt,
                stock = stock,
                severity = EventSeverity.MINOR,
                marketAction = MarketActionReference(
                    kind = MarketActionKind.INSTRUMENT_TRADING_HALT,
                    occurrenceId = releasedHalt.occurrenceId,
                    transition = MarketActionTransition.RELEASED,
                    announcedAt = legalReleaseAt,
                    effectiveAt = legalReleaseAt,
                    endsAt = legalReleaseAt,
                    stockId = stockId,
                    markets = setOf(stock.market),
                ),
            )
        }

        val vis = tradingProtectionSnapshot.krxVolatilityInterruptions.toMutableMap()
        for ((stockId, vi) in vis.toMap()) {
            if (vi.phase != KrxViPhase.CALL_AUCTION || at < requireNotNull(vi.auctionEndsAt)) continue
            val stock = stockById.getValue(stockId)
            val quote = quotes.getValue(stockId)
            vis[stockId] = TradingProtectionEngine.evaluateKrxVi(
                vi,
                KrxViObservation(
                    stockId = stockId,
                    market = stock.market,
                    observedAt = at,
                    kind = vi.kind ?: KrxViKind.DYNAMIC,
                    productClass = krxViProductClass(stock),
                    session = KrxViSession.CONTINUOUS_AUCTION,
                    referencePrice = vi.referencePrice ?: quote.price,
                    potentialExecutionPrice = quote.price.coerceAtLeast(1.0),
                    circuitBreakerPhase = krxCircuitBreakers.getValue(stock.market).phase,
                ),
            ).state
        }

        val luldStates = tradingProtectionSnapshot.usLuldStates.toMutableMap()
        for (stock in stocks.filter { stock ->
            stock.market.isUnitedStates && listingLifecycleStates.getValue(stock.id).isIndexEligible
        }) {
            var luld = luldStates[stock.id]
            val quote = quotes.getValue(stock.id)
            val local = GameCalendar.marketLocalDateTime(stock.market, at)
            if (luld == null || luld.tradingDate != local.date) {
                luld = TradingProtectionEngine.initialUsLuld(
                    stock.id,
                    stock.market,
                    local.date,
                    usLuldTier(stock),
                    quote.previousClose,
                    quote.price,
                    at,
                    local.time,
                )
            } else {
                var transition = TradingProtectionEngine.evaluateUsLuld(
                    luld,
                    UsLuldObservation(at, local.time),
                )
                luld = transition.state
                if (luld.phase == UsLuldPhase.REOPENING_AUCTION &&
                    at >= requireNotNull(luld.reopeningStartedAt) + US_REOPENING_AUCTION_MINUTES.minutes
                ) {
                    val beforeReopening = luld
                    val reopensAt = requireNotNull(luld.reopeningStartedAt) + US_REOPENING_AUCTION_MINUTES.minutes
                    val completion = TradingProtectionEngine.completeUsLuldReopening(
                        luld,
                        quote.price,
                        reopensAt,
                        GameCalendar.marketLocalDateTime(stock.market, reopensAt).time,
                    )
                    luld = completion.state
                    recordUsLuldTransition(stock, completion.event, luld, reopensAt, beforeReopening)
                }
                if (luld.phase == UsLuldPhase.CLOSING_AUCTION_ONLY && local.time >= LocalTime(16, 0)) {
                    luld = TradingProtectionEngine.closeUsLuldSession(luld).state
                }
            }
            luldStates[stock.id] = luld
        }

        tradingProtectionSnapshot = tradingProtectionSnapshot.copy(
            krxCircuitBreakers = krxCircuitBreakers,
            krxVolatilityInterruptions = vis,
            instrumentTradingHalts = halts,
            scheduledInstrumentTradingHalts = scheduledHalts,
            usMarketWideCircuitBreaker = usMwcb,
            usLuldStates = luldStates,
        )
    }

    private fun evaluateTradingProtections(
        from: Instant,
        to: Instant,
        bars: Map<String, PriceBar>,
        previousPrices: Map<String, Double>,
        provisionalIndices: Map<MarketIndexId, MarketIndexSnapshot>,
        ordinaryTradingFractions: Map<Market, Double>,
    ): TurnProtectionImpact {
        val impact = TurnProtectionImpactBuilder()
        evaluateKrxMarketProtections(from, to, bars, previousPrices, ordinaryTradingFractions, impact)
        evaluateKrxVolatilityInterruptions(from, to, bars, ordinaryTradingFractions, impact)
        evaluateUsMarketWideCircuitBreaker(
            from,
            to,
            bars,
            previousPrices,
            provisionalIndices,
            ordinaryTradingFractions,
            impact,
        )
        evaluateUsLuldProtections(from, to, bars, ordinaryTradingFractions, impact)
        return impact.build()
    }

    private fun evaluateKrxMarketProtections(
        from: Instant,
        to: Instant,
        bars: Map<String, PriceBar>,
        previousPrices: Map<String, Double>,
        ordinaryTradingFractions: Map<Market, Double>,
        impact: TurnProtectionImpactBuilder,
    ) {
        val cbStates = tradingProtectionSnapshot.krxCircuitBreakers.toMutableMap()
        val sidecarStates = tradingProtectionSnapshot.krxSidecars.toMutableMap()
        for (market in listOf(Market.KOSPI, Market.KOSDAQ)) {
            if (ordinaryTradingFractions.getValue(market) <= 0.0) continue
            val sessionInterval = runtimeTradableIntervals(market, from, to).firstOrNull() ?: continue
            val local = GameCalendar.marketLocalDateTime(market, to)
            val previousRate = krxMarketProxyRate(market, previousPrices)
            val (lowRate, highRate) = krxMarketProxyIntervalRates(market, bars)
            var cbState = cbStates[market]
                ?: TradingProtectionEngine.initialKrxCircuitBreaker(market, local.date)
            val targetRate = nextKrxCircuitBreakerTargetRate(market, cbState)
            val crossing = targetRate?.let { threshold ->
                downwardThresholdCrossing(
                    sessionInterval.startsAt,
                    sessionInterval.endsAt,
                    previousRate,
                    lowRate,
                    threshold,
                )
            }
            val conditionSince = when {
                cbState.pendingLevel != null && targetRate != null && lowRate <= targetRate -> cbState.conditionSince
                else -> crossing
            }
            val observationAt = conditionSince?.let { since ->
                runtimePersistenceObservationAt(
                    since,
                    sessionInterval.endsAt,
                    TradingProtectionRules.KRX_CB_PERSISTENCE,
                )
            } ?: sessionInterval.endsAt
            val observationLocal = GameCalendar.marketLocalDateTime(market, observationAt)
            val observedRate = lowRate
            val clockTransition = TradingProtectionEngine.advanceKrxCircuitBreaker(cbState, observationAt)
            recordKrxCircuitBreakerTransition(
                market = market,
                event = clockTransition.event,
                state = clockTransition.state,
                previous = cbState,
            )
            cbState = clockTransition.state
            val priorCircuitBreaker = cbState
            val cbTransition = TradingProtectionEngine.evaluateKrxCircuitBreaker(
                cbState,
                KrxCircuitBreakerObservation(
                    market = market,
                    tradingDate = local.date,
                    observedAt = observationAt,
                    indexValue = KRX_INDEX_BASE.getValue(market) * (1.0 + minOf(previousRate, observedRate)),
                    previousClose = KRX_INDEX_BASE.getValue(market),
                    minutesUntilClose = krxMinutesUntilClose(observationLocal.time),
                    conditionSatisfiedSince = conditionSince,
                ),
            )
            cbState = cbTransition.state
            cbStates[market] = cbState
            recordKrxCircuitBreakerTransition(
                market = market,
                event = cbTransition.event,
                state = cbState,
                previous = priorCircuitBreaker,
            )
            if (cbTransition.event in setOf(
                    KrxCircuitBreakerEvent.LEVEL_1_TRIGGERED,
                    KrxCircuitBreakerEvent.LEVEL_2_TRIGGERED,
                    KrxCircuitBreakerEvent.LEVEL_3_TRIGGERED,
                )
            ) {
                val blockEnd = cbState.reopeningEndsAt ?: to
                runtimeTradingBlock(observationAt, blockEnd, from, to)?.let { block ->
                    impact.addMarketBlock(
                        market,
                        block,
                        temporary = cbTransition.event != KrxCircuitBreakerEvent.LEVEL_3_TRIGGERED,
                    )
                }
            }

            val futuresLow = lowRate * KRX_FUTURES_BETA
            val futuresHigh = highRate * KRX_FUTURES_BETA
            val direction = if (abs(futuresLow) >= abs(futuresHigh)) {
                MarketMoveDirection.DOWN
            } else {
                MarketMoveDirection.UP
            }
            val futuresRate = if (direction == MarketMoveDirection.DOWN) futuresLow else futuresHigh
            val spotRate = if (direction == MarketMoveDirection.DOWN) lowRate else highRate
            val futuresThreshold = if (market == Market.KOSPI) {
                TradingProtectionRules.KOSPI_SIDECAR_FUTURES_RATE
            } else {
                TradingProtectionRules.KOSDAQ_SIDECAR_FUTURES_RATE
            }
            val futuresTarget = if (direction == MarketMoveDirection.DOWN) -futuresThreshold else futuresThreshold
            val futuresCrossing = if (direction == MarketMoveDirection.DOWN) {
                downwardThresholdCrossing(
                    sessionInterval.startsAt,
                    sessionInterval.endsAt,
                    previousRate * KRX_FUTURES_BETA,
                    futuresRate,
                    futuresTarget,
                )
            } else {
                upwardThresholdCrossing(
                    sessionInterval.startsAt,
                    sessionInterval.endsAt,
                    previousRate * KRX_FUTURES_BETA,
                    futuresRate,
                    futuresTarget,
                )
            }
            val spotCrossing = if (market == Market.KOSDAQ) {
                val target = if (direction == MarketMoveDirection.DOWN) {
                    -TradingProtectionRules.KOSDAQ_SIDECAR_SPOT_RATE
                } else {
                    TradingProtectionRules.KOSDAQ_SIDECAR_SPOT_RATE
                }
                if (direction == MarketMoveDirection.DOWN) {
                    downwardThresholdCrossing(
                        sessionInterval.startsAt,
                        sessionInterval.endsAt,
                        previousRate,
                        spotRate,
                        target,
                    )
                } else {
                    upwardThresholdCrossing(
                        sessionInterval.startsAt,
                        sessionInterval.endsAt,
                        previousRate,
                        spotRate,
                        target,
                    )
                }
            } else {
                futuresCrossing
            }
            val priorSidecar = sidecarStates[market]
                ?: TradingProtectionEngine.initialKrxSidecar(market, local.date)
            val freshSidecarSince = listOfNotNull(futuresCrossing, spotCrossing).maxOrNull()
            val sidecarSince = if (priorSidecar.pendingDirection == direction && freshSidecarSince != null) {
                minOf(priorSidecar.conditionSince ?: freshSidecarSince, freshSidecarSince)
            } else {
                freshSidecarSince
            }
            val sidecarObservationAt = sidecarSince?.let { since ->
                runtimePersistenceObservationAt(
                    since,
                    sessionInterval.endsAt,
                    TradingProtectionRules.KRX_SIDECAR_PERSISTENCE,
                )
            } ?: sessionInterval.endsAt
            val sidecarLocal = GameCalendar.marketLocalDateTime(market, sidecarObservationAt)
            val sidecarTransition = TradingProtectionEngine.evaluateKrxSidecar(
                priorSidecar,
                KrxSidecarObservation(
                    market = market,
                    tradingDate = local.date,
                    observedAt = sidecarObservationAt,
                    futuresChangeRate = futuresRate,
                    spotIndexChangeRate = if (market == Market.KOSDAQ) spotRate else null,
                    minutesAfterOpen = krxMinutesAfterOpen(sidecarLocal.time),
                    minutesUntilClose = krxMinutesUntilClose(sidecarLocal.time),
                    circuitBreakerPhase = cbState.phase,
                    conditionSatisfiedSince = sidecarSince,
                ),
            )
            sidecarStates[market] = sidecarTransition.state
            recordKrxSidecarTransition(
                market,
                sidecarTransition.event,
                sidecarTransition.state,
                sidecarObservationAt,
                priorSidecar,
            )
        }
        tradingProtectionSnapshot = tradingProtectionSnapshot.copy(
            krxCircuitBreakers = cbStates,
            krxSidecars = sidecarStates,
        )
    }

    private fun evaluateKrxVolatilityInterruptions(
        from: Instant,
        to: Instant,
        bars: Map<String, PriceBar>,
        ordinaryTradingFractions: Map<Market, Double>,
        impact: TurnProtectionImpactBuilder,
    ) {
        val states = tradingProtectionSnapshot.krxVolatilityInterruptions.toMutableMap()
        for (stock in stocks.filter { stock ->
            stock.market.isKorean && listingLifecycleStates.getValue(stock.id).isIndexEligible
        }) {
            if (ordinaryTradingFractions.getValue(stock.market) <= 0.0) continue
            val sessionInterval = runtimeTradableIntervals(stock.market, from, to).firstOrNull() ?: continue
            val bar = bars.getValue(stock.id)
            var state = states[stock.id] ?: TradingProtectionEngine.initialKrxVi(stock.id, stock.market)
            val cbPhase = tradingProtectionSnapshot.krxCircuitBreakers.getValue(stock.market).phase
            val productClass = krxViProductClass(stock)
            val isFirstRegularBar = !dailyTrackerSnapshot(
                stock,
                from,
                quotes.getValue(stock.id).price,
            ).hasRegularTrading
            val staticReference = quotes.getValue(stock.id).previousClose.coerceAtLeast(1.0)
            val dynamicReference = bar.open.coerceAtLeast(1.0)
            val staticMove = extremeMove(staticReference, bar)
            val dynamicMove = extremeMove(dynamicReference, bar)
            val dynamicRate = TradingProtectionEngine.krxViRate(
                KrxViKind.DYNAMIC,
                productClass,
                KrxViSession.CONTINUOUS_AUCTION,
            ) ?: Double.POSITIVE_INFINITY
            // The hourly opening bar's gap is the already-cleared opening auction price. Applying
            // continuous-session static VI retroactively would erase that auction gap; intrahour
            // dynamic VI still protects executions after the open.
            val staticScore = if (isFirstRegularBar) {
                Double.NEGATIVE_INFINITY
            } else {
                staticMove.absoluteRate / TradingProtectionRules.KRX_VI_STATIC_RATE
            }
            val dynamicScore = dynamicMove.absoluteRate / dynamicRate
            val kind = if (staticScore >= dynamicScore) KrxViKind.STATIC else KrxViKind.DYNAMIC
            val selected = if (kind == KrxViKind.STATIC) staticMove else dynamicMove
            val reference = if (kind == KrxViKind.STATIC) staticReference else dynamicReference
            val rate = if (kind == KrxViKind.STATIC) TradingProtectionRules.KRX_VI_STATIC_RATE else dynamicRate
            val triggerPrice = reference * (1.0 + if (selected.isUpper) rate else -rate)
            val crossing = if (selected.absoluteRate >= rate && bar.volume > 0L) {
                if (selected.isUpper) {
                    upwardThresholdCrossing(
                        sessionInterval.startsAt,
                        sessionInterval.endsAt,
                        bar.open,
                        selected.price,
                        triggerPrice,
                    )
                } else {
                    downwardThresholdCrossing(
                        sessionInterval.startsAt,
                        sessionInterval.endsAt,
                        bar.open,
                        selected.price,
                        triggerPrice,
                    )
                }
            } else {
                null
            }
            val observationAt = crossing ?: sessionInterval.endsAt
            val transition = TradingProtectionEngine.evaluateKrxVi(
                state,
                KrxViObservation(
                    stockId = stock.id,
                    market = stock.market,
                    observedAt = observationAt,
                    kind = kind,
                    productClass = productClass,
                    session = KrxViSession.CONTINUOUS_AUCTION,
                    referencePrice = reference,
                    potentialExecutionPrice = if (crossing == null) reference else triggerPrice,
                    circuitBreakerPhase = cbPhase,
                ),
            )
            state = transition.state
            recordKrxViTransition(stock, transition.event, state)
            if (transition.event == KrxViEvent.TRIGGERED) {
                val bounds = if (selected.isUpper) {
                    RuntimePriceBounds(upper = triggerPrice)
                } else {
                    RuntimePriceBounds(lower = triggerPrice)
                }
                impact.mergePriceBounds(stock.id, bounds)
                runtimeTradingBlock(
                    observationAt,
                    requireNotNull(state.auctionEndsAt),
                    from,
                    to,
                )?.let { impact.addInstrumentBlock(stock.id, it) }
            }
            if (state.phase == KrxViPhase.CALL_AUCTION && to >= requireNotNull(state.auctionEndsAt)) {
                val auctionEndsAt = requireNotNull(state.auctionEndsAt)
                val completed = TradingProtectionEngine.evaluateKrxVi(
                    state,
                    KrxViObservation(
                        stock.id,
                        stock.market,
                        auctionEndsAt,
                        kind,
                        productClass,
                        KrxViSession.CONTINUOUS_AUCTION,
                        reference,
                        reference,
                        circuitBreakerPhase = cbPhase,
                    ),
                )
                state = completed.state
                recordKrxViTransition(stock, completed.event, state)
            }
            states[stock.id] = state
        }
        tradingProtectionSnapshot = tradingProtectionSnapshot.copy(krxVolatilityInterruptions = states)
    }

    private fun evaluateUsMarketWideCircuitBreaker(
        from: Instant,
        to: Instant,
        bars: Map<String, PriceBar>,
        previousPrices: Map<String, Double>,
        provisionalIndices: Map<MarketIndexId, MarketIndexSnapshot>,
        ordinaryTradingFractions: Map<Market, Double>,
        impact: TurnProtectionImpactBuilder,
    ) {
        val snapshot = provisionalIndices[MarketIndexId.SP_500] ?: return
        if (ordinaryTradingFractions.none { (market, fraction) -> market.isUnitedStates && fraction > 0.0 }) return
        val sessionInterval = runtimeTradableIntervals(Market.NYSE, from, to).firstOrNull() ?: return
        var state = tradingProtectionSnapshot.usMarketWideCircuitBreaker
            ?: TradingProtectionEngine.initialUsMwcb(marketDate(Market.NYSE, to), to)
        if (state.phase != UsMwcbPhase.NORMAL) impact.usMwcbControlledTurn = true
        val previousValue = marketIndices[MarketIndexId.SP_500]?.value ?: snapshot.previousClose
        val intervalLow = sp500IntervalLow(bars, previousPrices, previousValue)
        val target = when {
            UsMwcbLevel.LEVEL_1 !in state.triggeredLevels -> 0.07
            UsMwcbLevel.LEVEL_2 !in state.triggeredLevels -> 0.13
            UsMwcbLevel.LEVEL_3 !in state.triggeredLevels -> 0.20
            else -> null
        }
        val thresholdValue = target?.let { snapshot.previousClose * (1.0 - it) }
        val crossing = thresholdValue?.let { threshold ->
            downwardThresholdCrossing(
                sessionInterval.startsAt,
                sessionInterval.endsAt,
                previousValue,
                intervalLow,
                threshold,
            )
        }
        val observedAt = crossing ?: sessionInterval.endsAt
        val local = GameCalendar.marketLocalDateTime(Market.NYSE, observedAt)
        val priorMwcb = state
        val transition = TradingProtectionEngine.evaluateUsMwcb(
            state,
            UsMwcbObservation(
                tradingDate = local.date,
                observedAt = observedAt,
                easternTime = local.time,
                sp500Value = if (crossing == null) snapshot.value else requireNotNull(thresholdValue),
                previousClose = snapshot.previousClose,
            ),
        )
        state = transition.state
        recordUsMwcbTransition(transition.event, state, observedAt, priorMwcb)
        if (transition.event in setOf(
                UsMwcbEvent.LEVEL_1_TRIGGERED,
                UsMwcbEvent.LEVEL_2_TRIGGERED,
                UsMwcbEvent.LEVEL_3_TRIGGERED,
            )
        ) {
            macro = macro.copy(
                usCircuitBreakerLevel = when (transition.event) {
                    UsMwcbEvent.LEVEL_1_TRIGGERED -> 1
                    UsMwcbEvent.LEVEL_2_TRIGGERED -> 2
                    UsMwcbEvent.LEVEL_3_TRIGGERED -> 3
                    else -> 0
                },
            )
            impact.usMwcbControlledTurn = true
            for (market in Market.entries.filter(Market::isUnitedStates)) {
                val blockEnd = if (transition.event == UsMwcbEvent.LEVEL_3_TRIGGERED) {
                    to
                } else {
                    requireNotNull(state.haltEndsAt) + usVenueReopeningDelayMinutes(market).minutes
                }
                runtimeTradingBlock(observedAt, blockEnd, from, to)?.let { block ->
                    impact.addMarketBlock(
                        market,
                        block,
                        temporary = transition.event != UsMwcbEvent.LEVEL_3_TRIGGERED,
                    )
                }
            }
        }
        if (state.phase == UsMwcbPhase.HALTED && to >= requireNotNull(state.haltEndsAt)) {
            state = TradingProtectionEngine.advanceUsMwcb(state, requireNotNull(state.haltEndsAt)).state
            val haltEnd = requireNotNull(state.haltEndsAt)
            for (market in state.venueStatuses.keys.sortedWith(usMwcbReopeningOrder())) {
                val completesAt = haltEnd + usVenueReopeningDelayMinutes(market).minutes
                if (to >= completesAt && state.phase == UsMwcbPhase.REOPENING_AUCTIONS) {
                    val beforeCompletion = state
                    val completion = TradingProtectionEngine.completeUsMwcbVenueReopening(state, market, completesAt)
                    state = completion.state
                    recordUsMwcbTransition(completion.event, state, completesAt, beforeCompletion)
                }
            }
        }
        tradingProtectionSnapshot = tradingProtectionSnapshot.copy(usMarketWideCircuitBreaker = state)
    }

    private fun evaluateUsLuldProtections(
        from: Instant,
        to: Instant,
        bars: Map<String, PriceBar>,
        ordinaryTradingFractions: Map<Market, Double>,
        impact: TurnProtectionImpactBuilder,
    ) {
        val states = tradingProtectionSnapshot.usLuldStates.toMutableMap()
        if (impact.usMwcbControlledTurn) {
            for (stock in stocks.filter { stock ->
                stock.market.isUnitedStates && listingLifecycleStates.getValue(stock.id).isIndexEligible
            }) {
                val bar = bars.getValue(stock.id)
                val local = GameCalendar.marketLocalDateTime(stock.market, to)
                val previous = states[stock.id]
                states[stock.id] = TradingProtectionEngine.initialUsLuld(
                    stockId = stock.id,
                    primaryMarket = stock.market,
                    tradingDate = local.date,
                    tier = usLuldTier(stock),
                    previousClose = previous?.previousClose ?: quotes.getValue(stock.id).previousClose,
                    referencePrice = bar.close,
                    referencePriceEffectiveAt = to,
                    easternTime = local.time,
                )
            }
            tradingProtectionSnapshot = tradingProtectionSnapshot.copy(usLuldStates = states)
            return
        }
        for (stock in stocks.filter { stock ->
            stock.market.isUnitedStates && listingLifecycleStates.getValue(stock.id).isIndexEligible
        }) {
            val bar = bars.getValue(stock.id)
            var state = states[stock.id] ?: continue
            if (ordinaryTradingFractions.getValue(stock.market) <= 0.0 ||
                state.tradingDate != marketDate(stock.market, to) || bar.volume == 0L
            ) {
                states[stock.id] = state
                continue
            }
            val sessionInterval = runtimeTradableIntervals(stock.market, from, to).firstOrNull() ?: continue
            val carriedBlock = when (state.phase) {
                UsLuldPhase.LIMIT_STATE -> {
                    val deadline = requireNotNull(state.limitStateDeadline)
                    runtimeTradingBlock(
                        deadline,
                        deadline + TradingProtectionRules.US_LULD_PAUSE + US_REOPENING_AUCTION_MINUTES.minutes,
                        from,
                        to,
                    )
                }
                UsLuldPhase.TRADING_PAUSE -> runtimeTradingBlock(
                    state.pauseStartedAt ?: from,
                    requireNotNull(state.pauseEndsAt) + US_REOPENING_AUCTION_MINUTES.minutes,
                    from,
                    to,
                )
                UsLuldPhase.REOPENING_AUCTION -> runtimeTradingBlock(
                    state.pauseStartedAt ?: state.reopeningStartedAt ?: from,
                    requireNotNull(state.reopeningStartedAt) + US_REOPENING_AUCTION_MINUTES.minutes,
                    from,
                    to,
                )
                UsLuldPhase.CLOSING_AUCTION_ONLY,
                UsLuldPhase.CLOSED_FOR_DAY,
                -> runtimeTradingBlock(state.pauseStartedAt ?: from, to, from, to)
                UsLuldPhase.NORMAL -> null
            }
            carriedBlock?.let { impact.addInstrumentBlock(stock.id, it) }
            impact.mergePriceBounds(
                stock.id,
                RuntimePriceBounds(state.bands.lower, state.bands.upper),
            )
            val upperCross = if (bar.high >= state.bands.upper) {
                upwardThresholdCrossing(
                    sessionInterval.startsAt,
                    sessionInterval.endsAt,
                    bar.open,
                    bar.high,
                    state.bands.upper,
                )
            } else null
            val lowerCross = if (bar.low <= state.bands.lower) {
                downwardThresholdCrossing(
                    sessionInterval.startsAt,
                    sessionInterval.endsAt,
                    bar.open,
                    bar.low,
                    state.bands.lower,
                )
            } else null
            val crossing = listOfNotNull(upperCross, lowerCross).minOrNull()
            if (state.phase == UsLuldPhase.NORMAL && crossing != null) {
                val side = if (upperCross != null && (lowerCross == null || upperCross <= lowerCross)) {
                    UsLuldLimitSide.UPPER
                } else {
                    UsLuldLimitSide.LOWER
                }
                var transition = TradingProtectionEngine.evaluateUsLuld(
                    state,
                    UsLuldObservation(
                        crossing,
                        GameCalendar.marketLocalDateTime(stock.market, crossing).time,
                        limitSide = side,
                    ),
                )
                state = transition.state
            }
            if (state.phase == UsLuldPhase.LIMIT_STATE && to >= requireNotNull(state.limitStateDeadline)) {
                val deadline = requireNotNull(state.limitStateDeadline)
                val beforeTransition = state
                val transition = TradingProtectionEngine.evaluateUsLuld(
                    state,
                    UsLuldObservation(deadline, GameCalendar.marketLocalDateTime(stock.market, deadline).time),
                )
                state = transition.state
                recordUsLuldTransition(stock, transition.event, state, deadline, beforeTransition)
                val blockEnd = when (state.phase) {
                    UsLuldPhase.TRADING_PAUSE ->
                        requireNotNull(state.pauseEndsAt) + US_REOPENING_AUCTION_MINUTES.minutes
                    UsLuldPhase.CLOSING_AUCTION_ONLY,
                    UsLuldPhase.CLOSED_FOR_DAY,
                    -> to
                    else -> deadline
                }
                runtimeTradingBlock(deadline, blockEnd, from, to)?.let {
                    impact.addInstrumentBlock(stock.id, it)
                }
            }
            if (state.phase == UsLuldPhase.TRADING_PAUSE && to >= requireNotNull(state.pauseEndsAt)) {
                val pauseEnd = requireNotNull(state.pauseEndsAt)
                val beforeTransition = state
                val transition = TradingProtectionEngine.evaluateUsLuld(
                    state,
                    UsLuldObservation(pauseEnd, GameCalendar.marketLocalDateTime(stock.market, pauseEnd).time),
                )
                state = transition.state
                recordUsLuldTransition(stock, transition.event, state, pauseEnd, beforeTransition)
            }
            if (state.phase == UsLuldPhase.REOPENING_AUCTION) {
                val reopensAt = requireNotNull(state.reopeningStartedAt) + US_REOPENING_AUCTION_MINUTES.minutes
                if (to >= reopensAt) {
                    val beforeReopening = state
                    val reopeningPrice = bar.close.coerceIn(
                        state.bands.lower,
                        state.bands.upper,
                    )
                    val transition = TradingProtectionEngine.completeUsLuldReopening(
                        state,
                        reopeningPrice,
                        reopensAt,
                        GameCalendar.marketLocalDateTime(stock.market, reopensAt).time,
                    )
                    state = transition.state
                    recordUsLuldTransition(stock, transition.event, state, reopensAt, beforeReopening)
                }
            }
            if (state.phase == UsLuldPhase.NORMAL) {
                val typical = ((bar.open + bar.high + bar.low + bar.close) / 4.0)
                    .coerceAtLeast(MarketMicrostructure.minimumPrice(stock.market))
                state = TradingProtectionEngine.updateUsLuldReferencePrice(
                    state,
                    typical,
                    sessionInterval.startsAt,
                    sessionInterval.endsAt,
                    GameCalendar.marketLocalDateTime(stock.market, sessionInterval.endsAt).time,
                ).state
            }
            states[stock.id] = state
        }
        tradingProtectionSnapshot = tradingProtectionSnapshot.copy(usLuldStates = states)
    }

    private fun krxMarketProxyRate(market: Market, prices: Map<String, Double>): Double {
        val constituents = stocks.filter {
            it.market == market && it.hasCorporateEarnings &&
                listingLifecycleStates.getValue(it.id).isIndexEligible && prices[it.id] != null
        }
        val weight = constituents.sumOf { it.marketCap }
        if (weight <= 0.0) return 0.0
        return constituents.sumOf { stock ->
            val base = quotes.getValue(stock.id).previousClose
            val rate = if (base <= 0.0) 0.0 else prices.getValue(stock.id) / base - 1.0
            rate * stock.marketCap
        } / weight
    }

    /**
     * 종목별 고가·저가는 서로 다른 시각에 형성되므로 그대로 합산하면 존재하지 않았던
     * 시장 전체 극값이 만들어진다. 시간봉 안에서 동시에 관측 가능한 시가·종가 포트폴리오
     * 두 점으로 지수 경로를 근사해 개별 종목 변동성을 시장 급락으로 오인하지 않는다.
     */
    private fun krxMarketProxyIntervalRates(
        market: Market,
        bars: Map<String, PriceBar>,
    ): Pair<Double, Double> {
        val opens = bars.mapValues { (_, bar) -> bar.open }
        val closes = bars.mapValues { (_, bar) -> bar.close }
        val openRate = krxMarketProxyRate(market, opens)
        val closeRate = krxMarketProxyRate(market, closes)
        return minOf(openRate, closeRate) to maxOf(openRate, closeRate)
    }

    /** Coherent current-hour endpoint low; a daily [MarketIndexSnapshot.low] is never reused next hour. */
    private fun sp500IntervalLow(
        bars: Map<String, PriceBar>,
        previousPrices: Map<String, Double>,
        previousIndexValue: Double,
    ): Double {
        val constituents = stocks.filter {
            it.market.isUnitedStates && it.hasCorporateEarnings &&
                listingLifecycleStates.getValue(it.id).isIndexEligible &&
                bars[it.id] != null && previousPrices[it.id]?.let { price -> price > 0.0 } == true
        }
        val weight = constituents.sumOf(StockDefinition::marketCap)
        if (weight <= 0.0) return previousIndexValue
        val openFactor = constituents.sumOf { stock ->
            stock.marketCap * bars.getValue(stock.id).open / previousPrices.getValue(stock.id)
        } / weight
        val closeFactor = constituents.sumOf { stock ->
            stock.marketCap * bars.getValue(stock.id).close / previousPrices.getValue(stock.id)
        } / weight
        return (previousIndexValue * minOf(openFactor, closeFactor)).coerceAtLeast(0.01)
    }

    private fun nextKrxCircuitBreakerTargetRate(
        market: Market,
        state: com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerState,
    ): Double? {
        val base = KRX_INDEX_BASE.getValue(market)
        return when {
            KrxCircuitBreakerLevel.LEVEL_1 !in state.triggeredLevels ->
                -TradingProtectionRules.KRX_CB_LEVEL_1_DECLINE
            KrxCircuitBreakerLevel.LEVEL_2 !in state.triggeredLevels -> minOf(
                -TradingProtectionRules.KRX_CB_LEVEL_2_DECLINE,
                requireNotNull(state.triggerIndexValues[KrxCircuitBreakerLevel.LEVEL_1]) / base *
                    (1.0 - TradingProtectionRules.KRX_CB_ADDITIONAL_DECLINE) - 1.0,
            )
            KrxCircuitBreakerLevel.LEVEL_3 !in state.triggeredLevels -> minOf(
                -TradingProtectionRules.KRX_CB_LEVEL_3_DECLINE,
                requireNotNull(state.triggerIndexValues[KrxCircuitBreakerLevel.LEVEL_2]) / base *
                    (1.0 - TradingProtectionRules.KRX_CB_ADDITIONAL_DECLINE) - 1.0,
            )
            else -> null
        }
    }

    private fun downwardThresholdCrossing(
        from: Instant,
        to: Instant,
        start: Double,
        end: Double,
        threshold: Double,
    ): Instant? = when {
        start <= threshold -> from
        end > threshold -> null
        else -> interpolatePriceCrossing(from, to, start, end, threshold)
    }

    private fun upwardThresholdCrossing(
        from: Instant,
        to: Instant,
        start: Double,
        end: Double,
        threshold: Double,
    ): Instant? = when {
        start >= threshold -> from
        end < threshold -> null
        else -> interpolatePriceCrossing(from, to, start, end, threshold)
    }

    private fun interpolatePriceCrossing(
        from: Instant,
        to: Instant,
        start: Double,
        end: Double,
        threshold: Double,
    ): Instant? {
        if (start == end) return if (start == threshold) from else null
        val ratio = (threshold - start) / (end - start)
        if (ratio !in 0.0..1.0) return null
        return from + ((to - from).inWholeNanoseconds * ratio).toLong().nanoseconds
    }

    private fun krxMinutesAfterOpen(time: LocalTime): Double =
        ((time.hour * 3_600 + time.minute * 60 + time.second + time.nanosecond / 1_000_000_000.0) - 9 * 3_600) /
            60.0

    private fun krxMinutesUntilClose(time: LocalTime): Double =
        ((15 * 3_600 + 30 * 60) -
            (time.hour * 3_600 + time.minute * 60 + time.second + time.nanosecond / 1_000_000_000.0)) /
            60.0

    private fun extremeMove(reference: Double, bar: PriceBar): ExtremeMove {
        val upper = bar.high / reference - 1.0
        val lower = bar.low / reference - 1.0
        return if (abs(upper) >= abs(lower)) {
            ExtremeMove(bar.high, abs(upper), true)
        } else {
            ExtremeMove(bar.low, abs(lower), false)
        }
    }

    private fun krxViProductClass(stock: StockDefinition): KrxViProductClass = when {
        stock.isFundLike && (
            stock.behavior.strategy == com.amond.kmpbook.domain.model.instrument.InstrumentStrategy.DAILY_INVERSE ||
                stock.behavior.strategy == com.amond.kmpbook.domain.model.instrument.InstrumentStrategy.TREASURY ||
                stock.behavior.strategy == com.amond.kmpbook.domain.model.instrument.InstrumentStrategy.MONEY_MARKET
            ) -> KrxViProductClass.CORE_INDEX_INVERSE_OR_BOND_ETP
        stock.isFundLike -> KrxViProductClass.OTHER_ETP
        stock.market == Market.KOSPI && stock.marketCap >= KOSPI_200_GAME_MARKET_CAP_FLOOR ->
            KrxViProductClass.KOSPI200_CONSTITUENT
        else -> KrxViProductClass.OTHER_EQUITY
    }

    private fun recordKrxCircuitBreakerTransition(
        market: Market,
        event: KrxCircuitBreakerEvent,
        state: com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerState,
        previous: com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerState,
    ) {
        if (event !in setOf(
                KrxCircuitBreakerEvent.LEVEL_1_TRIGGERED,
                KrxCircuitBreakerEvent.LEVEL_2_TRIGGERED,
                KrxCircuitBreakerEvent.LEVEL_3_TRIGGERED,
                KrxCircuitBreakerEvent.REOPENING_COMPLETED,
            )
        ) {
            return
        }
        val occurrenceState = if (event == KrxCircuitBreakerEvent.REOPENING_COMPLETED) previous else state
        val occurrenceLevel = requireNotNull(occurrenceState.activeLevel)
        val triggeredAt = requireNotNull(occurrenceState.triggeredAt)
        val level = occurrenceLevel.ordinal + 1
        val transitionAt = if (event == KrxCircuitBreakerEvent.REOPENING_COMPLETED) {
            requireNotNull(previous.reopeningEndsAt)
        } else {
            triggeredAt
        }
        val actionEndsAt = when (event) {
            KrxCircuitBreakerEvent.LEVEL_3_TRIGGERED -> regularSessionCloseAt(market, state.tradingDate)
            KrxCircuitBreakerEvent.REOPENING_COMPLETED -> transitionAt
            else -> requireNotNull(state.reopeningEndsAt)
        }
        val occurrenceId = krxCircuitBreakerOccurrenceId(market, occurrenceLevel, triggeredAt)
        val copy = when (event) {
            KrxCircuitBreakerEvent.LEVEL_1_TRIGGERED,
            KrxCircuitBreakerEvent.LEVEL_2_TRIGGERED,
            -> "국내 시장 거래가 20분간 멈췄어요" to
                "${market.displayName} 서킷브레이커 ${level}단계가 발동했습니다. 20분 정지 후 10분 단일가로 재개합니다."
            KrxCircuitBreakerEvent.LEVEL_3_TRIGGERED -> "오늘 국내 시장 거래가 끝났어요" to
                "${market.displayName} 서킷브레이커 3단계가 발동해 다음 거래일까지 거래를 종료합니다."
            KrxCircuitBreakerEvent.REOPENING_COMPLETED -> "${market.displayName} 거래가 다시 시작됐어요" to
                "서킷브레이커 단일가 절차가 끝나 연속매매가 재개됐습니다."
            else -> error("앞에서 처리할 KRX 서킷브레이커 전이만 통과합니다.")
        }
        addProtectionNews(
            id = "krx-cb:${market.name}:${event.name}:${transitionAt.epochSeconds}",
            title = copy.first,
            description = copy.second,
            at = transitionAt,
            markets = setOf(market),
            severity = when (event) {
                KrxCircuitBreakerEvent.LEVEL_3_TRIGGERED -> EventSeverity.CRITICAL
                KrxCircuitBreakerEvent.REOPENING_COMPLETED -> EventSeverity.MINOR
                else -> EventSeverity.MAJOR
            },
            marketAction = MarketActionReference(
                kind = MarketActionKind.KRX_CIRCUIT_BREAKER,
                occurrenceId = occurrenceId,
                transition = when (event) {
                    KrxCircuitBreakerEvent.LEVEL_1_TRIGGERED,
                    KrxCircuitBreakerEvent.LEVEL_2_TRIGGERED,
                    -> MarketActionTransition.HALT_STARTED
                    KrxCircuitBreakerEvent.LEVEL_3_TRIGGERED -> MarketActionTransition.MARKET_CLOSED_FOR_DAY
                    KrxCircuitBreakerEvent.REOPENING_COMPLETED -> MarketActionTransition.REOPENED
                },
                announcedAt = transitionAt,
                effectiveAt = if (event == KrxCircuitBreakerEvent.REOPENING_COMPLETED) transitionAt else triggeredAt,
                endsAt = actionEndsAt,
                markets = setOf(market),
                stage = if (event == KrxCircuitBreakerEvent.REOPENING_COMPLETED) null else level,
            ),
        )
    }

    private fun recordKrxSidecarTransition(
        market: Market,
        event: KrxSidecarEvent,
        state: com.amond.kmpbook.domain.model.protection.krx.KrxSidecarState,
        observedAt: Instant,
        previous: com.amond.kmpbook.domain.model.protection.krx.KrxSidecarState,
    ) {
        val copy = when (event) {
            KrxSidecarEvent.ACTIVATED -> "프로그램 매매가 잠시 멈췄어요" to
                "${market.displayName} 사이드카가 발동했습니다. ${state.suspendedProgramSide?.name ?: "해당"} 방향 프로그램 호가만 5분간 제한되며 일반 주식 주문은 계속됩니다."
            KrxSidecarEvent.RELEASED -> "${market.displayName} 사이드카가 해제됐어요" to
                "프로그램매매 호가 제한이 끝났습니다."
            else -> return
        }
        val occurrenceState = if (event == KrxSidecarEvent.RELEASED) previous else state
        val triggeredAt = requireNotNull(occurrenceState.triggeredAt)
        val transitionAt = when {
            event != KrxSidecarEvent.RELEASED -> observedAt
            state.releaseReason == com.amond.kmpbook.domain.model.protection.krx.KrxSidecarReleaseReason.FIVE_MINUTES_ELAPSED ->
                requireNotNull(previous.suspensionEndsAt)
            else -> state.releasedAt ?: observedAt
        }
        addProtectionNews(
            id = "krx-sidecar:${market.name}:${event.name}:${transitionAt.epochSeconds}",
            title = copy.first,
            description = copy.second,
            at = transitionAt,
            markets = setOf(market),
            severity = EventSeverity.MODERATE,
            marketAction = MarketActionReference(
                kind = MarketActionKind.KRX_SIDECAR,
                occurrenceId = krxSidecarOccurrenceId(market, triggeredAt),
                transition = if (event == KrxSidecarEvent.ACTIVATED) {
                    MarketActionTransition.PROGRAM_FLOW_SUSPENDED
                } else {
                    MarketActionTransition.RELEASED
                },
                announcedAt = transitionAt,
                effectiveAt = if (event == KrxSidecarEvent.ACTIVATED) triggeredAt else transitionAt,
                endsAt = if (event == KrxSidecarEvent.ACTIVATED) state.suspensionEndsAt else transitionAt,
                markets = setOf(market),
            ),
        )
    }

    private fun recordKrxViTransition(
        stock: StockDefinition,
        event: KrxViEvent,
        state: com.amond.kmpbook.domain.model.protection.krx.KrxViState,
    ) {
        if (event != KrxViEvent.TRIGGERED) return
        val triggeredAt = requireNotNull(state.triggeredAt)
        val kind = state.kind?.let { if (it == KrxViKind.STATIC) "정적 VI" else "동적 VI" } ?: "VI"
        addProtectionNews(
            id = "krx-vi:${stock.id}:${state.triggerCount}:${triggeredAt.epochSeconds}",
            title = "${stock.name} 단일가 매매 중이에요",
            description = "$kind 발동으로 가격 급변을 완화하기 위해 2분간 주문을 모읍니다. 발동 호가는 즉시 체결되지 않습니다.",
            at = triggeredAt,
            stock = stock,
            severity = EventSeverity.MODERATE,
            marketAction = MarketActionReference(
                kind = MarketActionKind.KRX_VOLATILITY_INTERRUPTION,
                occurrenceId = krxViOccurrenceId(stock.id, state.triggerCount, triggeredAt),
                transition = MarketActionTransition.CALL_AUCTION_STARTED,
                announcedAt = triggeredAt,
                effectiveAt = triggeredAt,
                endsAt = state.auctionEndsAt,
                stockId = stock.id,
                markets = setOf(stock.market),
                triggerSequence = state.triggerCount,
            ),
        )
    }

    private fun recordUsMwcbTransition(
        event: UsMwcbEvent,
        state: com.amond.kmpbook.domain.model.protection.us.UsMwcbState,
        observedAt: Instant,
        previous: com.amond.kmpbook.domain.model.protection.us.UsMwcbState,
    ) {
        if (event !in setOf(
                UsMwcbEvent.LEVEL_1_TRIGGERED,
                UsMwcbEvent.LEVEL_2_TRIGGERED,
                UsMwcbEvent.LEVEL_3_TRIGGERED,
                UsMwcbEvent.ALL_VENUES_REOPENED,
            )
        ) {
            return
        }
        val occurrenceState = if (event == UsMwcbEvent.ALL_VENUES_REOPENED) previous else state
        val occurrenceLevel = requireNotNull(occurrenceState.activeLevel)
        val triggeredAt = requireNotNull(occurrenceState.triggeredAt)
        val level = occurrenceLevel.ordinal + 1
        val transitionAt = if (event == UsMwcbEvent.ALL_VENUES_REOPENED) observedAt else triggeredAt
        val actionEndsAt = when (event) {
            UsMwcbEvent.LEVEL_3_TRIGGERED -> regularSessionCloseAt(Market.NYSE, state.tradingDate)
            UsMwcbEvent.ALL_VENUES_REOPENED -> observedAt
            else -> requireNotNull(state.haltEndsAt) +
                state.venueStatuses.keys.maxOf(::usVenueReopeningDelayMinutes).minutes
        }
        val occurrenceId = usMwcbOccurrenceId(occurrenceLevel, triggeredAt)
        val copy = when (event) {
            UsMwcbEvent.LEVEL_1_TRIGGERED,
            UsMwcbEvent.LEVEL_2_TRIGGERED,
            -> "미국 시장 거래가 15분간 멈췄어요" to
                "S&P 500 기준 미국 시장 서킷브레이커 ${level}단계가 발동했습니다. 각 거래소 재개 경매 후 거래가 이어집니다."
            UsMwcbEvent.LEVEL_3_TRIGGERED -> "오늘 미국 시장 거래가 끝났어요" to
                "S&P 500이 20% 하락 기준에 닿아 모든 미국 상장시장의 정규 거래가 종료됐습니다."
            UsMwcbEvent.ALL_VENUES_REOPENED -> "미국 시장 거래가 다시 시작됐어요" to
                "거래소별 재개 경매가 모두 끝났습니다."
            else -> error("앞에서 처리할 미국 시장 전체 서킷브레이커 전이만 통과합니다.")
        }
        addProtectionNews(
            id = "us-mwcb:${event.name}:${transitionAt.epochSeconds}",
            title = copy.first,
            description = copy.second,
            at = transitionAt,
            markets = Market.entries.filterTo(linkedSetOf(), Market::isUnitedStates),
            severity = when (event) {
                UsMwcbEvent.LEVEL_3_TRIGGERED -> EventSeverity.CRITICAL
                UsMwcbEvent.ALL_VENUES_REOPENED -> EventSeverity.MINOR
                else -> EventSeverity.MAJOR
            },
            marketAction = MarketActionReference(
                kind = MarketActionKind.US_MARKET_WIDE_CIRCUIT_BREAKER,
                occurrenceId = occurrenceId,
                transition = when (event) {
                    UsMwcbEvent.LEVEL_1_TRIGGERED,
                    UsMwcbEvent.LEVEL_2_TRIGGERED,
                    -> MarketActionTransition.HALT_STARTED
                    UsMwcbEvent.LEVEL_3_TRIGGERED -> MarketActionTransition.MARKET_CLOSED_FOR_DAY
                    UsMwcbEvent.ALL_VENUES_REOPENED -> MarketActionTransition.REOPENED
                },
                announcedAt = transitionAt,
                effectiveAt = transitionAt,
                endsAt = actionEndsAt,
                markets = Market.entries.filterTo(linkedSetOf(), Market::isUnitedStates),
                stage = if (event == UsMwcbEvent.ALL_VENUES_REOPENED) null else level,
            ),
        )
    }

    private fun usMwcbReopeningOrder(): Comparator<Market> =
        compareBy<Market>(::usVenueReopeningDelayMinutes).thenBy(Market::name)

    private fun recordUsLuldTransition(
        stock: StockDefinition,
        event: UsLuldEvent,
        state: com.amond.kmpbook.domain.model.protection.us.UsLuldState,
        observedAt: Instant,
        previous: com.amond.kmpbook.domain.model.protection.us.UsLuldState,
    ) {
        val copy = when (event) {
            UsLuldEvent.TRADING_PAUSE_STARTED -> "${stock.name} 거래가 잠시 멈췄어요" to
                "급격한 가격 변동으로 LULD 5분 거래정지가 적용됐습니다. 재개 경매 뒤 체결이 이어집니다."
            UsLuldEvent.CLOSING_AUCTION_ONLY -> "${stock.name} 종가 경매만 진행해요" to
                "장 마감 직전 LULD가 발동해 연속매매 없이 종가 경매로 전환됐습니다."
            UsLuldEvent.REOPENED -> "${stock.name} 거래가 다시 시작됐어요" to
                "변동성 정지와 재개 경매가 끝났습니다."
            else -> return
        }
        val occurrenceState = if (event == UsLuldEvent.REOPENED) previous else state
        val pauseStartedAt = requireNotNull(occurrenceState.pauseStartedAt)
        val sessionCloseAt = regularSessionCloseAt(stock.market, occurrenceState.tradingDate)
        val actionEndsAt = when (event) {
            UsLuldEvent.REOPENED -> observedAt
            UsLuldEvent.CLOSING_AUCTION_ONLY -> sessionCloseAt
            UsLuldEvent.TRADING_PAUSE_STARTED -> {
                val pauseEndsAt = requireNotNull(state.pauseEndsAt)
                if (GameCalendar.marketLocalDateTime(stock.market, pauseEndsAt).time >=
                    TradingProtectionRules.US_LULD_CLOSE_ONLY_FROM
                ) {
                    sessionCloseAt
                } else {
                    pauseEndsAt + US_REOPENING_AUCTION_MINUTES.minutes
                }
            }
        }
        addProtectionNews(
            id = "us-luld:${stock.id}:${event.name}:${observedAt.epochSeconds}",
            title = copy.first,
            description = copy.second,
            at = observedAt,
            stock = stock,
            severity = if (state.phase == UsLuldPhase.CLOSING_AUCTION_ONLY) EventSeverity.MAJOR else EventSeverity.MODERATE,
            marketAction = MarketActionReference(
                kind = MarketActionKind.US_LIMIT_UP_LIMIT_DOWN,
                occurrenceId = usLuldOccurrenceId(stock.id, pauseStartedAt),
                transition = when (event) {
                    UsLuldEvent.TRADING_PAUSE_STARTED -> MarketActionTransition.HALT_STARTED
                    UsLuldEvent.CLOSING_AUCTION_ONLY -> MarketActionTransition.CLOSING_AUCTION_STARTED
                    UsLuldEvent.REOPENED -> MarketActionTransition.REOPENED
                },
                announcedAt = observedAt,
                effectiveAt = observedAt,
                endsAt = actionEndsAt,
                stockId = stock.id,
                markets = setOf(stock.market),
            ),
        )
    }

    private fun regularSessionAt(
        market: Market,
        tradingDate: LocalDate,
    ): com.amond.kmpbook.domain.time.MarketSessionWindow = requireNotNull(
        GameCalendar.regularSessionWindow(market, tradingDate, runtimeClosedDates(market, tradingDate)),
    )

    private fun regularSessionCloseAt(market: Market, tradingDate: LocalDate): Instant =
        regularSessionAt(market, tradingDate).closesAt

    private fun addProtectionNews(
        id: String,
        title: String,
        description: String,
        at: Instant,
        stock: StockDefinition? = null,
        markets: Set<Market> = stock?.let { setOf(it.market) }.orEmpty(),
        severity: EventSeverity,
        marketAction: MarketActionReference,
    ) {
        val eventId = "market-protection:$id"
        if (newsEvents.any { it.id == eventId }) return
        newsEvents += GameEvent(
            id = eventId,
            title = title,
            description = description,
            scope = if (stock == null) EventScope.MARKET else EventScope.STOCK,
            type = EventType.REGULATION_POLICY,
            severity = severity,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = at,
            durationHours = if (stock == null) 24 else 12,
            recordKind = EventRecordKind.MARKET_ACTION,
            affectedMarkets = markets,
            affectedSectors = stock?.let { setOf(it.sector) }.orEmpty(),
            affectedStockIds = stock?.let { setOf(it.id) }.orEmpty(),
            sourceLabel = "거래소 시장조치 규칙",
            marketAction = marketAction,
        )
    }

    private fun updateBenchmark(
        bars: Map<String, PriceBar>,
        fractions: Map<Market, Double>,
    ) {
        val returns = stocks.asSequence()
            .filter {
                listingLifecycleStates.getValue(it.id).isIndexEligible &&
                    it.hasCorporateEarnings && fractions.getValue(it.market) > 0.0
            }
            .map { bars.getValue(it.id) }
            .map { if (it.open == 0.0) 0.0 else it.close / it.open - 1.0 }
            .toList()
        if (returns.isNotEmpty()) benchmarkValue *= 1.0 + returns.average()
    }

    private fun updateDrawdown() {
        val assets = totalAssetsKrw()
        peakAssetsKrw = maxOf(peakAssetsKrw, assets)
        val drawdown = if (peakAssetsKrw == 0.0) 0.0 else (peakAssetsKrw - assets) / peakAssetsKrw
        maximumDrawdown = maxOf(maximumDrawdown, drawdown)
    }

    private fun recordDailySnapshot(date: LocalDate, timestamp: Instant) {
        val snapshot = portfolioSnapshot(timestamp)
        // [timestamp] is the boundary at which the completed logical [date] is recorded. At a
        // midnight boundary its calendar date is therefore the following day, so timestamp-based
        // replacement would keep deleting the previous day's point. The explicitly stored daily
        // date is the canonical key for all three aligned histories.
        val replacesExistingDate = dailyStatistics.lastOrNull()?.date == date
        if (replacesExistingDate) {
            require(portfolioSnapshots.isNotEmpty() && benchmarkHistory.isNotEmpty())
            portfolioSnapshots.removeLast()
            benchmarkHistory.removeLast()
        }
        portfolioSnapshots += snapshot

        val previous = dailyStatistics.lastOrNull { it.date != date }
        val dailyReturn = if (previous == null || previous.totalAssetsKrw == 0.0) {
            snapshot.totalAssetValueKrw / options.initialCapitalKrw - 1.0
        } else {
            snapshot.totalAssetValueKrw / previous.totalAssetsKrw - 1.0
        }
        if (replacesExistingDate) dailyStatistics.removeLast()
        dailyStatistics += DailyPortfolioStat(
            date = date,
            totalAssetsKrw = snapshot.totalAssetValueKrw,
            cashValueKrw = snapshot.cashValueKrw,
            stockValueKrw = snapshot.stockValueKrw,
            dailyReturn = dailyReturn,
            drawdown = if (peakAssetsKrw == 0.0) 0.0 else (peakAssetsKrw - snapshot.totalAssetValueKrw) / peakAssetsKrw,
            benchmarkValue = benchmarkValue,
            usdKrw = macro.usdKrw,
        )
        benchmarkHistory += BenchmarkPoint(
            timestamp = timestamp,
            value = benchmarkValue,
            cumulativeReturn = benchmarkValue / BENCHMARK_START - 1.0,
        )
    }

    private fun portfolioSnapshot(timestamp: Instant): PortfolioSnapshot = PortfolioSnapshot(
        timestamp = timestamp,
        cashByCurrency = cash.toMap(),
        holdings = holdings.values.toList(),
        exchangeRatesToKrw = mapOf(Currency.USD to macro.usdKrw),
        initialCapitalKrw = options.initialCapitalKrw,
        realizedProfitKrw = realizedGains.sumOf(RealizedGainRecord::gainKrw),
        cumulativeCommissionKrw = transactionCosts.sumOf(TransactionCostRecord::commissionKrw),
        cumulativeTaxKrw = transactionCosts.sumOf(TransactionCostRecord::saleTaxKrw) +
            dividends.sumOf(DividendLedgerEntry::withholdingTaxKrw) +
            taxPaymentNotices.filter {
                it.status == com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus.PAID
            }.sumOf { it.amountKrw.toDouble() },
        holdingCostBasisKrw = fifoCostBasisBook.lots.groupBy { it.stockId }
            .mapValues { (_, lots) -> lots.sumOf { it.remainingCostBasisKrw }.toDouble() },
    )

    private fun totalAssetsKrw(): Double {
        val cashValue = cash.getValue(Currency.KRW) + cash.getValue(Currency.USD) * macro.usdKrw
        val stockValue = holdings.values.sumOf { holding ->
            holding.marketValue * if (holding.currency == Currency.USD) macro.usdKrw else 1.0
        }
        return cashValue + stockValue
    }

    private fun assessStockGainTreatment(
        stock: StockDefinition,
        assessedOn: LocalDate,
        preSaleHolding: Holding,
    ): Pair<StockGainTaxTreatment, List<String>> {
        if (stock.market.isUnitedStates) {
            return StockGainTaxTreatment.FOREIGN_STANDARD to listOf(
                "${stock.instrumentType.displayName} 구조로 분류하고 대한민국 거주자의 국외주식 양도소득 규칙을 적용했습니다.",
            )
        }
        stock.etfProfile?.let { profile ->
            return when (profile.taxCategory) {
                EtfTaxCategory.KOREAN_DOMESTIC_EQUITY -> StockGainTaxTreatment.DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE to
                    listOf("국내주식형 ETF 장내 매매차익 비과세와 ETF 증권거래세 면제를 적용했습니다.")
                EtfTaxCategory.KOREAN_OTHER -> StockGainTaxTreatment.DOMESTIC_ETF_HOLDING_PERIOD_WITHHELD to
                    listOf("매매차익과 게임 과표기준가격 증가분 중 작은 금액에 15.4%를 원천징수했습니다.")
                EtfTaxCategory.FOREIGN_LISTED -> error("한국 시장 ETF에 국외상장 세무 분류가 지정되었습니다.")
            }
        }

        val priorYear = assessedOn.year - 1
        val priorSnapshot = portfolioSnapshots
            .asReversed()
            .firstOrNull { gameDate(it.timestamp).year == priorYear }
        val priorHolding = priorSnapshot?.holdings?.firstOrNull { it.stockId == stock.id }
        val priorQuantity = priorHolding?.quantity ?: 0.0
        val priorMarketValue = (priorHolding?.marketValue ?: 0.0).coerceAtLeast(0.0).toLong()
        val currentSharesOutstanding = stockById.getValue(stock.id).sharesOutstanding
        val priorSharesOutstanding = priorSnapshot?.let { sharesOutstandingAt(stock.id, it.timestamp) }
            ?: baseStockById.getValue(stock.id).sharesOutstanding
        val currentOwnershipRatio = (preSaleHolding.quantity / currentSharesOutstanding.toDouble())
            .coerceIn(0.0, 1.0)
        val assessment = majorShareholderCalculator.assess(
            MajorShareholderAssessmentRequest(
                market = stock.market,
                assessedOn = assessedOn,
                priorBusinessYearEndHoldings = listOf(
                    ShareholderHoldingSnapshot(
                        ownerId = "game-player",
                        relation = ShareholderRelation.SELF,
                        ownershipRatio = (priorQuantity / priorSharesOutstanding.toDouble()).coerceIn(0.0, 1.0),
                        marketValueKrw = priorMarketValue,
                    ),
                ),
                isLargestShareholderGroup = false,
                ownershipRatioAfterCurrentYearAcquisition = currentOwnershipRatio,
            ),
        )
        val treatment = if (assessment.isMajorShareholder) {
            StockGainTaxTreatment.DOMESTIC_MAJOR_GENERAL
        } else {
            StockGainTaxTreatment.DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE
        }
        val notes = assessment.notes + listOf(
            "외부 증권계좌와 친족·경영지배관계인 보유분이 없는 게임 계좌 기준 추정입니다.",
            "직전 연말 스냅샷과 당해연도 취득 후 게임 계좌 지분율만 반영했습니다.",
        )
        return treatment to notes
    }

    private fun fifoBookMatchesHoldings(): Boolean {
        val lotQuantities = fifoCostBasisBook.lots.groupBy { it.stockId }
            .mapValues { (_, lots) -> lots.sumOf { it.remainingQuantity } }
        val allIds = lotQuantities.keys + holdings.keys
        return allIds.all { stockId ->
            abs((lotQuantities[stockId] ?: 0.0) - (holdings[stockId]?.quantity ?: 0.0)) < QUANTITY_EPSILON
        }
    }

    private fun restoreTaxExchangeRateLedger(state: SimulatorUiState) {
        val tradeIds = trades.map(Trade::id)
        require(tradeIds.distinct().size == tradeIds.size) { "체결 ID가 중복되었습니다." }
        require(trades.zipWithNext().all { (left, right) -> left.executedAt <= right.executedAt }) {
            "체결 원장의 시간 순서가 올바르지 않습니다."
        }
        val costsByTrade = transactionCosts.associateBy(TransactionCostRecord::tradeId)
        require(costsByTrade.keys.containsAll(tradeIds)) { "모든 체결의 비용 원장이 필요합니다." }

        taxExchangeRatesByTradeId.clear()
        pendingTaxSettlementTradeIds.clear()
        val savedRates = state.taxExchangeRatesByTradeId
        require(savedRates.keys == tradeIds.toSet()) { "모든 체결의 세무 환율이 필요합니다." }
        require(savedRates.values.all { it.isFinite() && it > 0.0 }) {
            "세무 환율은 유한한 양수여야 합니다."
        }
        taxExchangeRatesByTradeId.putAll(savedRates)
        pendingTaxSettlementTradeIds += state.pendingTaxSettlementTradeIds

        val tradesById = trades.associateBy(Trade::id)
        require(pendingTaxSettlementTradeIds.all { tradeId ->
            val trade = tradesById[tradeId] ?: return@all false
            stockById.getValue(trade.stockId).market.isUnitedStates &&
                tradeId in taxExchangeRatesByTradeId
        }) { "미결제 세무 환율 원장에는 해외 체결만 들어갈 수 있습니다." }
        require(trades.filter { it.currency == Currency.KRW }.all { trade ->
            abs(taxExchangeRatesByTradeId.getValue(trade.id) - 1.0) < TAX_RATE_EPSILON
        }) { "국내 체결의 세무 환율은 1.0이어야 합니다." }
    }

    /**
     * 체결 원장을 원래 순서 그대로 다시 재생한다. 해외 미결제 체결도 임시 환율로 lot 수량을
     * 유지하고, 결제일에 환율이 확정되면 같은 재생으로 취득가액과 양도가액을 함께 고친다.
     */
    private fun replayTaxAccountingLedger(): Set<Int> {
        val gainTemplates = realizedGains.associateBy(RealizedGainRecord::tradeId)
        val saleIds = trades.filter { it.side == OrderSide.SELL }.map(Trade::id).toSet()
        require(gainTemplates.keys == saleIds) { "매도 체결과 실현손익 원장이 일치하지 않습니다." }

        var rebuiltBook = FifoCostBasisBook()
        val rebuiltGains = mutableListOf<RealizedGainRecord>()
        val actionsById = corporateActionLedger.associateBy(CorporateActionRecord::id)
        val tradesById = trades.associateBy(Trade::id)
        val rocById = dividends.filter { it.rocAmount > 0.0 }.associateBy(DividendLedgerEntry::id)
        val dividendIndexById = dividends.mapIndexed { index, dividend -> dividend.id to index }.toMap()
        val replayEntries = buildList {
            corporateActionLedger.forEach { action ->
                add(
                    ReplayEntry(
                        priority = 0,
                        accountingSequence = action.accountingSequence,
                        id = action.id,
                    ),
                )
            }
            trades.forEach { trade ->
                add(
                    ReplayEntry(
                        priority = 1,
                        accountingSequence = trade.accountingSequence,
                        id = trade.id,
                    ),
                )
            }
            dividends.filter { it.rocAmount > 0.0 }.forEach { dividend ->
                add(
                    ReplayEntry(
                        priority = 2,
                        accountingSequence = dividend.accountingSequence,
                        id = dividend.id,
                    ),
                )
            }
        }.sortedBy(ReplayEntry::accountingSequence)

        for (entry in replayEntries) {
            when (entry.priority) {
                0 -> {
                    val action = actionsById.getValue(entry.id)
                    rebuiltBook = rebuiltBook.applyQuantityMultiplier(action.stockId, action.quantityMultiplier)
                }

                1 -> {
                    val trade = tradesById.getValue(entry.id)
                    val stock = stockById.getValue(trade.stockId)
                    val rate = taxExchangeRatesByTradeId.getValue(trade.id)
                    val settledOn = trade.settlementDateOverride
                        ?: settlementDate(stock.market, marketDate(stock.market, trade.executedAt))
                    val roundedGross = roundCurrency(trade.grossAmount, trade.currency)
                    if (trade.side == OrderSide.BUY) {
                        rebuiltBook = rebuiltBook.addPurchase(
                            lotId = trade.id,
                            stockId = stock.id,
                            acquiredOn = settledOn,
                            quantity = trade.quantity,
                            purchasePriceKrw = round(roundedGross * rate).toLong(),
                            directPurchaseCostsKrw = round(trade.commission * rate).toLong(),
                        )
                    } else {
                        val template = gainTemplates.getValue(trade.id)
                        val sale = rebuiltBook.sell(
                            stockId = stock.id,
                            soldOn = settledOn,
                            quantity = trade.quantity,
                            grossProceedsKrw = round(roundedGross * rate).toLong(),
                            directSellingCostsKrw = round((trade.commission + trade.tax) * rate).toLong(),
                        )
                        rebuiltBook = sale.updatedBook
                        rebuiltGains += template.copy(
                            settlementDate = settledOn,
                            exchangeRateToKrw = rate,
                            taxGrossProceedsKrw = sale.grossProceedsKrw,
                            taxCostBasisKrw = sale.allocatedCostBasisKrw,
                            taxDirectSellingCostsKrw = sale.directSellingCostsKrw,
                            taxGainKrw = sale.realizedGainKrw,
                        )
                    }
                }

                2 -> {
                    val dividend = rocById.getValue(entry.id)
                    val rocKrw = round(dividend.rocAmount * dividend.exchangeRateToKrw).toLong()
                    val (updatedBook, excessGainKrw) = rebuiltBook.applyReturnOfCapital(
                        dividend.stockId,
                        rocKrw,
                    )
                    rebuiltBook = updatedBook
                    val index = dividendIndexById.getValue(dividend.id)
                    dividends[index] = dividends[index].copy(
                        excessReturnOfCapitalGainKrw = excessGainKrw,
                    )
                }

                else -> error("지원하지 않는 세무 원장 재생 항목입니다.")
            }
        }
        fifoCostBasisBook = rebuiltBook
        realizedGains.clear()
        realizedGains += rebuiltGains
        return buildSet {
            realizedGains.mapTo(this) { it.settlementDate.year }
            dividends.filter { it.rocAmount > 0.0 }.mapTo(this) { gameDate(it.paidAt).year }
        }
    }

    private fun processTaxExchangeRateSettlements(at: Instant) {
        if (pendingTaxSettlementTradeIds.isEmpty()) return
        val tradesById = trades.associateBy(Trade::id)
        val dueTradeIds = pendingTaxSettlementTradeIds.filter { tradeId ->
            val trade = tradesById.getValue(tradeId)
            val stock = stockById.getValue(trade.stockId)
            val settledOn = trade.settlementDateOverride
                ?: settlementDate(stock.market, marketDate(stock.market, trade.executedAt))
            marketDate(stock.market, at) >= settledOn
        }
        if (dueTradeIds.isEmpty()) return

        for (tradeId in dueTradeIds) {
            taxExchangeRatesByTradeId[tradeId] = macro.usdKrw
            pendingTaxSettlementTradeIds.remove(tradeId)
        }
        val replayedTaxYears = replayTaxAccountingLedger()
        (replayedTaxYears.asSequence() + realizedGains.asSequence()
            .filter { it.tradeId in dueTradeIds }
            .map { it.settlementDate.year })
            .distinct()
            .forEach(::recalculateAnnualTax)
    }

    private fun calculateAnnualTaxProjection(
        year: Int,
        dividendEntries: List<DividendLedgerEntry>,
        gainEntries: List<RealizedGainRecord>,
    ): Pair<AnnualTaxLedger, List<TaxPaymentNotice>>? {
        if (year !in 2026..2040) return null
        val tradeGains = gainEntries.filter { it.settlementDate.year == year }.map { record ->
            RealizedStockGain(
                id = record.tradeId,
                stockId = record.stockId,
                realizedOn = record.settlementDate,
                gainKrw = record.taxGainKrw,
                treatment = record.taxTreatment,
                instrumentTaxClass = if (record.market.isUnitedStates) {
                    stockById[record.stockId]?.let(::foreignInstrumentTaxClass)
                        ?: ForeignInstrumentTaxClass.OTHER_FOREIGN_EQUITY
                } else {
                    null
                },
            )
        }
        val yearDividends = dividendEntries.filter { gameDate(it.paidAt).year == year }
        val rocGains = yearDividends.mapNotNull { entry ->
            val gain = entry.excessReturnOfCapitalGainKrw
            if (gain <= 0L) return@mapNotNull null
            val stock = stockById[entry.stockId] ?: return@mapNotNull null
            RealizedStockGain(
                id = "${entry.id}:excess-roc",
                stockId = entry.stockId,
                realizedOn = gameDate(entry.paidAt),
                gainKrw = gain,
                treatment = StockGainTaxTreatment.FOREIGN_STANDARD,
                instrumentTaxClass = foreignInstrumentTaxClass(stock),
            )
        }
        val gains = tradeGains + rocGains
        val dividendFinancialIncomeKrw = CheckedMonetaryArithmetic.sum(
            yearDividends.asSequence().map { entry ->
                CheckedMonetaryArithmetic.roundedToLong(
                    entry.financialIncomeAmountKrw,
                    "Dividend financial income",
                )
            },
            "Annual dividend financial income",
        )
        val domesticEtfFinancialIncomeKrw = CheckedMonetaryArithmetic.sum(
            gainEntries.asSequence()
                .filter {
                    it.settlementDate.year == year &&
                        it.taxTreatment == StockGainTaxTreatment.DOMESTIC_ETF_HOLDING_PERIOD_WITHHELD
                }
                .map(RealizedGainRecord::taxableFinancialIncomeKrw),
            "Annual domestic ETF financial income",
        )
        val financialIncomeGrossKrw = CheckedMonetaryArithmetic.add(
            dividendFinancialIncomeKrw,
            domesticEtfFinancialIncomeKrw,
            "Annual gross financial income",
        )
        val foreignTaxPaidKrw = CheckedMonetaryArithmetic.sum(
            yearDividends.asSequence()
                .filter { it.currency == Currency.USD }
                .map { entry ->
                    CheckedMonetaryArithmetic.roundedToLong(
                        entry.withholdingTaxKrw,
                        "Foreign dividend withholding tax",
                    )
                },
            "Annual foreign tax paid",
        )
        val withholdingCreditsKrw = CheckedMonetaryArithmetic.sum(
            gainEntries.asSequence()
                .filter {
                    it.settlementDate.year == year &&
                        it.taxTreatment == StockGainTaxTreatment.DOMESTIC_ETF_HOLDING_PERIOD_WITHHELD
                }
                .map { gain ->
                    CheckedMonetaryArithmetic.roundedToLong(
                        gain.saleTax * gain.exchangeRateToKrw,
                        "Domestic ETF withholding credit",
                    )
                },
            "Annual withholding credits",
        )
        val ledger = annualStockTaxCalculator.calculate(
            AnnualStockTaxRequest(
                taxYear = year,
                gains = gains,
                financialIncomeGrossKrw = financialIncomeGrossKrw,
                foreignTaxPaidKrw = foreignTaxPaidKrw,
                withholdingCreditsKrw = withholdingCreditsKrw,
            ),
        )
        val notices = ledger.liabilities.map { liability ->
            TaxPaymentNotice(
                id = liability.id,
                taxYear = year,
                dueDate = liability.dueDate ?: LocalDate(year + 1, 5, 31),
                amountKrw = liability.payableKrw,
                status = liability.status,
                message = "${year}년 ${liability.label} ${liability.payableKrw}원은 " +
                    "${liability.dueDate ?: LocalDate(year + 1, 5, 31)}까지 납부 예정입니다.",
            )
        }
        return ledger to notices
    }

    private fun applyAnnualTaxProjection(
        year: Int,
        projection: Pair<AnnualTaxLedger, List<TaxPaymentNotice>>,
    ) {
        val (ledger, notices) = projection
        annualTaxLedgers[year] = ledger
        taxPaymentNotices.removeAll { it.taxYear == year }
        taxPaymentNotices += notices
    }

    private fun recalculateAnnualTax(year: Int) {
        val projection = calculateAnnualTaxProjection(year, dividends, realizedGains) ?: return
        applyAnnualTaxProjection(year, projection)
    }

    private fun foreignInstrumentTaxClass(stock: StockDefinition): ForeignInstrumentTaxClass =
        when (stock.instrumentType) {
            InstrumentType.STOCK -> ForeignInstrumentTaxClass.US_COMMON_STOCK
            InstrumentType.ETF -> ForeignInstrumentTaxClass.US_ETF_RIC
            InstrumentType.CLOSED_END_FUND -> ForeignInstrumentTaxClass.US_CLOSED_END_FUND_RIC
            InstrumentType.ETN -> ForeignInstrumentTaxClass.US_ETN_DEBT_SECURITY
            InstrumentType.REIT -> ForeignInstrumentTaxClass.US_REIT_USRPI
            InstrumentType.ADR -> ForeignInstrumentTaxClass.ADR
        }

    private fun processDueTaxPayments(currentDate: LocalDate) {
        for (index in taxPaymentNotices.indices) {
            val notice = taxPaymentNotices[index]
            if (notice.taxYear >= 2040 || notice.status != com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus.DUE ||
                currentDate < notice.dueDate
            ) {
                continue
            }
            val required = notice.amountKrw.toDouble()
            if (cash.getValue(Currency.KRW) + CASH_EPSILON < required) continue
            cash[Currency.KRW] = roundCurrency(cash.getValue(Currency.KRW) - required, Currency.KRW)
            taxPaymentNotices[index] = notice.copy(
                status = com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus.PAID,
                message = "${notice.taxYear}년 귀속 세금 ${notice.amountKrw}원을 ${currentDate}에 납부했습니다.",
            )
            annualTaxLedgers[notice.taxYear]?.let { ledger ->
                annualTaxLedgers[notice.taxYear] = ledger.copy(
                    liabilities = ledger.liabilities.map { liability ->
                        if (liability.id == notice.id) liability.copy(status = com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus.PAID)
                        else liability
                    },
                )
            }
            lastMessage = taxPaymentNotices[index].message
        }
    }

    private fun settlementDate(market: Market, tradedOn: LocalDate): LocalDate {
        var date = tradedOn
        var days = 0
        val requiredBusinessDays = if (market.isUnitedStates) 1 else 2
        while (days < requiredBusinessDays) {
            date = date.plus(1, DateTimeUnit.DAY)
            val holidays = runtimeClosedDates(market, date)
            if (date.dayOfWeek !in WEEKEND && date !in holidays) days += 1
        }
        return date
    }

    private fun enterSettlement() {
        currentTime = GameCalendar.endInstant
        turn = GameCalendar.turnAt(currentTime)
        phase = GamePhase.SETTLEMENT
        screen = Screen.ENDING
        for (index in orders.indices) {
            val order = orders[index]
            if (order.isOpen) orders[index] = order.copy(status = OrderStatus.EXPIRED, updatedAt = currentTime)
        }
        updateHoldingPrices()
        updateDrawdown()
        recalculateAnnualTax(2040)
        recordDailySnapshot(GameCalendar.CAMPAIGN_END_DATE, currentTime)
        lastMessage = GameEndReason.DATE_LIMIT.displayName
    }

    private fun marketSessionAtCurrentTime(market: Market): MarketSession {
        val calendarSession = marketSession(market, currentTime)
        val protected = if (market.isKorean) {
            tradingProtectionSnapshot.krxCircuitBreakers[market]?.phase
                ?.let { it != KrxCircuitBreakerPhase.NORMAL } == true
        } else {
            tradingProtectionSnapshot.usMarketWideCircuitBreaker?.let { state ->
                state.tradingDate == marketDate(market, currentTime) && state.phase != UsMwcbPhase.NORMAL &&
                    state.venueStatuses[market]?.phase != UsMwcbVenuePhase.REOPENED
            } == true
        }
        return if (protected) MarketSession.CLOSED else calendarSession
    }

    private fun marketSession(market: Market, time: Instant): MarketSession {
        val localDate = marketDate(market, time)
        return GameCalendar.marketSession(
            market = market,
            time = time,
            closedDates = runtimeClosedDates(market, localDate),
        )
    }

    private fun regularTradingFraction(market: Market, from: Instant, to: Instant): Double {
        require(to >= from)
        val date = marketDate(market, from)
        return GameCalendar.regularTradingFraction(
            market = market,
            hourStart = from,
            closedDates = runtimeClosedDates(market, date),
        )
    }

    private fun regionalTradingFraction(
        region: EtfExposureRegion,
        time: Instant,
        effectiveMarketFractions: Map<Market, Double>? = null,
    ): Double {
        val utcHour = time.toLocalDateTime(kotlinx.datetime.TimeZone.UTC).hour
        fun marketFraction(market: Market): Double = effectiveMarketFractions?.get(market)
            ?: regularTradingFraction(market, time, time + 1.hours)
        return when (region) {
            EtfExposureRegion.KOREA -> maxOf(
                marketFraction(Market.KOSPI),
                marketFraction(Market.KOSDAQ),
            )
            EtfExposureRegion.UNITED_STATES -> Market.entries
                .filter(Market::isUnitedStates)
                .maxOfOrNull(::marketFraction)
                ?: 0.0
            // 일본·호주와 유럽 세션을 하나의 복합 선진국 팩터로 근사한다.
            EtfExposureRegion.DEVELOPED_EX_US -> if (utcHour in 0..15) 1.0 else 0.0
            EtfExposureRegion.EMERGING_MARKETS -> if (utcHour in 0..9) 1.0 else 0.0
            EtfExposureRegion.GLOBAL -> if (Market.entries.any {
                    marketFraction(it) > 0.0
                } || utcHour in 0..15
            ) 1.0 else 0.0
        }
    }

    private fun Iterable<Double>.averageOrZero(): Double {
        val values = toList()
        return if (values.isEmpty()) 0.0 else values.average()
    }

    private fun orderBook(
        stock: StockDefinition,
        quote: Quote,
        session: MarketSession,
    ): OrderBookSnapshot {
        val tracker = dailyTrackers.getValue(stock.id)
        val scheduledActive = scheduledEventEngine.activeImpactEventsAt(currentTime, stocks)
        val liquidity = EventShockCalculator.liquidityMultiplierAt(
            activeEvents + scheduledActive,
            stock,
            currentTime,
        )
        // Negative means a liquidity-providing event. Preserve that sign so positive liquidity
        // news can thicken the book instead of being erased by a one-sided stress clamp.
        val eventLiquidityStress = (-ln(liquidity) / ln(5.0)).coerceIn(-1.0, 1.0)
        val combinedLiquidityStress = if (eventLiquidityStress >= 0.0) {
            1.0 - (1.0 - macro.liquidityStress) * (1.0 - eventLiquidityStress)
        } else {
            macro.liquidityStress * (1.0 + eventLiquidityStress)
        }.coerceIn(0.0, 1.0)
        return orderBookEngine.generate(
            OrderBookGenerationInput(
                stock = stock,
                timestamp = currentTime,
                lastPrice = quote.price,
                dailyBasePrice = tracker.basePrice,
                session = session,
                buyPressure = (
                    macro.retailOrderFlow * 0.42 + macro.institutionalOrderFlow * 0.58
                    ).coerceIn(-1.0, 1.0),
                marketStress = (
                    combinedLiquidityStress * 0.68 +
                        ((macro.volatilityRegime - 1.0) / 3.0).coerceAtLeast(0.0) * 0.32
                    ).coerceIn(0.0, 1.0),
            ),
        )
    }

    private fun marketDate(market: Market, time: Instant): LocalDate =
        GameCalendar.marketLocalDateTime(market, time).date

    private fun gameDate(time: Instant): LocalDate = GameCalendar.campaignDate(time)

    private fun money(amount: Double, currency: Currency): MoneyAmount =
        MoneyRoundingPolicy.MINOR_UNIT_HALF_UP.fromMajorUnits(amount.coerceAtLeast(0.0), currency)

    private fun roundCurrency(amount: Double, currency: Currency): Double {
        val factor = if (currency == Currency.KRW) 1.0 else 100.0
        return round(amount * factor) / factor
    }

    private fun nextId(prefix: String): String = "$prefix-${options.seed}-${nextSequence++}"

    private fun fail(message: String): Boolean {
        lastMessage = message
        return false
    }

    companion object {
        /** 장외·거래정지 봉을 제외한 최근 거래 시간봉을 종목별로 보존한다. */
        const val MAX_RECENT_BARS = 256
        /** 각 캔들 주기별로 같은 화면 밀도를 유지하는 OHLCV 보존 한도. */
        const val MAX_CHART_BARS_PER_INTERVAL = 84
        val CHART_INTERVALS = setOf(
            PriceBarInterval.ONE_DAY,
            PriceBarInterval.ONE_WEEK,
            PriceBarInterval.ONE_MONTH,
            PriceBarInterval.THREE_MONTHS,
        )
        const val MAX_INDEX_BARS = 256
        const val MAX_DAILY_SURVEILLANCE_POINTS = 140
        const val INVESTMENT_ALERT_NOTICE_TRADING_DAYS = 10
        const val MAX_NEWS_EVENTS = 1_000
        const val MAX_DEBUG_CASH_KRW = 1.0e15
        const val MAX_DEBUG_CASH_USD = 1.0e12
        const val MAX_DEBUG_LEDGER_GROSS_KRW = 1.0e16
        const val MAX_DEBUG_PRICE_INPUT = 1.0e18
        const val MAX_DEBUG_NATIVE_PRICE = 1.0e15
        const val MAX_DEBUG_PRICE_CHANGE_PERCENT = 100_000.0
        const val MAX_MARKET_ACTION_OCCURRENCE_GROUPS = 4_000
        const val NARRATIVE_FAMILY_COOLDOWN_HOURS = 72
        /** 축소 게임 유니버스에서 2026 KRX 합산 시총 상위100 제외를 재현하는 기준일 프록시. */
        const val KRX_TOP_100_MARKET_CAP_PROXY_KRW = 3_000_000_000_000.0
        const val BENCHMARK_START = 100.0
        const val DAILY_RESET_INITIAL_REFERENCE_LEVEL = 100.0
        const val OPTION_INITIAL_REFERENCE_LEVEL = 100.0
        const val REFERENCE_TRADING_HOURS_PER_YEAR = 252.0 * 6.5
        const val NANOSECONDS_PER_YEAR = 365.25 * 24.0 * 60.0 * 60.0 * 1_000_000_000.0
        const val ETN_MAX_CREDIT_DURATION_YEARS = 7.0
        const val ETN_MIN_MARKED_VALUE = 1e-9
        const val DAILY_RESET_BASE_SHORT_BORROW_RATE = 0.005
        const val DAILY_RESET_STRESS_BORROW_SPREAD = 0.04
        /** 미국 10년 명목곡선 위 게임용 주택담보대출 스프레드 가정이다. */
        const val MODEL_MORTGAGE_SPREAD_ANNUAL = 0.0175
        const val BUY_RESERVE_MULTIPLIER = 1.003
        const val FX_SPREAD_RATE = 0.001
        const val FX_MEAN_REVERSION = 0.00025
        const val FX_HOURLY_VOLATILITY = 0.0015
        const val FX_CROSS_HOURLY_VOLATILITY = 0.00055
        const val FX_CROSS_MEAN_REVERSION = 0.00018
        const val FX_GLOBAL_KRW_LOADING = 0.72
        const val MIN_USD_KRW = 800.0
        const val MAX_USD_KRW = 2_500.0
        const val MACRO_LEVEL_REVERSION = 0.0015
        const val MACRO_LEVEL_INNOVATION = 0.000025
        const val MACRO_SURPRISE_DECAY = 0.84
        const val RISK_SENTIMENT_REVERSION = 0.045
        const val RISK_SENTIMENT_INNOVATION = 0.004
        const val MARKET_FACTOR_VOLATILITY = 0.0016
        const val FORWARD_SPLIT_STREAK_DAYS = 20
        const val CORPORATE_ACTION_NOTICE_HOURS = 24 * 5
        const val CORPORATE_ACTION_COOLDOWN_HOURS = 24 * 365
        const val CORPORATE_ACTION_BOARD_GATE = 8L
        const val LISTING_RESOLUTION_EVENT_PREFIX = "listing-resolution:"
        const val PRICE_EPSILON = 1e-7
        const val QUANTITY_EPSILON = 1e-7
        const val TAX_RATE_EPSILON = 1e-9
        const val CASH_EPSILON = 0.01
        const val KRX_FUTURES_BETA = 1.08
        const val KOSPI_200_GAME_MARKET_CAP_FLOOR = 10_000_000_000_000.0
        const val US_REOPENING_AUCTION_MINUTES = 1
        val KRX_INDEX_BASE = mapOf(Market.KOSPI to 2_700.0, Market.KOSDAQ to 900.0)
        val LISTING_TRADING_HALT_REASONS = setOf(
            TradingHaltReason.LISTING_MAINTENANCE_REVIEW,
            TradingHaltReason.DELISTING_PROCESS,
        )
        val INFLATION_RELEASE_KINDS = setOf(
            ScheduledEventKind.US_CPI,
            ScheduledEventKind.US_PCE,
            ScheduledEventKind.KR_CPI,
        )
        val GROWTH_RELEASE_KINDS = setOf(
            ScheduledEventKind.US_GDP,
            ScheduledEventKind.KR_GDP,
            ScheduledEventKind.US_WEEKLY_CLAIMS,
            ScheduledEventKind.US_EMPLOYMENT,
            ScheduledEventKind.KR_EMPLOYMENT,
            ScheduledEventKind.US_RETAIL_SALES,
        )
        val POLICY_RELEASE_KINDS = setOf(
            ScheduledEventKind.US_FOMC,
            ScheduledEventKind.KR_BOK,
        )

        fun initialFxRates(usdKrw: Double): Map<ReferenceCurrency, Double> {
            val scale = usdKrw / 1_350.0
            return mapOf(
                ReferenceCurrency.KRW to 1.0,
                ReferenceCurrency.USD to usdKrw,
                ReferenceCurrency.EUR to 1_570.0 * scale,
                ReferenceCurrency.JPY to 9.15 * scale,
                ReferenceCurrency.CNY to 188.0 * scale,
                ReferenceCurrency.HKD to 173.0 * scale,
                ReferenceCurrency.GBP to 1_820.0 * scale,
                ReferenceCurrency.CAD to 985.0 * scale,
                ReferenceCurrency.CHF to 1_665.0 * scale,
                ReferenceCurrency.AUD to 890.0 * scale,
                ReferenceCurrency.SGD to 1_060.0 * scale,
                ReferenceCurrency.TWD to 44.0 * scale,
                ReferenceCurrency.INR to 15.4 * scale,
                ReferenceCurrency.BRL to 252.0 * scale,
            )
        }
        const val MACRO_STREAM_ID = 0x4D4143524FL
        const val DYNAMICS_STREAM_ID = 0x44594E414D494353L
        const val PRICE_STREAM_ID = 0x5052494345L
        const val BOOK_STREAM_ID = 0x424F4F4BL
        const val EVENT_STREAM_ID = 0x4556454E54L
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val DEBUG_MUTABLE_PHASES = setOf(GamePhase.PLAYING, GamePhase.PAUSED)

        fun restore(
            state: SimulatorUiState,
            catalog: InstrumentCatalogSnapshot,
        ): SimulatorRuntime? = runCatching {
            SimulatorRuntime(state.options, catalog).apply { restoreFrom(state) }
        }.getOrNull()
    }
}
