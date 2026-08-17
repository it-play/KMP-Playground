package com.amond.kmpbook.persistence.validation

import com.amond.kmpbook.domain.data.InstrumentCatalogReference
import com.amond.kmpbook.domain.data.InstrumentCatalogSnapshot
import com.amond.kmpbook.domain.data.InstrumentCatalogSourceReference
import com.amond.kmpbook.domain.methodology.BuiltInEquityMethodologies
import com.amond.kmpbook.domain.methodology.EquityMethodologyPolicy
import com.amond.kmpbook.domain.methodology.EquityMethodologyPortfolioConstraints
import com.amond.kmpbook.domain.methodology.EquityMethodologyRegistry
import com.amond.kmpbook.domain.methodology.EquityMethodologySchedule
import com.amond.kmpbook.domain.model.causal.CausalEconomicFactor
import com.amond.kmpbook.domain.model.causal.CausalMarketRegimeSnapshot
import com.amond.kmpbook.domain.model.causal.CausalSignalDirection
import com.amond.kmpbook.domain.model.causal.CausalTransmissionProfile
import com.amond.kmpbook.domain.model.causal.MIN_CAUSAL_SIGNAL_STRENGTH
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionKind
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionCancellationReason
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionNewsTransition
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionRecord
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionSource
import com.amond.kmpbook.domain.model.event.EventImpactCoveragePolicy
import com.amond.kmpbook.domain.model.event.EventImpactHorizon
import com.amond.kmpbook.domain.model.event.EventImpactTargetKind
import com.amond.kmpbook.domain.model.event.EventRecordKind
import com.amond.kmpbook.domain.model.event.EventScope
import com.amond.kmpbook.domain.model.event.EventSeverity
import com.amond.kmpbook.domain.model.event.EventTradingHaltKind
import com.amond.kmpbook.domain.model.event.EventType
import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.event.GameEventImpact
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.game.GamePhase
import com.amond.kmpbook.domain.model.game.Screen
import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkEngineKind
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSourceKind
import com.amond.kmpbook.domain.model.fund.CompositeSleeveDirection
import com.amond.kmpbook.domain.model.fund.AlternativeRiskPremiaSignalDirectionPolicy
import com.amond.kmpbook.domain.model.fund.EquityMethodologyPathEntry
import com.amond.kmpbook.domain.model.fund.EquityMethodologyPathState
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.FixedIncomeAssetType
import com.amond.kmpbook.domain.model.fund.FixedIncomeCreditBucket
import com.amond.kmpbook.domain.model.fund.FundLegalStructure
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCompositionHasher
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateAction
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioPlan
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioPosition
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioRecord
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioState
import com.amond.kmpbook.domain.model.fundproduct.DailyResetCalendar
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadLifecycle
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadState
import com.amond.kmpbook.domain.model.fundproduct.DailyResetLifecycle
import com.amond.kmpbook.domain.model.fundproduct.DailyResetReference
import com.amond.kmpbook.domain.model.fundproduct.DailyResetReferenceKind
import com.amond.kmpbook.domain.model.fundproduct.DailyResetState
import com.amond.kmpbook.domain.model.fundproduct.OptionRollCalendar
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyKind
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyLifecycle
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyState
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundCapitalActionKind
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundFinancingActionKind
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundLedgerEntry
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundLedgerKind
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundState
import com.amond.kmpbook.domain.model.fundstructure.EtnCreditEvent
import com.amond.kmpbook.domain.model.fundstructure.EtnIndicativeValueObservation
import com.amond.kmpbook.domain.model.fundstructure.EtnLedgerEntry
import com.amond.kmpbook.domain.model.fundstructure.EtnLedgerKind
import com.amond.kmpbook.domain.model.fundstructure.EtnLifecycle
import com.amond.kmpbook.domain.model.fundstructure.EtnState
import com.amond.kmpbook.domain.model.fundstructure.amountsAreClose
import com.amond.kmpbook.domain.model.index.MarketIndexId
import com.amond.kmpbook.domain.model.instrument.InstrumentStrategy
import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.DistributionAmountBasis
import com.amond.kmpbook.domain.model.instrument.FundFinancialState
import com.amond.kmpbook.domain.model.instrument.MAX_FUND_REFERENCE_VALUE
import com.amond.kmpbook.domain.model.instrument.MIN_FUND_REFERENCE_VALUE
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.instrument.grossReceivableAmount
import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertStatus
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleEventKind
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleReason
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleState
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.listing.termination.PublishedInstrumentTerminationNotice
import com.amond.kmpbook.domain.model.listing.termination.rawEffectiveTradingDate
import com.amond.kmpbook.domain.model.listing.termination.resolveInstrumentTerminationAtSessionClose
import com.amond.kmpbook.domain.model.listing.termination.scheduledTerminationOn
import com.amond.kmpbook.domain.model.market.IndustrySegment
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Currency
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
import com.amond.kmpbook.domain.simulation.market.MarketMicrostructure
import com.amond.kmpbook.domain.simulation.protection.TradingProtectionEngine
import com.amond.kmpbook.domain.simulation.protection.TradingProtectionRules
import com.amond.kmpbook.domain.model.venue.MarketSession
import com.amond.kmpbook.domain.model.pricing.PriceBar
import com.amond.kmpbook.domain.model.pricing.PriceBarInterval
import com.amond.kmpbook.domain.model.reference.CreditQuality
import com.amond.kmpbook.domain.model.reference.CreditSpreadSnapshot
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaDriverPosition
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaActionKind
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaRebalanceRecord
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaState
import com.amond.kmpbook.domain.model.reference.CommodityAssetClass
import com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceState
import com.amond.kmpbook.domain.model.reference.CompositeReferenceActionKind
import com.amond.kmpbook.domain.model.reference.CompositeReferenceRebalanceRecord
import com.amond.kmpbook.domain.model.reference.CompositeReferenceSleevePosition
import com.amond.kmpbook.domain.model.reference.CompositeReferenceState
import com.amond.kmpbook.domain.model.reference.EquityReferenceActionKind
import com.amond.kmpbook.domain.model.reference.EquityReferenceFactorExposure
import com.amond.kmpbook.domain.model.reference.EquityReferencePosition
import com.amond.kmpbook.domain.model.reference.EquityReferenceRebalanceRecord
import com.amond.kmpbook.domain.model.reference.EquityReferenceState
import com.amond.kmpbook.domain.model.reference.FixedIncomeInstrumentKind
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferencePosition
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferenceState
import com.amond.kmpbook.domain.model.reference.FixedIncomeRollRecord
import com.amond.kmpbook.domain.model.reference.FuturesAllocationMode
import com.amond.kmpbook.domain.model.reference.FuturesAllocationRecord
import com.amond.kmpbook.domain.model.reference.FuturesPriceReturnConvention
import com.amond.kmpbook.domain.model.reference.FuturesReferenceState
import com.amond.kmpbook.domain.model.reference.FuturesRollCalendar
import com.amond.kmpbook.domain.model.reference.FuturesRollRecord
import com.amond.kmpbook.domain.model.reference.FuturesSleeveState
import com.amond.kmpbook.domain.model.reference.FundOfFundsActionKind
import com.amond.kmpbook.domain.model.reference.FundOfFundsPosition
import com.amond.kmpbook.domain.model.reference.FundOfFundsRebalanceRecord
import com.amond.kmpbook.domain.model.reference.FundOfFundsState
import com.amond.kmpbook.domain.model.reference.KofrIndexBook
import com.amond.kmpbook.domain.model.reference.KofrIndexState
import com.amond.kmpbook.domain.model.reference.YieldCurveSnapshot
import com.amond.kmpbook.domain.model.reference.ReferenceSourceCatalog
import com.amond.kmpbook.domain.model.reference.ReferenceSourceSnapshot
import com.amond.kmpbook.domain.model.protection.core.InstrumentTradingHalt
import com.amond.kmpbook.domain.model.protection.core.TradingHaltReason
import com.amond.kmpbook.domain.model.protection.core.TradingHaltStatus
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxViPhase
import com.amond.kmpbook.domain.model.protection.us.UsLuldPhase
import com.amond.kmpbook.domain.model.protection.us.UsMwcbLevel
import com.amond.kmpbook.domain.model.protection.us.UsMwcbPhase
import com.amond.kmpbook.domain.model.schedule.ScheduledEventKind
import com.amond.kmpbook.domain.model.schedule.ScheduledEventMetricKind
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.TradeSettlementKind
import com.amond.kmpbook.domain.simulation.event.DefaultEventTemplates
import com.amond.kmpbook.domain.simulation.event.EventEngine
import com.amond.kmpbook.domain.simulation.fund.BenchmarkMethodologyCompiler
import com.amond.kmpbook.domain.simulation.fund.ReferencePortfolioEngine
import com.amond.kmpbook.domain.simulation.reference.EquityReferenceBookEngine
import com.amond.kmpbook.domain.simulation.reference.FundOfFundsBookEngine
import com.amond.kmpbook.domain.simulation.reference.AlternativeRiskPremiaBookEngine
import com.amond.kmpbook.domain.simulation.reference.CompositeReferenceBookEngine
import com.amond.kmpbook.domain.simulation.reference.CompositeScheduleResolver
import com.amond.kmpbook.domain.simulation.reference.FixedIncomeReferenceBookEngine
import com.amond.kmpbook.domain.simulation.reference.KofrIndexBookEngine
import com.amond.kmpbook.domain.simulation.reference.KofrRateModel
import com.amond.kmpbook.domain.simulation.listing.ListingLifecyclePolicyCatalog
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
import com.amond.kmpbook.domain.simulation.market.MarketDynamicsEngine
import com.amond.kmpbook.domain.simulation.market.MarketDynamicsSnapshot
import com.amond.kmpbook.domain.simulation.market.MarketRegimeProbabilities
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import com.amond.kmpbook.domain.simulation.schedule.ScheduledEventEngine
import com.amond.kmpbook.domain.simulation.schedule.DistributionSchedule
import com.amond.kmpbook.domain.simulation.schedule.DistributionAmountProjection
import com.amond.kmpbook.domain.tax.dividend.DividendTaxCalculator
import com.amond.kmpbook.domain.tax.dividend.DividendTaxClass
import com.amond.kmpbook.domain.tax.dividend.DividendTaxRequest
import com.amond.kmpbook.domain.tax.dividend.DistributionReturnOfCapitalPolicy
import com.amond.kmpbook.domain.tax.core.MoneyRoundingPolicy
import com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus
import com.amond.kmpbook.domain.tax.liability.AccountingObservationBoundary
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.domain.time.KofrBusinessCalendar
import com.amond.kmpbook.modding.builtin.debug.DebugMod
import com.amond.kmpbook.presentation.simulator.NewGameOptions
import com.amond.kmpbook.presentation.simulator.SimulatorRuntime
import com.amond.kmpbook.presentation.simulator.SimulatorUiState
import com.amond.kmpbook.presentation.simulator.canonicalReadEventLedgerViolation
import com.amond.kmpbook.presentation.simulator.projectSimulatorMarketUi
import com.amond.kmpbook.presentation.portfolio.DividendLedgerEntry
import com.amond.kmpbook.presentation.portfolio.AnnualTaxProjectionEngine
import com.amond.kmpbook.presentation.portfolio.CanonicalCashAccountingReplay
import com.amond.kmpbook.presentation.portfolio.CanonicalDailyPriceBarProjection
import com.amond.kmpbook.presentation.portfolio.CanonicalPortfolioSnapshotAccountingReplay
import com.amond.kmpbook.presentation.portfolio.CanonicalPriceHistoryRetention
import com.amond.kmpbook.presentation.portfolio.canonicalPendingTaxSettlementTradeIds
import com.amond.kmpbook.presentation.portfolio.pendingTaxSettlementRatesMatchExecutionFacts
import com.amond.kmpbook.presentation.portfolio.CanonicalTaxAccountingReplay
import com.amond.kmpbook.presentation.portfolio.CanonicalTradingLedgerValidation
import com.amond.kmpbook.presentation.portfolio.KrxDailySurveillanceProjection
import com.amond.kmpbook.presentation.portfolio.PortfolioPerformanceExtrema
import com.amond.kmpbook.presentation.portfolio.roundCurrencyForAccounting
import kotlin.math.exp
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.pow
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private val TERMINAL_LISTING_STATUSES: Set<ListingLifecycleStatus> = setOf(
    ListingLifecycleStatus.DELISTED,
    ListingLifecycleStatus.TERMINATED,
)

private val CURRENT_EVENT_TEMPLATE_IDS: Set<String> =
    DefaultEventTemplates.all.mapTo(linkedSetOf()) { it.id }

private const val MAX_PENDING_FUND_FLOW_RATE: Double = 0.20
private const val DYNAMICS_MATCH_EPSILON: Double = 1e-9
private const val REFERENCE_WEIGHT_ALLOCATION_EPSILON: Double = 1e-12
private const val DAILY_RESET_ACCOUNTING_EPSILON: Double = 1e-9
private const val DAILY_RESET_MIN_POSITIVE_FACTOR: Double = 1e-12
private const val OPTION_STRATEGY_ACCOUNTING_EPSILON: Double = 1e-9
private const val OPTION_TRADING_DAYS_PER_YEAR: Double = 252.0
private const val FIXED_INCOME_STATIC_VALUE_EPSILON: Double = 1e-12
private const val FIXED_INCOME_CONVEXITY_MULTIPLIER: Double = 1.10
private const val COMMODITY_REFERENCE_VALUE_EPSILON: Double = 1e-10
private const val STRUCTURED_REFERENCE_EPSILON: Double = 1e-8
private const val STRUCTURED_REFERENCE_MORTGAGE_SPREAD: Double = 0.0175
private const val DISTRIBUTION_QUANTITY_EPSILON: Double = 1e-8
private const val REQUIRED_SURVEILLANCE_DECISION_POINTS: Int = 16
private const val SURVEILLANCE_TURNOVER_EPSILON: Double = 1e-9
private const val MAX_SAFE_PERSISTED_SEQUENCE: Long = Long.MAX_VALUE - 1_000_000L
private const val BENCHMARK_START_VALUE: Double = 100.0
private val REFERENCE_PORTFOLIO_ASSET_ID_PATTERN =
    Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
private val FIXED_INCOME_ASSET_ID_PATTERN =
    Regex("FI:([a-z0-9][a-z0-9._-]{2,159}):v([0-9]+):([A-Z]+):r([0-9]+):g([0-9]+)")

internal fun validateSimulatorUiState(
    state: SimulatorUiState,
    catalog: InstrumentCatalogSnapshot,
): String? = validateSimulatorUiStateInternal(state, catalog)

/**
 * 카탈로그를 해석하기 전 저장 데이터 자체에서 검증할 수 있는 불변식만 확인한다.
 * 설치된 모드팩이 필요한 canonical 종목·일정 비교는 전체 검증에 맡긴다.
 */
internal fun validateSimulatorUiStateIntrinsic(state: SimulatorUiState): String? =
    validateSimulatorUiStateInternal(state, catalog = null)

private fun validateSimulatorUiStateInternal(
    state: SimulatorUiState,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    if (!hasValidCatalogReference(state)) {
        return "저장 데이터의 종목 카탈로그 참조 구조가 유효하지 않습니다."
    }
    if (catalog != null && state.catalogReference != catalog.reference) {
        return "저장 데이터의 종목 카탈로그 참조가 현재 번들·모드 종목팩과 일치하지 않습니다."
    }
    if (!GameCalendar.isWithinGameRange(state.currentTime) ||
        state.turn != GameCalendar.turnAt(state.currentTime) ||
        state.currentTime != GameCalendar.startInstant + state.turn.hours
    ) {
        return "현재 게임 시각이 캠페인 범위·시간 격자·턴 번호와 일치하지 않습니다."
    }
    val atCampaignEnd = GameCalendar.isFinished(state.currentTime)
    if (state.phase == GamePhase.SETUP && state.currentTime != GameCalendar.startInstant ||
        state.phase in setOf(GamePhase.PLAYING, GamePhase.PAUSED) && atCampaignEnd ||
        state.phase in setOf(GamePhase.SETTLEMENT, GamePhase.FINISHED) && !atCampaignEnd ||
        state.phase in setOf(GamePhase.SETTLEMENT, GamePhase.FINISHED) &&
        state.screen != Screen.ENDING
    ) {
        return "게임 단계가 캠페인 시작·종료 시각 및 정산 화면과 일치하지 않습니다."
    }
    if (!state.options.initialCapitalKrw.isFinite() ||
        state.options.initialCapitalKrw < NewGameOptions.MIN_INITIAL_CAPITAL_KRW ||
        state.options.initialCapitalKrw.toBits() !=
        roundCurrencyForAccounting(state.options.initialCapitalKrw, Currency.KRW).toBits()
    ) {
        return "초기 원화 자금은 최소 자금 이상의 원 단위여야 합니다."
    }
    if (state.nextSequence !in 1L..MAX_SAFE_PERSISTED_SEQUENCE) {
        return "다음 원장 시퀀스가 양수 또는 안전한 증가 범위가 아닙니다."
    }
    if (state.eventEngineSnapshot.sequence !in 0L..MAX_SAFE_PERSISTED_SEQUENCE) {
        return "이벤트 엔진 시퀀스가 안전한 증가 범위가 아닙니다."
    }
    if (!state.currentBenchmarkValue.isFinite() || state.currentBenchmarkValue <= 0.0) {
        return "현재 벤치마크 값이 유한한 양수가 아닙니다."
    }
    if (state.benchmarkHistory.isEmpty() ||
        state.benchmarkHistory.any { point ->
            point.timestamp > state.currentTime ||
                !point.value.isFinite() || point.value <= 0.0 ||
                !point.cumulativeReturn.isFinite() ||
                point.cumulativeReturn.toBits() !=
                (point.value / BENCHMARK_START_VALUE - 1.0).toBits()
        }
    ) {
        return "벤치마크 이력의 값·누적수익률·기록시각이 유효하지 않습니다."
    }
    if (state.benchmarkHistory.zipWithNext().any { (previous, next) ->
            previous.timestamp >= next.timestamp
        }
    ) {
        return "벤치마크 이력이 시각 오름차순으로 정렬되지 않았습니다."
    }
    if (state.portfolioSnapshots.size != state.dailyStatistics.size ||
        state.dailyStatistics.size != state.benchmarkHistory.size
    ) {
        return "일별 포트폴리오·통계·벤치마크 이력의 기록 수가 다릅니다."
    }
    if (state.benchmarkHistory.indices.any { index ->
            val portfolio = state.portfolioSnapshots[index]
            val daily = state.dailyStatistics[index]
            val benchmark = state.benchmarkHistory[index]
            val timestampDate = GameCalendar.campaignDate(benchmark.timestamp)
            val immediatelyBeforeDate = GameCalendar.campaignDate(benchmark.timestamp - 1.nanoseconds)
            val validDailyDate = daily.date == timestampDate ||
                timestampDate != immediatelyBeforeDate && daily.date == immediatelyBeforeDate
            portfolio.timestamp != benchmark.timestamp || !validDailyDate ||
                listOf(
                    daily.totalAssetsKrw,
                    daily.cashValueKrw,
                    daily.stockValueKrw,
                    daily.dailyReturn,
                    daily.drawdown,
                    daily.benchmarkValue,
                    daily.usdKrw,
                ).any { value -> !value.isFinite() } ||
                daily.totalAssetsKrw < 0.0 || daily.cashValueKrw < 0.0 ||
                daily.stockValueKrw < 0.0 || daily.drawdown !in 0.0..1.0 ||
                daily.benchmarkValue <= 0.0 || daily.usdKrw <= 0.0 ||
                daily.benchmarkValue.toBits() != benchmark.value.toBits() ||
                daily.totalAssetsKrw.toBits() != portfolio.totalAssetValueKrw.toBits() ||
                daily.cashValueKrw.toBits() != portfolio.cashValueKrw.toBits() ||
                daily.stockValueKrw.toBits() != portfolio.stockValueKrw.toBits()
        }
    ) {
        return "일별 포트폴리오·통계·벤치마크 이력의 시각·날짜 또는 값이 다릅니다."
    }
    if (state.benchmarkHistory.last().timestamp == state.currentTime &&
        state.benchmarkHistory.last().value.toBits() != state.currentBenchmarkValue.toBits()
    ) {
        return "현재 시각에 기록된 벤치마크 이력이 현재 벤치마크 값과 다릅니다."
    }
    if (
        state.options.scenarioName.isBlank() ||
        state.options.scenarioName.length > NewGameOptions.MAX_GAME_LABEL_LENGTH ||
        state.options.difficultyName.isBlank() ||
        state.options.difficultyName.length > NewGameOptions.MAX_GAME_LABEL_LENGTH
    ) {
        return "시나리오 또는 난이도 이름이 유효하지 않습니다."
    }
    if (
        state.options.activeMods.size > NewGameOptions.MAX_ACTIVE_MODS ||
        state.options.activeMods.map { it.id }.distinct().size != state.options.activeMods.size ||
        state.options.activeMods.any { it.validate() != null }
    ) {
        return "활성 모드 정보가 유효하지 않습니다."
    }
    val macro = try {
        state.macro.validatedCopy()
    } catch (_: RuntimeException) {
        return "거시 환경의 금리·물가·성장·환율·시장 팩터 상태가 유효하지 않습니다."
    }
    var dailyCloseExtrema = runCatching {
        PortfolioPerformanceExtrema.initial(state.options.initialCapitalKrw)
    }.getOrNull() ?: return "일별 마감 성과 극값의 초기자본이 유효하지 않습니다."
    for (index in state.portfolioSnapshots.indices) {
        val assetsKrw = state.portfolioSnapshots[index].totalAssetValueKrw
        dailyCloseExtrema = runCatching { dailyCloseExtrema.observe(assetsKrw) }.getOrNull()
            ?: return "일별 마감 성과 극값의 자산 관측값이 유효하지 않습니다."
        val previousAssetsKrw = state.portfolioSnapshots.getOrNull(index - 1)?.totalAssetValueKrw
        val canonicalDailyReturn = if (previousAssetsKrw == null || previousAssetsKrw == 0.0) {
            assetsKrw / state.options.initialCapitalKrw - 1.0
        } else {
            assetsKrw / previousAssetsKrw - 1.0
        }
        if (state.dailyStatistics[index].dailyReturn.toBits() != canonicalDailyReturn.toBits()) {
            return "일별 수익률이 일별 마감 자산 계보의 canonical 값과 다릅니다."
        }
        if (state.dailyStatistics[index].drawdown.toBits() !=
            dailyCloseExtrema.drawdownAt(assetsKrw).toBits()
        ) {
            return "일별 낙폭이 일별 마감 자산의 running peak 계보와 다릅니다."
        }
    }
    val currentAssetsKrw = state.totalAssetsKrw
    val canonicalPerformanceExtrema = runCatching {
        dailyCloseExtrema.observe(currentAssetsKrw)
    }.getOrNull() ?: return "현재 포트폴리오의 성과 극값 자산 관측값이 유효하지 않습니다."
    if (state.peakAssetsKrw.toBits() != canonicalPerformanceExtrema.peakAssetsKrw.toBits() ||
        state.maximumDrawdown.toBits() != canonicalPerformanceExtrema.maximumDrawdown.toBits()
    ) {
        return "최고자산·최대 낙폭이 일별 마감 및 현재 평가 계보의 canonical 값과 다릅니다."
    }
    if (!state.options.initialExternalMarketForces.values.all { it.isFinite() && it in 0.0..1.0 } ||
        !state.externalMarketForcesTarget.values.all { it.isFinite() && it in 0.0..1.0 } ||
        !state.marketDynamicsSnapshot.effectiveForces.values.all { it.isFinite() && it in 0.0..1.0 }
    ) {
        return "외부 시장 환경의 시작값·목표값·실효값이 유효하지 않습니다."
    }
    val dynamics = state.marketDynamicsSnapshot
    val regimeValues = dynamics.regimeProbabilities.values
    if (regimeValues.any { !it.isFinite() || it !in 0.0..1.0 } ||
        kotlin.math.abs(regimeValues.sum() - 1.0) > MarketRegimeProbabilities.SUM_EPSILON ||
        !dynamics.conditionalVariance.isFinite() ||
        dynamics.conditionalVariance !in MarketDynamicsSnapshot.MIN_VARIANCE..MarketDynamicsSnapshot.MAX_VARIANCE ||
        !dynamics.newsExcitation.isFinite() ||
        dynamics.newsExcitation !in 0.0..MarketDynamicsSnapshot.MAX_NEWS_EXCITATION ||
        !dynamics.newsIntensity.isFinite() ||
        dynamics.newsIntensity !in MarketDynamicsSnapshot.MIN_NEWS_INTENSITY..
            MarketDynamicsSnapshot.MAX_NEWS_INTENSITY ||
        !dynamics.eventSentimentMemory.isFinite() || dynamics.eventSentimentMemory !in -1.0..1.0 ||
        !dynamics.liquidityStress.isFinite() || dynamics.liquidityStress !in 0.0..1.0 ||
        !dynamics.retailFlow.isFinite() || dynamics.retailFlow !in -1.0..1.0 ||
        !dynamics.institutionalFlow.isFinite() || dynamics.institutionalFlow !in -1.0..1.0 ||
        !dynamics.downsideMemory.isFinite() ||
        dynamics.downsideMemory !in 0.0..MarketDynamicsSnapshot.MAX_DOWNSIDE_MEMORY ||
        dynamics.previousObservedReturn?.let { !it.isFinite() || it !in -1.0..1.0 } == true
    ) {
        return "시장 동역학 스냅샷의 분산·국면·뉴스·수급 상태가 유효하지 않습니다."
    }
    val macroDynamicsValues = listOf(
        macro.volatilityRegime,
        macro.retailOrderFlow,
        macro.institutionalOrderFlow,
        macro.liquidityStress,
        macro.newsIntensity,
    )
    if (macroDynamicsValues.any { !it.isFinite() }) {
        return "거시 스냅샷의 변동성·수급·유동성·뉴스 강도가 유한하지 않습니다."
    }
    if (kotlin.math.abs(macro.volatilityRegime - dynamics.resolvedVolatilityRegime) >
        DYNAMICS_MATCH_EPSILON ||
        kotlin.math.abs(macro.retailOrderFlow - dynamics.retailFlow) > DYNAMICS_MATCH_EPSILON ||
        kotlin.math.abs(macro.institutionalOrderFlow - dynamics.institutionalFlow) >
        DYNAMICS_MATCH_EPSILON ||
        kotlin.math.abs(macro.liquidityStress - dynamics.liquidityStress) > DYNAMICS_MATCH_EPSILON ||
        kotlin.math.abs(macro.newsIntensity - dynamics.newsIntensity) > DYNAMICS_MATCH_EPSILON
    ) {
        return "거시 스냅샷과 시장 동역학의 변동성·수급·유동성·뉴스 강도가 일치하지 않습니다."
    }
    if (state.stocks.map { it.id }.distinct().size != state.stocks.size) return "종목 ID가 중복되었습니다."
    if (catalog != null && state.stocks != expectedStocks(state, catalog)) {
        return "저장 종목 목록이 현재 카탈로그와 적용 기업행동으로 재구축한 원본과 일치하지 않습니다."
    }
    val stocksById = state.stocks.associateBy { it.id }
    val stockIds = stocksById.keys
    validateInstrumentFinancialStates(state, stocksById, catalog)?.let { violation ->
        return violation
    }
    val eventSchemaEngine = EventEngine(seed = 0L)
    val scheduledEventSchemaEngine = ScheduledEventEngine(
        DeterministicRandom.mixSeed(state.options.seed, ScheduledEventEngine.STREAM_ID),
    )
    val possibleEventTriggerKeys = eventSchemaEngine.possibleTriggerKeys(state.stocks)
    val eventCooldowns = state.eventEngineSnapshot.lastTriggeredEpochSeconds
    if (eventCooldowns.keys.any { it.isBlank() || it !in possibleEventTriggerKeys } ||
        eventCooldowns.values.any { epochSeconds ->
            epochSeconds < GameCalendar.startInstant.epochSeconds ||
                epochSeconds > state.currentTime.epochSeconds
        }
    ) {
        return "이벤트 엔진 쿨다운 키 또는 마지막 발생 시각이 현재 규칙·게임 시간과 일치하지 않습니다."
    }
    if (state.stocks.any { stock ->
            stock.industrySegments.any { (it as IndustrySegment?) == null }
        }
    ) {
        return "종목의 세부 산업 enum이 유효하지 않습니다."
    }
    if (state.selectedStockId != null && state.stocks.none { it.id == state.selectedStockId }) {
        return "선택 종목이 종목 목록에 없습니다."
    }
    if (state.cashByCurrency.values.any { !it.isFinite() || it < 0.0 }) return "현금 잔액이 유효하지 않습니다."
    if (state.holdings.any { (id, holding) -> id != holding.stockId }) return "보유 종목 맵 키가 일치하지 않습니다."
    if (state.quotes.keys != stockIds || state.quotes.any { (id, quote) ->
            val stock = stocksById[id] ?: return@any true
            val listing = state.listingLifecycleStates[id] ?: return@any true
            val permitsZeroTerminalQuote = listing.isTerminal &&
                listing.finalDisposition?.type in setOf(
                    ListingFinalDispositionType.WORTHLESS_DISPOSITION,
                    ListingFinalDispositionType.OTC_TRANSFER,
                )
            if (permitsZeroTerminalQuote) {
                return@any id != quote.stockId || quote.price != 0.0 || quote.open != 0.0 ||
                    quote.high != 0.0 ||
                    quote.low != 0.0 || quote.volume != 0L || quote.bidPrice != null ||
                    quote.askPrice != null || quote.bidQuantity != 0.0 ||
                    quote.askQuantity != 0.0 || quote.session != MarketSession.CLOSED ||
                    !quote.previousClose.isFinite() || quote.previousClose < 0.0 ||
                    quote.timestamp > state.currentTime
            }
            val minimum = MarketMicrostructure.minimumPrice(stock.market)
            val prices = listOf(
                quote.price,
                quote.previousClose,
                quote.open,
                quote.high,
                quote.low,
            ) + listOfNotNull(quote.bidPrice, quote.askPrice)
            id != quote.stockId || quote.timestamp > state.currentTime ||
                prices.any { price -> !price.isFinite() || price < minimum } ||
                quote.high < maxOf(quote.price, quote.open, quote.low) ||
                quote.low > minOf(quote.price, quote.open, quote.high) ||
                quote.volume < 0L || !quote.bidQuantity.isFinite() || !quote.askQuantity.isFinite() ||
                quote.bidQuantity < 0.0 || quote.askQuantity < 0.0 ||
                quote.bidPrice?.let { bid ->
                    quote.askPrice?.let { ask -> bid > ask }
                } == true ||
                prices.any { price ->
                    val rounded = MarketMicrostructure.roundNearest(stock, price)
                    val tolerance = maxOf(1e-12, MarketMicrostructure.tickSize(stock, price) * 1e-9)
                    kotlin.math.abs(rounded - price) > tolerance
                }
        }
    ) {
        return "시세 맵의 ID·시각·유한 양수 가격·OHLC 포함관계·호가·거래소 tick이 유효하지 않습니다."
    }
    if (state.priceHistory.keys != stockIds || state.priceHistory.any { (id, bars) ->
            val stock = stocksById[id] ?: return@any true
            bars.isEmpty() || bars.size > SimulatorRuntime.MAX_RECENT_BARS ||
                bars.any { bar ->
                    val isInitialPlaceholder = bars.size == 1 && bar.volume == 0L &&
                        bar.endTime == GameCalendar.startInstant &&
                        bar.startTime == GameCalendar.startInstant - 1.hours
                    bar.stockId != id || bar.step != PriceBarInterval.ONE_HOUR ||
                        bar.endTime > state.currentTime ||
                        bar.endTime - bar.startTime != 1.hours ||
                        !isInitialPlaceholder &&
                        GameCalendar.regularTradingFraction(stock.market, bar.startTime) <= 0.0 ||
                        runCatching { bar.copy() }.isFailure ||
                        listOf(bar.open, bar.high, bar.low, bar.close).any { !it.isFinite() }
                } ||
                bars.zipWithNext().any { (previous, next) -> previous.endTime > next.startTime }
        }
    ) {
        return "시간봉 가격 히스토리의 종목·주기·OHLCV·시간·보존 한도가 올바르지 않습니다."
    }
    val chartIntervals = setOf(
        PriceBarInterval.ONE_DAY,
        PriceBarInterval.ONE_WEEK,
        PriceBarInterval.ONE_MONTH,
        PriceBarInterval.THREE_MONTHS,
    )
    if (state.chartPriceHistory.keys != stockIds || state.chartPriceHistory.any { (id, histories) ->
            histories.keys != chartIntervals || histories.any { (interval, bars) ->
                bars.size > SimulatorRuntime.MAX_CHART_BARS_PER_INTERVAL ||
                    bars.any { bar ->
                        bar.stockId != id || bar.step != interval ||
                            bar.endTime > state.currentTime ||
                            runCatching { bar.copy() }.isFailure ||
                            listOf(bar.open, bar.high, bar.low, bar.close).any { !it.isFinite() }
                    } ||
                    bars.zipWithNext().any { (previous, next) -> previous.endTime > next.startTime }
            }
        }
    ) {
        return "주기별 차트 히스토리의 종목·주기·OHLCV·시간·보존 한도가 올바르지 않습니다."
    }
    if (state.portfolioSnapshots.any { snapshot ->
            snapshot.holdingCostBasisKrw.keys != snapshot.holdings.mapTo(linkedSetOf()) { it.stockId } ||
                snapshot.holdingCostBasisKrw.values.any { !it.isFinite() || it < 0.0 } ||
                snapshot.distributionReceivableByCurrency.values.any { amount ->
                    !amount.isFinite() || amount !in 0.0..MAX_FUND_REFERENCE_VALUE || amount == 0.0
                } ||
                (snapshot.distributionReceivableByCurrency.keys - Currency.KRW).any { currency ->
                    snapshot.exchangeRatesToKrw[currency]?.let { rate ->
                        rate.isFinite() && rate > 0.0
                    } != true
                } ||
                !snapshot.distributionReceivableValueKrw.isFinite() ||
                snapshot.distributionReceivableValueKrw !in 0.0..MAX_FUND_REFERENCE_VALUE ||
                !snapshot.totalAssetValueKrw.isFinite() || snapshot.totalAssetValueKrw < 0.0
        }
    ) {
        return "포트폴리오 스냅샷의 FIFO 원가·분배 미수금·환율·총자산이 유효하지 않습니다."
    }
    if (state.pendingEtfReferenceReturns.any { (stockId, returnRate) ->
            stocksById[stockId]?.isFundLike != true || !returnRate.isFinite() ||
                returnRate !in -2.5..2.5
        }
    ) {
        return "ETF 기초시장 이월 수익률이 유효하지 않습니다."
    }
    if (state.pendingClosedEventLogReturns.any { (stockId, logReturn) ->
            stockId !in stockIds || !logReturn.isFinite() || logReturn !in -2.5..2.5
        }
    ) {
        return "폐장 중 이벤트 이월 수익률이 유효하지 않습니다."
    }
    val requiredIndexIds = MarketIndexId.entries.toSet()
    if (state.marketIndices.keys != requiredIndexIds ||
        state.marketIndices.any { (id, snapshot) ->
            id != snapshot.id || snapshot.timestamp != state.currentTime ||
                runCatching { snapshot.copy() }.isFailure ||
                snapshot.sessionDate?.let { date ->
                    date > GameCalendar.marketLocalDateTime(Market.NYSE, snapshot.timestamp).date
                } == true
        }
    ) {
        return "대표 지수 현재값에 필수 지수가 없거나 맵 키와 지수 ID가 일치하지 않습니다."
    }
    if (state.marketIndexHistory.keys != requiredIndexIds ||
        state.marketIndexHistory.any { (id, values) ->
            values.isEmpty() || values.size > SimulatorRuntime.MAX_INDEX_BARS ||
                values.last() != state.marketIndices.getValue(id) ||
                values.any { snapshot ->
                    snapshot.id != id || snapshot.timestamp > state.currentTime ||
                        runCatching { snapshot.copy() }.isFailure ||
                        snapshot.sessionDate?.let { date ->
                            date > GameCalendar.marketLocalDateTime(Market.NYSE, snapshot.timestamp).date
                        } == true
                } ||
                values.zipWithNext().any { (previous, next) ->
                    next.timestamp != previous.timestamp + 1.hours
                }
        }
    ) {
        return "대표 지수 이력에 필수 지수가 없거나 시간·ID 순서가 올바르지 않습니다."
    }
    CanonicalTradingLedgerValidation.validate(
        orders = state.orders,
        trades = state.trades,
        stocksById = stocksById,
        holdingsByStockId = state.holdings,
        listingLifecycleStates = state.listingLifecycleStates,
        corporateActions = state.corporateActionLedger,
        currentTime = state.currentTime,
    )?.let { violation -> return violation }
    val canonicalTaxReplay = runCatching {
        CanonicalTaxAccountingReplay.replay(
            stocksById = stocksById,
            orders = state.orders,
            trades = state.trades,
            transactionCosts = state.transactionCosts,
            taxExchangeRatesByTradeId = state.taxExchangeRatesByTradeId,
            corporateActions = state.corporateActionLedger,
            distributionOrigins = state.distributionEntitlementOrigins,
            dividendEntries = state.dividendLedger,
            portfolioSnapshots = state.portfolioSnapshots,
        )
    }.getOrNull() ?: return "거래·FIFO·기업행동·ROC에서 canonical 세무 원장을 재생할 수 없습니다."
    val canonicalNativeHoldings = canonicalTaxReplay.nativeHoldingsByStockId
    val nativeHoldingsMismatch = state.holdings.keys != canonicalNativeHoldings.keys ||
        state.holdings.any { (stockId, holding) ->
            val canonical = canonicalNativeHoldings[stockId] ?: return@any true
            holding.stockId != canonical.stockId ||
                holding.quantity.toBits() != canonical.quantity.toBits() ||
                holding.averagePrice.toBits() != canonical.averagePrice.toBits() ||
                holding.currency != canonical.currency ||
                holding.realizedProfit.toBits() != canonical.realizedProfit.toBits()
        }
    if (state.fifoCostBasisBook != canonicalTaxReplay.fifoCostBasisBook ||
        state.realizedGains != canonicalTaxReplay.realizedGains ||
        nativeHoldingsMismatch ||
        state.distributionEntitlementOrigins.any { origin ->
            canonicalTaxReplay.originExcessReturnOfCapitalGainKrw[origin.id] !=
                origin.excessReturnOfCapitalGainKrw
        } || state.dividendLedger.any { dividend ->
            canonicalTaxReplay.dividendExcessReturnOfCapitalGainKrw[dividend.id] !=
                dividend.excessReturnOfCapitalGainKrw
        }
    ) {
        return "보유 수량·평균원가·실현손익·FIFO·세무 원장이 canonical 회계 재생 결과와 다릅니다."
    }
    val debugCashEnabled = state.options.activeMods.any { mod ->
        DebugMod.isCompatible(mod.id, mod.version)
    }
    if (state.cashAdjustmentLedger.isNotEmpty() && !debugCashEnabled) {
        return "디버그 현금 조정 원장은 호환되는 신뢰 디버그 모드에서만 허용됩니다."
    }
    if (state.cashAdjustmentLedger.any { adjustment ->
            adjustment.balanceAfter > when (adjustment.currency) {
                Currency.KRW -> SimulatorRuntime.MAX_DEBUG_CASH_KRW
                Currency.USD -> SimulatorRuntime.MAX_DEBUG_CASH_USD
            }
        }
    ) {
        return "디버그 현금 조정 결과가 통화별 허용 한도를 벗어났습니다."
    }
    val canonicalCash = runCatching {
        CanonicalCashAccountingReplay.replay(
            initialCapitalKrw = state.options.initialCapitalKrw,
            campaignSeed = state.options.seed,
            currentTime = state.currentTime,
            trades = state.trades,
            transactionCosts = state.transactionCosts,
            foreignExchanges = state.foreignExchangeLedger,
            dividends = state.dividendLedger,
            taxPaymentNotices = state.taxPaymentNotices,
            cashAdjustments = state.cashAdjustmentLedger,
        )
    }.getOrNull() ?: return "초기자본과 현금 회계 원장에서 통화별 잔액을 재생할 수 없습니다."
    if (canonicalCash != state.cashByCurrency) {
        return "현재 통화별 현금이 canonical 체결·환전·분배·세금 원장과 다릅니다."
    }
    validatePortfolioSnapshotAccountingLineage(state, stocksById)?.let { violation ->
        return violation
    }

    val currentTaxYear = GameCalendar.campaignDate(state.currentTime).year
    val canonicalTaxYears = (2026..currentTaxYear).toSet()
    if (state.annualTaxLedgers.keys != canonicalTaxYears ||
        state.annualTaxLedgers.any { (year, ledger) -> year != ledger.taxYear } ||
        state.taxPaymentNotices.map { notice -> notice.id }.distinct().size !=
        state.taxPaymentNotices.size
    ) {
        return "연간 세금 원장의 연도 키 또는 납부 고지 ID가 canonical 집합과 다릅니다."
    }
    val noticesByYear = state.taxPaymentNotices.groupBy { notice -> notice.taxYear }
    for (year in canonicalTaxYears) {
        val canonicalProjection = runCatching {
            AnnualTaxProjectionEngine.calculate(
                year = year,
                stocksById = stocksById,
                dividendEntries = state.dividendLedger,
                gainEntries = canonicalTaxReplay.realizedGains,
            )
        }.getOrNull() ?: return "연간 세금 원장을 canonical 거래·분배 계보에서 계산할 수 없습니다."
        val actualNotices = noticesByYear[year].orEmpty()
        val mergedProjection = runCatching {
            AnnualTaxProjectionEngine.mergeCanonicalProjectionWithPaidFacts(
                projection = canonicalProjection,
                paidFacts = actualNotices.filter { notice ->
                    notice.status == TaxLiabilityStatus.PAID
                },
                currentTime = state.currentTime,
            )
        }.getOrNull() ?: return "세금 납부 사실이 canonical 세액·기한·회계 시각과 다릅니다."
        if (state.annualTaxLedgers.getValue(year) != mergedProjection.first ||
            actualNotices != mergedProjection.second
        ) {
            return "연간 세금 원장·납부 고지가 canonical projection 및 납부 사실과 다릅니다."
        }
    }
    if (noticesByYear.keys.any { year -> year !in canonicalTaxYears }) {
        return "캠페인 현재 연도 밖의 세금 납부 고지가 있습니다."
    }
    val tradesById = state.trades.associateBy { it.id }
    val tradeIds = tradesById.keys
    if (tradeIds.size != state.trades.size) return "체결 ID가 중복되었습니다."
    val transactionCostTradeIds = state.transactionCosts.map { it.tradeId }
    if (transactionCostTradeIds.toSet() != tradeIds ||
        transactionCostTradeIds.distinct().size != transactionCostTradeIds.size ||
        state.transactionCosts.any { cost ->
            val trade = tradesById[cost.tradeId]
            trade == null || trade.stockId != cost.stockId || trade.currency != cost.currency ||
                !cost.exchangeRateToKrw.isFinite() || cost.exchangeRateToKrw <= 0.0
        }
    ) {
        return "모든 체결에는 종목·통화가 일치하는 유효한 거래비용 원장이 하나씩 필요합니다."
    }
    if (state.taxExchangeRatesByTradeId.keys != tradeIds ||
        state.taxExchangeRatesByTradeId.any { (_, rate) -> !rate.isFinite() || rate <= 0.0 }
    ) {
        return "체결별 세무 환율 원장에 모든 체결의 유효한 환율이 필요합니다."
    }
    val canonicalPendingTaxSettlements = canonicalPendingTaxSettlementTradeIds(
        trades = state.trades,
        stocksById = stocksById,
        currentTime = state.currentTime,
    )
    if (state.pendingTaxSettlementTradeIds != canonicalPendingTaxSettlements ||
        !pendingTaxSettlementRatesMatchExecutionFacts(
            pendingTradeIds = canonicalPendingTaxSettlements,
            transactionCosts = state.transactionCosts,
            taxExchangeRatesByTradeId = state.taxExchangeRatesByTradeId,
        )
    ) {
        return "미결제 세무 환율 원장이 미국 거래소 체결의 canonical 결제 일정과 다릅니다."
    }
    if (state.watchlistedStockIds.any { it !in stockIds }) {
        return "관심 종목에 알 수 없는 종목 ID가 있습니다."
    }
    if (state.pendingCorporateActions.map { it.id }.distinct().size != state.pendingCorporateActions.size ||
        state.pendingCorporateActions.any { it.stockId !in stockIds }
    ) {
        return "대기 기업행동 원장의 ID 또는 종목이 유효하지 않습니다."
    }
    if (state.corporateActionLedger.map { it.id }.distinct().size != state.corporateActionLedger.size ||
        state.corporateActionLedger.any { it.stockId !in stockIds || it.effectiveAt > state.currentTime } ||
        state.corporateActionLedger.zipWithNext().any { (previous, next) ->
            previous.effectiveAt > next.effectiveAt ||
                previous.accountingSequence >= next.accountingSequence
        }
    ) {
        return "적용 기업행동 원장의 ID·종목·효력시각·회계 순서가 유효하지 않습니다."
    }
    val etnProductIds = stocksById.values.asSequence()
        .filter { stock ->
            stock.fundProductProfile?.legalStructure == FundLegalStructure.EXCHANGE_TRADED_NOTE
        }
        .map(StockDefinition::id)
        .toSet()
    if (state.pendingCorporateActions.any { action -> action.stockId in etnProductIds } ||
        state.corporateActionLedger.any { action -> action.stockId in etnProductIds }
    ) {
        return "현 ETN 계약에는 note 분할·단주 현금정산 약관이 없으므로 기업행동을 생성·적용할 수 없습니다."
    }
    if (state.dividendLedger.map { it.id }.distinct().size != state.dividendLedger.size) {
        return "분배 원장의 ID가 중복되었습니다."
    }
    for (entry in state.dividendLedger) {
        val stock = stocksById[entry.stockId]
            ?: return "분배 원장에 알 수 없는 종목이 있습니다."
        val paidOn = GameCalendar.marketLocalDateTime(stock.market, entry.paidAt).date
        val expectedId = "dividend:${stock.id}:${entry.exDate}:$paidOn"
        val grossRoundingTolerance = if (stock.currency == Currency.KRW) 0.500001 else 0.005001
        if (entry.id != expectedId || entry.currency != stock.currency ||
            entry.exDate > entry.recordDate || entry.recordDate > paidOn ||
            !entry.grossPerUnit.isFinite() || entry.grossPerUnit <= 0.0 ||
            entry.grossPerUnit > MAX_FUND_REFERENCE_VALUE ||
            !entry.entitledQuantity.isFinite() || entry.entitledQuantity <= 0.0 ||
            entry.entitledQuantity > MAX_FUND_REFERENCE_VALUE ||
            abs(entry.grossAmount - entry.grossPerUnit * entry.entitledQuantity) > grossRoundingTolerance ||
            !entry.grossAmount.isFinite() || entry.grossAmount !in
            0.0..MAX_FUND_REFERENCE_VALUE || entry.grossAmount == 0.0 ||
            !entry.withholdingTax.isFinite() || entry.withholdingTax !in
            0.0..MAX_FUND_REFERENCE_VALUE ||
            !entry.netAmount.isFinite() || entry.netAmount !in 0.0..MAX_FUND_REFERENCE_VALUE ||
            !entry.exchangeRateToKrw.isFinite() || entry.exchangeRateToKrw <= 0.0 ||
            !entry.taxableIncomeAmount.isFinite() || entry.taxableIncomeAmount !in
            0.0..MAX_FUND_REFERENCE_VALUE ||
            !entry.returnOfCapitalAmount.isFinite() || entry.returnOfCapitalAmount !in
            0.0..MAX_FUND_REFERENCE_VALUE ||
            entry.excessReturnOfCapitalGainKrw < 0L ||
            (!DistributionReturnOfCapitalPolicy.isEligible(stock) &&
                (entry.returnOfCapitalAmount.toBits() != 0.0.toBits() ||
                    entry.excessReturnOfCapitalGainKrw != 0L)) ||
            entry.taxableIncomeAmount > entry.grossAmount ||
            entry.withholdingTax > entry.taxableIncomeAmount ||
            !amountsAreClose(
                entry.grossAmount,
                entry.taxableIncomeAmount + entry.returnOfCapitalAmount,
            ) ||
            !amountsAreClose(entry.netAmount, entry.grossAmount - entry.withholdingTax) ||
            stock.currency == Currency.KRW && entry.exchangeRateToKrw != 1.0 ||
            stock.currency == Currency.USD && entry.exchangeRateToKrw !in
            SimulatorRuntime.MIN_USD_KRW..SimulatorRuntime.MAX_USD_KRW
        ) {
            return "분배 원장의 canonical ID·통화·총액·세금·순액·환율 회계식이 유효하지 않습니다."
        }
        val breakdown = entry.taxBreakdown
            ?: return "분배 원장에는 지급일 세금 산출 내역이 필요합니다."
        val expectedBreakdown = runCatching {
            val taxClass = when {
                stock.market.isKorean && stock.isFundLike ->
                    DividendTaxClass.KOREAN_ETF_DISTRIBUTION
                stock.market.isKorean -> DividendTaxClass.KOREAN_ORDINARY_CASH
                else -> when (stock.instrumentType) {
                    InstrumentType.ETF -> DividendTaxClass.US_RIC_ETF_DISTRIBUTION
                    InstrumentType.CLOSED_END_FUND ->
                        DividendTaxClass.US_RIC_CLOSED_END_DISTRIBUTION
                    InstrumentType.ETN -> DividendTaxClass.US_ETN_CONTINGENT_COUPON
                    InstrumentType.REIT -> DividendTaxClass.US_REIT_DISTRIBUTION
                    InstrumentType.ADR -> DividendTaxClass.FOREIGN_ADR_DISTRIBUTION
                    InstrumentType.STOCK -> DividendTaxClass.US_ORDINARY_CORPORATION
                }
            }
            DividendTaxCalculator().calculate(
                DividendTaxRequest(
                    taxClass = taxClass,
                    grossAmount = breakdown.taxableBase,
                    paidOn = paidOn,
                    taxExchangeRateToKrw = entry.exchangeRateToKrw,
                    w8BenValid = true,
                ),
            ).breakdown
        }.getOrNull()
        val recordedTax = runCatching { breakdown.totalTax.amount }.getOrNull()
        if (expectedBreakdown == null || expectedBreakdown != breakdown ||
            breakdown.calculatedOn != paidOn ||
            breakdown.taxableBase.currency != stock.currency ||
            !amountsAreClose(breakdown.taxableBase.amount, entry.taxableIncomeAmount) ||
            recordedTax == null || !amountsAreClose(recordedTax, entry.withholdingTax)
        ) {
            return "분배 원장의 과세표준·세목·원천징수 산출 내역이 canonical 세무 규칙과 다릅니다."
        }
    }
    val accountingSequences = buildList {
        state.trades.mapTo(this) { it.accountingSequence }
        state.dividendLedger.mapTo(this) { it.accountingSequence }
        state.corporateActionLedger.mapTo(this) { it.accountingSequence }
        state.distributionEntitlementOrigins.mapTo(this) { it.accountingSequence }
        state.taxPaymentNotices.mapNotNullTo(this) { it.accountingSequence }
        state.foreignExchangeLedger.mapTo(this) { it.accountingSequence }
        state.cashAdjustmentLedger.mapTo(this) { it.accountingSequence }
    }
    if (accountingSequences.any { it <= 0L || it >= state.nextSequence } ||
        accountingSequences.distinct().size != accountingSequences.size
    ) {
        return "회계 원장 시퀀스가 양수가 아니거나 중복되었거나 다음 시퀀스보다 작지 않습니다."
    }
    if (state.activeEvents.map { it.id }.distinct().size != state.activeEvents.size) {
        return "활성 이벤트 ID가 중복되었습니다."
    }
    if (state.newsEvents.map { it.id }.distinct().size != state.newsEvents.size) {
        return "뉴스 이벤트 ID가 중복되었습니다."
    }
    val newsEventsById = state.newsEvents.associateBy(GameEvent::id)
    canonicalReadEventLedgerViolation(
        newsEvents = state.newsEvents,
        readEventIds = state.readEventIds,
        readStockNewsEventIds = state.readStockNewsEventIds,
        stocksById = stocksById,
    )?.let { violation -> return violation }
    if (state.newsEvents.any { it.startsAt > state.currentTime }) {
        return "아직 발표되지 않은 이벤트가 뉴스 원장에 포함되었습니다."
    }
    if (state.eventEngineSnapshot.activeEvents.map { it.id }.distinct().size !=
        state.eventEngineSnapshot.activeEvents.size
    ) {
        return "이벤트 엔진 활성 이벤트 ID가 중복되었습니다."
    }
    if (state.eventEngineSnapshot.activeEvents.any { it.generatorTemplateId == null }) {
        return "이벤트 엔진 활성 이벤트에 생성 템플릿 참조가 없습니다."
    }
    val expectedActiveEventsById = state.newsEvents
        .filter { event ->
            when {
                event.generatorTemplateId != null -> event.isActiveAt(state.currentTime)
                event.recordKind == EventRecordKind.SCHEDULED_RELEASE ->
                    state.currentTime >= event.effectStartsAt && state.currentTime < event.effectEndsAt
                else -> false
            }
        }
        .associateBy(GameEvent::id)
    val actualActiveEventsById = state.activeEvents.associateBy(GameEvent::id)
    val generatedActiveEventsById = state.eventEngineSnapshot.activeEvents.associateBy(GameEvent::id)
    val expectedGeneratedActiveEventsById = expectedActiveEventsById.values
        .filter { it.generatorTemplateId != null }
        .associateBy(GameEvent::id)
    if (actualActiveEventsById != expectedActiveEventsById ||
        generatedActiveEventsById != expectedGeneratedActiveEventsById ||
        state.activeEvents.any { event -> newsEventsById[event.id] != event }
    ) {
        return "뉴스 원장의 현재 영향 구간과 활성 이벤트·이벤트 엔진 상태가 동일하지 않습니다."
    }
    val generatedEvents = (state.newsEvents + state.eventEngineSnapshot.activeEvents)
        .distinctBy(GameEvent::id)
        .filter { it.generatorTemplateId != null }
    if (generatedEvents.any { event ->
            if (eventSchemaEngine.generatedEventInvariantViolation(event, state.stocks) != null) {
                true
            } else {
                val triggerKey = eventSchemaEngine.triggerKeyFor(event) ?: return@any true
                eventCooldowns[triggerKey]?.let { it >= event.startsAt.epochSeconds } != true
            }
        }
    ) {
        return "확률 뉴스의 생성 규칙·대상과 쿨다운 원장이 일치하지 않습니다."
    }
    if (catalog != null) {
        val canonicalStocksByScheduledEventId = canonicalStockSnapshotsAtScheduledReleases(state, catalog)
        if (state.newsEvents.any { event ->
                event.recordKind == EventRecordKind.SCHEDULED_RELEASE &&
                    !scheduledEventSchemaEngine.isCanonicalNewsEvent(
                        event,
                        canonicalStocksByScheduledEventId[event.id] ?: state.stocks,
                    )
            }
        ) {
            return "정기 발표 뉴스가 현재 일정·시드·종목 카탈로그에서 재생한 원본과 일치하지 않습니다."
        }
        validateCurrentCorporateReportLineage(
            state = state,
            scheduledEventEngine = scheduledEventSchemaEngine,
            canonicalStocksByScheduledEventId = canonicalStocksByScheduledEventId,
        )?.let { violation -> return violation }
    }
    validateCorporateActionNewsLineage(state, stocksById)?.let { violation ->
        return violation
    }
    listOf(
        "활성 이벤트" to state.activeEvents,
        "뉴스 이벤트" to state.newsEvents,
        "이벤트 엔진 활성 이벤트" to state.eventEngineSnapshot.activeEvents,
    ).forEach { (label, events) ->
        events.forEach { event ->
            validateGameEvent(event, stocksById, state.listingLifecycleLedger)?.let { error ->
                return "$label: $error"
            }
        }
    }
    val actionKeys = state.newsEvents.mapNotNull { event -> event.marketAction?.let { action ->
        Triple(action.kind, action.occurrenceId, action.transition)
    } }
    if (actionKeys.distinct().size != actionKeys.size) {
        return "시장조치 뉴스의 발생 ID와 전이 조합이 중복되었습니다."
    }
    val actionsByOccurrence = state.newsEvents.mapNotNull(GameEvent::marketAction)
        .groupBy { action -> action.kind to action.occurrenceId }
    actionsByOccurrence.forEach { (_, actions) ->
        actions.forEach { action ->
            val requiredPrecursors = when (action.kind to action.transition) {
                MarketActionKind.KRX_CIRCUIT_BREAKER to MarketActionTransition.REOPENED,
                MarketActionKind.US_MARKET_WIDE_CIRCUIT_BREAKER to MarketActionTransition.REOPENED,
                MarketActionKind.US_LIMIT_UP_LIMIT_DOWN to MarketActionTransition.REOPENED,
                -> setOf(MarketActionTransition.HALT_STARTED)
                MarketActionKind.KRX_SIDECAR to MarketActionTransition.RELEASED ->
                    setOf(MarketActionTransition.PROGRAM_FLOW_SUSPENDED)
                MarketActionKind.INSTRUMENT_TRADING_HALT to MarketActionTransition.RELEASED ->
                    setOf(MarketActionTransition.HALT_SCHEDULED, MarketActionTransition.HALT_STARTED)
                MarketActionKind.INVESTMENT_ALERT to MarketActionTransition.RELEASE_ANNOUNCED ->
                    setOf(MarketActionTransition.DESIGNATED)
                else -> emptySet()
            }
            if (requiredPrecursors.isNotEmpty() && actions.none { precursor ->
                    precursor.transition in requiredPrecursors &&
                        precursor.announcedAt <= action.announcedAt &&
                        precursor.effectiveAt <= action.effectiveAt
                }
            ) {
                return "시장조치 ${action.kind}의 ${action.transition} 전이에 같은 발생 ID의 선행 조치가 없습니다."
            }
        }
    }

    val listings = state.listingLifecycleStates
    if (listings.keys != stockIds) {
        return "모든 종목의 현재 상장 생명주기 상태가 필요합니다."
    }
    if (listings.any { (stockId, listing) -> stockId != listing.stockId }) {
        return "상장 생명주기 맵 키와 상태 종목 ID가 일치하지 않습니다."
    }
    if (listings.any { (stockId, listing) ->
            val stock = stocksById[stockId]
            stock == null || stock.market != listing.market || stock.instrumentType != listing.instrumentType
        }
    ) {
        return "상장 생명주기 상태가 종목 시장·상품 유형과 일치하지 않습니다."
    }
    listings.forEach { (stockId, listing) ->
        listing.finalDisposition?.semanticInvariantViolation()?.let { violation ->
            return "${stockId}의 최종 잔고 처분이 유효하지 않습니다: $violation"
        }
    }
    if (listings.values.any { listing ->
            listing.lastEvaluatedTradingDate?.let { it > state.currentDate } == true
        }
    ) {
        return "상장 생명주기 최종 평가일이 현재 게임 날짜보다 미래입니다."
    }
    if (listings.values.any { listing ->
            listing.status in TERMINAL_LISTING_STATUSES && listing.finalDisposition == null
        }
    ) {
        return "최종 상장 상태에 잔고 처분 방식이 없습니다."
    }
    if (state.holdings.any { (stockId, holding) ->
            val contractualUnitValue = listings[stockId]
                ?.takeIf { listing -> listing.status == ListingLifecycleStatus.LIQUIDATION_PENDING }
                ?.finalDisposition
                ?.takeIf { disposition ->
                    disposition.type == ListingFinalDispositionType.CASH_LIQUIDATION
                }
                ?.cashPerUnit
            val canonicalCurrentPrice = contractualUnitValue ?: state.quotes.getValue(stockId).price
            holding.currentPrice.toBits() != canonicalCurrentPrice.toBits()
        }
    ) {
        return "보유 종목의 현재가는 canonical 호가·계약상 청산 단가와 일치해야 합니다."
    }
    if (listings.any { (stockId, listing) ->
            val disposition = listing.finalDisposition ?: return@any false
            val stock = stocksById.getValue(stockId)
            val entitlementMissing = disposition.entitledQuantity == null ||
                disposition.entitledCostBasis == null
            when {
                disposition.effectiveOn > state.currentDate -> true
                listing.settlementDueOn != disposition.settlementDueOn -> true
                listing.status == ListingLifecycleStatus.LIQUIDATION_PENDING -> {
                    val holding = state.holdings[stockId]
                    val entitlementDoesNotMatchHolding = holding?.let {
                        it.currency != stock.currency ||
                            !it.quantity.isFinite() || it.quantity <= 0.0 ||
                            !it.averagePrice.isFinite() || it.averagePrice < 0.0 ||
                            disposition.entitledQuantity != it.quantity ||
                            disposition.entitledCostBasis != it.costBasis
                    } ?: (disposition.entitledQuantity != 0.0 || disposition.entitledCostBasis != 0.0)
                    disposition.type != ListingFinalDispositionType.CASH_LIQUIDATION ||
                        entitlementMissing ||
                        entitlementDoesNotMatchHolding
                }
                listing.status == ListingLifecycleStatus.DELISTED ->
                    disposition.type == ListingFinalDispositionType.CASH_LIQUIDATION ||
                        stock.instrumentType == InstrumentType.ETN
                listing.status == ListingLifecycleStatus.TERMINATED -> when (disposition.type) {
                    ListingFinalDispositionType.CASH_LIQUIDATION ->
                        entitlementMissing ||
                            requireNotNull(disposition.settlementDueOn) > state.currentDate
                    else -> stock.instrumentType != InstrumentType.ETN
                }
                else -> true
            }
        }
    ) {
        return "최종 잔고 처분의 상태·효력일·지급일·확정 권리·결제 통화가 종목과 일치하지 않습니다."
    }
    if (listings.any { (stockId, listing) ->
            val occurrenceId = listing.controllingTerminationOccurrenceId
            val priority = listing.controllingTerminationNoticePriority
            val rawEffectiveOn = listing.controllingTerminationRawEffectiveOn
            val orderlyStage = listing.activeReason.isOrderlyTerminationReason() &&
                listing.status.isOrderlyTerminationStatus()
            occurrenceId?.isBlank() == true || priority?.let { it < 0 } == true ||
                (occurrenceId != null) != orderlyStage || (priority != null) != orderlyStage ||
                (rawEffectiveOn != null) != orderlyStage ||
                !listing.orderlyTerminationReasonMatches(stocksById.getValue(stockId).instrumentType) ||
                occurrenceId != null && !state.newsEvents.hasExactTerminationNotice(
                    occurrenceId = occurrenceId,
                    stock = stocksById.getValue(stockId),
                    priority = priority,
                    rawEffectiveOn = rawEffectiveOn,
                )
        }
    ) {
        return "상품 종료 상태의 사유·단계·상품 유형·지배 공시 ID가 일치하지 않습니다."
    }

    val lifecycleLedger = state.listingLifecycleLedger
    if (lifecycleLedger.any { it.stockId !in stockIds }) {
        return "상장 생명주기 원장에 알 수 없는 종목 ID가 있습니다."
    }
    if (lifecycleLedger.any { it.reason == null }) {
        return "상장 생명주기 원장의 모든 전이에는 직접 원인이 필요합니다."
    }
    if (lifecycleLedger.any { it.sequence <= 0L }) return "상장 생명주기 원장 시퀀스가 양수가 아닙니다."
    if (lifecycleLedger.map { it.id }.distinct().size != lifecycleLedger.size) {
        return "상장 생명주기 원장 이벤트 ID가 중복되었습니다."
    }
    lifecycleLedger.forEach { ledgerEvent ->
        ledgerEvent.disposition?.semanticInvariantViolation()?.let { violation ->
            return "${ledgerEvent.id}의 최종 잔고 처분이 유효하지 않습니다: $violation"
        }
    }
    if (lifecycleLedger.any { ledgerEvent ->
            val disposition = ledgerEvent.disposition
            when (ledgerEvent.kind) {
                ListingLifecycleEventKind.LIQUIDATION_STARTED ->
                    disposition == null ||
                        disposition.type != ListingFinalDispositionType.CASH_LIQUIDATION ||
                        disposition.entitledQuantity == null || disposition.entitledCostBasis == null ||
                        disposition.effectiveOn != ledgerEvent.tradingDate ||
                        disposition.settlementDueOn != ledgerEvent.deadline ||
                        ledgerEvent.toStatus != ListingLifecycleStatus.LIQUIDATION_PENDING

                ListingLifecycleEventKind.DELISTED ->
                    disposition == null ||
                        disposition.type == ListingFinalDispositionType.CASH_LIQUIDATION ||
                        disposition.effectiveOn != ledgerEvent.tradingDate ||
                        ledgerEvent.deadline != null ||
                        ledgerEvent.toStatus != ListingLifecycleStatus.DELISTED

                ListingLifecycleEventKind.TERMINATED -> when {
                    disposition == null || ledgerEvent.deadline != null ||
                        ledgerEvent.toStatus != ListingLifecycleStatus.TERMINATED -> true
                    disposition.type == ListingFinalDispositionType.CASH_LIQUIDATION ->
                        disposition.entitledQuantity == null || disposition.entitledCostBasis == null ||
                            requireNotNull(disposition.settlementDueOn) > ledgerEvent.tradingDate
                    else -> disposition.effectiveOn != ledgerEvent.tradingDate ||
                        stocksById.getValue(ledgerEvent.stockId).instrumentType != InstrumentType.ETN
                }

                else -> disposition != null
            }
        }
    ) {
        return "최종 잔고 처분이 상장 원장의 전이·효력일·지급일·확정 권리와 일치하지 않습니다."
    }
    if (lifecycleLedger.any { ledgerEvent ->
            val occurrenceId = ledgerEvent.controllingTerminationOccurrenceId
            val priority = ledgerEvent.controllingTerminationNoticePriority
            val rawEffectiveOn = ledgerEvent.controllingTerminationRawEffectiveOn
            val orderlyStage = ledgerEvent.reason.isOrderlyTerminationReason() &&
                ledgerEvent.toStatus.isOrderlyTerminationStatus()
            val instrumentType = stocksById.getValue(ledgerEvent.stockId).instrumentType
            occurrenceId?.isBlank() == true || priority?.let { it < 0 } == true ||
                (occurrenceId != null) != orderlyStage || (priority != null) != orderlyStage ||
                (rawEffectiveOn != null) != orderlyStage ||
                !ledgerEvent.reason.orderlyTerminationReasonMatches(instrumentType) ||
                occurrenceId != null && !state.newsEvents.hasExactTerminationNotice(
                    occurrenceId = occurrenceId,
                    stock = stocksById.getValue(ledgerEvent.stockId),
                    priority = priority,
                    rawEffectiveOn = rawEffectiveOn,
                )
        }
    ) {
        return "상품 종료 원장의 사유·단계·상품 유형·지배 공시 ID가 일치하지 않습니다."
    }
    if (lifecycleLedger.any { ledgerEvent ->
            val occurrenceId = ledgerEvent.controllingTerminationOccurrenceId ?: return@any false
            val stock = stocksById.getValue(ledgerEvent.stockId)
            val notice = state.newsEvents.exactTerminationNotice(occurrenceId, stock) ?: return@any true
            val profile = ListingLifecyclePolicyCatalog[state.listingLifecycleStates.getValue(stock.id).profileId]
            when (ledgerEvent.kind) {
                ListingLifecycleEventKind.DELISTING_SCHEDULED ->
                    ledgerEvent.deadline != notice.scheduledTerminationOn(stock, ledgerEvent.tradingDate) ||
                        ledgerEvent.disposition != null
                ListingLifecycleEventKind.LIQUIDATION_STARTED -> {
                    val disposition = ledgerEvent.disposition
                    val expectedSettlementOn = ledgerEvent.tradingDate.plus(
                        profile.liquidationSettlementCalendarDays,
                        DateTimeUnit.DAY,
                    )
                    disposition?.type != ListingFinalDispositionType.CASH_LIQUIDATION ||
                        disposition.effectiveOn != ledgerEvent.tradingDate ||
                        disposition.settlementDueOn != expectedSettlementOn ||
                        ledgerEvent.deadline != expectedSettlementOn
                }
                ListingLifecycleEventKind.TERMINATED -> {
                    val disposition = ledgerEvent.disposition
                    disposition?.type != ListingFinalDispositionType.CASH_LIQUIDATION ||
                        disposition.settlementDueOn?.let { ledgerEvent.tradingDate < it } != false
                }
                else -> false
            }
        }
    ) {
        return "상품 종료 원장의 예정일·평가 효력일·지급일이 지배 공시와 현재 정책에서 파생되지 않았습니다."
    }
    val ledgerByStock = lifecycleLedger.groupBy { it.stockId }
    if (ledgerByStock.values.any { events ->
            events.withIndex().any { (index, event) -> event.sequence != index + 1L } ||
                events.any { it.tradingDate > state.currentDate } ||
                events.zipWithNext().any { (previous, next) ->
                    previous.tradingDate > next.tradingDate ||
                        previous.toStatus != next.fromStatus ||
                        previous.tradingDate == next.tradingDate && !(
                        previous.kind == ListingLifecycleEventKind.DELISTING_SCHEDULED &&
                            next.kind == ListingLifecycleEventKind.LIQUIDATION_STARTED &&
                            previous.controllingTerminationOccurrenceId ==
                            next.controllingTerminationOccurrenceId
                        )
                } ||
                events.firstOrNull()?.fromStatus?.let { it != ListingLifecycleStatus.LISTED } == true ||
                events.any { event -> event.toStatus != event.kind.expectedListingStatus() }
        }
    ) {
        return "상장 생명주기 원장의 연속 시퀀스·거래일·상태 전이가 엔진 규칙과 일치하지 않습니다."
    }
    for ((stockId, events) in ledgerByStock) {
        val stock = stocksById.getValue(stockId)
        var incumbentOccurrenceId: String? = null
        for (event in events) {
            val orderlyStage = event.reason.isOrderlyTerminationReason() &&
                event.toStatus.isOrderlyTerminationStatus()
            if (event.kind in setOf(
                    ListingLifecycleEventKind.DELISTING_SCHEDULED,
                    ListingLifecycleEventKind.LIQUIDATION_STARTED,
                ) && orderlyStage
            ) {
                val decision = runCatching {
                    resolveInstrumentTerminationAtSessionClose(
                        stock = stock,
                        events = state.newsEvents,
                        evaluatedOn = event.tradingDate,
                        incumbentOccurrenceId = incumbentOccurrenceId,
                    )
                }.getOrNull()
                val controllerMismatch = decision?.let { expected ->
                    event.controllingTerminationOccurrenceId != expected.notice.event.id ||
                        event.controllingTerminationNoticePriority != expected.notice.terms.kind.noticePriority ||
                        event.controllingTerminationRawEffectiveOn != expected.rawEffectiveOn
                } != false
                val liquidationBreaksFrozenLineage =
                    event.kind == ListingLifecycleEventKind.LIQUIDATION_STARTED &&
                        event.controllingTerminationOccurrenceId != incumbentOccurrenceId
                val scheduleDateMismatch = event.kind == ListingLifecycleEventKind.DELISTING_SCHEDULED &&
                    event.deadline != decision?.scheduledTerminationOn
                if (controllerMismatch ||
                    liquidationBreaksFrozenLineage || scheduleDateMismatch
                ) {
                    return "${event.id}의 상품 종료 지배 공시와 예정일이 해당 거래일 종가의 canonical 결정과 일치하지 않습니다."
                }
            } else if (orderlyStage &&
                event.controllingTerminationOccurrenceId != incumbentOccurrenceId
            ) {
                return "${event.id}의 상품 종료 계보가 직전 확정 공시와 연속되지 않습니다."
            }
            incumbentOccurrenceId = event.controllingTerminationOccurrenceId
        }
    }
    for ((stockId, listing) in listings) {
        val disposition = listing.finalDisposition
            ?.takeIf { listing.status == ListingLifecycleStatus.TERMINATED }
            ?.takeIf { it.type == ListingFinalDispositionType.CASH_LIQUIDATION }
            ?: continue
        val quantity = requireNotNull(disposition.entitledQuantity)
        val terminalLedgerEvent = ledgerByStock[stockId]
            ?.lastOrNull()
            ?.takeIf { it.kind == ListingLifecycleEventKind.TERMINATED }
            ?: return "${stockId}의 계약상 현금청산에 상품 종료 원장이 없습니다."
        if (stockId in state.holdings || state.fifoCostBasisBook.lots.any { it.stockId == stockId }) {
            return "${stockId}의 계약상 현금청산 뒤에도 보유 잔고 또는 FIFO 권리가 남아 있습니다."
        }
        val settlementTrades = state.trades.filter { trade ->
            trade.stockId == stockId &&
                trade.side == OrderSide.SELL &&
                trade.settlementKind == TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT
        }
        val settlementTradeMatches = settlementTrades.singleOrNull()?.let { trade ->
            quantity > 0.0 &&
                trade.quantity == quantity &&
                trade.price == disposition.cashPerUnit &&
                trade.currency == stocksById.getValue(stockId).currency &&
                trade.settlementDateOverride == terminalLedgerEvent.tradingDate
        } ?: (quantity == 0.0 && settlementTrades.isEmpty())
        if (!settlementTradeMatches) {
            return "${stockId}의 계약상 현금청산 체결이 확정 권리·단가·통화·실제 지급일과 일치하지 않습니다."
        }
    }
    if (ledgerByStock.values.any { events ->
            events.zipWithNext().any { (previous, next) ->
                val previousId = previous.controllingTerminationOccurrenceId
                val nextId = next.controllingTerminationOccurrenceId
                when {
                    previousId == null || nextId == null -> false
                    previousId == nextId ->
                        previous.controllingTerminationNoticePriority !=
                            next.controllingTerminationNoticePriority ||
                            previous.controllingTerminationRawEffectiveOn !=
                            next.controllingTerminationRawEffectiveOn
                    else -> {
                        val previousRaw = requireNotNull(previous.controllingTerminationRawEffectiveOn)
                        val nextRaw = requireNotNull(next.controllingTerminationRawEffectiveOn)
                        val preempts = nextRaw < previousRaw ||
                            nextRaw == previousRaw &&
                            requireNotNull(next.controllingTerminationNoticePriority) <
                            requireNotNull(previous.controllingTerminationNoticePriority)
                        next.kind != ListingLifecycleEventKind.DELISTING_SCHEDULED ||
                            !preempts
                    }
                }
            }
        }
    ) {
        return "상품 종료 지배 공시는 더 이른 효력일 또는 같은 날의 높은 계약 우선순위로만 교체할 수 있습니다."
    }
    if (ledgerByStock.values.any { events ->
            events.groupBy(ListingLifecycleLedgerEvent::controllingTerminationOccurrenceId)
                .filterKeys { it != null }
                .values
                .any { lineage ->
                    val dispositions = lineage.mapNotNull(ListingLifecycleLedgerEvent::disposition)
                    dispositions.isNotEmpty() && dispositions.distinct().size != 1
                }
        }
    ) {
        return "같은 상품 종료 공시의 확정 청산 조건이 원장 단계 사이에서 달라졌습니다."
    }
    if (listings.any { (stockId, listing) ->
            val lastLedgerEvent = ledgerByStock[stockId]?.lastOrNull()
            val expectedDeadline = when (listing.status) {
                ListingLifecycleStatus.DEFICIENCY_NOTICE -> listing.cureDeadline
                ListingLifecycleStatus.UNDER_REVIEW,
                ListingLifecycleStatus.TRADING_SUSPENDED,
                -> listing.reviewDeadline
                ListingLifecycleStatus.DELISTING_SCHEDULED -> listing.scheduledDelistingOn
                ListingLifecycleStatus.LIQUIDATION_PENDING -> listing.settlementDueOn
                else -> null
            }
            val activeReasonMismatch = if (listing.status == ListingLifecycleStatus.LISTED) {
                listing.activeReason != null
            } else {
                listing.activeReason == null || lastLedgerEvent?.reason != listing.activeReason
            }
            listing.ledgerSequence != (lastLedgerEvent?.sequence ?: 0L) ||
                activeReasonMismatch ||
                lastLedgerEvent != null && (
                    listing.status != lastLedgerEvent.toStatus ||
                        listing.finalDisposition != lastLedgerEvent.disposition ||
                        expectedDeadline != lastLedgerEvent.deadline ||
                        listing.controllingTerminationOccurrenceId !=
                        lastLedgerEvent.controllingTerminationOccurrenceId ||
                        listing.controllingTerminationNoticePriority !=
                        lastLedgerEvent.controllingTerminationNoticePriority ||
                        listing.controllingTerminationRawEffectiveOn !=
                        lastLedgerEvent.controllingTerminationRawEffectiveOn
                    )
        }
    ) {
        return "상장 생명주기 상태와 원장의 마지막 시퀀스·상태·원인이 일치하지 않습니다."
    }

    val protection = state.tradingProtectionSnapshot
    val krxMarkets = Market.entries.filter(Market::isKorean).toSet()
    val krxStockIds = stocksById.filter { (stockId, stock) ->
        stock.market.isKorean && listings.getValue(stockId).isIndexEligible
    }.keys
    val usStockIds = stocksById.filter { (stockId, stock) ->
        stock.market.isUnitedStates && listings.getValue(stockId).isIndexEligible
    }.keys
    if (protection.krxCircuitBreakers.keys != krxMarkets ||
        protection.krxCircuitBreakers.any { (market, protectionState) ->
            market != protectionState.market
        }
    ) {
        return "KRX 서킷브레이커에 필수 시장 상태가 없거나 맵 키가 일치하지 않습니다."
    }
    if (protection.krxSidecars.keys != krxMarkets ||
        protection.krxSidecars.any { (market, protectionState) ->
            market != protectionState.market
        }
    ) {
        return "KRX 사이드카에 필수 시장 상태가 없거나 맵 키가 일치하지 않습니다."
    }
    if (protection.krxVolatilityInterruptions.keys != krxStockIds ||
        protection.krxVolatilityInterruptions.any { (stockId, protectionState) ->
            val stock = stocksById[stockId]
            stockId != protectionState.stockId || stock == null || stock.market != protectionState.market
        }
    ) {
        return "KRX VI에 필수 종목 상태가 없거나 맵 키·종목·시장이 일치하지 않습니다."
    }
    if (protection.instrumentTradingHalts.any { (stockId, protectionState) ->
            stockId != protectionState.stockId || stockId !in stockIds ||
                !listings.getValue(stockId).isIndexEligible ||
                invalidInstrumentTradingHalt(protectionState)
        }
    ) {
        return "종목 거래정지의 발생 ID·맵 키·종목 ID가 유효하지 않습니다."
    }
    if (protection.scheduledInstrumentTradingHalts.any { (scheduleId, protectionState) ->
            scheduleId.isBlank() || scheduleId != protectionState.occurrenceId ||
                protectionState.stockId !in stockIds ||
                !listings.getValue(protectionState.stockId).isIndexEligible ||
                protectionState.scheduledReleaseAt == null ||
                protectionState.status != TradingHaltStatus.ACTIVE ||
                invalidInstrumentTradingHalt(protectionState)
        }
    ) {
        return "예정 종목 거래정지의 발생 ID·종목·해제 시각이 올바르지 않습니다."
    }
    val haltOccurrenceIds = protection.instrumentTradingHalts.values.map { it.occurrenceId } +
        protection.scheduledInstrumentTradingHalts.values.map { it.occurrenceId }
    if (haltOccurrenceIds.distinct().size != haltOccurrenceIds.size) {
        return "현재·예정 종목 거래정지의 발생 ID가 중복되었습니다."
    }
    if (protection.investmentAlerts.any { (stockId, protectionState) ->
            stockId != protectionState.stockId || stocksById[stockId]?.market?.isKorean != true ||
                !listings.getValue(stockId).isIndexEligible
        }
    ) {
        return "투자경보 맵 키와 KRX 종목 ID가 일치하지 않습니다."
    }
    if (protection.usLuldStates.keys != usStockIds ||
        protection.usLuldStates.any { (stockId, protectionState) ->
            val stock = stocksById[stockId]
            stockId != protectionState.stockId || stock == null || stock.market != protectionState.primaryMarket
        }
    ) {
        return "미국 LULD에 적격 종목 상태가 없거나 맵 키·종목·주 상장시장이 일치하지 않습니다."
    }
    val mwcb = protection.usMarketWideCircuitBreaker
        ?: return "미국 MWCB 상태가 없습니다."
    val requiredVenues = Market.entries.filter(Market::isUnitedStates).toSet()
    if (mwcb.venueStatuses.keys != requiredVenues) {
        return "미국 MWCB 상태에 필수 주 상장시장이 모두 포함되지 않았습니다."
    }
    if (mwcb.venueStatuses.any { (market, venue) ->
            market != venue.market || !market.isUnitedStates
        }
    ) {
        return "미국 MWCB 거래소 맵 키와 내부 시장이 일치하지 않습니다."
    }
    val marketUi = runCatching {
        projectSimulatorMarketUi(
            campaignSeed = state.options.seed,
            currentTime = state.currentTime,
            selectedStockId = state.selectedStockId,
            stocksById = stocksById,
            quotes = state.quotes,
            listingLifecycleStates = listings,
            protection = protection,
            macro = state.macro,
            activeEvents = state.activeEvents,
        )
    }.getOrNull() ?: return "시장 세션·선택 호가창의 canonical UI projection을 계산할 수 없습니다."
    if (state.marketSessions != marketUi.marketSessions ||
        state.selectedOrderBook != marketUi.selectedOrderBook ||
        state.quotes != marketUi.quotes
    ) {
        return "시장 세션·종목 호가·선택 호가창이 달력·상장·보호·이벤트 정본과 다릅니다."
    }

    fun regularSessionCloseTime(market: Market, tradingDate: LocalDate): LocalTime? =
        GameCalendar.regularSessionWindow(market, tradingDate)?.let { session ->
            GameCalendar.marketLocalDateTime(market, session.closesAt).time
        }

    val mwcbTimingInvalid = if (mwcb.phase == UsMwcbPhase.NORMAL) {
        false
    } else {
        val level = mwcb.activeLevel
        val triggeredAt = mwcb.triggeredAt
        val session = GameCalendar.regularSessionWindow(Market.NYSE, mwcb.tradingDate)
        if (level == null || triggeredAt == null || session == null) {
            true
        } else {
            val triggeredLocal = GameCalendar.marketLocalDateTime(Market.NYSE, triggeredAt)
            val closeTime = GameCalendar.marketLocalDateTime(Market.NYSE, session.closesAt).time
            triggeredLocal.date != mwcb.tradingDate ||
                triggeredAt !in session.opensAt..<session.closesAt ||
                level in setOf(UsMwcbLevel.LEVEL_1, UsMwcbLevel.LEVEL_2) &&
                triggeredLocal.time >= TradingProtectionRules.usMwcbLevel12Cutoff(closeTime)
        }
    }
    if (mwcbTimingInvalid) {
        return "미국 MWCB 발동 시각이 해당 거래일 정규장·마감 전 경계와 일치하지 않습니다."
    }

    val usLuldCanonicalStateInvalid = protection.usLuldStates.values.any { current ->
        val currentLocal = GameCalendar.marketLocalDateTime(current.primaryMarket, state.currentTime)
        if (currentLocal.date != current.tradingDate) return@any true
        val session = GameCalendar.regularSessionWindow(current.primaryMarket, current.tradingDate)
        val closeTime = regularSessionCloseTime(current.primaryMarket, current.tradingDate)
        val canonicalBands = runCatching {
            TradingProtectionEngine.calculateUsLuldBands(
                tier = current.tier,
                previousClose = current.previousClose,
                referencePrice = current.referencePrice,
                easternTime = currentLocal.time,
                regularSessionClose = closeTime,
            )
        }.getOrNull() ?: return@any true
        val active = current.phase !in setOf(UsLuldPhase.NORMAL, UsLuldPhase.CLOSED_FOR_DAY)
        current.bands != canonicalBands ||
            active && (session == null || state.currentTime !in session.opensAt..<session.closesAt) ||
            current.phase == UsLuldPhase.CLOSED_FOR_DAY &&
            (session == null || state.currentTime < session.closesAt)
    }
    if (usLuldCanonicalStateInvalid) {
        return "미국 LULD 상태가 해당 거래일 정규장 마감·가격밴드 정본과 일치하지 않습니다."
    }

    val marketActions = state.newsEvents.mapNotNull(GameEvent::marketAction)
    fun hasMarketAction(
        kind: MarketActionKind,
        occurrenceId: String,
        predicate: (MarketActionReference) -> Boolean,
    ): Boolean = marketActions.any { action ->
        action.kind == kind && action.occurrenceId == occurrenceId && predicate(action)
    }

    val krxCircuitBreakerLineageInvalid = protection.krxCircuitBreakers.values.any { current ->
        if (current.phase == KrxCircuitBreakerPhase.NORMAL) return@any false
        val level = current.activeLevel ?: return@any true
        val triggeredAt = current.triggeredAt ?: return@any true
        val occurrenceId = krxCircuitBreakerOccurrenceId(current.market, level, triggeredAt)
        !hasMarketAction(MarketActionKind.KRX_CIRCUIT_BREAKER, occurrenceId) { action ->
            action.effectiveAt == triggeredAt && action.markets == setOf(current.market) &&
                action.stage == level.ordinal + 1 && action.transition in setOf(
                MarketActionTransition.HALT_STARTED,
                MarketActionTransition.MARKET_CLOSED_FOR_DAY,
            )
        }
    }
    val krxSidecarLineageInvalid = protection.krxSidecars.values.any { current ->
        if (current.phase != KrxSidecarPhase.PROGRAM_FLOW_SUSPENDED) return@any false
        val triggeredAt = current.triggeredAt ?: return@any true
        val occurrenceId = krxSidecarOccurrenceId(current.market, triggeredAt)
        !hasMarketAction(MarketActionKind.KRX_SIDECAR, occurrenceId) { action ->
            action.transition == MarketActionTransition.PROGRAM_FLOW_SUSPENDED &&
                action.effectiveAt == triggeredAt && action.markets == setOf(current.market)
        }
    }
    val krxViLineageInvalid = protection.krxVolatilityInterruptions.values.any { current ->
        if (current.phase != KrxViPhase.CALL_AUCTION) return@any false
        val triggeredAt = current.triggeredAt ?: return@any true
        val occurrenceId = krxViOccurrenceId(current.stockId, current.triggerCount, triggeredAt)
        !hasMarketAction(MarketActionKind.KRX_VOLATILITY_INTERRUPTION, occurrenceId) { action ->
            action.transition == MarketActionTransition.CALL_AUCTION_STARTED &&
                action.effectiveAt == triggeredAt && action.stockId == current.stockId &&
                action.markets == setOf(current.market) && action.triggerSequence == current.triggerCount
        }
    }
    val usMwcbLineageInvalid = if (mwcb.phase == UsMwcbPhase.NORMAL) {
        false
    } else {
        val level = mwcb.activeLevel
        val triggeredAt = mwcb.triggeredAt
        if (level == null || triggeredAt == null) {
            true
        } else {
            val occurrenceId = usMwcbOccurrenceId(level, triggeredAt)
            !hasMarketAction(MarketActionKind.US_MARKET_WIDE_CIRCUIT_BREAKER, occurrenceId) { action ->
                action.effectiveAt == triggeredAt && action.stage == level.ordinal + 1 &&
                    action.transition in setOf(
                    MarketActionTransition.HALT_STARTED,
                    MarketActionTransition.MARKET_CLOSED_FOR_DAY,
                )
            }
        }
    }
    val usLuldLineageInvalid = protection.usLuldStates.values.any { current ->
        if (current.phase !in setOf(
                UsLuldPhase.TRADING_PAUSE,
                UsLuldPhase.REOPENING_AUCTION,
                UsLuldPhase.CLOSING_AUCTION_ONLY,
            )
        ) {
            return@any false
        }
        val pauseStartedAt = current.pauseStartedAt ?: return@any true
        val occurrenceId = usLuldOccurrenceId(current.stockId, pauseStartedAt)
        !hasMarketAction(MarketActionKind.US_LIMIT_UP_LIMIT_DOWN, occurrenceId) { action ->
            if (action.stockId != current.stockId || action.markets != setOf(current.primaryMarket)) {
                false
            } else if (current.phase == UsLuldPhase.CLOSING_AUCTION_ONLY) {
                val session = GameCalendar.regularSessionWindow(current.primaryMarket, current.tradingDate)
                    ?: return@hasMarketAction false
                val closeTime = GameCalendar.marketLocalDateTime(current.primaryMarket, session.closesAt).time
                val actionLocal = GameCalendar.marketLocalDateTime(current.primaryMarket, action.effectiveAt)
                action.transition == MarketActionTransition.CLOSING_AUCTION_STARTED &&
                    action.endsAt == session.closesAt && actionLocal.date == current.tradingDate &&
                    actionLocal.time >= TradingProtectionRules.usLuldCloseOnlyFrom(closeTime) &&
                    action.effectiveAt < session.closesAt
            } else {
                action.transition == MarketActionTransition.HALT_STARTED
            }
        }
    }
    val instrumentHaltLineageInvalid = (
        protection.instrumentTradingHalts.values + protection.scheduledInstrumentTradingHalts.values
        ).any { halt ->
        if (halt.status != TradingHaltStatus.ACTIVE) return@any false
        if (halt.reason in setOf(
                TradingHaltReason.LISTING_MAINTENANCE_REVIEW,
                TradingHaltReason.DELISTING_PROCESS,
            )
        ) {
            return@any listings[halt.stockId]?.isTradable != false
        }
        !hasMarketAction(MarketActionKind.INSTRUMENT_TRADING_HALT, halt.occurrenceId) { action ->
            action.stockId == halt.stockId && action.effectiveAt == halt.startedAt &&
                action.transition in setOf(
                MarketActionTransition.HALT_SCHEDULED,
                MarketActionTransition.HALT_STARTED,
            )
        }
    }
    val investmentAlertLineageInvalid = protection.investmentAlerts.values.any { designation ->
        if (designation.status != InvestmentAlertStatus.ACTIVE) return@any false
        val occurrenceId = investmentAlertOccurrenceId(designation.stockId, designation.designatedAt)
        !hasMarketAction(MarketActionKind.INVESTMENT_ALERT, occurrenceId) { action ->
            action.transition == MarketActionTransition.DESIGNATED &&
                action.stockId == designation.stockId && action.alertLevel == designation.level &&
                action.effectiveOn == designation.designatedOn
        }
    }
    if (krxCircuitBreakerLineageInvalid || krxSidecarLineageInvalid || krxViLineageInvalid ||
        usMwcbLineageInvalid || usLuldLineageInvalid || instrumentHaltLineageInvalid ||
        investmentAlertLineageInvalid
    ) {
        return "현재 거래 보호상태와 시장조치 뉴스의 발생 계보가 일치하지 않습니다."
    }

    val listingEventsByStockId = state.listingLifecycleLedger.groupBy { event -> event.stockId }
    val debugPriceFactsAllowed = state.options.activeMods.any { mod ->
        DebugMod.isCompatible(mod.id, mod.version)
    }
    val oneDayChartBarsByStockId = state.chartPriceHistory.mapValues { (_, histories) ->
        histories.getValue(PriceBarInterval.ONE_DAY)
    }
    val canonicalRetainedDailyBarsByStockId = stocksById.mapValues { (stockId, stock) ->
        CanonicalDailyPriceBarProjection.aggregateRetainedHourly(
            stockId = stock.id,
            market = stock.market,
            hourlyBars = state.priceHistory.getValue(stockId),
            dropPotentiallyTruncatedFirstDate = false,
        )
    }
    val canonicalDailyBarsByStockId = canonicalRetainedDailyBarsByStockId.mapValues {
            (stockId, bars) ->
        val stock = stocksById.getValue(stockId)
        bars.filter { bar ->
            val date = GameCalendar.marketLocalDateTime(stock.market, bar.startTime).date
            GameCalendar.regularSessionWindow(stock.market, date)?.closesAt?.let { closeAt ->
                closeAt in GameCalendar.startInstant..state.currentTime
            } == true
        }
    }
    val incompleteDailyCoverage = stocksById.any { (stockId, stock) ->
        val canonicalRetainedBars = canonicalRetainedDailyBarsByStockId.getValue(stockId)
        val canonicalCompletedBars = canonicalDailyBarsByStockId.getValue(stockId)
        val completedDates = canonicalCompletedBars.map { bar ->
            GameCalendar.marketLocalDateTime(stock.market, bar.startTime).date
        }
        val incompleteDates = (canonicalRetainedBars - canonicalCompletedBars.toSet()).map { bar ->
            GameCalendar.marketLocalDateTime(stock.market, bar.startTime).date
        }
        val expectedDecisionDates = expectedRecentCompletedSessionDates(
            stock = stock,
            listingEvents = listingEventsByStockId[stockId].orEmpty(),
            currentTime = state.currentTime,
            count = REQUIRED_SURVEILLANCE_DECISION_POINTS,
        )
        val expectedChronologicalCompletedDates = expectedRecentCompletedSessionDates(
            stock = stock,
            listingEvents = listingEventsByStockId[stockId].orEmpty(),
            currentTime = state.currentTime,
            count = (
                CanonicalPriceHistoryRetention.CHRONOLOGICAL_ONE_DAY_TAIL -
                    incompleteDates.size
                ).coerceAtLeast(0),
        )
        val protectedPositiveDates = canonicalCompletedBars
            .filter { bar -> bar.volume > 0L }
            .takeLast(CanonicalPriceHistoryRetention.POSITIVE_DECISION_DAYS)
            .asSequence()
            .map { bar -> GameCalendar.marketLocalDateTime(stock.market, bar.startTime).date }
            .toSet()
        val expectedSavedDates = (
            expectedChronologicalCompletedDates + incompleteDates + protectedPositiveDates
            ).distinct().sorted()
        val savedBars = oneDayChartBarsByStockId.getValue(stockId)
        val savedDates = savedBars.map { bar ->
            GameCalendar.marketLocalDateTime(stock.market, bar.startTime).date
        }
        val savedByDate = savedBars.associateBy { bar ->
            GameCalendar.marketLocalDateTime(stock.market, bar.startTime).date
        }
        completedDates.takeLast(expectedDecisionDates.size) != expectedDecisionDates ||
            savedDates != expectedSavedDates || savedByDate.size != savedBars.size ||
            canonicalRetainedBars.any { canonical ->
                val date = GameCalendar.marketLocalDateTime(stock.market, canonical.startTime).date
                savedByDate[date] != canonical
            }
    }
    if (incompleteDailyCoverage) {
        return "시간봉에서 독립 재구성한 최근 거래일과 일봉·시장감시 의사결정 구간이 다릅니다."
    }
    val requiredSurveillanceTailByStockId = canonicalDailyBarsByStockId.mapValues { (_, bars) ->
        bars.filter { bar -> bar.volume > 0L }.takeLast(REQUIRED_SURVEILLANCE_DECISION_POINTS)
    }
    val krxProjectionDates = requiredSurveillanceTailByStockId.asSequence()
        .filter { (stockId, _) -> stocksById.getValue(stockId).market.isKorean }
        .flatMap { (stockId, bars) ->
            val market = stocksById.getValue(stockId).market
            bars.asSequence().map { bar ->
                GameCalendar.marketLocalDateTime(market, bar.startTime).date
            }
        }
        .distinct()
        .toList()
    val expectedKrxProxyByStockDate = linkedMapOf<Pair<String, LocalDate>, Double>()
    val expectedKrxRankByStockDate = linkedMapOf<Pair<String, LocalDate>, Int>()
    for (date in krxProjectionDates) {
        val indexEligibleIds = stocksById.values.asSequence().filter { stock ->
            stock.market.isKorean && stock.hasCorporateEarnings &&
                listingStatusBeforeSurveillance(
                    listingEventsByStockId[stock.id].orEmpty(),
                    date,
                ) !in setOf(
                    ListingLifecycleStatus.LIQUIDATION_PENDING,
                    ListingLifecycleStatus.DELISTED,
                    ListingLifecycleStatus.TERMINATED,
                )
        }.mapTo(linkedSetOf(), StockDefinition::id)
        val closeByStockId = stocksById.values.asSequence()
            .filter { stock -> stock.market.isKorean }
            .associate { stock ->
            val close = canonicalDailyBarsByStockId.getValue(stock.id)
                .lastOrNull { bar ->
                    GameCalendar.marketLocalDateTime(stock.market, bar.startTime).date <= date
                }
                ?.close
                ?: stock.initialPrice
            stock.id to close
        }
        val projection = KrxDailySurveillanceProjection.project(
            stocks = stocksById.values,
            closeByStockId = closeByStockId,
            indexEligibleStockIds = indexEligibleIds,
            top100MarketCapProxyKrw = SimulatorRuntime.KRX_TOP_100_MARKET_CAP_PROXY_KRW,
        )
        projection.marketProxyByStockId.forEach { (stockId, proxy) ->
            expectedKrxProxyByStockDate[stockId to date] = proxy
        }
        projection.marketCapRankByStockId.forEach { (stockId, rank) ->
            expectedKrxRankByStockDate[stockId to date] = rank
        }
    }
    val krxSurveillanceDates = state.dailyTradingSurveillance.values.asSequence()
        .flatten()
        .map { point -> point.date }
        .distinct()
        .toList()
    val krxProxyAvailableAt = buildSet {
        krxSurveillanceDates.forEach { date ->
            stocksById.values.asSequence()
                .filter { stock -> stock.market.isKorean && stock.hasCorporateEarnings }
                .filter { stock ->
                    listingStatusBeforeSurveillance(
                        listingEventsByStockId[stock.id].orEmpty(),
                        date,
                    ) !in setOf(
                        ListingLifecycleStatus.LIQUIDATION_PENDING,
                        ListingLifecycleStatus.DELISTED,
                        ListingLifecycleStatus.TERMINATED,
                    )
                }
                .mapTo(this) { stock -> stock.market to date }
        }
    }
    if (state.dailyTradingSurveillance.keys != stockIds ||
        state.dailyTradingSurveillance.any { (stockId, points) ->
                val stock = stocksById.getValue(stockId)
                points.size > SimulatorRuntime.MAX_DAILY_SURVEILLANCE_POINTS ||
                points.zipWithNext().any { (previous, next) -> previous.date >= next.date } ||
                invalidDailyTradingSurveillanceHistory(
                    stock = stock,
                    points = points,
                    listingEvents = listingEventsByStockId[stockId].orEmpty(),
                    krxProxyAvailableAt = krxProxyAvailableAt,
                    requiredTailBars = requiredSurveillanceTailByStockId.getValue(stockId),
                    expectedKrxProxyByStockDate = expectedKrxProxyByStockDate,
                    expectedKrxRankByStockDate = expectedKrxRankByStockDate,
                    debugPriceFactsAllowed = debugPriceFactsAllowed,
                    currentDate = state.currentDate,
                )
        }
    ) {
        return "일별 시장감시 이력의 종목·값·거래일·날짜 순서·보존 한도가 올바르지 않습니다."
    }
    return null
}

private fun invalidDailyTradingSurveillanceHistory(
    stock: StockDefinition,
    points: List<com.amond.kmpbook.presentation.portfolio.DailyTradingSurveillancePoint>,
    listingEvents: List<ListingLifecycleLedgerEvent>,
    krxProxyAvailableAt: Set<Pair<Market, LocalDate>>,
    requiredTailBars: List<com.amond.kmpbook.domain.model.pricing.PriceBar>,
    expectedKrxProxyByStockDate: Map<Pair<String, LocalDate>, Double>,
    expectedKrxRankByStockDate: Map<Pair<String, LocalDate>, Int>,
    debugPriceFactsAllowed: Boolean,
    currentDate: LocalDate,
): Boolean {
    val orderedEvents = listingEvents.sortedWith(
        compareBy(ListingLifecycleLedgerEvent::tradingDate)
            .thenBy(ListingLifecycleLedgerEvent::sequence),
    )
    var lifecycleStatus = ListingLifecycleStatus.LISTED
    var eventIndex = 0
    val requiredDates = requiredTailBars.map { bar ->
        GameCalendar.marketLocalDateTime(stock.market, bar.startTime).date
    }
    val actualTail = points.takeLast(requiredDates.size)
    if (actualTail.map { point -> point.date } != requiredDates ||
        actualTail.zip(requiredTailBars).any { (point, bar) ->
            !debugPriceFactsAllowed && point.close.toBits() != bar.close.toBits() ||
                point.volume != bar.volume ||
                abs(point.turnoverRate -
                    point.volume.toDouble() / stock.sharesOutstanding.toDouble()) >
                SURVEILLANCE_TURNOVER_EPSILON
        }
    ) {
        return true
    }
    for (point in points) {
        while (eventIndex < orderedEvents.size &&
            orderedEvents[eventIndex].tradingDate < point.date
        ) {
            lifecycleStatus = orderedEvents[eventIndex].toStatus
            eventIndex += 1
        }
        val requiresKrxRank = stock.market.isKorean && stock.hasCorporateEarnings &&
            lifecycleStatus !in setOf(
                ListingLifecycleStatus.LIQUIDATION_PENDING,
                ListingLifecycleStatus.DELISTED,
                ListingLifecycleStatus.TERMINATED,
            )
        val projectionKey = stock.id to point.date
        val expectedProxy = expectedKrxProxyByStockDate[projectionKey]
        if (point.date > currentDate || runCatching { point.copy() }.isFailure ||
            GameCalendar.regularSessionWindow(stock.market, point.date) == null ||
            stock.market.isUnitedStates &&
            (point.marketProxyClose != null || point.krxMarketCapRank != null) ||
            ((stock.market to point.date) in krxProxyAvailableAt) !=
            (point.marketProxyClose != null) ||
            requiresKrxRank != (point.krxMarketCapRank != null) ||
            !debugPriceFactsAllowed && expectedProxy != null &&
            point.marketProxyClose?.toBits() != expectedProxy.toBits() ||
            !debugPriceFactsAllowed && requiresKrxRank &&
            expectedKrxRankByStockDate[projectionKey]?.let { expectedRank ->
                point.krxMarketCapRank != expectedRank
            } == true
        ) {
            return true
        }
    }
    return false
}

private fun listingStatusBeforeSurveillance(
    events: List<ListingLifecycleLedgerEvent>,
    date: LocalDate,
): ListingLifecycleStatus = events.asSequence()
    .filter { event -> event.tradingDate < date }
    .maxByOrNull(ListingLifecycleLedgerEvent::sequence)
    ?.toStatus
    ?: ListingLifecycleStatus.LISTED

/**
 * Rebuilds the completed daily suffix from the independent retained hourly series. The first
 * date is discarded when the fixed-size hourly ring is full because it may start mid-session.
 */
private fun canonicalCompletedDailyBarsFromRetainedHourly(
    stock: StockDefinition,
    hourlyBars: List<PriceBar>,
    currentTime: kotlin.time.Instant,
): List<PriceBar> {
    return CanonicalDailyPriceBarProjection.aggregateRetainedHourly(
        stockId = stock.id,
        market = stock.market,
        hourlyBars = hourlyBars,
        dropPotentiallyTruncatedFirstDate = false,
    ).filter { bar ->
        val date = GameCalendar.marketLocalDateTime(stock.market, bar.startTime).date
        GameCalendar.regularSessionWindow(stock.market, date)?.closesAt?.let { closeAt ->
            closeAt in GameCalendar.startInstant..currentTime
        } == true
    }
}

private fun expectedRecentCompletedSessionDates(
    stock: StockDefinition,
    listingEvents: List<ListingLifecycleLedgerEvent>,
    currentTime: kotlin.time.Instant,
    count: Int,
): List<LocalDate> {
    val result = ArrayDeque<LocalDate>()
    val firstMarketDate = GameCalendar.marketLocalDateTime(
        stock.market,
        GameCalendar.startInstant,
    ).date
    val orderedEvents = listingEvents.sortedWith(
        compareBy(ListingLifecycleLedgerEvent::tradingDate)
            .thenBy(ListingLifecycleLedgerEvent::sequence),
    )
    var eventIndex = orderedEvents.lastIndex
    var date = GameCalendar.marketLocalDateTime(stock.market, currentTime).date
    while (date >= firstMarketDate && result.size < count) {
        val session = GameCalendar.regularSessionWindow(stock.market, date)
        while (eventIndex >= 0 && orderedEvents[eventIndex].tradingDate >= date) {
            eventIndex -= 1
        }
        val priorListingEvent = orderedEvents.getOrNull(eventIndex)
        val status = priorListingEvent?.toStatus ?: ListingLifecycleStatus.LISTED
        val stillListed = status !in setOf(
            ListingLifecycleStatus.DELISTED,
            ListingLifecycleStatus.TERMINATED,
        )
        if (stillListed && session?.closesAt?.let { closeAt ->
                closeAt in GameCalendar.startInstant..currentTime
            } == true
        ) {
            result.addFirst(date)
        }
        date = date.minus(1, DateTimeUnit.DAY)
    }
    return result.toList()
}

private fun validatePortfolioSnapshotAccountingLineage(
    state: SimulatorUiState,
    stocksById: Map<String, StockDefinition>,
): String? {
    if (state.portfolioSnapshots.isEmpty()) return "일별 포트폴리오 스냅샷이 비어 있습니다."
    val debugPriceFactsAllowed = state.options.activeMods.any { mod ->
        DebugMod.isCompatible(mod.id, mod.version)
    }
    val expectedCashCurrencies = Currency.entries.toSet()
    val expectedExchangeRateCurrencies = setOf(Currency.USD)
    var expectedDate = GameCalendar.START_LOCAL_DATE_TIME.date
    var previousBoundary: AccountingObservationBoundary? = null
    state.portfolioSnapshots.forEachIndexed { index, snapshot ->
        val daily = state.dailyStatistics[index]
        if (daily.date != expectedDate) {
            return "일별 포트폴리오 이력의 게임 날짜가 시작일부터 연속적이지 않습니다."
        }
        expectedDate = expectedDate.plus(1, DateTimeUnit.DAY)
        val boundary = AccountingObservationBoundary(
            snapshot.timestamp,
            snapshot.accountingSequenceExclusiveUpperBound,
        )
        if (snapshot.timestamp !in GameCalendar.startInstant..state.currentTime ||
            snapshot.accountingSequenceExclusiveUpperBound !in 0L..state.nextSequence ||
            previousBoundary?.let { previous ->
                previous.timestamp > boundary.timestamp ||
                    previous.timestamp == boundary.timestamp &&
                    previous.accountingSequenceExclusiveUpperBound >=
                    boundary.accountingSequenceExclusiveUpperBound
            } == true
        ) {
            return "일별 포트폴리오 관측의 시각·회계 순번 상한이 오름차순 계보와 다릅니다."
        }
        previousBoundary = boundary

        val isCurrentLogicalDate = daily.date == state.currentDate
        val isLast = index == state.portfolioSnapshots.lastIndex
        val canonicalCompletedAt = LocalDateTime(
            daily.date.plus(1, DateTimeUnit.DAY),
            LocalTime(0, 0),
        ).toInstant(GameCalendar.KOREA_TIME_ZONE) - 1.nanoseconds
        val earliestCurrentObservation = if (daily.date == GameCalendar.START_LOCAL_DATE_TIME.date) {
            GameCalendar.startInstant
        } else {
            LocalDateTime(daily.date, LocalTime(0, 0)).toInstant(GameCalendar.KOREA_TIME_ZONE)
        }
        if (isCurrentLogicalDate) {
            if (!isLast || snapshot.timestamp !in earliestCurrentObservation..state.currentTime) {
                return "현재 게임 날짜의 포트폴리오 관측 위치가 마지막 기록과 다릅니다."
            }
        } else if (daily.date > state.currentDate || snapshot.timestamp != canonicalCompletedAt) {
            return "완료된 게임 날짜의 포트폴리오 관측은 다음 KST 자정 직전이어야 합니다."
        }

        if (snapshot.cashByCurrency.keys != expectedCashCurrencies ||
            snapshot.cashByCurrency.any { (currency, amount) ->
                !amount.isFinite() || amount < 0.0 ||
                    amount.toBits() != roundCurrencyForAccounting(amount, currency).toBits()
            } || snapshot.exchangeRatesToKrw.keys != expectedExchangeRateCurrencies ||
            snapshot.exchangeRatesToKrw.getValue(Currency.USD) !in 800.0..2_500.0 ||
            snapshot.initialCapitalKrw.toBits() != state.options.initialCapitalKrw.toBits() ||
            !snapshot.realizedProfitKrw.isFinite() ||
            !snapshot.cumulativeCommissionKrw.isFinite() ||
            !snapshot.cumulativeTaxKrw.isFinite() ||
            snapshot.cumulativeCommissionKrw < 0.0 || snapshot.cumulativeTaxKrw < 0.0 ||
            daily.usdKrw.toBits() != snapshot.exchangeRatesToKrw.getValue(Currency.USD).toBits()
        ) {
            return "일별 포트폴리오의 현금·환율·초기자본·누계 값이 canonical 형식과 다릅니다."
        }
        val holdingIds = snapshot.holdings.map { holding -> holding.stockId }
        if (holdingIds.distinct().size != holdingIds.size ||
            holdingIds.any { stockId -> stockId !in stocksById } ||
            snapshot.holdings.any { holding ->
                val stock = stocksById.getValue(holding.stockId)
                val boundaryListingEvent = state.listingLifecycleLedger.asSequence()
                    .filter { event ->
                        event.stockId == stock.id &&
                            GameCalendar.regularSessionWindow(stock.market, event.tradingDate)
                                ?.closesAt?.let { closeAt ->
                                    boundary.includes(closeAt, event.sequence)
                                } == true
                    }
                    .maxByOrNull(ListingLifecycleLedgerEvent::sequence)
                val contractualLiquidationPrice = boundaryListingEvent
                    ?.takeIf { event ->
                        event.toStatus == ListingLifecycleStatus.LIQUIDATION_PENDING &&
                            event.disposition?.type == ListingFinalDispositionType.CASH_LIQUIDATION
                    }
                    ?.disposition
                    ?.cashPerUnit
                val retainedKrxDailyClose = if (contractualLiquidationPrice == null &&
                    !debugPriceFactsAllowed && stock.market.isKorean &&
                    state.corporateActionLedger.none { action ->
                        action.stockId == stock.id && action.effectiveAt > snapshot.timestamp
                    } && GameCalendar.regularSessionWindow(stock.market, daily.date)
                        ?.closesAt?.let { closeAt -> closeAt <= snapshot.timestamp } == true
                ) {
                    state.chartPriceHistory.getValue(stock.id)
                        .getValue(PriceBarInterval.ONE_DAY)
                        .lastOrNull { bar ->
                            GameCalendar.marketLocalDateTime(stock.market, bar.startTime).date ==
                                daily.date
                        }
                        ?.close
                } else {
                    null
                }
                runCatching { holding.copy() }.isFailure || holding.currency != stock.currency ||
                    listOf(
                        holding.quantity,
                        holding.averagePrice,
                        holding.currentPrice,
                    holding.realizedProfit,
                ).any { value -> !value.isFinite() } ||
                    holding.quantity !in Double.MIN_VALUE..MAX_FUND_REFERENCE_VALUE ||
                    holding.averagePrice !in Double.MIN_VALUE..SimulatorRuntime.MAX_DEBUG_NATIVE_PRICE ||
                    if (contractualLiquidationPrice == null) {
                        holding.currentPrice !in Double.MIN_VALUE..SimulatorRuntime.MAX_DEBUG_NATIVE_PRICE
                    } else {
                        holding.currentPrice !in 0.0..SimulatorRuntime.MAX_DEBUG_NATIVE_PRICE ||
                            holding.currentPrice.toBits() != contractualLiquidationPrice.toBits()
                    } ||
                    abs(holding.realizedProfit) > MAX_FUND_REFERENCE_VALUE ||
                    retainedKrxDailyClose?.let { close ->
                        holding.currentPrice.toBits() != close.toBits()
                    } == true ||
                    contractualLiquidationPrice == null && run {
                        val rounded = MarketMicrostructure.roundNearest(stock, holding.currentPrice)
                        val tolerance = maxOf(
                            1e-12,
                            MarketMicrostructure.tickSize(stock, holding.currentPrice) * 1e-9,
                        )
                        abs(rounded - holding.currentPrice) > tolerance
                    }
            } || snapshot.holdingCostBasisKrw.keys != holdingIds.toSet()
        ) {
            return "일별 포트폴리오 보유 종목의 ID·통화·가격·FIFO 원가 구조가 유효하지 않습니다."
        }
    }
    val finalRecordedDate = state.dailyStatistics.last().date
    val previousGameDate = state.currentDate.minus(1, DateTimeUnit.DAY)
    if (finalRecordedDate != state.currentDate && finalRecordedDate != previousGameDate) {
        return "일별 포트폴리오 이력이 현재 날짜 직전의 완료된 KST 마감까지 이어지지 않았습니다."
    }
    val lastSnapshot = state.portfolioSnapshots.last()
    if (lastSnapshot.timestamp == state.currentTime &&
        lastSnapshot.accountingSequenceExclusiveUpperBound == state.nextSequence &&
        lastSnapshot != state.currentPortfolio
    ) {
        return "현재 시각 포트폴리오 관측이 현재 상태 및 회계 순번과 다릅니다."
    }

    val canonicalFacts = runCatching {
        CanonicalPortfolioSnapshotAccountingReplay.replay(
            stocksById = stocksById,
            initialCapitalKrw = state.options.initialCapitalKrw,
            campaignSeed = state.options.seed,
            currentTime = state.currentTime,
            orders = state.orders,
            trades = state.trades,
            transactionCosts = state.transactionCosts,
            taxExchangeRatesByTradeId = state.taxExchangeRatesByTradeId,
            corporateActions = state.corporateActionLedger,
            distributionOrigins = state.distributionEntitlementOrigins,
            dividendEntries = state.dividendLedger,
            taxPaymentNotices = state.taxPaymentNotices,
            foreignExchanges = state.foreignExchangeLedger,
            cashAdjustments = state.cashAdjustmentLedger,
            portfolioSnapshots = state.portfolioSnapshots,
        )
    }.getOrNull() ?: return "일별 포트폴리오 회계 prefix를 canonical 원장에서 재생할 수 없습니다."
    state.portfolioSnapshots.forEach { snapshot ->
        val boundary = AccountingObservationBoundary(
            snapshot.timestamp,
            snapshot.accountingSequenceExclusiveUpperBound,
        )
        val canonical = canonicalFacts[boundary]
            ?: return "일별 포트폴리오 관측 경계의 canonical 회계 결과가 없습니다."
        val holdingsById = snapshot.holdings.associateBy { holding -> holding.stockId }
        val holdingsMismatch = holdingsById.keys != canonical.nativeHoldingsByStockId.keys ||
            holdingsById.any { (stockId, holding) ->
                val expected = canonical.nativeHoldingsByStockId[stockId] ?: return@any true
                holding.quantity.toBits() != expected.quantity.toBits() ||
                    holding.averagePrice.toBits() != expected.averagePrice.toBits() ||
                    holding.currency != expected.currency ||
                    holding.realizedProfit.toBits() != expected.realizedProfit.toBits()
            }
        if (snapshot.cashByCurrency != canonical.cashByCurrency || holdingsMismatch ||
            snapshot.holdingCostBasisKrw != canonical.holdingCostBasisKrw ||
            snapshot.distributionReceivableByCurrency !=
            canonical.distributionReceivableByCurrency ||
            snapshot.realizedProfitKrw.toBits() != canonical.realizedProfitKrw.toBits() ||
            snapshot.cumulativeCommissionKrw.toBits() !=
            canonical.cumulativeCommissionKrw.toBits() ||
            snapshot.cumulativeTaxKrw.toBits() != canonical.cumulativeTaxKrw.toBits()
        ) {
            return "일별 포트폴리오의 현금·보유·FIFO·분배 미수금·손익·비용 누계가 회계 prefix와 다릅니다."
        }
    }
    return null
}

private fun hasValidCatalogReference(state: SimulatorUiState): Boolean = runCatching {
    val stored = state.catalogReference
    val reconstructed = InstrumentCatalogReference(
        schemaVersion = stored.schemaVersion,
        orderedSources = stored.orderedSources.map { source ->
            InstrumentCatalogSourceReference(
                sourceId = source.sourceId,
                contentSha256 = source.contentSha256,
            )
        },
    )
    reconstructed == stored
}.getOrDefault(false)

private fun invalidInstrumentTradingHalt(halt: InstrumentTradingHalt): Boolean {
    val reason = halt.reason as TradingHaltReason?
    val status = halt.status as TradingHaltStatus?
    if (halt.occurrenceId.isBlank() || halt.stockId.isBlank() || halt.detail.isBlank() ||
        reason == null || reason !in TradingHaltReason.entries ||
        status == null || status !in TradingHaltStatus.entries ||
        halt.scheduledReleaseAt?.let { it < halt.startedAt } == true
    ) {
        return true
    }
    return when (status) {
        TradingHaltStatus.ACTIVE -> halt.releasedAt != null
        TradingHaltStatus.RELEASED -> halt.releasedAt?.let { it < halt.startedAt } != false
    }
}

private fun ListingLifecycleReason?.isOrderlyTerminationReason(): Boolean = this in setOf(
    ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION,
    ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION,
)

private fun ListingLifecycleStatus.isOrderlyTerminationStatus(): Boolean = this in setOf(
    ListingLifecycleStatus.DELISTING_SCHEDULED,
    ListingLifecycleStatus.LIQUIDATION_PENDING,
    ListingLifecycleStatus.TERMINATED,
)

private fun ListingLifecycleEventKind.expectedListingStatus(): ListingLifecycleStatus = when (this) {
    ListingLifecycleEventKind.DEFICIENCY_DESIGNATED,
    ListingLifecycleEventKind.DEFICIENCY_REDESIGNATED,
    -> ListingLifecycleStatus.DEFICIENCY_NOTICE
    ListingLifecycleEventKind.REVIEW_STARTED -> ListingLifecycleStatus.UNDER_REVIEW
    ListingLifecycleEventKind.TRADING_SUSPENDED -> ListingLifecycleStatus.TRADING_SUSPENDED
    ListingLifecycleEventKind.DEFICIENCY_CURED,
    ListingLifecycleEventKind.TRADING_RESUMED,
    -> ListingLifecycleStatus.LISTED
    ListingLifecycleEventKind.DELISTING_SCHEDULED -> ListingLifecycleStatus.DELISTING_SCHEDULED
    ListingLifecycleEventKind.LIQUIDATION_STARTED -> ListingLifecycleStatus.LIQUIDATION_PENDING
    ListingLifecycleEventKind.DELISTED -> ListingLifecycleStatus.DELISTED
    ListingLifecycleEventKind.TERMINATED -> ListingLifecycleStatus.TERMINATED
}

private fun ListingLifecycleReason?.orderlyTerminationReasonMatches(instrumentType: InstrumentType): Boolean = when {
    !isOrderlyTerminationReason() -> true
    this == ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION -> instrumentType == InstrumentType.ETN
    else -> instrumentType in setOf(InstrumentType.ETF, InstrumentType.CLOSED_END_FUND)
}

private fun ListingLifecycleState.orderlyTerminationReasonMatches(instrumentType: InstrumentType): Boolean =
    activeReason.orderlyTerminationReasonMatches(instrumentType)

private fun List<GameEvent>.hasExactTerminationNotice(
    occurrenceId: String,
    stock: StockDefinition,
    priority: Int?,
    rawEffectiveOn: kotlinx.datetime.LocalDate?,
): Boolean =
    exactTerminationNotice(occurrenceId, stock)?.let { notice ->
        notice.terms.kind.noticePriority == priority &&
            notice.rawEffectiveOn == rawEffectiveOn
    } == true

private fun List<GameEvent>.exactTerminationNotice(
    occurrenceId: String,
    stock: StockDefinition,
): PublishedInstrumentTerminationNotice? =
    singleOrNull { event -> event.id == occurrenceId }?.let { event ->
        val terms = event.instrumentTermination ?: return@let null
        if (stock.id !in event.affectedStockIds || !terms.isEligibleFor(stock)) return@let null
        PublishedInstrumentTerminationNotice(
            event = event,
            terms = terms,
            rawEffectiveOn = runCatching { terms.rawEffectiveTradingDate(stock) }.getOrNull()
                ?: return@let null,
        )
    }

/** Direct single-instrument wrappers must liquidate rather than resume from a ghost underlying. */
private fun validateDirectUnderlyingLiquidationNotices(
    state: SimulatorUiState,
    stocksById: Map<String, StockDefinition>,
): String? {
    val recognizedEventIds = linkedSetOf<String>()
    fun hourlyBoundaryAtOrAfter(at: kotlin.time.Instant): kotlin.time.Instant {
        val elapsedHours = (at - GameCalendar.startInstant).inWholeHours
        val floor = GameCalendar.startInstant + elapsedHours.hours
        return if (floor == at) floor else floor + 1.hours
    }

    fun canonicalNoticeTimings(
        underlyingIds: List<String>,
    ): Set<Pair<kotlin.time.Instant, kotlin.time.Instant>> {
        val closesAt = underlyingIds.map { underlyingId ->
            val underlying = stocksById.getValue(underlyingId)
            val transition = state.listingLifecycleLedger
                .asSequence()
                .filter { ledgerEvent ->
                    ledgerEvent.stockId == underlyingId &&
                        ledgerEvent.toStatus in setOf(
                            ListingLifecycleStatus.LIQUIDATION_PENDING,
                            ListingLifecycleStatus.DELISTED,
                            ListingLifecycleStatus.TERMINATED,
                        )
                }
                .minByOrNull(ListingLifecycleLedgerEvent::sequence)
                ?: return emptySet()
            GameCalendar.regularSessionWindow(
                underlying.market,
                transition.tradingDate,
                DefaultMarketHolidays.closedDates(
                    underlying.market,
                    transition.tradingDate.year,
                ),
            )?.closesAt ?: return emptySet()
        }
        val effectiveClose = closesAt.maxOrNull() ?: return emptySet()
        val transitionBoundary = hourlyBoundaryAtOrAfter(effectiveClose)
        return setOf(
            (transitionBoundary - 1.hours) to effectiveClose,
            transitionBoundary to transitionBoundary,
        )
    }

    fun exactNoticeIsCanonical(
        event: GameEvent,
        stock: StockDefinition,
        expectedId: String,
        underlyingIds: List<String>,
    ): Boolean {
        val terms = event.instrumentTermination ?: return false
        return event.id == expectedId && event.startsAt <= state.currentTime &&
            event.recordKind == EventRecordKind.INSTRUMENT_LIFECYCLE &&
            event.scope == EventScope.STOCK && event.type == EventType.FUND_OPERATION &&
            event.severity == EventSeverity.CRITICAL &&
            event.impact == GameEventImpact(direction = ImpactDirection.NEGATIVE) &&
            event.durationHours == 24 && event.effectStartsAt == event.startsAt &&
            event.effectDurationHours == 24 && event.generatorTemplateId == null &&
            event.scheduledEventReference == null && event.corporateActionReference == null &&
            event.marketAction == null && event.tradingHaltDirective == null &&
            event.impactCoveragePolicy == EventImpactCoveragePolicy.SCOPE_FALLBACK_WITH_OVERRIDES &&
            event.impactInsights.isEmpty() && event.causalSignals.isEmpty() &&
            event.marketRegimeSnapshot == CausalMarketRegimeSnapshot() &&
            event.reportedFacts.isEmpty() && event.listingRiskTags.isEmpty() &&
            event.listingRecoveryConditions.isEmpty() && event.listingFinalDispositionHint == null &&
            event.affectedMarkets == setOf(stock.market) &&
            event.affectedSectors == setOf(stock.sector) &&
            event.affectedStockIds == setOf(stock.id) &&
            event.sourceLabel == "직접 기초자산 생명주기 연동" &&
            terms.kind == InstrumentTerminationKind.FUND_LIQUIDATION &&
            terms.valuationMethod == InstrumentTerminationValuationMethod.FINAL_NET_ASSET_VALUE &&
            terms.contractualDate == null && terms.accelerationRecoveryRate == null &&
            terms.effectiveNotBefore != null &&
            (event.startsAt to terms.effectiveNotBefore) in canonicalNoticeTimings(underlyingIds)
    }

    for (stock in stocksById.values) {
        val product = stock.fundProductProfile ?: continue
        val directUnderlyingIds = buildSet {
            product.dailyResetTerms?.reference
                ?.takeIf { reference -> reference.kind == DailyResetReferenceKind.INSTRUMENT }
                ?.instrumentId
                ?.let(::add)
            product.optionStrategyTerms?.reference
                ?.takeIf { reference -> reference.kind == DailyResetReferenceKind.INSTRUMENT }
                ?.instrumentId
                ?.let(::add)
            product.cashCollateralizedPutSpreadTerms?.optionReference
                ?.takeIf { reference -> reference.kind == DailyResetReferenceKind.INSTRUMENT }
                ?.instrumentId
                ?.let(::add)
        }.toList().sorted()
        if (directUnderlyingIds.isEmpty()) continue
        if (directUnderlyingIds.any { underlyingId -> underlyingId !in stocksById }) {
            return "${stock.id}의 직접 기초자산 참조가 현재 종목 카탈로그에 없습니다."
        }
        val unavailableIds = directUnderlyingIds.filter { underlyingId ->
            state.listingLifecycleStates.getValue(underlyingId).isIndexEligible.not()
        }
        val prefix = "direct-underlying-liquidation:${stock.id}:"
        val published = state.newsEvents.filter { event -> event.id.startsWith(prefix) }
        if (unavailableIds.isEmpty()) {
            if (published.isNotEmpty()) {
                return "${stock.id}에 적격 직접 기초자산을 종료한 유령 청산 공시가 있습니다."
            }
            continue
        }

        val expectedId = prefix + unavailableIds.joinToString("+")
        recognizedEventIds += expectedId
        if (published.any { event -> event.id != expectedId } || published.size > 1) {
            return "${stock.id}의 직접 기초자산 청산 공시 ID·중복 상태가 canonical 집합과 다릅니다."
        }
        val wrapperListing = state.listingLifecycleStates.getValue(stock.id)
        val controllingId = wrapperListing.controllingTerminationOccurrenceId
        val noticeIsRequired = wrapperListing.isIndexEligible || controllingId?.startsWith(prefix) == true
        val notice = published.singleOrNull()
        if (noticeIsRequired && notice == null) {
            return "${stock.id}의 사용할 수 없는 직접 기초자산에 필수 청산 공시가 없습니다."
        }
        if (notice != null && !exactNoticeIsCanonical(notice, stock, expectedId, unavailableIds)) {
            return "${stock.id}의 직접 기초자산 청산 공시 대상·약관·효력시각·출처가 canonical 규칙과 다릅니다."
        }
        if (controllingId?.startsWith(prefix) == true && controllingId != expectedId) {
            return "${stock.id}의 지배 청산 공시 ID가 현재 직접 기초자산 계보와 다릅니다."
        }
    }
    if (state.newsEvents.any { event ->
            event.id.startsWith("direct-underlying-liquidation:") && event.id !in recognizedEventIds
        }
    ) {
        return "직접 기초자산 청산 공시에 현재 상품·기초자산 계보가 없는 유령 ID가 있습니다."
    }
    return null
}

private fun validateInstrumentFinancialStates(
    state: SimulatorUiState,
    stocksById: Map<String, StockDefinition>,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    val expectedCorporateIds = stocksById.values
        .filter(StockDefinition::hasCorporateEarnings)
        .mapTo(linkedSetOf(), StockDefinition::id)
    if (state.corporateFundamentals.keys != expectedCorporateIds) {
        return "기업 원시 재무 상태는 실적 발표 대상 종목에 정확히 하나씩 필요합니다."
    }
    if (state.corporateFundamentals.any { (stockId, fundamentals) ->
            fundamentals.stockId != stockId || stocksById[stockId]?.hasCorporateEarnings != true
        }
    ) {
        return "기업 원시 재무 맵의 키·종목 ID·종목 유형이 일치하지 않습니다."
    }

    state.corporateFundamentals.values.forEach { fundamentals ->
        val quarters = fundamentals.quarters
        if (quarters.size != 4 ||
            quarters.any { report ->
                report.periodId.isBlank() || !report.revenue.isFinite() || report.revenue < 0.0 ||
                    !report.netIncome.isFinite() || !report.dilutedShares.isFinite() ||
                    report.dilutedShares <= 0.0 || report.reportedAt > fundamentals.asOf ||
                    report.sourceOccurrenceId?.isBlank() == true
            } ||
            quarters.map { it.periodId }.distinct().size != quarters.size ||
            quarters.zipWithNext().any { (previous, next) -> previous.reportedAt >= next.reportedAt } ||
            !fundamentals.bookEquity.isFinite() || fundamentals.bookEquity <= 0.0 ||
            !fundamentals.equityAtTtmStart.isFinite() || fundamentals.equityAtTtmStart <= 0.0 ||
            fundamentals.appliedEarningsOccurrenceIds.any(String::isBlank) ||
            fundamentals.appliedEarningsOccurrenceIds.distinct().size !=
            fundamentals.appliedEarningsOccurrenceIds.size ||
            fundamentals.asOf > state.currentTime
        ) {
            return "${fundamentals.stockId}의 4개 분기 원시 재무·자본·기준 시각이 유효하지 않습니다."
        }
        val currentQuarterSourceIds = quarters.mapNotNull { it.sourceOccurrenceId }
        if (currentQuarterSourceIds.distinct().size != currentQuarterSourceIds.size ||
            currentQuarterSourceIds.any { it !in fundamentals.appliedEarningsOccurrenceIds }
        ) {
            return "${fundamentals.stockId}의 분기 원장과 적용된 실적 발표 ID가 일치하지 않습니다."
        }
    }

    val expectedFundIds = stocksById.values
        .filter { stock ->
            stock.fundProductProfile?.legalStructure == FundLegalStructure.OPEN_END_ETF
        }
        .mapTo(linkedSetOf(), StockDefinition::id)
    if (state.fundFinancialStates.keys != expectedFundIds) {
        return "개방형 ETF 원시 재무 상태는 개방형 ETF마다 정확히 하나씩 필요합니다."
    }
    if (state.fundFinancialStates.any { (stockId, financialState) ->
            financialState.stockId != stockId || stockId !in expectedFundIds ||
                financialState.navPerUnit !in MIN_FUND_REFERENCE_VALUE..MAX_FUND_REFERENCE_VALUE ||
                !financialState.indicativeValuePerUnit.isFinite() ||
                financialState.indicativeValuePerUnit !in
                MIN_FUND_REFERENCE_VALUE..MAX_FUND_REFERENCE_VALUE ||
                !financialState.unitsOrNotesOutstanding.isFinite() ||
                financialState.unitsOrNotesOutstanding <= 0.0 ||
                !financialState.lastNetFlow.isFinite() ||
                !financialState.accruedDistributionPerUnit.isFinite() ||
                financialState.accruedDistributionPerUnit !in 0.0..MAX_FUND_REFERENCE_VALUE ||
                financialState.asOf > state.currentTime
        }
    ) {
        return "개방형 ETF 원시 재무 맵의 ID·NAV·지표가치·존속 좌수·기준 시각이 유효하지 않습니다."
    }
    for ((stockId, financialState) in state.fundFinancialStates) {
        val reconstructed = runCatching {
            FundFinancialState(
                stockId = financialState.stockId,
                navPerUnit = financialState.navPerUnit,
                indicativeValuePerUnit = financialState.indicativeValuePerUnit,
                unitsOrNotesOutstanding = financialState.unitsOrNotesOutstanding,
                lastNetFlow = financialState.lastNetFlow,
                accruedDistributionPerUnit = financialState.accruedDistributionPerUnit,
                cumulativeUnitAdjustmentFactor = financialState.cumulativeUnitAdjustmentFactor,
                lastCorporateActionAccountingSequence =
                financialState.lastCorporateActionAccountingSequence,
                asOf = financialState.asOf,
            )
        }.getOrNull()
        val expectedAdjustment = state.unitAdjustmentLineage(stockId)
        if (reconstructed != financialState || !unitAdjustmentMarkerMatches(
                actualFactor = financialState.cumulativeUnitAdjustmentFactor,
                actualLastSequence = financialState.lastCorporateActionAccountingSequence,
                expected = expectedAdjustment,
            )
        ) {
            return "개방형 ETF 원시 재무 상태의 누적 좌수조정 배수·기업행동 계보가 적용 원장과 다릅니다."
        }
    }
    if (state.pendingFundFlowRates.any { (stockId, rate) ->
            stockId !in expectedFundIds || !rate.isFinite() ||
                rate !in -MAX_PENDING_FUND_FLOW_RATE..MAX_PENDING_FUND_FLOW_RATE ||
                state.listingLifecycleStates[stockId]?.let { listing ->
                    listing.isTerminal || listing.isSettlementPending
                } != false
        }
    ) {
        return "미소비 상장상품 설정·환매 충격의 종목·비율이 유효하지 않습니다."
    }
    val methodologyRegistry = catalog?.equityMethodologyRegistry ?: BuiltInEquityMethodologies.registry
    validateReferencePortfolioPersistenceState(state, catalog, methodologyRegistry)?.let { violation ->
        return violation
    }
    validateDailyResetPersistenceState(state, stocksById, catalog)?.let { violation -> return violation }
    validateOptionStrategyPersistenceState(state, stocksById, catalog)?.let { violation ->
        return violation
    }
    validateCashCollateralizedPutSpreadPersistenceState(state, stocksById, catalog)?.let { violation ->
        return violation
    }
    validateDirectUnderlyingLiquidationNotices(state, stocksById)?.let { violation ->
        return violation
    }
    validateEtnPersistenceState(state, stocksById, catalog)?.let { violation -> return violation }
    validateClosedEndFundPersistenceState(state, stocksById, catalog)?.let { violation ->
        return violation
    }
    validateFixedIncomeReferencePersistenceState(state, catalog)?.let { violation -> return violation }
    validateKofrIndexPersistenceState(state, catalog)?.let { violation -> return violation }
    validateCommodityReferencePersistenceState(state, catalog)?.let { violation -> return violation }
    validateEquityReferencePersistenceState(state, catalog)?.let { violation -> return violation }
    validateFundOfFundsPersistenceState(state, catalog)?.let { violation -> return violation }
    validateStructuredReferencePersistenceState(state, catalog)?.let { violation -> return violation }
    validateDistributionEvaluationPersistenceState(state, stocksById)?.let { violation ->
        return violation
    }

    val allAppliedIds = state.corporateFundamentals.values
        .flatMap { it.appliedEarningsOccurrenceIds }
    if (allAppliedIds.distinct().size != allAppliedIds.size) {
        return "하나의 실적 발표 ID가 둘 이상의 기업 원장에 중복 적용되었습니다."
    }
    val newsById = state.newsEvents.groupBy(GameEvent::id)
    state.corporateFundamentals.values.forEach { fundamentals ->
        fundamentals.appliedEarningsOccurrenceIds.forEach { occurrenceId ->
            val news = newsById[occurrenceId]?.singleOrNull()
                ?: return "적용된 실적 발표 '$occurrenceId'의 저장 뉴스가 없거나 중복되었습니다."
            if (news.scheduledEventReference?.kind != ScheduledEventKind.EARNINGS ||
                news.scheduledEventReference.occurrenceId != occurrenceId ||
                news.affectedStockIds != setOf(fundamentals.stockId) ||
                news.startsAt > fundamentals.asOf
            ) {
                return "적용된 실적 발표 '$occurrenceId'의 뉴스 종류·종목·시각 계보가 원장과 다릅니다."
            }
        }
    }
    return null
}

/** Rebuilds the bounded, holder-independent distribution idempotency checkpoint. */
private fun validateDistributionEvaluationPersistenceState(
    state: SimulatorUiState,
    stocksById: Map<String, StockDefinition>,
): String? {
    val blockedStatuses = setOf(
        ListingLifecycleStatus.LIQUIDATION_PENDING,
        ListingLifecycleStatus.DELISTED,
        ListingLifecycleStatus.TERMINATED,
    )
    val firstBlockedDateByStock = state.listingLifecycleLedger
        .asSequence()
        .filter { event -> event.toStatus in blockedStatuses }
        .groupBy(ListingLifecycleLedgerEvent::stockId)
        .mapValues { (_, events) -> events.minBy(ListingLifecycleLedgerEvent::sequence).tradingDate }
    val expected = linkedMapOf<String, LocalDate>()
    for ((stockId, stock) in stocksById) {
        val frequency = stock.behavior.distributionFrequency
        if (frequency.periodsPerYear <= 0) continue
        val zone = GameCalendar.timeZoneFor(stock.market)
        val currentLocalDate = GameCalendar.marketLocalDateTime(stock.market, state.currentTime).date
        var candidate = minOf(currentLocalDate, firstBlockedDateByStock[stockId] ?: currentLocalDate)
        while (true) {
            val boundary = LocalDateTime(candidate, LocalTime(0, 0)).toInstant(zone)
            val beforeBlockedClose = firstBlockedDateByStock[stockId]?.let { blockedDate ->
                val close = GameCalendar.regularSessionWindow(stock.market, blockedDate)?.closesAt
                    ?: return "분배 평가 중단 상장 원장의 거래일에 canonical 정규장 종가가 없습니다."
                boundary < close
            } != false
            val isExDate = if (stock.instrumentType == InstrumentType.ETF) {
                DistributionSchedule.eventOnExDate(stock, candidate) != null
            } else {
                DistributionSchedule.isDistributionDate(
                    date = candidate,
                    frequency = frequency,
                    calendar = stock.behavior.distributionCalendar,
                )
            }
            if (boundary > GameCalendar.startInstant && boundary <= state.currentTime &&
                beforeBlockedClose && isExDate
            ) {
                expected[stockId] = candidate
                break
            }
            if (boundary <= GameCalendar.startInstant) break
            candidate = candidate.minus(1, DateTimeUnit.DAY)
        }
    }
    if (state.lastEvaluatedDistributionDateByStock != expected) {
        return "종목별 마지막 분배 평가 날짜가 canonical 현지 자정·분배 주기·상장 중단 경계와 다릅니다."
    }

    fun validDistributionInstant(stockId: String, at: kotlin.time.Instant): Boolean {
        val stock = stocksById[stockId] ?: return false
        val local = GameCalendar.marketLocalDateTime(stock.market, at)
        return at > GameCalendar.startInstant && at <= state.currentTime &&
            local.time == LocalTime(0, 0) &&
            DistributionSchedule.isDistributionDate(
                date = local.date,
                frequency = stock.behavior.distributionFrequency,
                calendar = stock.behavior.distributionCalendar,
            ) &&
            state.lastEvaluatedDistributionDateByStock[stockId]?.let { local.date <= it } == true
    }

    val paidEtfDistributionKeys = state.dividendLedger.asSequence()
        .filter { entry -> stocksById[entry.stockId]?.instrumentType == InstrumentType.ETF }
        .map { entry -> entry.stockId to entry.exDate }
        .toSet()
    val originsById = state.distributionEntitlementOrigins.associateBy { origin -> origin.id }
    val originsByKey = state.distributionEntitlementOrigins.associateBy { origin ->
        origin.stockId to origin.exDate
    }
    if (originsById.size != state.distributionEntitlementOrigins.size ||
        originsByKey.size != state.distributionEntitlementOrigins.size
    ) {
        return "ETF 분배 권리 origin 원장의 ID 또는 종목·분배락 키가 중복되었습니다."
    }

    fun replayEntitledQuantity(originId: String): Double? {
        val origin = originsById[originId] ?: return null
        if (state.trades.any { trade ->
                trade.stockId == origin.stockId &&
                    (trade.executedAt < origin.establishedAt) !=
                    (trade.accountingSequence < origin.accountingSequence)
            } || state.corporateActionLedger.any { action ->
                action.stockId == origin.stockId &&
                    (action.effectiveAt < origin.establishedAt) !=
                    (action.accountingSequence < origin.accountingSequence)
            }
        ) return null
        val transitions = buildList {
            state.trades.asSequence()
                .filter { trade ->
                    trade.stockId == origin.stockId && trade.executedAt < origin.establishedAt
                }
                .forEach { trade ->
                    val signedQuantity = if (trade.side == OrderSide.BUY) trade.quantity else -trade.quantity
                    add(Triple(trade.accountingSequence, signedQuantity, false))
                }
            state.corporateActionLedger.asSequence()
                .filter { action ->
                    action.stockId == origin.stockId && action.effectiveAt < origin.establishedAt
                }
                .forEach { action ->
                    add(Triple(action.accountingSequence, action.quantityMultiplier, true))
                }
        }.sortedBy { transition -> transition.first }
        var quantity = 0.0
        for ((_, value, isMultiplier) in transitions) {
            quantity = if (isMultiplier) quantity * value else quantity + value
            if (!quantity.isFinite() || quantity < -DISTRIBUTION_QUANTITY_EPSILON) return null
        }
        return if (abs(quantity) <= DISTRIBUTION_QUANTITY_EPSILON) 0.0 else quantity
    }

    if (state.distributionEntitlementOrigins.any { origin ->
            val stock = stocksById[origin.stockId] ?: return@any true
            val schedule = DistributionSchedule.eventOnExDate(stock, origin.exDate)
            val expectedEstablishedAt = LocalDateTime(origin.exDate, LocalTime(0, 0))
                .toInstant(GameCalendar.timeZoneFor(stock.market))
            val expectedBasis = if (schedule?.declaredGrossPerUnit == null) {
                DistributionAmountBasis.DETERMINISTIC_POLICY_PROJECTION
            } else {
                DistributionAmountBasis.ANNOUNCED
            }
            val expectedGrossPerUnit = schedule?.declaredGrossPerUnit
                ?: runCatching {
                    DistributionAmountProjection.projectedGrossPerUnit(stock, origin.exDate)
                }.getOrNull()
            stock.instrumentType != InstrumentType.ETF || schedule == null || schedule.skip ||
                origin.id != "distribution-origin:${stock.id}:${origin.exDate}" ||
                origin.establishedAt != expectedEstablishedAt ||
                origin.establishedAt <= GameCalendar.startInstant ||
                origin.establishedAt > state.currentTime ||
                origin.amountBasis != expectedBasis ||
                expectedGrossPerUnit == null ||
                origin.grossPerUnit.toBits() != expectedGrossPerUnit.toBits() ||
                !origin.grossPerUnit.isFinite() ||
                origin.grossPerUnit !in 0.0..MAX_FUND_REFERENCE_VALUE || origin.grossPerUnit == 0.0 ||
                !origin.entitledQuantity.isFinite() ||
                origin.entitledQuantity !in 0.0..MAX_FUND_REFERENCE_VALUE ||
                origin.entitledQuantity == 0.0 ||
                replayEntitledQuantity(origin.id)?.let { quantity ->
                    abs(quantity - origin.entitledQuantity) <= DISTRIBUTION_QUANTITY_EPSILON
                } != true ||
                !origin.taxableCoverageRatio.isFinite() ||
                origin.taxableCoverageRatio.toBits() !=
                DistributionReturnOfCapitalPolicy.modeledTaxableCoverageRatio(stock).toBits() ||
                !origin.taxBasisExchangeRateToKrw.isFinite() ||
                stock.currency == Currency.KRW && origin.taxBasisExchangeRateToKrw.toBits() != 1.0.toBits() ||
                stock.currency == Currency.USD && origin.taxBasisExchangeRateToKrw !in
                SimulatorRuntime.MIN_USD_KRW..SimulatorRuntime.MAX_USD_KRW ||
                runCatching {
                    val canonicalGross = grossReceivableAmount(
                        stock.currency,
                        origin.grossPerUnit,
                        origin.entitledQuantity,
                    )
                    val canonicalTaxable = MoneyRoundingPolicy.MINOR_UNIT_HALF_UP.fromMajorUnits(
                        canonicalGross * origin.taxableCoverageRatio,
                        stock.currency,
                    ).amount
                    origin.returnOfCapitalAmount.toBits() !=
                        (canonicalGross - canonicalTaxable).coerceAtLeast(0.0).toBits()
                }.getOrDefault(true) ||
                !origin.accruedDistributionPerUnitBeforeEx.isFinite() ||
                origin.accruedDistributionPerUnitBeforeEx !in 0.0..MAX_FUND_REFERENCE_VALUE ||
                !origin.navPerUnitBeforeEx.isFinite() ||
                origin.navPerUnitBeforeEx !in MIN_FUND_REFERENCE_VALUE..MAX_FUND_REFERENCE_VALUE ||
                !origin.navPerUnitAfterEx.isFinite() ||
                origin.navPerUnitAfterEx !in MIN_FUND_REFERENCE_VALUE..MAX_FUND_REFERENCE_VALUE ||
                origin.navPerUnitAfterEx.toBits() !=
                (origin.navPerUnitBeforeEx - origin.grossPerUnit)
                    .coerceAtLeast(MIN_FUND_REFERENCE_VALUE).toBits() ||
                schedule.declaredGrossPerUnit?.toBits()?.let { declared ->
                    declared != origin.grossPerUnit.toBits()
                } == true ||
                state.lastEvaluatedDistributionDateByStock[stock.id]
                    ?.let { origin.exDate <= it } != true ||
                origin.excessReturnOfCapitalGainKrw < 0L
        }
    ) {
        return "ETF 분배 권리 origin의 ex 시각·금액 source·NAV 전이·보유 원장 계보가 유효하지 않습니다."
    }

    val pendingEntitlementCount = state.pendingDistributionEntitlements.size
    if (state.pendingDistributionEntitlements.map { it.id }.distinct().size != pendingEntitlementCount ||
        state.pendingDistributionEntitlements.map { it.originId }.distinct().size != pendingEntitlementCount ||
        state.pendingDistributionEntitlements
            .map { entitlement -> entitlement.stockId to entitlement.exDate }
            .distinct()
            .size != pendingEntitlementCount ||
        state.pendingDistributionEntitlements.any { entitlement ->
            val stock = stocksById[entitlement.stockId] ?: return@any true
            val schedule = DistributionSchedule.eventOnExDate(stock, entitlement.exDate)
            val origin = originsById[entitlement.originId]
            val currentLocalDate = GameCalendar.marketLocalDateTime(stock.market, state.currentTime).date
            stock.instrumentType != InstrumentType.ETF ||
                entitlement.id != "distribution-entitlement:${stock.id}:${entitlement.exDate}" ||
                origin == null || origin.stockId != entitlement.stockId ||
                origin.exDate != entitlement.exDate ||
                origin.grossPerUnit.toBits() != entitlement.grossPerUnit.toBits() ||
                origin.entitledQuantity.toBits() != entitlement.entitledQuantity.toBits() ||
                origin.taxableCoverageRatio.toBits() != entitlement.taxableCoverageRatio.toBits() ||
                replayEntitledQuantity(entitlement.originId)?.let { replayed ->
                    abs(replayed - entitlement.entitledQuantity) <= DISTRIBUTION_QUANTITY_EPSILON
                } != true ||
                (entitlement.stockId to entitlement.exDate) in paidEtfDistributionKeys ||
                entitlement.currency != stock.currency ||
                schedule == null || schedule.skip ||
                entitlement.recordDate != schedule.recordDate || entitlement.payDate != schedule.payDate ||
                entitlement.payDate <= currentLocalDate ||
                state.lastEvaluatedDistributionDateByStock[stock.id]?.let { entitlement.exDate <= it } != true ||
                !entitlement.grossPerUnit.isFinite() ||
                entitlement.grossPerUnit !in 0.0..MAX_FUND_REFERENCE_VALUE || entitlement.grossPerUnit == 0.0 ||
                !entitlement.entitledQuantity.isFinite() ||
                entitlement.entitledQuantity !in 0.0..MAX_FUND_REFERENCE_VALUE || entitlement.entitledQuantity == 0.0 ||
                entitlement.grossReceivableAmount().let { gross ->
                    !gross.isFinite() || gross !in 0.0..MAX_FUND_REFERENCE_VALUE || gross == 0.0
                } ||
                !entitlement.taxableCoverageRatio.isFinite() ||
                entitlement.taxableCoverageRatio !in 0.0..1.0 ||
                entitlement.taxableCoverageRatio.toBits() !=
                DistributionReturnOfCapitalPolicy.modeledTaxableCoverageRatio(stock).toBits() ||
                schedule.declaredGrossPerUnit?.toBits()?.let { declared ->
                    declared != entitlement.grossPerUnit.toBits()
                } == true
        }
    ) {
        return "미지급 ETF 분배 권리의 ID·일정·통화·분배락 평가 계보가 유효하지 않습니다."
    }
    val paidEtfEntries = state.dividendLedger.filter { entry ->
        stocksById[entry.stockId]?.instrumentType == InstrumentType.ETF
    }
    if (paidEtfEntries.any { entry ->
            val stock = stocksById[entry.stockId] ?: return@any true
            val origin = originsByKey[entry.stockId to entry.exDate] ?: return@any true
            val canonicalTaxableCoverageRatio =
                DistributionReturnOfCapitalPolicy.modeledTaxableCoverageRatio(stock)
            val replayedQuantity = replayEntitledQuantity(origin.id) ?: return@any true
            if (abs(replayedQuantity - origin.entitledQuantity) > DISTRIBUTION_QUANTITY_EPSILON) {
                return@any true
            }
            val canonicalGrossAmount = grossReceivableAmount(
                currency = entry.currency,
                grossPerUnit = origin.grossPerUnit,
                entitledQuantity = replayedQuantity,
            )
            val paidOn = GameCalendar.marketLocalDateTime(stock.market, entry.paidAt).date
            val canonicalTaxableInput = runCatching {
                MoneyRoundingPolicy.MINOR_UNIT_HALF_UP.fromMajorUnits(
                    canonicalGrossAmount * canonicalTaxableCoverageRatio,
                    entry.currency,
                )
            }.getOrNull() ?: return@any true
            val canonicalTaxResult = runCatching {
                DividendTaxCalculator().calculate(
                    DividendTaxRequest(
                        taxClass = if (stock.market.isKorean) {
                            DividendTaxClass.KOREAN_ETF_DISTRIBUTION
                        } else {
                            DividendTaxClass.US_RIC_ETF_DISTRIBUTION
                        },
                        grossAmount = canonicalTaxableInput,
                        paidOn = paidOn,
                        taxExchangeRateToKrw = entry.exchangeRateToKrw,
                        w8BenValid = true,
                    ),
                )
            }.getOrNull() ?: return@any true
            val canonicalTaxableAmount = canonicalTaxResult.breakdown.taxableBase.amount
            val canonicalReturnOfCapital =
                (canonicalGrossAmount - canonicalTaxableAmount).coerceAtLeast(0.0)
            val canonicalWithholdingTax = canonicalTaxResult.breakdown.totalTax.amount
            val canonicalNetAmount = grossReceivableAmount(
                currency = entry.currency,
                grossPerUnit = canonicalTaxResult.netCash.amount + canonicalReturnOfCapital,
                entitledQuantity = 1.0,
            )
            origin.grossPerUnit.toBits() != entry.grossPerUnit.toBits() ||
                origin.entitledQuantity.toBits() != entry.entitledQuantity.toBits() ||
                origin.taxableCoverageRatio.toBits() != canonicalTaxableCoverageRatio.toBits() ||
                entry.grossAmount.toBits() != canonicalGrossAmount.toBits() ||
                entry.taxBreakdown != canonicalTaxResult.breakdown ||
                entry.taxableIncomeAmount.toBits() != canonicalTaxableAmount.toBits() ||
                origin.returnOfCapitalAmount.toBits() != canonicalReturnOfCapital.toBits() ||
                entry.returnOfCapitalAmount.toBits() != origin.returnOfCapitalAmount.toBits() ||
                entry.excessReturnOfCapitalGainKrw != origin.excessReturnOfCapitalGainKrw ||
                entry.withholdingTax.toBits() != canonicalWithholdingTax.toBits() ||
                entry.netAmount.toBits() != canonicalNetAmount.toBits() ||
                origin.accountingSequence >= entry.accountingSequence
        }
    ) {
        return "지급된 ETF 분배 원장의 권리 수량·통화 반올림 gross·과세/ROC·세금·net이 canonical 계보와 다릅니다."
    }
    val pendingOriginIds = state.pendingDistributionEntitlements.mapTo(linkedSetOf()) { it.originId }
    val paidOriginIds = paidEtfEntries.mapNotNullTo(linkedSetOf()) { entry ->
        originsByKey[entry.stockId to entry.exDate]?.id
    }
    if (pendingOriginIds.intersect(paidOriginIds).isNotEmpty() ||
        pendingOriginIds + paidOriginIds != originsById.keys
    ) {
        return "ETF 분배 권리 origin은 정확히 하나의 미지급 권리 또는 지급 원장에 연결되어야 합니다."
    }
    if (state.distributionReceivableByCurrency.values.any { amount ->
            !amount.isFinite() || amount !in 0.0..MAX_FUND_REFERENCE_VALUE || amount == 0.0
        } || state.portfolioSnapshots.lastOrNull()
            ?.takeIf { snapshot -> snapshot.timestamp == state.currentTime }
            ?.distributionReceivableByCurrency
            ?.let { receivables -> receivables != state.distributionReceivableByCurrency } == true
    ) {
        return "현재 ETF 분배 미수금의 통화별 gross 평가액이 권리 원장·현재 스냅샷과 다릅니다."
    }

    fun validDividendEntry(entry: DividendLedgerEntry): Boolean {
        val stock = stocksById[entry.stockId] ?: return false
        val paidLocal = GameCalendar.marketLocalDateTime(stock.market, entry.paidAt)
        if (entry.paidAt <= GameCalendar.startInstant || entry.paidAt > state.currentTime ||
            paidLocal.time != LocalTime(0, 0)
        ) return false
        if (stock.instrumentType != InstrumentType.ETF) {
            return entry.exDate == paidLocal.date && entry.recordDate == paidLocal.date &&
                validDistributionInstant(entry.stockId, entry.paidAt)
        }
        val schedule = DistributionSchedule.eventOnExDate(stock, entry.exDate) ?: return false
        return !schedule.skip && entry.recordDate == schedule.recordDate &&
            paidLocal.date == schedule.payDate &&
            state.lastEvaluatedDistributionDateByStock[stock.id]?.let { entry.exDate <= it } == true
    }

    if (state.dividendLedger.any { entry -> !validDividendEntry(entry) } ||
        state.dividendLedger.map { entry ->
            entry.stockId to GameCalendar.marketLocalDateTime(
                stocksById.getValue(entry.stockId).market,
                entry.paidAt,
            ).date to entry.exDate
        }.distinct().size != state.dividendLedger.size
    ) {
        return "보유자 분배 원장의 지급 시각·주기·종목별 날짜 유일성이 분배 평가 계보와 다릅니다."
    }
    val cefDistributions = state.closedEndFundLedger.filter { entry ->
        entry.kind == ClosedEndFundLedgerKind.DISTRIBUTION
    }
    if (cefDistributions.any { entry -> !validDistributionInstant(entry.fundId, entry.effectiveAt) } ||
        cefDistributions.map { entry ->
            entry.fundId to GameCalendar.marketLocalDateTime(
                stocksById.getValue(entry.fundId).market,
                entry.effectiveAt,
            ).date
        }.distinct().size != cefDistributions.size
    ) {
        return "CEF 분배 원장의 효력 시각·주기·종목별 날짜 유일성이 분배 평가 계보와 다릅니다."
    }
    val etnCoupons = state.etnLedger.filter { entry -> entry.kind == EtnLedgerKind.COUPON_PAYMENT }
    if (etnCoupons.any { entry -> !validDistributionInstant(entry.productId, entry.effectiveAt) } ||
        etnCoupons.map { entry ->
            entry.productId to GameCalendar.marketLocalDateTime(
                stocksById.getValue(entry.productId).market,
                entry.effectiveAt,
            ).date
        }.distinct().size != etnCoupons.size
    ) {
        return "ETN 쿠폰 원장의 효력 시각·주기·종목별 날짜 유일성이 분배 평가 계보와 다릅니다."
    }
    return null
}

/** Gson이 생성자를 우회해도 기준 포트폴리오 원장의 도메인·계보 불변조건을 다시 검증한다. */
private fun validateReferencePortfolioPersistenceState(
    state: SimulatorUiState,
    catalog: InstrumentCatalogSnapshot?,
    methodologyRegistry: EquityMethodologyRegistry,
): String? {
    val engine = ReferencePortfolioEngine.forCampaignSeed(state.options.seed, methodologyRegistry)
    val definitionsByPortfolioId: Map<String, BenchmarkDefinition>? = catalog
        ?.benchmarksInEvaluationOrder
        ?.filter { definition -> definition.engineKind == BenchmarkEngineKind.EQUITY_METHODOLOGY }
        ?.associateBy { definition -> ReferencePortfolioEngine.portfolioIdFor(definition.ref) }
    if (definitionsByPortfolioId != null &&
        state.referencePortfolioStates.keys != definitionsByPortfolioId.keys
    ) {
        return "기준 포트폴리오 상태는 실행 가능한 주식 방법론 벤치마크마다 정확히 하나씩 필요합니다."
    }
    if (state.referencePortfolioStates.values
            .map(ReferencePortfolioState::benchmarkRef)
            .distinct().size != state.referencePortfolioStates.size
    ) {
        return "하나의 벤치마크 버전에 둘 이상의 기준 포트폴리오 상태가 있습니다."
    }
    val compiledByPortfolioId = definitionsByPortfolioId?.mapValues { (_, definition) ->
        runCatching {
            BenchmarkMethodologyCompiler.compile(definition, methodologyRegistry)
        }.getOrNull()
    }
    if (compiledByPortfolioId?.values?.any { compiled -> compiled == null } == true) {
        return "기준 포트폴리오의 주식 방법론을 컴파일할 수 없습니다."
    }
    val constraintsByPortfolioId = compiledByPortfolioId?.mapValues { (_, compiled) ->
        compiled?.constraints
    }
    if (constraintsByPortfolioId?.values?.any { constraints -> constraints == null } == true) {
        return "기준 포트폴리오의 provider 제약을 복원할 수 없습니다."
    }

    fun reconstructPosition(position: ReferencePortfolioPosition): ReferencePortfolioPosition =
        ReferencePortfolioPosition(
            assetId = position.assetId,
            currentWeight = position.currentWeight,
            targetWeight = position.targetWeight,
            referenceFloatMarketValue = position.referenceFloatMarketValue,
            enteredOn = position.enteredOn,
            selectionRank = position.selectionRank,
        )

    fun reconstructMethodologyPathState(
        pathState: EquityMethodologyPathState,
    ): EquityMethodologyPathState = EquityMethodologyPathState(
        entries = pathState.entries.map { entry ->
            EquityMethodologyPathEntry(
                assetId = entry.assetId,
                decimalValues = buildMap {
                    entry.decimalValues.forEach { (key, value) -> put(key, value) }
                },
                booleanValues = buildMap {
                    entry.booleanValues.forEach { (key, value) -> put(key, value) }
                },
            )
        },
    )

    fun reconstructCorporateAction(
        action: ReferencePortfolioCorporateAction,
    ): ReferencePortfolioCorporateAction = ReferencePortfolioCorporateAction(
        eventId = action.eventId,
        kind = action.kind,
        announcementDate = action.announcementDate,
        effectiveDate = action.effectiveDate,
        primaryAssetId = action.primaryAssetId,
        secondaryAssetId = action.secondaryAssetId,
        considerationKind = action.considerationKind,
        valueTransferFraction = action.valueTransferFraction,
        followUpEffectiveDate = action.followUpEffectiveDate,
    )

    fun validatePositions(
        positions: List<ReferencePortfolioPosition>,
        constraints: EquityMethodologyPortfolioConstraints?,
        enteredNoLaterThan: kotlinx.datetime.LocalDate,
        label: String,
        enforceTargetCaps: Boolean,
    ): String? {
        val minimumCount = constraints?.minimumConstituentCount ?: 1
        val maximumCount = constraints?.maximumConstituentCount
            ?: ReferencePortfolioLimits.MAX_CONSTITUENTS
        if (positions.size !in minimumCount..maximumCount) {
            return "$label 구성종목 수가 방법론의 provider 제약 범위를 벗어났습니다."
        }
        val reconstructed = runCatching { positions.map(::reconstructPosition) }.getOrNull()
        if (reconstructed != positions) {
            return "$label 구성종목 필드가 도메인 불변조건을 위반했습니다."
        }
        if (positions.any { position -> position.enteredOn > enteredNoLaterThan }) {
            return "$label 구성종목 편입일이 허용 기준일보다 늦습니다."
        }
        if (positions.any { position -> !engine.hasCanonicalReferenceIdentity(position) }) {
            return "$label 구성종목이 캠페인 기준자산 원본에 없습니다."
        }
        if (constraints != null && enforceTargetCaps) {
            val individualCap = constraints.individualWeightCap ?: 1.0
            val sectorCap = constraints.sectorWeightCap ?: 1.0
            val recappingCapacity = positions
                .groupBy { position ->
                    checkNotNull(engine.referenceAssetIdentity(position.assetId)).methodologySector
                }
                .values
                .sumOf { sectorPositions ->
                    minOf(
                        sectorCap,
                        sectorPositions.size * individualCap,
                    )
                }
            if (recappingCapacity < 1.0 - REFERENCE_WEIGHT_ALLOCATION_EPSILON) {
                return "$label 구성은 다음 비중 재조정에서 100%를 배분할 상한 용량이 부족합니다."
            }
            val epsilon = ReferencePortfolioState.WEIGHT_EPSILON
            if (constraints.individualWeightCap?.let { cap ->
                    positions.any { position -> position.targetWeight > cap + epsilon }
                } == true
            ) {
                return "$label 목표 비중이 방법론의 개별 종목 상한을 초과했습니다."
            }
            val sectorTargets = positions.groupBy { position ->
                checkNotNull(engine.referenceAssetIdentity(position.assetId)).methodologySector
            }.values.map { sectorPositions ->
                sectorPositions.sumOf(ReferencePortfolioPosition::targetWeight)
            }
            if (constraints.sectorWeightCap?.let { cap ->
                    sectorTargets.any { target -> target > cap + epsilon }
                } == true
            ) {
                return "$label 목표 비중이 방법론의 섹터 상한을 초과했습니다."
            }
        }
        return null
    }

    fun transitionTargetsMatchCanonicalBlend(
        positions: List<ReferencePortfolioPosition>,
        initialTargetWeights: Map<String, Double>,
        finalPositions: List<ReferencePortfolioPosition>,
        completionFraction: Double,
    ): Boolean {
        val finalById = finalPositions.associateBy(ReferencePortfolioPosition::assetId)
        val expectedAssetIds = (initialTargetWeights.keys + finalById.keys).sorted()
        if (positions.map(ReferencePortfolioPosition::assetId) != expectedAssetIds) return false

        val finalMaximumRank = finalPositions.maxOf(ReferencePortfolioPosition::selectionRank)
        val outgoingRanks = (initialTargetWeights.keys - finalById.keys).sorted()
            .withIndex().associate { (index, assetId) -> assetId to finalMaximumRank + index + 1 }
        val rawTargetWeights = expectedAssetIds.associateWith { assetId ->
            (initialTargetWeights[assetId] ?: 0.0) * (1.0 - completionFraction) +
                (finalById[assetId]?.targetWeight ?: 0.0) * completionFraction
        }
        if (rawTargetWeights.values.any { weight -> !weight.isFinite() || weight <= 0.0 }) {
            return false
        }
        val repairedTargetWeights = rawTargetWeights.toMutableMap()
        val largestAssetId = expectedAssetIds.maxBy { assetId ->
            rawTargetWeights.getValue(assetId)
        }
        repairedTargetWeights[largestAssetId] = repairedTargetWeights.getValue(largestAssetId) +
            (1.0 - rawTargetWeights.values.sum())
        val epsilon = ReferencePortfolioState.WEIGHT_EPSILON * 4.0
        return positions.all { position ->
            val expectedRank = finalById[position.assetId]?.selectionRank
                ?: outgoingRanks[position.assetId]
                ?: return@all false
            position.selectionRank == expectedRank &&
                abs(position.targetWeight - repairedTargetWeights.getValue(position.assetId)) <=
                epsilon
        }
    }

    fun transitionTargetsFollowPreviousStage(
        positions: List<ReferencePortfolioPosition>,
        previousPositions: List<ReferencePortfolioPosition>,
        finalPositions: List<ReferencePortfolioPosition>,
        incrementalCompletionFraction: Double,
    ): Boolean {
        if (incrementalCompletionFraction <= 0.0 || incrementalCompletionFraction >= 1.0) {
            return false
        }
        val previousById = previousPositions.associateBy(ReferencePortfolioPosition::assetId)
        val finalById = finalPositions.associateBy(ReferencePortfolioPosition::assetId)
        val expectedAssetIds = (previousById.keys + finalById.keys).sorted()
        if (positions.map(ReferencePortfolioPosition::assetId) != expectedAssetIds) return false
        val rawTargetWeights = expectedAssetIds.associateWith { assetId ->
            (previousById[assetId]?.targetWeight ?: 0.0) *
                (1.0 - incrementalCompletionFraction) +
                (finalById[assetId]?.targetWeight ?: 0.0) * incrementalCompletionFraction
        }
        if (rawTargetWeights.values.any { weight -> !weight.isFinite() || weight <= 0.0 }) {
            return false
        }
        val repairedTargetWeights = rawTargetWeights.toMutableMap()
        val largestAssetId = expectedAssetIds.maxBy(rawTargetWeights::getValue)
        repairedTargetWeights[largestAssetId] = repairedTargetWeights.getValue(largestAssetId) +
            (1.0 - rawTargetWeights.values.sum())
        val epsilon = ReferencePortfolioState.WEIGHT_EPSILON * 4.0
        return positions.all { position ->
            abs(position.targetWeight - repairedTargetWeights.getValue(position.assetId)) <= epsilon
        }
    }

    // Intrinsic validation has no benchmark definition from which to resolve a market clock. Use
    // the inclusive KRX/NYSE date envelope; catalog-aware validation below uses the exact schedule.
    val intrinsicMarketDates = listOf(
        GameCalendar.marketLocalDateTime(Market.KOSPI, state.currentTime).date,
        GameCalendar.marketLocalDateTime(Market.NYSE, state.currentTime).date,
    )
    val intrinsicEarliestDate = intrinsicMarketDates.min()
    val intrinsicLatestDate = intrinsicMarketDates.max()
    val recordsByPortfolio = state.referencePortfolioLedger.groupBy(ReferencePortfolioRecord::portfolioId)
    val canonicalBootstrapStates = mutableMapOf<String, ReferencePortfolioState>()
    for ((portfolioId, portfolio) in state.referencePortfolioStates) {
        val definition = definitionsByPortfolioId?.get(portfolioId)
        if (definitionsByPortfolioId != null && definition == null) {
            return "기준 포트폴리오 상태에 현재 카탈로그가 알 수 없는 ID가 있습니다."
        }
        val compiled = compiledByPortfolioId?.get(portfolioId)
        val methodology = compiled?.profile
        val policy = compiled?.policy
        val schedule = compiled?.schedule
        val constraints = constraintsByPortfolioId?.get(portfolioId)
        val scheduledCurrentDate = schedule?.marketDate(state.currentTime)
        val currentDate = scheduledCurrentDate ?: intrinsicLatestDate
        val earliestCurrentDate = scheduledCurrentDate ?: intrinsicEarliestDate
        if (portfolio.portfolioId != portfolioId ||
            portfolioId != ReferencePortfolioEngine.portfolioIdFor(portfolio.benchmarkRef) ||
            definition?.ref?.let { ref -> ref != portfolio.benchmarkRef } == true ||
            portfolio.asOf != state.currentTime
        ) {
            return "기준 포트폴리오의 ID·벤치마크·기준 시각이 일치하지 않습니다."
        }
        val initialAction = if (methodology != null && schedule != null) {
            runCatching { schedule.initialScheduledAction(methodology) }.getOrNull()
                ?: return "기준 포트폴리오의 최초 정기 행동을 복원할 수 없습니다."
        } else {
            null
        }
        val canonicalBootstrap = if (definition != null && schedule != null) {
            val campaignStart = GameCalendar.startInstant
            runCatching {
                engine.initialState(
                    portfolioId = portfolioId,
                    definition = definition,
                    atDate = schedule.marketDate(campaignStart),
                    at = campaignStart,
                )
            }.getOrNull()
                ?.also { bootstrap -> canonicalBootstrapStates[portfolioId] = bootstrap }
                ?: return "기준 포트폴리오의 캠페인 시작 구성을 재구축할 수 없습니다."
        } else {
            null
        }
        val hasRecurringScheduledReconstitution =
            policy?.hasRecurringScheduledReconstitution == true
        val nextReconstitutionAction = if (
            methodology != null && schedule != null && hasRecurringScheduledReconstitution
        ) {
            runCatching {
                schedule.nextScheduledAction(
                    profile = methodology,
                    afterExclusive = portfolio.lastReconstitutionDate,
                    kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
                )
            }.getOrNull() ?: return "기준 포트폴리오의 다음 정기 재구성을 복원할 수 없습니다."
        } else {
            null
        }
        val nextScheduledAction = if (methodology != null && schedule != null) {
            runCatching {
                schedule.nextScheduledAction(
                    profile = methodology,
                    afterExclusive = portfolio.lastRebalanceDate,
                )
            }.getOrNull() ?: return "기준 포트폴리오의 다음 정기 행동을 복원할 수 없습니다."
        } else {
            null
        }
        if (methodology != null && schedule != null && initialAction != null &&
            nextScheduledAction != null
        ) {
            val lastReconstitutionAction = runCatching {
                schedule.scheduledActionOn(methodology, portfolio.lastReconstitutionDate)
            }.getOrNull()
            val storedNextReconstitutionAction = portfolio.nextReconstitutionDate?.let { date ->
                runCatching { schedule.scheduledActionOn(methodology, date) }.getOrNull()
            }
            val storedNextScheduledAction = runCatching {
                schedule.scheduledActionOn(methodology, portfolio.nextRebalanceDate)
            }.getOrNull()
            val reconstitutionScheduleIsCanonical = if (hasRecurringScheduledReconstitution) {
                nextReconstitutionAction != null &&
                    portfolio.nextReconstitutionDate != null &&
                    nextReconstitutionAction.kind ==
                    ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION &&
                    storedNextReconstitutionAction == nextReconstitutionAction &&
                    portfolio.nextReconstitutionDate >= earliestCurrentDate &&
                    portfolio.nextReconstitutionDate == nextReconstitutionAction.effectiveDate
            } else {
                portfolio.nextReconstitutionDate == null &&
                    storedNextReconstitutionAction == null &&
                    portfolio.lastReconstitutionDate == initialAction.effectiveDate &&
                    nextScheduledAction.kind !=
                    ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
            }
            if (!reconstitutionScheduleIsCanonical ||
                initialAction.kind != ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION ||
                initialAction.effectiveDate != methodology.effectiveFrom ||
                storedNextScheduledAction != nextScheduledAction ||
                portfolio.lastReconstitutionDate !in initialAction.effectiveDate..currentDate ||
                portfolio.lastRebalanceDate !in initialAction.effectiveDate..currentDate ||
                portfolio.nextRebalanceDate < earliestCurrentDate ||
                lastReconstitutionAction?.kind != ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION ||
                portfolio.nextRebalanceDate != nextScheduledAction.effectiveDate
            ) {
                return "기준 포트폴리오의 이전·다음 정기 행동이 provider 일정과 일치하지 않습니다."
            }
            if (portfolio.revision == 0L) {
                val bootstrap = checkNotNull(canonicalBootstrap)
                if (portfolio.lastReconstitutionDate != bootstrap.lastReconstitutionDate ||
                    portfolio.lastRebalanceDate != bootstrap.lastRebalanceDate ||
                    portfolio.lastAppliedActionKind != bootstrap.lastAppliedActionKind
                ) {
                    return "원장 생성 전 기준 포트폴리오의 마지막 일정·행동이 캠페인 시작 원본과 다릅니다."
                }
            }
        }

        if (policy != null && definition != null) {
            if (policy.usesPathState) {
                val canonicalPathState = runCatching {
                    engine.canonicalMethodologyPathStateAt(definition, state.currentTime)
                }.getOrNull() ?: return "기준 포트폴리오의 방법론 경로 상태를 재구축할 수 없습니다."
                if (portfolio.methodologyPathState != canonicalPathState) {
                    return "기준 포트폴리오의 방법론 경로 상태가 canonical 분기 계보와 다릅니다."
                }
            } else if (portfolio.methodologyPathState != EquityMethodologyPathState.EMPTY ||
                portfolio.pendingPlans.any { plan ->
                    plan.methodologyPathState != EquityMethodologyPathState.EMPTY
                }
            ) {
                return "경로 비의존 방법론에는 경로 상태를 저장할 수 없습니다."
            }
        }
        if (portfolio.methodologyPathState.entries.any { entry ->
                !engine.hasCanonicalReferenceAssetId(entry.assetId)
            } || portfolio.pendingPlans.any { plan ->
                plan.methodologyPathState.entries.any { entry ->
                    !engine.hasCanonicalReferenceAssetId(entry.assetId)
                }
            }
        ) {
            return "기준 포트폴리오의 방법론 경로 상태에 캠페인 기준자산이 아닌 ID가 있습니다."
        }

        val currentCapsApply = !portfolio.lastAppliedActionKind.allowsTemporaryTargetCapBreach()
        validatePositions(
            positions = portfolio.positions,
            constraints = constraints,
            enteredNoLaterThan = currentDate,
            label = "현재 기준 포트폴리오",
            enforceTargetCaps = currentCapsApply,
        )?.let { violation -> return violation }

        val currentIds = portfolio.positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        if (portfolio.pendingPlans != portfolio.pendingPlans.sortedWith(REFERENCE_PLAN_ORDER)) {
            return "대기 중 기준 포트폴리오 계획의 효력일·실행 우선순위 순서가 유효하지 않습니다."
        }
        if (portfolio.pendingPlans.filter { plan -> plan.corporateAction == null }
                .filter { plan ->
                    plan.kind != ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION
                }
                .groupingBy(ReferencePortfolioPlan::kind).eachCount()
                .values.any { count -> count > 1 }
        ) {
            return "같은 종류의 비기업행동 기준 포트폴리오 계획을 둘 이상 대기시킬 수 없습니다."
        }
        if (methodology != null && schedule != null && nextScheduledAction != null) {
            if (hasRecurringScheduledReconstitution) {
                val canonicalNextReconstitution = nextReconstitutionAction
                    ?: return "기준 포트폴리오의 다음 정기 재구성을 복원할 수 없습니다."
                val selectionSnapshotShouldExist =
                    schedule.hasReachedRegularClose(
                        canonicalNextReconstitution.selectionDate,
                        state.currentTime,
                    ) && !schedule.hasReachedRegularClose(
                        canonicalNextReconstitution.weightReferenceDate,
                        state.currentTime,
                    )
                val hasSelectionSnapshot = portfolio.pendingSelectionDate != null &&
                    portfolio.pendingSelectionIncumbentAssetIds != null
                if (selectionSnapshotShouldExist) {
                    if (!hasSelectionSnapshot ||
                        portfolio.pendingSelectionDate != canonicalNextReconstitution.selectionDate
                    ) {
                        return "대기 중 선택일 구성 스냅샷이 다음 정기 재구성의 선택 종가 단계와 다릅니다."
                    }
                } else if (hasSelectionSnapshot) {
                    return "대기 중 선택일 구성 스냅샷이 허용된 선택 종가·기준 종가 사이 밖에 남아 있습니다."
                }
                if (portfolio.pendingSelectionIncumbentAssetIds?.any { assetId ->
                        !engine.hasCanonicalReferenceAssetId(assetId)
                    } == true
                ) {
                    return "대기 중 선택일 구성 스냅샷에 캠페인 기준자산 원본이 아닌 ID가 있습니다."
                }
                val pendingReconstitution = portfolio.pendingPlans.singleOrNull { plan ->
                    plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
                }
                val reconstitutionShouldBePending = schedule.hasReachedRegularClose(
                    canonicalNextReconstitution.weightReferenceDate,
                    state.currentTime,
                )
                if (schedule.hasPassedRegularOpen(
                        canonicalNextReconstitution.effectiveDate,
                        state.currentTime,
                    ) || (pendingReconstitution != null) != reconstitutionShouldBePending
                ) {
                    return "정기 재구성 계획의 생성·적용 단계가 현재 시각과 다릅니다."
                }
                val transitionSteps = runCatching {
                    checkNotNull(policy).scheduledReconstitutionTransitionSteps(
                        profile = methodology,
                        action = canonicalNextReconstitution,
                    )
                }.getOrNull() ?: return "정기 재구성의 단계별 전환 일정을 복원할 수 없습니다."
                if (transitionSteps.size >= ReferencePortfolioState.MAX_PENDING_PLANS ||
                    transitionSteps != transitionSteps.sortedBy { step -> step.effectiveDate } ||
                    transitionSteps.map { step -> step.effectiveDate }.distinct().size !=
                    transitionSteps.size ||
                    transitionSteps.zipWithNext().any { (left, right) ->
                        left.completionFraction >= right.completionFraction
                    } || transitionSteps.any { step ->
                        !step.completionFraction.isFinite() ||
                            step.completionFraction <= 0.0 || step.completionFraction >= 1.0 ||
                            step.effectiveDate <= canonicalNextReconstitution.weightReferenceDate ||
                            step.effectiveDate >= canonicalNextReconstitution.effectiveDate ||
                            !schedule.isTradingDate(step.effectiveDate)
                    }
                ) {
                    return "정기 재구성의 단계별 전환 일정이 provider 제약을 위반했습니다."
                }
                val expectedPendingTransitionSteps = if (reconstitutionShouldBePending) {
                    transitionSteps.filterNot { step ->
                        schedule.hasPassedRegularOpen(step.effectiveDate, state.currentTime)
                    }
                } else {
                    emptyList()
                }
                val pendingTransitionPlans = portfolio.pendingPlans.filter { plan ->
                    plan.kind ==
                        ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION
                }
                if (pendingTransitionPlans.size != expectedPendingTransitionSteps.size ||
                    pendingTransitionPlans.zip(expectedPendingTransitionSteps).any { (plan, step) ->
                        plan.selectionDate != canonicalNextReconstitution.selectionDate ||
                            plan.weightReferenceDate !=
                            canonicalNextReconstitution.weightReferenceDate ||
                            plan.effectiveDate != step.effectiveDate
                    }
                ) {
                    return "대기 중 정기 재구성 전환 계획의 단계·일정이 canonical 흐름과 다릅니다."
                }
                if (reconstitutionShouldBePending && transitionSteps.isNotEmpty()) {
                    val appliedTransitionSteps = transitionSteps.filter { step ->
                        schedule.hasPassedRegularOpen(step.effectiveDate, state.currentTime)
                    }
                    val appliedTransitionRecords = recordsByPortfolio[portfolioId].orEmpty()
                        .filter { record ->
                            record.kind == ReferencePortfolioActionKind
                                .SCHEDULED_RECONSTITUTION_TRANSITION &&
                                record.selectionDate == canonicalNextReconstitution.selectionDate &&
                                record.weightReferenceDate ==
                                canonicalNextReconstitution.weightReferenceDate
                        }
                    if (appliedTransitionRecords.map(ReferencePortfolioRecord::effectiveDate) !=
                        appliedTransitionSteps.map { step -> step.effectiveDate }
                    ) {
                        return "정기 재구성 전환 원장의 적용 단계가 canonical 흐름과 다릅니다."
                    }

                    val stagedKinds = setOf(
                        ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION,
                        ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
                    )
                    val hasInterleavedAction = portfolio.pendingPlans.any { plan ->
                        plan.effectiveDate > canonicalNextReconstitution.selectionDate &&
                            plan.effectiveDate <= canonicalNextReconstitution.effectiveDate &&
                            plan.kind !in stagedKinds
                    } || recordsByPortfolio[portfolioId].orEmpty().any { record ->
                        record.effectiveDate > canonicalNextReconstitution.selectionDate &&
                            record.effectiveDate <= canonicalNextReconstitution.effectiveDate &&
                            record.kind !in stagedKinds
                    }
                    if (!hasInterleavedAction && appliedTransitionSteps.isNotEmpty()) {
                        val finalPlan = pendingReconstitution
                            ?: return "단계별 전환의 최종 정기 재구성 계획이 없습니다."
                        val initialAssetIds = finalPlan.selectionIncumbentAssetIds?.toSet()
                            ?: return "단계별 전환의 초기 구성 근거가 없습니다."
                        val finalById = finalPlan.positions.associateBy(
                            ReferencePortfolioPosition::assetId,
                        )
                        val appliedFraction = appliedTransitionSteps.lastOrNull()
                            ?.completionFraction ?: 0.0
                        val observedById = portfolio.positions.associateBy(
                            ReferencePortfolioPosition::assetId,
                        )
                        val expectedObservedAssetIds = initialAssetIds + finalById.keys
                        if (observedById.keys != expectedObservedAssetIds) {
                            return "현재 구성종목이 정기 재구성 전환의 적용 단계와 다릅니다."
                        }
                        val initialTargetWeights = buildMap {
                            val remainingFraction = 1.0 - appliedFraction
                            val epsilon = ReferencePortfolioState.WEIGHT_EPSILON * 4.0
                            for (assetId in initialAssetIds + finalById.keys) {
                                val observedWeight = observedById[assetId]?.targetWeight ?: 0.0
                                val finalWeight = finalById[assetId]?.targetWeight ?: 0.0
                                val initialWeight =
                                    (observedWeight - appliedFraction * finalWeight) /
                                        remainingFraction
                                if (assetId in initialAssetIds) {
                                    if (!initialWeight.isFinite() || initialWeight <= 0.0) {
                                        return "정기 재구성 전환의 초기 목표 비중을 복원할 수 없습니다."
                                    }
                                    put(assetId, initialWeight)
                                } else if (abs(initialWeight) > epsilon) {
                                    return "현재 목표 비중이 정기 재구성 전환율과 다릅니다."
                                }
                            }
                        }
                        if (abs(initialTargetWeights.values.sum() - 1.0) >
                            ReferencePortfolioState.WEIGHT_EPSILON * 4.0
                        ) {
                            return "정기 재구성 전환의 초기 목표 비중 합이 유효하지 않습니다."
                        }
                        val appliedStep = appliedTransitionSteps.last()
                        if (!transitionTargetsMatchCanonicalBlend(
                                positions = portfolio.positions,
                                initialTargetWeights = initialTargetWeights,
                                finalPositions = finalPlan.positions,
                                completionFraction = appliedStep.completionFraction,
                            )
                        ) {
                            return "현재 구성·순위·목표 비중이 적용된 정기 재구성 전환율과 다릅니다."
                        }
                    }
                    val finalPlan = pendingReconstitution
                        ?: return "단계별 전환의 최종 정기 재구성 계획이 없습니다."
                    pendingTransitionPlans.zip(expectedPendingTransitionSteps)
                        .forEach { (plan, step) ->
                            val previousFraction = transitionSteps.lastOrNull { candidate ->
                                candidate.effectiveDate < step.effectiveDate
                            }?.completionFraction ?: 0.0
                            val incrementalFraction =
                                (step.completionFraction - previousFraction) /
                                    (1.0 - previousFraction)
                            val transitionBaselineWeights = plan.transitionBaselineWeights
                                ?: return "대기 중 정기 재구성 전환 계획의 고정 baseline이 없습니다."
                            if (!transitionTargetsMatchCanonicalBlend(
                                    positions = plan.positions,
                                    initialTargetWeights = transitionBaselineWeights,
                                    finalPositions = finalPlan.positions,
                                    completionFraction = incrementalFraction,
                                )
                            ) {
                                return "대기 중 정기 재구성 전환 계획의 목표 비중이 고정 baseline 계보와 다릅니다."
                            }
                        }
                    pendingTransitionPlans.zipWithNext().forEach { (previousPlan, nextPlan) ->
                        val hasInterleavedPendingAction = portfolio.pendingPlans.any { pending ->
                            pending.kind !in stagedKinds &&
                                pending.effectiveDate > previousPlan.effectiveDate &&
                                pending.effectiveDate <= nextPlan.effectiveDate
                        }
                        if (!hasInterleavedPendingAction) {
                            val previousFraction = transitionSteps.single { step ->
                                step.effectiveDate == previousPlan.effectiveDate
                            }.completionFraction
                            val nextFraction = transitionSteps.single { step ->
                                step.effectiveDate == nextPlan.effectiveDate
                            }.completionFraction
                            val incrementalFraction = (nextFraction - previousFraction) /
                                (1.0 - previousFraction)
                            if (!transitionTargetsFollowPreviousStage(
                                    positions = nextPlan.positions,
                                    previousPositions = previousPlan.positions,
                                    finalPositions = finalPlan.positions,
                                    incrementalCompletionFraction = incrementalFraction,
                                )
                            ) {
                                return "대기 중 정기 재구성 전환 계획의 목표 비중이 선행 단계 계보와 다릅니다."
                            }
                        }
                    }
                }
            } else if (portfolio.pendingSelectionDate != null ||
                portfolio.pendingSelectionIncumbentAssetIds != null ||
                portfolio.pendingPlans.any { plan ->
                    plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
                }
            ) {
                return "비정기 구성변경 방법론에는 선택 스냅샷이나 정기 재구성 계획을 대기시킬 수 없습니다."
            }
            if (nextScheduledAction.kind == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT) {
                val pendingReweight = portfolio.pendingPlans.singleOrNull { plan ->
                    plan.kind == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT
                }
                val reweightShouldBePending = schedule.hasReachedRegularClose(
                    nextScheduledAction.weightReferenceDate,
                    state.currentTime,
                )
                if (schedule.hasPassedRegularOpen(
                        nextScheduledAction.effectiveDate,
                        state.currentTime,
                    ) || (pendingReweight != null) != reweightShouldBePending
                ) {
                    return "정기 비중조정 계획의 생성·적용 단계가 현재 시각과 다릅니다."
                }
            }
        }

        var pendingBaselinePositions = portfolio.positions
        var pendingBaselineIds = currentIds
        var pendingBaselinePathState = portfolio.methodologyPathState
        for (plan in portfolio.pendingPlans) {
            val violatesPlanWindow = if (schedule != null) {
                !schedule.hasReachedRegularClose(plan.weightReferenceDate, state.currentTime) ||
                    schedule.hasPassedRegularOpen(plan.effectiveDate, state.currentTime)
            } else {
                plan.weightReferenceDate > currentDate || plan.effectiveDate < earliestCurrentDate
            }
            if (violatesPlanWindow) {
                return "대기 중 기준 포트폴리오 계획이 기준일 종가·효력일 개장 시각과 다릅니다."
            }
            if (!referenceCorporateActionLinkIsValid(
                    kind = plan.kind,
                    selectionDate = plan.selectionDate,
                    weightReferenceDate = plan.weightReferenceDate,
                    effectiveDate = plan.effectiveDate,
                    corporateAction = plan.corporateAction,
                )
            ) {
                return "대기 중 기준 포트폴리오 계획의 기업행동 종류·관측일·효력일 연결이 유효하지 않습니다."
            }
            if (!referenceWeightingInputIsValid(plan)) {
                return "대기 중 기준 포트폴리오 계획의 기준일 종가 시가가치 입력이 유효하지 않습니다."
            }
            if (!referenceScheduledSelectionBasisIsValid(plan)) {
                return "대기 중 기준 포트폴리오 계획의 정기 재구성 선택 근거가 유효하지 않습니다."
            }
            val isProviderWeightingPlan = when (plan.kind) {
                ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
                ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
                ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT,
                -> true
                ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION,
                ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION,
                ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL,
                ReferencePortfolioActionKind.CONSTITUENT_MERGER,
                ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
                ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
                ReferencePortfolioActionKind.TERMINAL_REMOVAL,
                -> false
            }
            val usesCanonicalScheduledWeightingBasis =
                plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION &&
                    policy?.usesSelectionSnapshotMarketValuesForScheduledReconstitution == true
            if (definition != null && isProviderWeightingPlan &&
                !usesCanonicalScheduledWeightingBasis &&
                !referenceTargetWeightsMatch(
                    plan.positions,
                    engine.canonicalWeightingTargetWeights(definition, plan),
                )
            ) {
                return "대기 중 기준 포트폴리오 계획의 목표 비중이 저장된 기준일 시가가치와 다릅니다."
            }
            if (definition != null &&
                plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
            ) {
                val canonicalReconstitution = engine.canonicalScheduledReconstitution(
                    definition = definition,
                    plan = plan,
                    previousPathState = pendingBaselinePathState,
                    knownAt = state.currentTime,
                ) ?: return "대기 중 정기 재구성 계획의 canonical 결정을 재구축할 수 없습니다."
                if (!referenceSelectionRanksMatch(
                        plan.positions,
                        canonicalReconstitution.selectionRanks,
                    ) || plan.methodologyPathState != canonicalReconstitution.nextPathState ||
                    plan.selectionAvailabilityDate !=
                    canonicalReconstitution.selectionAvailabilityDate
                ) {
                    return "대기 중 정기 재구성 계획의 종목·선정 순위·경로 상태가 저장된 선택 근거와 다릅니다."
                }
                val permitsFinalStagedWeightReset = if (
                    methodology != null && policy != null && schedule != null
                ) {
                    val action = runCatching {
                        schedule.scheduledActionOn(methodology, plan.effectiveDate)
                    }.getOrNull()
                    action?.let { scheduledAction ->
                        runCatching {
                            policy.scheduledReconstitutionTransitionSteps(
                                profile = methodology,
                                action = scheduledAction,
                            )
                        }.getOrNull()?.lastOrNull()?.let { lastTransitionStep ->
                            schedule.hasReachedRegularClose(
                                lastTransitionStep.effectiveDate,
                                state.currentTime,
                            )
                        }
                    } == true
                } else {
                    false
                }
                if (usesCanonicalScheduledWeightingBasis &&
                    (!referenceMarketValuesMatch(
                        actualMarketValues = plan.weightReferenceMarketValues,
                        canonicalMarketValues = canonicalReconstitution.weightReferenceMarketValues,
                    ) || !referenceTargetWeightsMatch(
                        positions = plan.positions,
                        canonicalTargetWeights = canonicalReconstitution.targetWeights,
                    ) || !referencePositionsMatchCanonical(
                        positions = plan.positions,
                        canonicalPositions = canonicalReconstitution.canonicalFinalPositions,
                        permitsFinalStagedWeightReset = permitsFinalStagedWeightReset,
                    ))
                ) {
                    return "대기 중 정기 재구성 계획의 기준일 시가가치·구성 drift가 canonical weighting 근거와 다릅니다."
                }
                if (usesCanonicalScheduledWeightingBasis) {
                    val actualTransitionPlansByDate = portfolio.pendingPlans
                        .filter { pending ->
                            pending.kind == ReferencePortfolioActionKind
                                .SCHEDULED_RECONSTITUTION_TRANSITION &&
                                pending.selectionDate == plan.selectionDate &&
                                pending.weightReferenceDate == plan.weightReferenceDate
                        }
                        .associateBy(ReferencePortfolioPlan::effectiveDate)
                    val canonicalTransitionPositions =
                        canonicalReconstitution.canonicalTransitionPositionsByEffectiveDate
                    if (actualTransitionPlansByDate.keys != canonicalTransitionPositions.keys ||
                        actualTransitionPlansByDate.any { (effectiveDate, transitionPlan) ->
                            !referenceTransitionStructureMatchesCanonical(
                                positions = transitionPlan.positions,
                                canonicalPositions = canonicalTransitionPositions[effectiveDate],
                            )
                        }
                    ) {
                        return "대기 중 정기 재구성 전환 계획의 구성 drift가 canonical 잔여 전환 바스켓과 다릅니다."
                    }
                }
            }
            if (plan.kind != ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION &&
                plan.methodologyPathState != pendingBaselinePathState
            ) {
                return "정기 재구성 외 계획은 선행 방법론 경로 상태를 변경할 수 없습니다."
            }
            if (methodology != null && schedule != null) {
                validateReferencePortfolioActionDates(
                    methodology = methodology,
                    policy = checkNotNull(policy),
                    schedule = schedule,
                    kind = plan.kind,
                    selectionDate = plan.selectionDate,
                    weightReferenceDate = plan.weightReferenceDate,
                    effectiveDate = plan.effectiveDate,
                    currentDate = currentDate,
                    pending = true,
                    corporateAction = plan.corporateAction,
                )?.let { violation -> return violation }
            }
            if (plan.id != referencePortfolioPlanId(plan) ||
                plan.portfolioId != portfolioId ||
                plan.benchmarkRef != portfolio.benchmarkRef ||
                plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION &&
                plan.effectiveDate != portfolio.nextReconstitutionDate ||
                plan.kind == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT &&
                plan.effectiveDate != portfolio.nextRebalanceDate
            ) {
                return "대기 중 기준 포트폴리오 계획의 ID·벤치마크·다음 일정 계보가 다릅니다."
            }
            if (constraints?.scheduledSelectionCount?.let { expectedCount ->
                    plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION &&
                        plan.positions.size != expectedCount
                } == true
            ) {
                return "정기 재구성 계획은 방법론 목표 종목 수를 정확히 복원해야 합니다."
            }
            validatePositions(
                positions = plan.positions,
                constraints = constraints,
                enteredNoLaterThan = plan.effectiveDate,
                label = "대기 중 기준 포트폴리오 계획",
                enforceTargetCaps = !plan.kind.allowsTemporaryTargetCapBreach(),
            )?.let { violation -> return violation }
            if (plan.corporateAction?.let { action ->
                    listOfNotNull(action.primaryAssetId, action.secondaryAssetId).any { assetId ->
                        !engine.hasCanonicalReferenceAssetId(assetId)
                    } || definition != null && !engine.isCanonicalCorporateAction(definition, action)
                } == true
            ) {
                return "대기 중 기준 포트폴리오 기업행동이 캠페인 seed 원본과 다릅니다."
            }
            val reconstructedPlan = runCatching {
                ReferencePortfolioPlan(
                    id = plan.id,
                    portfolioId = plan.portfolioId,
                    benchmarkRef = plan.benchmarkRef,
                    kind = plan.kind,
                    selectionDate = plan.selectionDate,
                    weightReferenceDate = plan.weightReferenceDate,
                    effectiveDate = plan.effectiveDate,
                    selectionIncumbentAssetIds = plan.selectionIncumbentAssetIds?.toList(),
                    selectionAvailabilityDate = plan.selectionAvailabilityDate,
                    positions = plan.positions.map(::reconstructPosition),
                    methodologyPathState = reconstructMethodologyPathState(
                        plan.methodologyPathState,
                    ),
                    addedAssetIds = plan.addedAssetIds.toList(),
                    removedAssetIds = plan.removedAssetIds.toList(),
                    transitionBaselineWeights = plan.transitionBaselineWeights?.let { weights ->
                        buildMap {
                            weights.forEach { (assetId, weight) -> put(assetId, weight) }
                        }
                    },
                    weightReferenceMarketValues = plan.weightReferenceMarketValues?.let { values ->
                        buildMap {
                            values.forEach { (assetId, marketValue) -> put(assetId, marketValue) }
                        }
                    },
                    corporateAction = plan.corporateAction?.let(::reconstructCorporateAction),
                )
            }.getOrNull()
            if (reconstructedPlan != plan ||
                reconstructedPlan.weightReferenceMarketValues?.entries?.toList() !=
                plan.weightReferenceMarketValues?.entries?.toList() ||
                reconstructedPlan.transitionBaselineWeights?.entries?.toList() !=
                plan.transitionBaselineWeights?.entries?.toList()
            ) {
                return "대기 중 기준 포트폴리오 계획의 일정·구성·편입·편출 조건이 유효하지 않습니다."
            }
            val plannedIds = plan.positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
            if (plan.addedAssetIds != (plannedIds - pendingBaselineIds).sorted() ||
                plan.removedAssetIds != (pendingBaselineIds - plannedIds).sorted()
            ) {
                return "대기 중 기준 포트폴리오 계획의 편입·편출 목록이 선행 계획 결과와 다릅니다."
            }
            when (plan.kind) {
                ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION -> Unit
                ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION -> Unit
                ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION -> if (
                    plan.corporateAction?.kind !in setOf(
                        ReferencePortfolioCorporateActionKind.MERGER,
                        ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL,
                    ) ||
                    plan.corporateAction?.primaryAssetId !in pendingBaselineIds ||
                    plan.corporateAction?.primaryAssetId !in plannedIds ||
                    plan.removedAssetIds.isNotEmpty() || plan.addedAssetIds.size > 1 ||
                    plannedIds != pendingBaselineIds + plan.addedAssetIds ||
                    plan.addedAssetIds.any { assetId ->
                        plan.positions.single { position -> position.assetId == assetId }.enteredOn !=
                            plan.corporateAction?.effectiveDate
                    }
                ) {
                    return "기업행동 전환 계획은 대상 종목을 유지하며 canonical 대체 종목만 단계 편입해야 합니다."
                }
                ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
                ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT,
                -> if (plannedIds != pendingBaselineIds) {
                    return "비중조정 계획은 선행 계획의 구성종목을 바꿀 수 없습니다."
                }
                ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL -> if (
                    plan.addedAssetIds.isNotEmpty() || plan.removedAssetIds.isEmpty() ||
                    plannedIds != pendingBaselineIds - plan.removedAssetIds.toSet()
                ) {
                    return "특별 제거 계획은 대체 편입 없이 선행 계획 구성종목만 제거해야 합니다."
                }
                ReferencePortfolioActionKind.CONSTITUENT_MERGER -> if (
                    plan.removedAssetIds.isEmpty() ||
                    plan.corporateAction?.kind != ReferencePortfolioCorporateActionKind.MERGER ||
                    plan.corporateAction.primaryAssetId !in plan.removedAssetIds ||
                    plannedIds !=
                    (pendingBaselineIds - plan.removedAssetIds.toSet()) + plan.addedAssetIds
                ) {
                    return "합병 계획은 대상 종목을 제거하고 canonical 결정의 대체 종목만 편입해야 합니다."
                }
                ReferencePortfolioActionKind.SPIN_OFF_ADDITION -> if (
                    plan.corporateAction?.kind != ReferencePortfolioCorporateActionKind.SPIN_OFF ||
                    plan.corporateAction.primaryAssetId !in pendingBaselineIds ||
                    plan.addedAssetIds != listOfNotNull(plan.corporateAction.secondaryAssetId) ||
                    plan.removedAssetIds.isNotEmpty() ||
                    plannedIds != pendingBaselineIds + plan.addedAssetIds
                ) {
                    return "분사 편입 계획은 모기업을 유지하며 canonical 자회사를 한 종목 편입해야 합니다."
                }
                ReferencePortfolioActionKind.SPIN_OFF_REMOVAL -> if (
                    plan.corporateAction?.kind != ReferencePortfolioCorporateActionKind.SPIN_OFF ||
                    plan.addedAssetIds.isNotEmpty() ||
                    plan.removedAssetIds != listOfNotNull(plan.corporateAction.secondaryAssetId) ||
                    plannedIds != pendingBaselineIds - plan.removedAssetIds.toSet()
                ) {
                    return "분사 후속 제거 계획은 앞서 편입된 자회사만 제거해야 합니다."
                }
                ReferencePortfolioActionKind.TERMINAL_REMOVAL -> if (
                    plan.corporateAction?.kind != ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL ||
                    plan.removedAssetIds.isEmpty() ||
                    plan.corporateAction.primaryAssetId !in plan.removedAssetIds ||
                    plannedIds !=
                    (pendingBaselineIds - plan.removedAssetIds.toSet()) + plan.addedAssetIds
                ) {
                    return "소멸 제거 계획은 대상 종목을 제거하고 canonical 결정의 대체 종목만 편입해야 합니다."
                }
            }
            if (definition != null && plan.corporateAction != null) {
                val action = requireNotNull(plan.corporateAction)
                val transitionSteps = engine.canonicalCorporateActionTransitionSteps(
                    definition,
                    action,
                ) ?: return "대기 중 기준 포트폴리오 기업행동의 provider 전환 단계를 복원할 수 없습니다."
                val isStagedCompletion = transitionSteps.isNotEmpty() &&
                    plan.kind == action.primaryReferenceActionKind() &&
                    plan.effectiveDate == transitionSteps.last().effectiveDate
                if (plan.kind == ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION) {
                    val step = transitionSteps.singleOrNull { candidate ->
                        candidate.effectiveDate == plan.effectiveDate &&
                            candidate.completionFraction < 1.0
                    } ?: return "대기 중 기업행동 전환 계획이 canonical 단계와 다릅니다."
                    val finalPlan = portfolio.pendingPlans.singleOrNull { candidate ->
                        candidate.corporateAction?.eventId == action.eventId &&
                            candidate.kind == action.primaryReferenceActionKind() &&
                            candidate.effectiveDate == transitionSteps.last().effectiveDate
                    } ?: return "대기 중 기업행동 전환 계획의 최종 완료 계획이 없습니다."
                    val previousFraction = transitionSteps.lastOrNull { candidate ->
                        candidate.effectiveDate < step.effectiveDate
                    }?.completionFraction ?: 0.0
                    val incrementalFraction = (step.completionFraction - previousFraction) /
                        (1.0 - previousFraction)
                    val baselineWeights = plan.transitionBaselineWeights
                        ?: return "대기 중 기업행동 전환 계획의 고정 baseline이 없습니다."
                    if (!transitionTargetsMatchCanonicalBlend(
                            positions = plan.positions,
                            initialTargetWeights = baselineWeights,
                            finalPositions = finalPlan.positions,
                            completionFraction = incrementalFraction,
                        )
                    ) {
                        return "대기 중 기업행동 전환 계획의 목표 비중이 30/70 단계 계보와 다릅니다."
                    }
                    if (plan.addedAssetIds.isNotEmpty()) {
                        val decision = engine.canonicalCorporateActionDecision(
                            definition,
                            action,
                            pendingBaselineIds,
                        )
                        if (decision == null ||
                            decision.addedAssetIds != plan.addedAssetIds.toSet() ||
                            decision.removedAssetIds != setOf(action.primaryAssetId)
                        ) {
                            return "대기 중 기업행동 전환의 최초 대체 편입이 방법론 결정과 다릅니다."
                        }
                    }
                } else if (plan.kind != ReferencePortfolioActionKind.SPIN_OFF_REMOVAL &&
                    !isStagedCompletion
                ) {
                    val decision = engine.canonicalCorporateActionDecision(
                        definition,
                        action,
                        pendingBaselineIds,
                    )
                    if (decision == null ||
                        decision.addedAssetIds != plan.addedAssetIds.toSet() ||
                        decision.removedAssetIds != plan.removedAssetIds.toSet()
                    ) {
                        return "대기 중 기준 포트폴리오 기업행동의 편입·편출이 방법론 결정과 다릅니다."
                    }
                }
                if (isStagedCompletion) {
                    val replacementPosition = plan.positions.maxBy(
                        ReferencePortfolioPosition::selectionRank,
                    )
                    val originalPositions = pendingBaselinePositions.filterNot { position ->
                        position.assetId == replacementPosition.assetId
                    }
                    val decision = engine.canonicalCorporateActionDecision(
                        definition,
                        action,
                        originalPositions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId),
                    )
                    val canonicalTargetWeights = engine.canonicalCorporateActionTargetWeights(
                        definition,
                        action,
                        originalPositions,
                        plan.kind,
                        plan.weightReferenceMarketValues,
                    )
                    if (replacementPosition.enteredOn != action.effectiveDate ||
                        decision == null ||
                        decision.addedAssetIds != setOf(replacementPosition.assetId) ||
                        decision.removedAssetIds != setOf(action.primaryAssetId) ||
                        !referenceTargetWeightsMatch(plan.positions, canonicalTargetWeights)
                    ) {
                        return "대기 중 기업행동 완료 계획의 대체 종목·목표 비중이 canonical 결정과 다릅니다."
                    }
                }
                if (plan.kind != ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION &&
                    !isStagedCompletion
                ) {
                    val canonicalTargetWeights = engine.canonicalCorporateActionTargetWeights(
                        definition,
                        action,
                        pendingBaselinePositions,
                        plan.kind,
                        plan.weightReferenceMarketValues,
                    )
                    if (!referenceTargetWeightsMatch(plan.positions, canonicalTargetWeights)) {
                        return "대기 중 기준 포트폴리오 기업행동의 목표 비중이 방법론 결정과 다릅니다."
                    }
                }
            }
            pendingBaselinePositions = plan.positions
            pendingBaselineIds = plannedIds
            pendingBaselinePathState = plan.methodologyPathState
        }

        val reconstructed = runCatching {
            ReferencePortfolioState(
                portfolioId = portfolio.portfolioId,
                benchmarkRef = portfolio.benchmarkRef,
                positions = portfolio.positions.map(::reconstructPosition),
                revision = portfolio.revision,
                lastReconstitutionDate = portfolio.lastReconstitutionDate,
                lastRebalanceDate = portfolio.lastRebalanceDate,
                nextReconstitutionDate = portfolio.nextReconstitutionDate,
                nextRebalanceDate = portfolio.nextRebalanceDate,
                pendingSelectionDate = portfolio.pendingSelectionDate,
                pendingSelectionIncumbentAssetIds =
                    portfolio.pendingSelectionIncumbentAssetIds?.toList(),
                methodologyPathState = reconstructMethodologyPathState(
                    portfolio.methodologyPathState,
                ),
                pendingPlans = portfolio.pendingPlans.map { plan ->
                    plan.copy(
                        positions = plan.positions.map(::reconstructPosition),
                        methodologyPathState = reconstructMethodologyPathState(
                            plan.methodologyPathState,
                        ),
                    )
                },
                lastTurnoverRate = portfolio.lastTurnoverRate,
                estimatedAnnualIncomeYield = portfolio.estimatedAnnualIncomeYield,
                asOf = portfolio.asOf,
                lastAppliedActionKind = portfolio.lastAppliedActionKind,
            )
        }.getOrNull()
        if (reconstructed != portfolio) {
            return "기준 포트폴리오 상태의 비중·일정·회전율 조건이 유효하지 않습니다."
        }
    }

    if (state.referencePortfolioLedger.map(ReferencePortfolioRecord::id).distinct().size !=
        state.referencePortfolioLedger.size
    ) {
        return "기준 포트폴리오 재조정 원장 ID가 중복되었습니다."
    }
    if (recordsByPortfolio.keys.any { portfolioId -> portfolioId !in state.referencePortfolioStates }) {
        return "기준 포트폴리오 원장에 현재 상태가 없는 포트폴리오가 있습니다."
    }
    val savedCorporateActions = buildList {
        state.referencePortfolioStates.values.forEach { portfolio ->
            portfolio.pendingPlans.mapNotNullTo(this) { plan -> plan.corporateAction }
        }
        state.referencePortfolioLedger.mapNotNullTo(this) { record -> record.corporateAction }
    }
    if (savedCorporateActions.groupBy(ReferencePortfolioCorporateAction::eventId)
            .values.any { occurrences -> occurrences.distinct().size != 1 }
    ) {
        return "같은 기준 포트폴리오 기업행동 ID에 서로 다른 사건 사실이 저장되었습니다."
    }
    val savedCorporateExecutionKeys = buildList {
        state.referencePortfolioStates.values.forEach { portfolio ->
            portfolio.pendingPlans.filter { plan -> plan.corporateAction != null }
                .mapTo(this) { plan ->
                    Triple(requireNotNull(plan.corporateAction).eventId, plan.kind, plan.effectiveDate)
                }
        }
        state.referencePortfolioLedger.filter { record -> record.corporateAction != null }
            .mapTo(this) { record ->
                Triple(requireNotNull(record.corporateAction).eventId, record.kind, record.effectiveDate)
            }
    }
    if (savedCorporateExecutionKeys.distinct().size != savedCorporateExecutionKeys.size) {
        return "같은 기업행동의 동일 실행 단계가 계획·원장에 중복 저장되었습니다."
    }
    for ((portfolioId, portfolio) in state.referencePortfolioStates) {
        val definition = definitionsByPortfolioId?.get(portfolioId)
        val compiled = compiledByPortfolioId?.get(portfolioId)
        val methodology = compiled?.profile
        val policy = compiled?.policy
        val schedule = compiled?.schedule
        val constraints = constraintsByPortfolioId?.get(portfolioId)
        val currentDate = schedule?.marketDate(state.currentTime) ?: intrinsicLatestDate
        val records = recordsByPortfolio[portfolioId].orEmpty()
        if (records.size.toLong() != portfolio.revision ||
            records.withIndex().any { (index, record) -> record.revision != index + 1L } ||
            records != records.sortedWith(REFERENCE_RECORD_ORDER) ||
            records.zipWithNext().any { (previous, next) ->
                previous.afterCompositionHash != next.beforeCompositionHash
            }
        ) {
            return "기준 포트폴리오 원장의 revision·효력일·실행 우선순위·구성 해시 계보가 현재 상태와 다릅니다."
        }
        val beforeMembershipByRevision = mutableMapOf<Long, Set<String>>()
        var reverseMembership: Set<String> = portfolio.positions
            .mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        for (record in records.asReversed()) {
            val addedIds = record.addedAssetIds.toSet()
            val removedIds = record.removedAssetIds.toSet()
            if (record.resultingConstituentCount != reverseMembership.size ||
                !reverseMembership.containsAll(addedIds) ||
                removedIds.any(reverseMembership::contains)
            ) {
                return "기준 포트폴리오 원장의 역방향 구성종목 계보가 현재 상태와 다릅니다."
            }
            val beforeMembership = ((reverseMembership - addedIds) + removedIds).toSortedSet()
            beforeMembershipByRevision[record.revision] = beforeMembership
            reverseMembership = beforeMembership
        }
        if (definition != null && schedule != null) {
            val actualPendingCorporateExecutions = portfolio.pendingPlans
                .mapNotNull { plan ->
                    plan.corporateAction?.let { action ->
                        ReferenceCorporateExecutionKey(action.eventId, plan.kind, plan.effectiveDate)
                    }
                }
                .toSet()
            val actualAppliedCorporateExecutions = records
                .mapNotNull { record ->
                    record.corporateAction?.let { action ->
                        ReferenceCorporateExecutionKey(action.eventId, record.kind, record.effectiveDate)
                    }
                }
                .toSet()
            val canonicalEvents = runCatching {
                engine.canonicalCorporateActionsThrough(definition, currentDate)
            }.getOrNull() ?: return "기준 포트폴리오 기업행동 원본을 현재 시점까지 복원할 수 없습니다."
            val announcedEvents = canonicalEvents.filter { event ->
                event.effectiveDate <= GameCalendar.CAMPAIGN_END_DATE &&
                    schedule.hasReachedRegularClose(event.announcementDate, state.currentTime)
            }
            var bootstrapPendingCorporateExecutionCache: Set<ReferenceCorporateExecutionKey>? = null

            fun bootstrapPendingCorporateExecutions(): Set<ReferenceCorporateExecutionKey>? {
                bootstrapPendingCorporateExecutionCache?.let { return it }
                val bootstrap = runCatching {
                    engine.initialState(
                        portfolioId = portfolioId,
                        definition = definition,
                        atDate = schedule.marketDate(GameCalendar.startInstant),
                        at = GameCalendar.startInstant,
                    )
                }.getOrNull() ?: return null
                return bootstrap.pendingPlans.mapNotNullTo(linkedSetOf()) { plan ->
                    plan.corporateAction?.let { action ->
                        ReferenceCorporateExecutionKey(action.eventId, plan.kind, plan.effectiveDate)
                    }
                }.also { executions -> bootstrapPendingCorporateExecutionCache = executions }
            }

            fun membershipBeforeExecution(
                event: ReferencePortfolioCorporateAction,
                kind: ReferencePortfolioActionKind,
                effectiveDate: LocalDate,
            ): Set<String> {
                records.singleOrNull { record ->
                    record.kind == kind && record.effectiveDate == effectiveDate &&
                        record.corporateAction?.eventId == event.eventId
                }?.let { record ->
                    return beforeMembershipByRevision.getValue(record.revision)
                }

                val expectedOrder = ReferenceExecutionOrder(
                    effectiveDate = effectiveDate,
                    kind = kind,
                    corporateEventId = event.eventId,
                )
                var membership: Set<String> = portfolio.positions
                    .mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
                records.asReversed()
                    .filter { record -> record.effectiveDate > event.announcementDate }
                    .forEach { record ->
                        membership = (
                            (membership - record.addedAssetIds.toSet()) + record.removedAssetIds
                        ).toSortedSet()
                    }
                val precedingTransitions = buildList {
                    records.asSequence()
                        .filter { record -> record.effectiveDate > event.announcementDate }
                        .map { record ->
                            ReferenceMembershipTransition(
                                order = record.referenceExecutionOrder(),
                                addedAssetIds = record.addedAssetIds.toSet(),
                                removedAssetIds = record.removedAssetIds.toSet(),
                            )
                        }
                        .filter { transition ->
                            REFERENCE_EXECUTION_ORDER.compare(transition.order, expectedOrder) < 0
                        }
                        .forEach(::add)
                    portfolio.pendingPlans.asSequence()
                        .map { plan ->
                            ReferenceMembershipTransition(
                                order = plan.referenceExecutionOrder(),
                                resultingAssetIds = plan.positions.mapTo(linkedSetOf()) { position ->
                                    position.assetId
                                },
                            )
                        }
                        .filter { transition ->
                            REFERENCE_EXECUTION_ORDER.compare(transition.order, expectedOrder) < 0
                        }
                        .forEach(::add)
                }.sortedWith(compareBy(REFERENCE_EXECUTION_ORDER, ReferenceMembershipTransition::order))
                precedingTransitions.forEach { transition ->
                    membership = transition.resultingAssetIds ?: (
                        (membership - transition.removedAssetIds) + transition.addedAssetIds
                    ).toSortedSet()
                }
                return membership
            }

            val expectedPendingCorporateExecutions = linkedSetOf<ReferenceCorporateExecutionKey>()
            val expectedAppliedCorporateExecutions = linkedSetOf<ReferenceCorporateExecutionKey>()
            fun expectExecution(
                key: ReferenceCorporateExecutionKey,
                effectiveDate: LocalDate,
            ) {
                if (schedule.hasPassedRegularOpen(effectiveDate, state.currentTime)) {
                    if (!schedule.hasPassedRegularOpen(effectiveDate, GameCalendar.startInstant)) {
                        expectedAppliedCorporateExecutions += key
                    }
                } else {
                    expectedPendingCorporateExecutions += key
                }
            }

            for (event in announcedEvents) {
                val primaryKind = event.primaryReferenceActionKind()
                val transitionSteps = engine.canonicalCorporateActionTransitionSteps(
                    definition,
                    event,
                ) ?: return "기준 포트폴리오 기업행동의 provider 전환 단계를 복원할 수 없습니다."
                val executionPoints = if (transitionSteps.isEmpty()) {
                    listOf(primaryKind to event.effectiveDate)
                } else {
                    transitionSteps.map { step ->
                        val kind = if (step.completionFraction < 1.0) {
                            ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION
                        } else {
                            primaryKind
                        }
                        kind to step.effectiveDate
                    }
                }
                val firstExecution = executionPoints.first()
                val primaryPassedAtBootstrap = schedule.hasPassedRegularOpen(
                    firstExecution.second,
                    GameCalendar.startInstant,
                )
                val eventWasApplicable = if (primaryPassedAtBootstrap) {
                    val bootstrapExecutions = bootstrapPendingCorporateExecutions()
                        ?: return "캠페인 시작 시점의 기준 포트폴리오 기업행동을 복원할 수 없습니다."
                    executionPoints.any { (kind, effectiveDate) ->
                        ReferenceCorporateExecutionKey(event.eventId, kind, effectiveDate) in
                            bootstrapExecutions
                    } || event.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF &&
                        ReferenceCorporateExecutionKey(
                            event.eventId,
                            ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
                            requireNotNull(event.followUpEffectiveDate),
                        ) in bootstrapExecutions
                } else {
                    val baselineIds = membershipBeforeExecution(
                        event = event,
                        kind = firstExecution.first,
                        effectiveDate = firstExecution.second,
                    )
                    engine.canonicalCorporateActionDecision(definition, event, baselineIds) != null
                }
                if (!eventWasApplicable) continue

                executionPoints.forEach { (kind, effectiveDate) ->
                    expectExecution(
                        ReferenceCorporateExecutionKey(event.eventId, kind, effectiveDate),
                        effectiveDate,
                    )
                }
                if (event.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF) {
                    val followUpDate = requireNotNull(event.followUpEffectiveDate)
                    expectExecution(
                        key = ReferenceCorporateExecutionKey(
                            event.eventId,
                            ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
                            followUpDate,
                        ),
                        effectiveDate = followUpDate,
                    )
                }
            }
            if (actualPendingCorporateExecutions != expectedPendingCorporateExecutions ||
                actualAppliedCorporateExecutions != expectedAppliedCorporateExecutions
            ) {
                return "기준 포트폴리오 기업행동의 대기·적용 실행 단계가 canonical 사건 계보와 다릅니다."
            }
        }
        fun membershipAtSelectionClose(selectionDate: kotlinx.datetime.LocalDate): List<String> {
            var membership: Set<String> = portfolio.positions
                .mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
            records.asReversed()
                .filter { record -> record.effectiveDate > selectionDate }
                .forEach { record ->
                    membership = (
                        (membership - record.addedAssetIds.toSet()) + record.removedAssetIds
                    ).toSortedSet()
                }
            return membership.sorted()
        }
        portfolio.pendingPlans.singleOrNull { plan ->
            plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
        }?.let { annualPlan ->
            val expectedIncumbents = membershipAtSelectionClose(annualPlan.selectionDate)
            if (annualPlan.selectionIncumbentAssetIds != expectedIncumbents) {
                return "대기 중 정기 재구성 계획의 선택일 구성 근거가 적용 원장 역재생 결과와 다릅니다."
            }
        }
        portfolio.pendingSelectionDate?.let { selectionDate ->
            val expectedIncumbents = membershipAtSelectionClose(selectionDate)
            if (portfolio.pendingSelectionIncumbentAssetIds != expectedIncumbents) {
                return "대기 중 선택일 구성 스냅샷이 적용 원장 역재생 결과와 다릅니다."
            }
        }
        var constituentCount = records.firstOrNull()?.let { first ->
            first.resultingConstituentCount - first.addedAssetIds.size + first.removedAssetIds.size
        } ?: portfolio.positions.size
        if (constituentCount <= 0) {
            return "기준 포트폴리오 원장의 최초 구성종목 수 계보가 유효하지 않습니다."
        }
        if (constraints?.let { providerConstraints ->
                constituentCount !in providerConstraints.minimumConstituentCount..
                    providerConstraints.maximumConstituentCount
            } == true
        ) {
            return "기준 포트폴리오 원장의 최초 구성종목 수가 provider 제약을 벗어났습니다."
        }
        for (record in records) {
            val recordIsEffective = schedule?.hasPassedRegularOpen(record.effectiveDate, portfolio.asOf)
                ?: (record.effectiveDate <= currentDate)
            if (record.portfolioId != portfolioId ||
                record.benchmarkRef != portfolio.benchmarkRef ||
                !recordIsEffective
            ) {
                return "기준 포트폴리오 원장의 포트폴리오·벤치마크·시점이 유효하지 않습니다."
            }
            if (!referenceCorporateActionLinkIsValid(
                    kind = record.kind,
                    selectionDate = record.selectionDate,
                    weightReferenceDate = record.weightReferenceDate,
                    effectiveDate = record.effectiveDate,
                    corporateAction = record.corporateAction,
                )
            ) {
                return "기준 포트폴리오 원장의 기업행동 종류·관측일·효력일 연결이 유효하지 않습니다."
            }
            if (methodology != null && schedule != null) {
                validateReferencePortfolioActionDates(
                    methodology = methodology,
                    policy = checkNotNull(policy),
                    schedule = schedule,
                    kind = record.kind,
                    selectionDate = record.selectionDate,
                    weightReferenceDate = record.weightReferenceDate,
                    effectiveDate = record.effectiveDate,
                    currentDate = currentDate,
                    pending = false,
                    corporateAction = record.corporateAction,
                )?.let { violation -> return violation }
            }
            if (record.id !=
                "reference-rebalance:$portfolioId:${record.effectiveDate}:${record.revision}"
            ) {
                return "기준 포트폴리오 원장의 ID 계보가 유효하지 않습니다."
            }
            val reconstructed = runCatching {
                ReferencePortfolioRecord(
                    id = record.id,
                    portfolioId = record.portfolioId,
                    benchmarkRef = record.benchmarkRef,
                    kind = record.kind,
                    selectionDate = record.selectionDate,
                    weightReferenceDate = record.weightReferenceDate,
                    effectiveDate = record.effectiveDate,
                    addedAssetIds = record.addedAssetIds.toList(),
                    removedAssetIds = record.removedAssetIds.toList(),
                    beforeCompositionHash = record.beforeCompositionHash,
                    afterCompositionHash = record.afterCompositionHash,
                    turnoverRate = record.turnoverRate,
                    resultingConstituentCount = record.resultingConstituentCount,
                    revision = record.revision,
                    corporateAction = record.corporateAction?.let(::reconstructCorporateAction),
                )
            }.getOrNull()
            if (reconstructed != record) {
                return "기준 포트폴리오 원장의 편입·편출·회전율 조건이 유효하지 않습니다."
            }
            if ((record.addedAssetIds + record.removedAssetIds).any { assetId ->
                    !engine.hasCanonicalReferenceAssetId(assetId)
                }
            ) {
                return "기준 포트폴리오 원장의 편입·편출 ID가 캠페인 기준자산에 없습니다."
            }
            if (record.corporateAction?.let { action ->
                    listOfNotNull(action.primaryAssetId, action.secondaryAssetId).any { assetId ->
                        !engine.hasCanonicalReferenceAssetId(assetId)
                    } || definition != null && !engine.isCanonicalCorporateAction(definition, action)
                } == true
            ) {
                return "기준 포트폴리오 원장의 기업행동이 캠페인 seed 원본과 다릅니다."
            }
            if (definition != null && record.corporateAction != null &&
                record.kind != ReferencePortfolioActionKind.SPIN_OFF_REMOVAL
            ) {
                val action = requireNotNull(record.corporateAction)
                val transitionSteps = engine.canonicalCorporateActionTransitionSteps(
                    definition,
                    action,
                ) ?: return "기준 포트폴리오 원장의 provider 기업행동 전환 단계를 복원할 수 없습니다."
                val stagedRecords = records.filter { candidate ->
                    candidate.corporateAction?.eventId == action.eventId
                }
                val firstStagedRecord = stagedRecords.minWithOrNull(REFERENCE_RECORD_ORDER)
                val decisionBaseline = firstStagedRecord?.let { first ->
                    beforeMembershipByRevision.getValue(first.revision)
                }
                val decision = decisionBaseline?.let { baseline ->
                    engine.canonicalCorporateActionDecision(definition, action, baseline)
                }
                val canonicalMembershipChange = when {
                    transitionSteps.isEmpty() -> decision != null &&
                        decision.addedAssetIds == record.addedAssetIds.toSet() &&
                        decision.removedAssetIds == record.removedAssetIds.toSet()
                    record.kind == ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION -> {
                        val stepIsCanonical = transitionSteps.any { step ->
                            step.completionFraction < 1.0 &&
                                step.effectiveDate == record.effectiveDate
                        }
                        val isInitialProviderStep =
                            record.effectiveDate == transitionSteps.first().effectiveDate
                        stepIsCanonical && record.removedAssetIds.isEmpty() && when {
                            !isInitialProviderStep -> record.addedAssetIds.isEmpty()
                            decision == null -> false
                            else -> decision.addedAssetIds == record.addedAssetIds.toSet() &&
                                decision.removedAssetIds == setOf(action.primaryAssetId)
                        }
                    }
                    record.kind == action.primaryReferenceActionKind() &&
                        record.effectiveDate == transitionSteps.last().effectiveDate ->
                        record.addedAssetIds.isEmpty() &&
                            record.removedAssetIds == setOf(action.primaryAssetId)
                    else -> false
                }
                if (!canonicalMembershipChange) {
                    return "기준 포트폴리오 원장의 기업행동 편입·편출이 방법론 단계 결정과 다릅니다."
                }
            }
            val expectedCount = constituentCount +
                record.addedAssetIds.size - record.removedAssetIds.size
            if (record.resultingConstituentCount != expectedCount) {
                return "기준 포트폴리오 원장의 구성종목 수가 편입·편출 계보와 다릅니다."
            }
            if (constraints?.let { providerConstraints ->
                    record.resultingConstituentCount !in providerConstraints.minimumConstituentCount..
                        providerConstraints.maximumConstituentCount
                } == true
            ) {
                return "기준 포트폴리오 원장의 결과 종목 수가 provider 제약을 벗어났습니다."
            }
            if (constraints?.scheduledSelectionCount?.let { expectedCount ->
                    record.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION &&
                        record.resultingConstituentCount != expectedCount
                } == true
            ) {
                return "정기 재구성 원장의 결과 종목 수가 방법론 목표와 다릅니다."
            }
            when (record.kind) {
                ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION -> Unit
                ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION -> Unit
                ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION -> if (
                    record.corporateAction?.kind !in setOf(
                        ReferencePortfolioCorporateActionKind.MERGER,
                        ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL,
                    ) || record.removedAssetIds.isNotEmpty() || record.addedAssetIds.size > 1
                ) {
                    return "기업행동 전환 원장은 대상 종목을 유지하며 대체 종목을 단계 편입해야 합니다."
                }
                ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
                ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT,
                -> if (record.addedAssetIds.isNotEmpty() || record.removedAssetIds.isNotEmpty()) {
                    return "비중조정 원장은 구성종목을 바꿀 수 없습니다."
                }
                ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL -> if (
                    record.addedAssetIds.isNotEmpty() || record.removedAssetIds.isEmpty()
                ) {
                    return "특별 제거 원장은 대체 편입 없이 기존 구성종목을 줄여야 합니다."
                }
                ReferencePortfolioActionKind.CONSTITUENT_MERGER -> if (
                    record.corporateAction?.kind != ReferencePortfolioCorporateActionKind.MERGER ||
                    record.removedAssetIds.isEmpty() ||
                    record.corporateAction.primaryAssetId !in record.removedAssetIds
                ) {
                    return "합병 원장은 대상 종목을 제거하고 canonical 결정의 대체 종목만 편입해야 합니다."
                }
                ReferencePortfolioActionKind.SPIN_OFF_ADDITION -> if (
                    record.corporateAction?.kind != ReferencePortfolioCorporateActionKind.SPIN_OFF ||
                    record.addedAssetIds != listOfNotNull(record.corporateAction.secondaryAssetId) ||
                    record.removedAssetIds.isNotEmpty()
                ) {
                    return "분사 편입 원장은 canonical 자회사 한 종목만 편입해야 합니다."
                }
                ReferencePortfolioActionKind.SPIN_OFF_REMOVAL -> if (
                    record.corporateAction?.kind != ReferencePortfolioCorporateActionKind.SPIN_OFF ||
                    record.addedAssetIds.isNotEmpty() ||
                    record.removedAssetIds != listOfNotNull(record.corporateAction.secondaryAssetId)
                ) {
                    return "분사 후속 제거 원장은 canonical 자회사 한 종목만 제거해야 합니다."
                }
                ReferencePortfolioActionKind.TERMINAL_REMOVAL -> if (
                    record.corporateAction?.kind != ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL ||
                    record.removedAssetIds.isEmpty() ||
                    record.corporateAction.primaryAssetId !in record.removedAssetIds
                ) {
                    return "소멸 제거 원장은 대상 종목을 제거하고 canonical 결정의 대체 종목만 편입해야 합니다."
                }
            }
            constituentCount = record.resultingConstituentCount
        }
        if (methodology != null && schedule != null) {
            val initialEffectiveDate = runCatching {
                schedule.initialScheduledAction(methodology).effectiveDate
            }.getOrNull() ?: return "기준 포트폴리오의 최초 정기 행동을 복원할 수 없습니다."
            val lastRecordedReconstitution = records
                .lastOrNull { record ->
                    record.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
                }
                ?.effectiveDate
                ?: canonicalBootstrapStates[portfolioId]?.lastReconstitutionDate
                ?: initialEffectiveDate
            if (portfolio.lastReconstitutionDate != lastRecordedReconstitution) {
                return "마지막 정기 재구성일이 기준 포트폴리오 원장 계보와 다릅니다."
            }
        }
        records.lastOrNull()?.let { latest ->
            if (latest.resultingConstituentCount != portfolio.positions.size ||
                kotlin.math.abs(latest.turnoverRate - portfolio.lastTurnoverRate) >
                ReferencePortfolioState.WEIGHT_EPSILON ||
                latest.effectiveDate != portfolio.lastRebalanceDate ||
                latest.kind != portfolio.lastAppliedActionKind ||
                latest.afterCompositionHash != ReferencePortfolioCompositionHasher.hash(portfolio.positions)
            ) {
                return "기준 포트폴리오 원장의 최신 결과가 현재 구성 상태와 다릅니다."
            }
        }
    }
    return null
}

private fun validateReferencePortfolioActionDates(
    methodology: EquityMethodologyProfile,
    policy: EquityMethodologyPolicy,
    schedule: EquityMethodologySchedule,
    kind: ReferencePortfolioActionKind,
    selectionDate: kotlinx.datetime.LocalDate,
    weightReferenceDate: kotlinx.datetime.LocalDate,
    effectiveDate: kotlinx.datetime.LocalDate,
    currentDate: kotlinx.datetime.LocalDate,
    pending: Boolean,
    corporateAction: ReferencePortfolioCorporateAction?,
): String? {
    if (selectionDate > currentDate || weightReferenceDate > currentDate ||
        pending && effectiveDate < currentDate
    ) {
        return "기준 포트폴리오 계획·원장의 선정·효력일이 현재 시점과 양립하지 않습니다."
    }
    val valid = when (kind) {
        ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
        ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
        -> runCatching {
            schedule.scheduledActionOn(methodology, effectiveDate)
        }.getOrNull()?.let { canonical ->
            canonical.kind == kind &&
                canonical.selectionDate == selectionDate &&
                canonical.weightReferenceDate == weightReferenceDate &&
                canonical.effectiveDate == effectiveDate
        } == true

        ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION -> runCatching {
            val finalAction = schedule.nextScheduledAction(
                profile = methodology,
                afterExclusive = weightReferenceDate,
                kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
            )
            finalAction.selectionDate == selectionDate &&
                finalAction.weightReferenceDate == weightReferenceDate &&
                policy.scheduledReconstitutionTransitionSteps(methodology, finalAction)
                    .any { step -> step.effectiveDate == effectiveDate }
        }.getOrDefault(false)

        // Event-trigger inputs are not persisted, so the validator must not synthesize historical
        // weights or candidate signals. Their exact decision truth is enforced when the engine
        // creates the action; persisted state can independently rebind only structural dates and
        // the provider-owned extraordinary-review cadence.
        ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT ->
            selectionDate == weightReferenceDate &&
                schedule.isTradingDate(weightReferenceDate) &&
                schedule.isTradingDate(effectiveDate)

        ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL -> {
            val canonicalReviewDate = runCatching {
                policy.nextExtraordinaryRemovalReviewDate(
                    profile = methodology,
                    afterExclusive = weightReferenceDate.minus(1, DateTimeUnit.DAY),
                )
            }.getOrNull()
            selectionDate == weightReferenceDate &&
                canonicalReviewDate == weightReferenceDate &&
                schedule.isTradingDate(weightReferenceDate) &&
                schedule.isTradingDate(effectiveDate)
        }

        ReferencePortfolioActionKind.CONSTITUENT_MERGER,
        ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION,
        ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
        ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
        ReferencePortfolioActionKind.TERMINAL_REMOVAL,
        -> corporateAction?.let { action ->
            val transitionSteps = runCatching {
                policy.corporateActionTransitionSteps(methodology, action)
            }.getOrNull() ?: return@let false
            val transitionStepsAreValid = transitionSteps.size <
                ReferencePortfolioState.MAX_PENDING_PLANS &&
                transitionSteps == transitionSteps.sortedBy { step -> step.effectiveDate } &&
                transitionSteps.map { step -> step.effectiveDate }.distinct().size ==
                transitionSteps.size &&
                transitionSteps.zipWithNext().all { (left, right) ->
                    left.completionFraction < right.completionFraction
                } && transitionSteps.all { step ->
                    step.completionFraction.isFinite() &&
                        step.completionFraction > 0.0 && step.completionFraction <= 1.0 &&
                        schedule.isTradingDate(step.effectiveDate)
                } && if (transitionSteps.isEmpty()) {
                    true
                } else {
                    action.kind != ReferencePortfolioCorporateActionKind.SPIN_OFF &&
                        transitionSteps.first().effectiveDate == action.effectiveDate &&
                        transitionSteps.last().completionFraction == 1.0 &&
                        transitionSteps.dropLast(1).all { step ->
                            step.completionFraction < 1.0
                        }
                }
            if (!transitionStepsAreValid) return@let false
            val stagedDateIsCanonical = when (kind) {
                ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION ->
                    transitionSteps.any { step ->
                        step.completionFraction < 1.0 && step.effectiveDate == effectiveDate
                    }
                ReferencePortfolioActionKind.CONSTITUENT_MERGER,
                ReferencePortfolioActionKind.TERMINAL_REMOVAL,
                -> if (transitionSteps.isEmpty()) {
                    effectiveDate == action.effectiveDate
                } else {
                    transitionSteps.lastOrNull()?.let { step ->
                        step.completionFraction == 1.0 && step.effectiveDate == effectiveDate
                    } == true
                }
                ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
                ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
                -> transitionSteps.isEmpty()
            }
            stagedDateIsCanonical && referenceCorporateActionLinkIsValid(
                kind = kind,
                selectionDate = selectionDate,
                weightReferenceDate = weightReferenceDate,
                effectiveDate = effectiveDate,
                corporateAction = action,
            ) &&
                schedule.isTradingDate(action.announcementDate) &&
                schedule.isTradingDate(effectiveDate)
        } == true
    }
    return if (valid) null else {
        "기준 포트폴리오 계획·원장의 일정이 방법론 달력과 일치하지 않습니다."
    }
}

private fun referenceCorporateActionLinkIsValid(
    kind: ReferencePortfolioActionKind,
    selectionDate: kotlinx.datetime.LocalDate,
    weightReferenceDate: kotlinx.datetime.LocalDate,
    effectiveDate: kotlinx.datetime.LocalDate,
    corporateAction: ReferencePortfolioCorporateAction?,
): Boolean {
    if (kind !in CORPORATE_REFERENCE_ACTION_KINDS) return corporateAction == null
    val action = corporateAction ?: return false
    val eventKindMatches = when (kind) {
        ReferencePortfolioActionKind.CONSTITUENT_MERGER ->
            action.kind == ReferencePortfolioCorporateActionKind.MERGER
        ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION ->
            action.kind == ReferencePortfolioCorporateActionKind.MERGER ||
                action.kind == ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL
        ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
        ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
        -> action.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF
        ReferencePortfolioActionKind.TERMINAL_REMOVAL ->
            action.kind == ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL
        else -> return false
    }
    val observationDate = action.announcementDate
    val effectiveDateMatches = when (kind) {
        ReferencePortfolioActionKind.SPIN_OFF_REMOVAL ->
            effectiveDate == action.followUpEffectiveDate
        ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION,
        ReferencePortfolioActionKind.CONSTITUENT_MERGER,
        ReferencePortfolioActionKind.TERMINAL_REMOVAL,
        -> effectiveDate >= action.effectiveDate
        ReferencePortfolioActionKind.SPIN_OFF_ADDITION -> effectiveDate == action.effectiveDate
    }
    return eventKindMatches &&
        selectionDate == observationDate && weightReferenceDate == observationDate &&
        effectiveDateMatches
}

private fun referenceWeightingInputIsValid(plan: ReferencePortfolioPlan): Boolean {
    val requiresInput = when (plan.kind) {
        ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
        ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
        ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT,
        -> true
        ReferencePortfolioActionKind.CONSTITUENT_MERGER,
        ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
        ReferencePortfolioActionKind.TERMINAL_REMOVAL,
        -> plan.addedAssetIds.isNotEmpty() ||
            plan.corporateAction?.let { action -> plan.effectiveDate > action.effectiveDate } == true
        ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION,
        ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION,
        ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL,
        ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
        -> false
    }
    val marketValues = plan.weightReferenceMarketValues
    if (!requiresInput) return marketValues == null
    marketValues ?: return false
    val positionAssetIds = plan.positions.map(ReferencePortfolioPosition::assetId)
    return marketValues.keys.toList() == positionAssetIds &&
        marketValues.values.all { value -> value.isFinite() && value > 0.0 } &&
        (plan.kind != ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT ||
            abs(marketValues.values.sum() - 1.0) <= ReferencePortfolioState.WEIGHT_EPSILON)
}

private fun referenceScheduledSelectionBasisIsValid(plan: ReferencePortfolioPlan): Boolean {
    val incumbentAssetIds = plan.selectionIncumbentAssetIds
    val availabilityDate = plan.selectionAvailabilityDate
    if (plan.kind != ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) {
        return incumbentAssetIds == null && availabilityDate == null
    }
    return incumbentAssetIds != null && availabilityDate != null &&
        incumbentAssetIds.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS &&
        incumbentAssetIds == incumbentAssetIds.distinct().sorted() &&
        incumbentAssetIds.all(REFERENCE_PORTFOLIO_ASSET_ID_PATTERN::matches) &&
        availabilityDate in plan.selectionDate..plan.effectiveDate
}

private fun referenceTargetWeightsMatch(
    positions: List<ReferencePortfolioPosition>,
    canonicalTargetWeights: Map<String, Double>?,
): Boolean {
    canonicalTargetWeights ?: return false
    val actualTargetWeights = positions.associate { position ->
        position.assetId to position.targetWeight
    }
    return canonicalTargetWeights.keys == actualTargetWeights.keys &&
        canonicalTargetWeights.all { (assetId, canonicalWeight) ->
            canonicalWeight.isFinite() &&
                abs(actualTargetWeights.getValue(assetId) - canonicalWeight) <=
                ReferencePortfolioState.WEIGHT_EPSILON
        }
}

private fun referenceMarketValuesMatch(
    actualMarketValues: Map<String, Double>?,
    canonicalMarketValues: Map<String, Double>?,
): Boolean {
    actualMarketValues ?: return false
    canonicalMarketValues ?: return false
    return actualMarketValues.keys == canonicalMarketValues.keys &&
        canonicalMarketValues.all { (assetId, canonicalMarketValue) ->
            val actualMarketValue = actualMarketValues.getValue(assetId)
            val tolerance = REFERENCE_MARKET_VALUE_RELATIVE_EPSILON *
                maxOf(1.0, abs(canonicalMarketValue))
            canonicalMarketValue.isFinite() && actualMarketValue.isFinite() &&
                abs(actualMarketValue - canonicalMarketValue) <= tolerance
        }
}

/**
 * Price drift multiplies each line's current weight and reference FMC by the same asset return.
 * The absolute FMC path is macro-dependent, but the normalized current-weight/FMC ratios remain
 * bound to the canonical plan while all structural fields stay exact.
 */
private fun referencePositionsMatchCanonical(
    positions: List<ReferencePortfolioPosition>,
    canonicalPositions: List<ReferencePortfolioPosition>?,
    permitsFinalStagedWeightReset: Boolean,
): Boolean {
    canonicalPositions ?: return false
    val actualById = positions.associateBy(ReferencePortfolioPosition::assetId)
    val canonicalById = canonicalPositions.associateBy(ReferencePortfolioPosition::assetId)
    if (actualById.keys != canonicalById.keys) return false
    if (actualById.any { (assetId, actual) ->
            val canonical = canonicalById.getValue(assetId)
            actual.enteredOn != canonical.enteredOn ||
                actual.selectionRank != canonical.selectionRank ||
                abs(actual.targetWeight - canonical.targetWeight) >
                ReferencePortfolioState.WEIGHT_EPSILON
        }
    ) return false

    // On the close immediately preceding the final staged application, the engine resets both
    // canonical and live current weights to their fixed targets. Their reference FMCs have already
    // followed different macro paths, so the drift-ratio invariant below no longer applies.
    val actualWasReset = actualById.values.all { position ->
        abs(position.currentWeight - position.targetWeight) <=
            ReferencePortfolioState.WEIGHT_EPSILON
    }
    val canonicalWasReset = canonicalById.values.all { position ->
        abs(position.currentWeight - position.targetWeight) <=
            ReferencePortfolioState.WEIGHT_EPSILON
    }
    if (actualWasReset || canonicalWasReset) {
        if (actualWasReset != canonicalWasReset) return false
        if (permitsFinalStagedWeightReset) return true
    }

    val actualRatios = actualById.mapValues { (_, position) ->
        position.currentWeight / position.referenceFloatMarketValue
    }
    val canonicalRatios = canonicalById.mapValues { (_, position) ->
        position.currentWeight / position.referenceFloatMarketValue
    }
    if (actualRatios.values.any { ratio -> !ratio.isFinite() || ratio <= 0.0 } ||
        canonicalRatios.values.any { ratio -> !ratio.isFinite() || ratio <= 0.0 }
    ) return false
    val actualTotal = actualRatios.values.sum()
    val canonicalTotal = canonicalRatios.values.sum()
    return actualById.keys.all { assetId ->
        abs(
            actualRatios.getValue(assetId) / actualTotal -
                canonicalRatios.getValue(assetId) / canonicalTotal,
        ) <= ReferencePortfolioState.WEIGHT_EPSILON
    }
}

/** Transition weights start from the live macro-dependent basket; only their lineage is canonical. */
private fun referenceTransitionStructureMatchesCanonical(
    positions: List<ReferencePortfolioPosition>,
    canonicalPositions: List<ReferencePortfolioPosition>?,
): Boolean {
    canonicalPositions ?: return false
    val actualById = positions.associateBy(ReferencePortfolioPosition::assetId)
    val canonicalById = canonicalPositions.associateBy(ReferencePortfolioPosition::assetId)
    return actualById.keys == canonicalById.keys && actualById.all { (assetId, actual) ->
        val canonical = canonicalById.getValue(assetId)
        actual.selectionRank == canonical.selectionRank && actual.enteredOn == canonical.enteredOn
    }
}

private fun referenceSelectionRanksMatch(
    positions: List<ReferencePortfolioPosition>,
    canonicalSelectionRanks: Map<String, Int>?,
): Boolean = canonicalSelectionRanks == positions.associate { position ->
    position.assetId to position.selectionRank
}

private const val REFERENCE_MARKET_VALUE_RELATIVE_EPSILON: Double = 1e-12

private val CORPORATE_REFERENCE_ACTION_KINDS: Set<ReferencePortfolioActionKind> = setOf(
    ReferencePortfolioActionKind.CONSTITUENT_MERGER,
    ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION,
    ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
    ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
    ReferencePortfolioActionKind.TERMINAL_REMOVAL,
)

private data class ReferenceCorporateExecutionKey(
    val corporateEventId: String,
    val kind: ReferencePortfolioActionKind,
    val effectiveDate: LocalDate,
)

private data class ReferenceExecutionOrder(
    val effectiveDate: LocalDate,
    val kind: ReferencePortfolioActionKind,
    val corporateEventId: String,
)

private data class ReferenceMembershipTransition(
    val order: ReferenceExecutionOrder,
    val addedAssetIds: Set<String> = emptySet(),
    val removedAssetIds: Set<String> = emptySet(),
    val resultingAssetIds: Set<String>? = null,
)

private fun ReferencePortfolioCorporateAction.primaryReferenceActionKind(): ReferencePortfolioActionKind =
    when (kind) {
        ReferencePortfolioCorporateActionKind.MERGER ->
            ReferencePortfolioActionKind.CONSTITUENT_MERGER
        ReferencePortfolioCorporateActionKind.SPIN_OFF ->
            ReferencePortfolioActionKind.SPIN_OFF_ADDITION
        ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL ->
            ReferencePortfolioActionKind.TERMINAL_REMOVAL
    }

private fun ReferencePortfolioPlan.referenceExecutionOrder(): ReferenceExecutionOrder =
    ReferenceExecutionOrder(
        effectiveDate = effectiveDate,
        kind = kind,
        corporateEventId = corporateAction?.eventId.orEmpty(),
    )

private fun ReferencePortfolioRecord.referenceExecutionOrder(): ReferenceExecutionOrder =
    ReferenceExecutionOrder(
        effectiveDate = effectiveDate,
        kind = kind,
        corporateEventId = corporateAction?.eventId.orEmpty(),
    )

private fun ReferencePortfolioActionKind.allowsTemporaryTargetCapBreach(): Boolean = when (this) {
    ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION,
    ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION,
    ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL,
    ReferencePortfolioActionKind.CONSTITUENT_MERGER,
    ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
    ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
    ReferencePortfolioActionKind.TERMINAL_REMOVAL,
    -> true
    ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
    ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
    ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT,
    -> false
}

private fun ReferencePortfolioActionKind.referenceExecutionPriority(): Int = when (this) {
    ReferencePortfolioActionKind.CONSTITUENT_MERGER,
    ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION,
    ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
    ReferencePortfolioActionKind.TERMINAL_REMOVAL,
    -> 0
    ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL -> 1
    ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION -> 2
    ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION -> 3
    ReferencePortfolioActionKind.SCHEDULED_REWEIGHT -> 4
    ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT -> 5
    ReferencePortfolioActionKind.SPIN_OFF_ADDITION -> 6
}

private val REFERENCE_EXECUTION_ORDER = compareBy<ReferenceExecutionOrder>(
    ReferenceExecutionOrder::effectiveDate,
).thenBy { execution -> execution.kind.referenceExecutionPriority() }
    .thenBy { execution -> execution.kind.name }
    .thenBy(ReferenceExecutionOrder::corporateEventId)

private val REFERENCE_PLAN_ORDER = compareBy<ReferencePortfolioPlan>(ReferencePortfolioPlan::effectiveDate)
    .thenBy { plan -> plan.kind.referenceExecutionPriority() }
    .thenBy(ReferencePortfolioPlan::id)

private val REFERENCE_RECORD_ORDER = compareBy<ReferencePortfolioRecord>(ReferencePortfolioRecord::effectiveDate)
    .thenBy { record -> record.kind.referenceExecutionPriority() }
    .thenBy { record -> record.kind.name }
    .thenBy { record -> record.corporateAction?.eventId.orEmpty() }
    .thenBy(ReferencePortfolioRecord::revision)

private fun referencePortfolioPlanId(plan: ReferencePortfolioPlan): String {
    val actionSegment = plan.corporateAction?.let { action -> ":${action.eventId}" }.orEmpty()
    return "reference-plan:${plan.portfolioId}:${plan.kind.name}$actionSegment:" +
        "${plan.weightReferenceDate}:${plan.effectiveDate}"
}

private data class UnitAdjustmentLineage(
    val cumulativeFactor: Double,
    val lastAccountingSequence: Long?,
)

/** Replays the exact applied split/reverse-split order used by the runtime. */
private fun SimulatorUiState.unitAdjustmentLineage(stockId: String): UnitAdjustmentLineage {
    var factor = 1.0
    var lastSequence: Long? = null
    corporateActionLedger.asSequence()
        .filter { action -> action.stockId == stockId }
        .forEach { action ->
            factor *= action.quantityMultiplier
            lastSequence = action.accountingSequence
        }
    return UnitAdjustmentLineage(factor, lastSequence)
}

private fun unitAdjustmentMarkerMatches(
    actualFactor: Double,
    actualLastSequence: Long?,
    expected: UnitAdjustmentLineage,
): Boolean = actualFactor == expected.cumulativeFactor &&
    actualLastSequence == expected.lastAccountingSequence

private fun validateDailyResetPersistenceState(
    state: SimulatorUiState,
    stocksById: Map<String, StockDefinition>,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    val expectedProductIds = stocksById.values
        .filter { stock -> stock.fundProductProfile?.dailyResetTerms != null }
        .mapTo(linkedSetOf(), StockDefinition::id)
    if (state.dailyResetStates.keys != expectedProductIds) {
        return "일일 reset 상태는 레버리지·인버스 상품마다 정확히 하나씩 필요합니다."
    }

    fun amountsAreClose(left: Double, right: Double): Boolean {
        val scale = maxOf(1.0, kotlin.math.abs(left), kotlin.math.abs(right))
        return kotlin.math.abs(left - right) <= scale * DAILY_RESET_ACCOUNTING_EPSILON
    }

    for ((productId, resetState) in state.dailyResetStates) {
        val stock = stocksById[productId]
            ?: return "일일 reset 상태에 현재 종목 카탈로그가 알 수 없는 ID가 있습니다."
        val product = stock.fundProductProfile
            ?: return "일일 reset 상태의 종목에 펀드 상품 프로필이 없습니다."
        val terms = product.dailyResetTerms
            ?: return "일일 reset 상태의 종목에 reset 약관이 없습니다."
        if (stock.etfProfile?.leverage != terms.targetLeverage) {
            return "일일 reset 목표배율이 상품 가격 프로필의 배율과 다릅니다."
        }
        if (catalog != null) {
            val canonicalProduct = catalog.findById(productId)?.fundProductProfile
                ?: return "일일 reset 상태의 종목이 현재 카탈로그 상품 프로필과 다릅니다."
            if (canonicalProduct != product || canonicalProduct.dailyResetTerms != terms) {
                return "일일 reset의 목표배율·캘린더·기준 종류와 ID가 카탈로그 약관과 다릅니다."
            }
        }
        val reconstructed = runCatching {
            DailyResetState(
                productId = resetState.productId,
                resetTradingDate = resetState.resetTradingDate,
                referenceLevelAtReset = resetState.referenceLevelAtReset,
                navAtReset = resetState.navAtReset,
                currentReferenceLevel = resetState.currentReferenceLevel,
                currentNav = resetState.currentNav,
                cumulativeCarryLogReturn = resetState.cumulativeCarryLogReturn,
                exposureNotional = resetState.exposureNotional,
                collateralBalance = resetState.collateralBalance,
                lifecycle = resetState.lifecycle,
                cumulativeUnitAdjustmentFactor = resetState.cumulativeUnitAdjustmentFactor,
                lastCorporateActionAccountingSequence =
                resetState.lastCorporateActionAccountingSequence,
                asOf = resetState.asOf,
                revision = resetState.revision,
            )
        }.getOrNull()
        val expectedAdjustment = state.unitAdjustmentLineage(productId)
        if (reconstructed != resetState || resetState.productId != productId ||
            terms.productId != productId || resetState.asOf > state.currentTime ||
            !unitAdjustmentMarkerMatches(
                actualFactor = resetState.cumulativeUnitAdjustmentFactor,
                actualLastSequence = resetState.lastCorporateActionAccountingSequence,
                expected = expectedAdjustment,
            )
        ) {
            return "일일 reset 상태의 상품 ID·수치 범위·기준 시각이 유효하지 않습니다."
        }
        val referenceMarket = when (terms.resetCalendar) {
            DailyResetCalendar.KRX_EQUITY -> Market.KOSPI
            DailyResetCalendar.US_EQUITY -> Market.NYSE
        }
        val resetDate = resetState.resetTradingDate
        val asOfReferenceDate = GameCalendar.marketLocalDateTime(referenceMarket, resetState.asOf).date
        if (resetDate > asOfReferenceDate || resetDate.year !in 2026..2040 ||
            GameCalendar.isWeekend(resetDate) ||
            resetDate in DefaultMarketHolidays.closedDates(referenceMarket, resetDate.year)
        ) {
            return "일일 reset 기준일이 약관 캘린더의 실제 거래일·기준 시각과 다릅니다."
        }
        when (terms.reference.kind) {
            DailyResetReferenceKind.BENCHMARK -> if (
                terms.reference.benchmarkRef != product.benchmarkRef ||
                terms.reference.instrumentId != null
            ) {
                return "일일 reset 벤치마크 참조가 상품의 기준 벤치마크와 다릅니다."
            }
            DailyResetReferenceKind.INSTRUMENT -> if (
                terms.reference.benchmarkRef != null ||
                terms.reference.instrumentId !in stocksById ||
                terms.reference.instrumentId == productId ||
                stocksById[terms.reference.instrumentId]?.behavior?.strategy !=
                InstrumentStrategy.OPERATING_COMPANY
            ) {
                return "일일 reset 단일종목 참조가 현재 종목 카탈로그와 다릅니다."
            }
        }

        val cumulativeReferenceReturn =
            resetState.currentReferenceLevel / resetState.referenceLevelAtReset - 1.0
        val leveragedFactor = 1.0 + terms.targetLeverage * cumulativeReferenceReturn
        when (resetState.lifecycle) {
            DailyResetLifecycle.ACTIVE -> {
                if (leveragedFactor <= DAILY_RESET_MIN_POSITIVE_FACTOR) {
                    return "활성 일일 reset 상태의 목표배율 가치 계수가 소진 경계를 넘었습니다."
                }
                val expectedNav = (
                    resetState.navAtReset * leveragedFactor *
                        exp(resetState.cumulativeCarryLogReturn)
                    ).coerceIn(DailyResetState.MIN_NAV, DailyResetState.MAX_NAV)
                if (!amountsAreClose(resetState.currentNav, expectedNav) ||
                    !amountsAreClose(
                        resetState.exposureNotional,
                        terms.targetLeverage * resetState.currentNav,
                    ) ||
                    !amountsAreClose(resetState.collateralBalance, resetState.currentNav)
                ) {
                    return "활성 일일 reset 상태의 NAV·목표 노출·담보 계정식이 일치하지 않습니다."
                }
            }
            DailyResetLifecycle.VALUE_EXHAUSTED -> if (
                leveragedFactor > DAILY_RESET_MIN_POSITIVE_FACTOR ||
                resetState.currentNav != DailyResetState.MIN_NAV ||
                resetState.exposureNotional != 0.0 ||
                resetState.collateralBalance != DailyResetState.MIN_NAV
            ) {
                return "가치소진 일일 reset 상태의 종단 불변조건이 일치하지 않습니다."
            }
        }
    }
    return null
}

/** 옵션 전략 상태를 상품 약관, 거래일 roll 계보, 이중계상 방지 계정식에 결속한다. */
private fun validateOptionStrategyPersistenceState(
    state: SimulatorUiState,
    stocksById: Map<String, StockDefinition>,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    val expectedProductIds = stocksById.values
        .filter { stock -> stock.fundProductProfile?.optionStrategyTerms != null }
        .mapTo(linkedSetOf(), StockDefinition::id)
    if (state.optionStrategyStates.keys != expectedProductIds) {
        return "옵션 운용 상태는 옵션 전략 약관이 있는 상품마다 정확히 하나씩 필요합니다."
    }

    fun amountsAreClose(left: Double, right: Double): Boolean {
        val scale = maxOf(1.0, kotlin.math.abs(left), kotlin.math.abs(right))
        return kotlin.math.abs(left - right) <= scale * OPTION_STRATEGY_ACCOUNTING_EPSILON
    }

    fun referenceMarket(calendar: OptionRollCalendar): Market = when (calendar) {
        OptionRollCalendar.KRX_EQUITY -> Market.KOSPI
        OptionRollCalendar.US_EQUITY -> Market.NYSE
    }

    fun lastCompletedTradingDate(calendar: OptionRollCalendar, at: kotlin.time.Instant): kotlinx.datetime.LocalDate {
        val market = referenceMarket(calendar)
        // The campaign ends at the final New York close. In KRX local time that instant is
        // already 2041-01-01, while the frozen holiday/calendar pack intentionally ends in
        // 2040. Search backward from the last supported campaign date instead of asking the
        // holiday provider to invent a 2041 session.
        var candidate = minOf(
            GameCalendar.marketLocalDateTime(market, at).date,
            GameCalendar.CAMPAIGN_END_DATE,
        )
        while (candidate >= GameCalendar.START_LOCAL_DATE_TIME.date) {
            val closedDates = DefaultMarketHolidays.closedDates(market, candidate.year)
            val window = GameCalendar.regularSessionWindow(market, candidate, closedDates)
            if (window != null && at >= window.closesAt) return candidate
            candidate = candidate.minus(1, DateTimeUnit.DAY)
        }
        return candidate
    }

    fun tradingDatesAfter(
        calendar: OptionRollCalendar,
        startExclusive: kotlinx.datetime.LocalDate,
        endInclusive: kotlinx.datetime.LocalDate,
    ): Int {
        if (endInclusive <= startExclusive) return 0
        var count = 0
        var candidate = startExclusive.plus(1, DateTimeUnit.DAY)
        while (candidate <= endInclusive) {
            if (calendar.isTradingDate(candidate)) count += 1
            candidate = candidate.plus(1, DateTimeUnit.DAY)
        }
        return count
    }

    fun legMatches(
        actualUnits: Double,
        actualStrike: Double?,
        expectedUnits: Double,
        expectedStrike: Double?,
    ): Boolean = amountsAreClose(actualUnits, expectedUnits) &&
        if (expectedUnits == 0.0) {
            actualStrike == null
        } else {
            actualStrike != null && expectedStrike != null &&
                amountsAreClose(actualStrike, expectedStrike)
        }

    for ((productId, optionState) in state.optionStrategyStates) {
        val stock = stocksById[productId]
            ?: return "옵션 운용 상태에 현재 종목 카탈로그가 알 수 없는 ID가 있습니다."
        val product = stock.fundProductProfile
            ?: return "옵션 운용 상태의 종목에 펀드 상품 프로필이 없습니다."
        val terms = product.optionStrategyTerms
            ?: return "옵션 운용 상태의 종목에 옵션 전략 약관이 없습니다."
        if (catalog != null) {
            val canonicalProduct = catalog.findById(productId)?.fundProductProfile
                ?: return "옵션 운용 상태의 종목이 현재 카탈로그 상품 프로필과 다릅니다."
            if (canonicalProduct != product || canonicalProduct.optionStrategyTerms != terms) {
                return "옵션 운용 상태의 전략·기준·roll 캘린더가 카탈로그 약관과 다릅니다."
            }
        }

        val reconstructed = runCatching {
            OptionStrategyState(
                productId = optionState.productId,
                strategyKind = optionState.strategyKind,
                rollCalendar = optionState.rollCalendar,
                currentReferenceLevel = optionState.currentReferenceLevel,
                currentNav = optionState.currentNav,
                underlyingUnits = optionState.underlyingUnits,
                cashBalance = optionState.cashBalance,
                cycleReferenceLevel = optionState.cycleReferenceLevel,
                optionNotionalAtRoll = optionState.optionNotionalAtRoll,
                cycleStartedOn = optionState.cycleStartedOn,
                remainingTradingDays = optionState.remainingTradingDays,
                remainingTimeYears = optionState.remainingTimeYears,
                lastProcessedTradingDate = optionState.lastProcessedTradingDate,
                longCallUnits = optionState.longCallUnits,
                longCallStrike = optionState.longCallStrike,
                shortCallUnits = optionState.shortCallUnits,
                shortCallStrike = optionState.shortCallStrike,
                longPutUnits = optionState.longPutUnits,
                longPutStrike = optionState.longPutStrike,
                shortPutUnits = optionState.shortPutUnits,
                shortPutStrike = optionState.shortPutStrike,
                netOptionMark = optionState.netOptionMark,
                cycleGrossPremiumReceived = optionState.cycleGrossPremiumReceived,
                cycleGrossPremiumPaid = optionState.cycleGrossPremiumPaid,
                cycleImplementationCost = optionState.cycleImplementationCost,
                cumulativePremiumReceived = optionState.cumulativePremiumReceived,
                cumulativePremiumPaid = optionState.cumulativePremiumPaid,
                cumulativeSettlementCashFlow = optionState.cumulativeSettlementCashFlow,
                cumulativeImplementationCost = optionState.cumulativeImplementationCost,
                lifecycle = optionState.lifecycle,
                cumulativeUnitAdjustmentFactor = optionState.cumulativeUnitAdjustmentFactor,
                lastCorporateActionAccountingSequence =
                optionState.lastCorporateActionAccountingSequence,
                asOf = optionState.asOf,
                revision = optionState.revision,
            )
        }.getOrNull()
        val expectedAdjustment = state.unitAdjustmentLineage(productId)
        val listing = state.listingLifecycleStates[productId]
            ?: return "옵션 운용 상품의 상장 생명주기 상태가 없습니다."
        val freezesAtLastMark = listing.isSettlementPending || listing.isTerminal
        if (reconstructed != optionState || optionState.productId != productId ||
            terms.productId != productId || optionState.strategyKind != terms.kind ||
            optionState.rollCalendar != terms.rollCalendar ||
            optionState.asOf !in GameCalendar.startInstant..state.currentTime ||
            !freezesAtLastMark && optionState.asOf != state.currentTime ||
            !unitAdjustmentMarkerMatches(
                actualFactor = optionState.cumulativeUnitAdjustmentFactor,
                actualLastSequence = optionState.lastCorporateActionAccountingSequence,
                expected = expectedAdjustment,
            )
        ) {
            return "옵션 운용 상태의 상품 ID·약관·수치 범위·기준 시각이 유효하지 않습니다."
        }

        when (terms.reference.kind) {
            DailyResetReferenceKind.BENCHMARK -> if (
                terms.reference.benchmarkRef == null || terms.reference.instrumentId != null ||
                product.legalStructure == FundLegalStructure.OPEN_END_ETF &&
                terms.reference.benchmarkRef != product.benchmarkRef ||
                catalog != null && catalog.benchmarksInEvaluationOrder.none { definition ->
                    definition.ref == terms.reference.benchmarkRef
                }
            ) {
                return "옵션 운용 벤치마크 참조가 상품의 기준 벤치마크와 다릅니다."
            }
            DailyResetReferenceKind.INSTRUMENT -> if (
                terms.reference.benchmarkRef != null ||
                terms.reference.instrumentId !in stocksById ||
                terms.reference.instrumentId == productId ||
                stocksById[terms.reference.instrumentId]?.behavior?.strategy !=
                InstrumentStrategy.OPERATING_COMPANY
            ) {
                return "옵션 운용 단일종목 참조가 현재 종목 카탈로그와 다릅니다."
            }
        }

        val market = referenceMarket(terms.rollCalendar)
        val asOfReferenceDate = GameCalendar.marketLocalDateTime(market, optionState.asOf).date
        if (optionState.cycleStartedOn > asOfReferenceDate ||
            optionState.cycleStartedOn.year !in
            GameCalendar.START_LOCAL_DATE_TIME.year..GameCalendar.CAMPAIGN_END_DATE.year ||
            optionState.lastProcessedTradingDate?.let { date ->
                date > asOfReferenceDate || !terms.rollCalendar.isTradingDate(date)
            } == true ||
            !terms.rollCalendar.isTradingDate(optionState.cycleStartedOn) ||
            optionState.remainingTimeYears >
            terms.tenorTradingDays / OPTION_TRADING_DAYS_PER_YEAR +
            OPTION_STRATEGY_ACCOUNTING_EPSILON
        ) {
            return "옵션 운용 주기의 거래일·잔존 tenor가 약관 캘린더와 다릅니다."
        }

        val accountedNav = optionState.underlyingUnits * optionState.currentReferenceLevel +
            optionState.cashBalance + optionState.netOptionMark
        if (!amountsAreClose(accountedNav, optionState.currentNav)) {
            return "옵션 운용 상태가 기초자산·현금·옵션 공정가치를 NAV에 한 번씩만 반영하지 않습니다."
        }

        val bootstrapDate = lastCompletedTradingDate(terms.rollCalendar, GameCalendar.startInstant)
        when (optionState.lifecycle) {
            OptionStrategyLifecycle.AWAITING_PRODUCT_LIQUIDATION -> {
                val directUnderlyingId = terms.reference.instrumentId
                val notice = directUnderlyingId?.let { underlyingId ->
                    state.newsEvents.singleOrNull { event ->
                        event.id == "direct-underlying-liquidation:$productId:$underlyingId"
                    }
                }
                if (terms.reference.kind != DailyResetReferenceKind.INSTRUMENT ||
                    optionState.revision == 0L ||
                    optionState.cycleStartedOn < bootstrapDate ||
                    optionState.lastProcessedTradingDate != optionState.cycleStartedOn ||
                    notice?.instrumentTermination?.effectiveNotBefore?.let { it <= optionState.asOf } != true ||
                    GameCalendar.marketLocalDateTime(market, optionState.asOf).date !=
                    optionState.cycleStartedOn
                ) {
                    return "상품 청산 대기 옵션 운용 상태의 종료 주기·revision 계보가 유효하지 않습니다."
                }
            }
            OptionStrategyLifecycle.VALUE_EXHAUSTED -> {
                if (optionState.revision == 0L || optionState.cycleStartedOn < bootstrapDate) {
                    return "가치소진 옵션 운용 상태의 사건 revision·주기 계보가 유효하지 않습니다."
                }
            }
            OptionStrategyLifecycle.ACTIVE -> {
                if (optionState.optionNotionalAtRoll <= 0.0) {
                    return "활성 옵션 운용 상태에는 양수의 주기 option notional이 필요합니다."
                }
                if (optionState.revision == 0L) {
                    if (optionState.cycleStartedOn != bootstrapDate ||
                        optionState.cycleGrossPremiumReceived != 0.0 ||
                        optionState.cycleGrossPremiumPaid != 0.0 ||
                        optionState.cycleImplementationCost != 0.0 ||
                        optionState.cumulativePremiumReceived != 0.0 ||
                        optionState.cumulativePremiumPaid != 0.0 ||
                        optionState.cumulativeSettlementCashFlow != 0.0 ||
                        optionState.cumulativeImplementationCost != 0.0
                    ) {
                        return "옵션 운용 bootstrap 상태에 캠페인 이전 premium·정산·비용이 원장처럼 기록되었습니다."
                    }
                } else {
                    val rollInterval = terms.tenorTradingDays - terms.rollLeadTradingDays
                    val closesToCycleStart = tradingDatesAfter(
                        terms.rollCalendar,
                        bootstrapDate,
                        optionState.cycleStartedOn,
                    )
                    if (optionState.cycleStartedOn <= bootstrapDate ||
                        optionState.lastProcessedTradingDate == null ||
                        closesToCycleStart.toLong() != optionState.revision * rollInterval.toLong()
                    ) {
                        return "옵션 운용 revision과 현재 주기 시작일이 정규장 roll 계보와 다릅니다."
                    }
                }

                val latestCompleted = lastCompletedTradingDate(terms.rollCalendar, optionState.asOf)
                val expectedLastProcessed = if (
                    optionState.revision == 0L && latestCompleted == optionState.cycleStartedOn
                ) {
                    null
                } else {
                    latestCompleted
                }
                val consumedTradingDays = tradingDatesAfter(
                    terms.rollCalendar,
                    optionState.cycleStartedOn,
                    expectedLastProcessed ?: optionState.cycleStartedOn,
                )
                if (optionState.lastProcessedTradingDate != expectedLastProcessed ||
                    consumedTradingDays >=
                    terms.tenorTradingDays - terms.rollLeadTradingDays ||
                    optionState.remainingTradingDays != terms.tenorTradingDays - consumedTradingDays
                ) {
                    return "옵션 운용의 최종 처리 거래일·잔존일수가 현재 roll 주기와 다릅니다."
                }

                val spot = optionState.cycleReferenceLevel
                val notional = optionState.optionNotionalAtRoll
                val baseUnits = notional / spot
                val strategyMatches = when (optionState.strategyKind) {
                    OptionStrategyKind.COVERED_CALL -> {
                        val detail = requireNotNull(terms.coveredCall)
                        legMatches(optionState.longCallUnits, optionState.longCallStrike, 0.0, null) &&
                            legMatches(
                                optionState.shortCallUnits,
                                optionState.shortCallStrike,
                                optionState.underlyingUnits * detail.overwriteRatio,
                                spot * detail.callStrikeMoneyness,
                            ) &&
                            legMatches(optionState.longPutUnits, optionState.longPutStrike, 0.0, null) &&
                            legMatches(optionState.shortPutUnits, optionState.shortPutStrike, 0.0, null) &&
                            amountsAreClose(notional, optionState.shortCallUnits * spot)
                    }
                    OptionStrategyKind.OPTION_INCOME -> {
                        val detail = requireNotNull(terms.optionIncome)
                        val preTradeNav = notional / detail.optionIncomeAllocation
                        val upsideUnits = baseUnits * detail.upsideParticipation
                        val downsideUnits = baseUnits * detail.downsideParticipation
                        legMatches(
                            optionState.longCallUnits,
                            optionState.longCallStrike,
                            upsideUnits,
                            if (upsideUnits == 0.0) null else spot,
                        ) &&
                            legMatches(
                                optionState.shortCallUnits,
                                optionState.shortCallStrike,
                                upsideUnits,
                                if (upsideUnits == 0.0) null else spot * detail.callStrikeMoneyness,
                            ) &&
                            legMatches(optionState.longPutUnits, optionState.longPutStrike, 0.0, null) &&
                            legMatches(
                                optionState.shortPutUnits,
                                optionState.shortPutStrike,
                                downsideUnits,
                                if (downsideUnits == 0.0) null else spot,
                            ) &&
                            amountsAreClose(
                                optionState.underlyingUnits,
                                preTradeNav * detail.coreEquityAllocation / spot,
                            )
                    }
                    OptionStrategyKind.BUFFERED_PUT_SPREAD -> {
                        val detail = requireNotNull(terms.bufferedPutSpread)
                        val shortPutUnits = baseUnits * detail.downsideParticipationBeyondBuffer
                        legMatches(optionState.longCallUnits, optionState.longCallStrike, 0.0, null) &&
                            legMatches(
                                optionState.shortCallUnits,
                                optionState.shortCallStrike,
                                baseUnits,
                                spot * (1.0 + detail.upsideCapFraction),
                            ) &&
                            legMatches(
                                optionState.longPutUnits,
                                optionState.longPutStrike,
                                baseUnits,
                                spot * detail.longPutStrikeMoneyness,
                            ) &&
                            legMatches(
                                optionState.shortPutUnits,
                                optionState.shortPutStrike,
                                shortPutUnits,
                                if (shortPutUnits == 0.0) null else {
                                    spot * (
                                        detail.longPutStrikeMoneyness -
                                            detail.downsideBufferFraction
                                        )
                                },
                            ) &&
                            amountsAreClose(
                                notional,
                                optionState.underlyingUnits * spot * detail.outcomeNotionalRatio,
                            )
                    }
                }
                if (!strategyMatches) {
                    return "옵션 운용의 leg 수량·행사가·notional이 상품 전략 약관과 다릅니다."
                }
            }
        }
    }
    return null
}

/** Cash collateral, option-reference legs and deterministic roll revision for put-spread ETFs. */
private fun validateCashCollateralizedPutSpreadPersistenceState(
    state: SimulatorUiState,
    stocksById: Map<String, StockDefinition>,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    val expectedProductIds = stocksById.values
        .filter { stock -> stock.fundProductProfile?.cashCollateralizedPutSpreadTerms != null }
        .mapTo(linkedSetOf(), StockDefinition::id)
    if (state.cashCollateralizedPutSpreadStates.keys != expectedProductIds) {
        return "현금담보 풋스프레드 상태는 전용 약관이 있는 상품마다 정확히 하나씩 필요합니다."
    }

    fun amountsAreClose(left: Double, right: Double): Boolean {
        val scale = maxOf(1.0, kotlin.math.abs(left), kotlin.math.abs(right))
        return kotlin.math.abs(left - right) <= scale * OPTION_STRATEGY_ACCOUNTING_EPSILON
    }

    fun referenceMarket(calendar: OptionRollCalendar): Market = when (calendar) {
        OptionRollCalendar.KRX_EQUITY -> Market.KOSPI
        OptionRollCalendar.US_EQUITY -> Market.NYSE
    }

    fun lastCompletedTradingDate(
        calendar: OptionRollCalendar,
        at: kotlin.time.Instant,
    ): kotlinx.datetime.LocalDate {
        val market = referenceMarket(calendar)
        // At the final New York close KRX local time is 2041-01-01, outside the deliberately
        // frozen 2026-2040 holiday pack. Start at the final supported campaign date and locate
        // the actual last venue session by walking backward through the canonical calendar.
        var candidate = minOf(
            GameCalendar.marketLocalDateTime(market, at).date,
            GameCalendar.CAMPAIGN_END_DATE,
        )
        while (candidate >= GameCalendar.START_LOCAL_DATE_TIME.date) {
            val closedDates = DefaultMarketHolidays.closedDates(market, candidate.year)
            val window = GameCalendar.regularSessionWindow(market, candidate, closedDates)
            if (window != null && at >= window.closesAt) return candidate
            candidate = candidate.minus(1, DateTimeUnit.DAY)
        }
        return candidate
    }

    fun tradingDatesAfter(
        calendar: OptionRollCalendar,
        startExclusive: kotlinx.datetime.LocalDate,
        endInclusive: kotlinx.datetime.LocalDate,
    ): Int {
        if (endInclusive <= startExclusive) return 0
        var count = 0
        var candidate = startExclusive.plus(1, DateTimeUnit.DAY)
        while (candidate <= endInclusive) {
            if (calendar.isTradingDate(candidate)) count += 1
            candidate = candidate.plus(1, DateTimeUnit.DAY)
        }
        return count
    }

    for ((productId, spreadState) in state.cashCollateralizedPutSpreadStates) {
        val stock = stocksById[productId]
            ?: return "현금담보 풋스프레드 상태에 현재 종목 카탈로그가 알 수 없는 ID가 있습니다."
        val product = stock.fundProductProfile
            ?: return "현금담보 풋스프레드 상태의 종목에 펀드 상품 프로필이 없습니다."
        val terms = product.cashCollateralizedPutSpreadTerms
            ?: return "현금담보 풋스프레드 상태의 종목에 전용 약관이 없습니다."
        if (catalog != null) {
            val canonicalProduct = catalog.findById(productId)?.fundProductProfile
                ?: return "현금담보 풋스프레드 상품이 현재 카탈로그 상품 프로필과 다릅니다."
            if (canonicalProduct != product ||
                canonicalProduct.cashCollateralizedPutSpreadTerms != terms
            ) {
                return "현금담보 풋스프레드의 현금/옵션 기준·roll 약관이 카탈로그와 다릅니다."
            }
        }

        val reconstructed = runCatching {
            CashCollateralizedPutSpreadState(
                productId = spreadState.productId,
                cashBenchmarkRef = spreadState.cashBenchmarkRef,
                optionReference = DailyResetReference(
                    kind = spreadState.optionReference.kind,
                    benchmarkRef = spreadState.optionReference.benchmarkRef,
                    instrumentId = spreadState.optionReference.instrumentId,
                ),
                rollCalendar = spreadState.rollCalendar,
                currentCashReferenceLevel = spreadState.currentCashReferenceLevel,
                currentOptionReferenceLevel = spreadState.currentOptionReferenceLevel,
                currentNav = spreadState.currentNav,
                cashBalance = spreadState.cashBalance,
                cycleOptionReferenceLevel = spreadState.cycleOptionReferenceLevel,
                navAtRoll = spreadState.navAtRoll,
                optionNotionalAtRoll = spreadState.optionNotionalAtRoll,
                maximumSettlementLossAtRoll = spreadState.maximumSettlementLossAtRoll,
                cycleStartedOn = spreadState.cycleStartedOn,
                remainingTradingDays = spreadState.remainingTradingDays,
                remainingTimeYears = spreadState.remainingTimeYears,
                lastProcessedTradingDate = spreadState.lastProcessedTradingDate,
                longPutUnits = spreadState.longPutUnits,
                longPutStrike = spreadState.longPutStrike,
                shortPutUnits = spreadState.shortPutUnits,
                shortPutStrike = spreadState.shortPutStrike,
                netOptionMark = spreadState.netOptionMark,
                cycleGrossPremiumReceived = spreadState.cycleGrossPremiumReceived,
                cycleGrossPremiumPaid = spreadState.cycleGrossPremiumPaid,
                cycleImplementationCost = spreadState.cycleImplementationCost,
                cumulativePremiumReceived = spreadState.cumulativePremiumReceived,
                cumulativePremiumPaid = spreadState.cumulativePremiumPaid,
                cumulativeSettlementCashFlow = spreadState.cumulativeSettlementCashFlow,
                cumulativeImplementationCost = spreadState.cumulativeImplementationCost,
                lifecycle = spreadState.lifecycle,
                cumulativeUnitAdjustmentFactor = spreadState.cumulativeUnitAdjustmentFactor,
                lastCorporateActionAccountingSequence =
                spreadState.lastCorporateActionAccountingSequence,
                asOf = spreadState.asOf,
                revision = spreadState.revision,
            )
        }.getOrNull()
        val expectedAdjustment = state.unitAdjustmentLineage(productId)
        val listing = state.listingLifecycleStates[productId]
            ?: return "현금담보 풋스프레드 상품의 상장 생명주기 상태가 없습니다."
        val freezesAtLastMark = listing.isSettlementPending || listing.isTerminal
        if (reconstructed != spreadState || spreadState.productId != productId ||
            terms.productId != productId || spreadState.cashBenchmarkRef != terms.cashBenchmarkRef ||
            spreadState.optionReference != terms.optionReference ||
            spreadState.rollCalendar != terms.rollCalendar ||
            spreadState.asOf !in GameCalendar.startInstant..state.currentTime ||
            !freezesAtLastMark && spreadState.asOf != state.currentTime ||
            !unitAdjustmentMarkerMatches(
                actualFactor = spreadState.cumulativeUnitAdjustmentFactor,
                actualLastSequence = spreadState.lastCorporateActionAccountingSequence,
                expected = expectedAdjustment,
            )
        ) {
            return "현금담보 풋스프레드 상태의 ID·약관·수치 범위·기준 시각이 유효하지 않습니다."
        }
        if (product.legalStructure != FundLegalStructure.OPEN_END_ETF ||
            terms.cashBenchmarkRef != product.benchmarkRef
        ) {
            return "현금담보 풋스프레드는 상품 자체 머니마켓 기준을 가진 개방형 ETF여야 합니다."
        }
        if (catalog != null) {
            val cashDefinition = catalog.findBenchmark(terms.cashBenchmarkRef)
                ?: return "현금담보 풋스프레드의 현금 benchmark가 카탈로그에 없습니다."
            val isMoneyMarketCurve =
                cashDefinition.engineKind == BenchmarkEngineKind.FIXED_INCOME_CURVE &&
                    cashDefinition.fixedIncomeProfile?.assetType == FixedIncomeAssetType.MONEY_MARKET
            val isOvernightRateIndex =
                cashDefinition.engineKind == BenchmarkEngineKind.OVERNIGHT_RATE_INDEX &&
                    cashDefinition.kofrIndexProfile != null
            if (!isMoneyMarketCurve && !isOvernightRateIndex) {
                return "현금담보 풋스프레드의 현금 benchmark가 실행 가능한 머니마켓 기준이 아닙니다."
            }
        }
        when (terms.optionReference.kind) {
            DailyResetReferenceKind.BENCHMARK -> {
                val optionRef = terms.optionReference.benchmarkRef
                    ?: return "현금담보 풋스프레드의 옵션 benchmark 참조가 비어 있습니다."
                val optionDefinition = catalog?.findBenchmark(optionRef)
                if (terms.optionReference.instrumentId != null ||
                    optionRef == terms.cashBenchmarkRef ||
                    catalog != null && optionDefinition?.engineKind !in setOf(
                        BenchmarkEngineKind.EQUITY_METHODOLOGY,
                        BenchmarkEngineKind.EQUITY_REFERENCE,
                    )
                ) {
                    return "현금담보 풋스프레드의 옵션 benchmark가 실행 가능한 주식 기준과 다릅니다."
                }
            }
            DailyResetReferenceKind.INSTRUMENT -> if (
                terms.optionReference.benchmarkRef != null ||
                terms.optionReference.instrumentId !in stocksById ||
                terms.optionReference.instrumentId == productId ||
                stocksById[terms.optionReference.instrumentId]?.behavior?.strategy !=
                InstrumentStrategy.OPERATING_COMPANY
            ) {
                return "현금담보 풋스프레드의 옵션 기초 종목 참조가 현재 카탈로그와 다릅니다."
            }
        }

        val market = referenceMarket(terms.rollCalendar)
        val asOfReferenceDate = GameCalendar.marketLocalDateTime(market, spreadState.asOf).date
        if (spreadState.cycleStartedOn > asOfReferenceDate ||
            spreadState.cycleStartedOn.year !in
            GameCalendar.START_LOCAL_DATE_TIME.year..GameCalendar.CAMPAIGN_END_DATE.year ||
            !terms.rollCalendar.isTradingDate(spreadState.cycleStartedOn) ||
            spreadState.lastProcessedTradingDate?.let { date ->
                date > asOfReferenceDate || !terms.rollCalendar.isTradingDate(date)
            } == true ||
            spreadState.remainingTimeYears >
            terms.tenorTradingDays / OPTION_TRADING_DAYS_PER_YEAR +
            OPTION_STRATEGY_ACCOUNTING_EPSILON
        ) {
            return "현금담보 풋스프레드 주기의 거래일·잔존 tenor가 약관 캘린더와 다릅니다."
        }

        val bootstrapDate = lastCompletedTradingDate(terms.rollCalendar, GameCalendar.startInstant)
        val rollInterval = terms.tenorTradingDays - terms.rollLeadTradingDays
        val closesToCycleStart = tradingDatesAfter(
            terms.rollCalendar,
            bootstrapDate,
            spreadState.cycleStartedOn,
        )
        val hasInvalidRevisionLineage = when (spreadState.lifecycle) {
            CashCollateralizedPutSpreadLifecycle.ACTIVE,
            CashCollateralizedPutSpreadLifecycle.VALUE_EXHAUSTED,
            -> spreadState.revision == 0L && spreadState.cycleStartedOn != bootstrapDate ||
                spreadState.revision > 0L &&
                (spreadState.cycleStartedOn <= bootstrapDate ||
                    closesToCycleStart.toLong() != spreadState.revision * rollInterval.toLong())
            CashCollateralizedPutSpreadLifecycle.AWAITING_PRODUCT_LIQUIDATION -> false
        }
        if (hasInvalidRevisionLineage) {
            return "현금담보 풋스프레드 revision과 현재 주기 시작일의 roll 계보가 다릅니다."
        }
        if (spreadState.revision == 0L &&
            (spreadState.cycleGrossPremiumReceived != 0.0 ||
                spreadState.cycleGrossPremiumPaid != 0.0 ||
                spreadState.cycleImplementationCost != 0.0 ||
                spreadState.cumulativePremiumReceived != 0.0 ||
                spreadState.cumulativePremiumPaid != 0.0 ||
                spreadState.cumulativeSettlementCashFlow != 0.0 ||
                spreadState.cumulativeImplementationCost != 0.0)
        ) {
            return "현금담보 풋스프레드 bootstrap 상태에 캠페인 이전 premium·정산·비용이 기록되었습니다."
        }

        if (spreadState.lifecycle == CashCollateralizedPutSpreadLifecycle.ACTIVE) {
            val expectedLongStrike =
                spreadState.cycleOptionReferenceLevel * terms.longPutStrikeMoneyness
            val expectedShortStrike =
                spreadState.cycleOptionReferenceLevel * terms.shortPutStrikeMoneyness
            val longStrike = requireNotNull(spreadState.longPutStrike)
            val shortStrike = requireNotNull(spreadState.shortPutStrike)
            val expectedMaximumLoss = spreadState.longPutUnits * (shortStrike - longStrike)
            if (!amountsAreClose(spreadState.longPutUnits, spreadState.shortPutUnits) ||
                !amountsAreClose(longStrike, expectedLongStrike) ||
                !amountsAreClose(shortStrike, expectedShortStrike) ||
                !amountsAreClose(
                    spreadState.optionNotionalAtRoll,
                    spreadState.longPutUnits * spreadState.cycleOptionReferenceLevel,
                ) ||
                !amountsAreClose(spreadState.maximumSettlementLossAtRoll, expectedMaximumLoss) ||
                spreadState.maximumSettlementLossAtRoll >
                spreadState.navAtRoll * terms.maximumSettlementLossRatio +
                OPTION_STRATEGY_ACCOUNTING_EPSILON * maxOf(1.0, spreadState.navAtRoll)
            ) {
                return "현금담보 풋스프레드의 leg·행사가·notional·최대손실이 약관과 다릅니다."
            }

            val latestCompleted = lastCompletedTradingDate(terms.rollCalendar, spreadState.asOf)
            val expectedLastProcessed = if (
                spreadState.revision == 0L && latestCompleted == spreadState.cycleStartedOn
            ) {
                null
            } else {
                latestCompleted
            }
            val consumedTradingDays = tradingDatesAfter(
                terms.rollCalendar,
                spreadState.cycleStartedOn,
                expectedLastProcessed ?: spreadState.cycleStartedOn,
            )
            if (spreadState.lastProcessedTradingDate != expectedLastProcessed ||
                consumedTradingDays >= rollInterval ||
                spreadState.remainingTradingDays != terms.tenorTradingDays - consumedTradingDays
            ) {
                return "현금담보 풋스프레드의 최종 처리 거래일·잔존일수가 현재 roll 주기와 다릅니다."
            }
        } else if (
            spreadState.lifecycle == CashCollateralizedPutSpreadLifecycle.AWAITING_PRODUCT_LIQUIDATION &&
            run {
                val directUnderlyingId = terms.optionReference.instrumentId
                val notice = directUnderlyingId?.let { underlyingId ->
                    state.newsEvents.singleOrNull { event ->
                        event.id == "direct-underlying-liquidation:$productId:$underlyingId"
                    }
                }
                terms.optionReference.kind != DailyResetReferenceKind.INSTRUMENT ||
                    spreadState.revision == 0L || spreadState.cycleStartedOn < bootstrapDate ||
                    spreadState.lastProcessedTradingDate != spreadState.cycleStartedOn ||
                    notice?.instrumentTermination?.effectiveNotBefore?.let { it <= spreadState.asOf } != true ||
                    GameCalendar.marketLocalDateTime(market, spreadState.asOf).date !=
                    spreadState.cycleStartedOn
            }
        ) {
            return "상품 청산 대기 현금담보 풋스프레드의 종료 주기·revision 계보가 유효하지 않습니다."
        }
    }
    return null
}

/** ETN 계약 상태와 사건 배치 원장을 발행자·정산 관측창·존속 수량 계보에 결속한다. */
private fun validateEtnPersistenceState(
    state: SimulatorUiState,
    stocksById: Map<String, StockDefinition>,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    val expectedProductIds = stocksById.values.filter { stock ->
        stock.fundProductProfile?.legalStructure == FundLegalStructure.EXCHANGE_TRADED_NOTE
    }.mapTo(linkedSetOf(), StockDefinition::id)
    if (state.etnStates.keys != expectedProductIds ||
        state.etnLedger.any { entry -> entry.productId !in expectedProductIds }
    ) {
        return "ETN 상태·원장은 ETN 법적 구조 상품 집합과 정확히 일치해야 합니다."
    }
    if (state.etnLedger.map(EtnLedgerEntry::id).distinct().size != state.etnLedger.size) {
        return "ETN 원장 ID가 중복되었습니다."
    }
    val issuerModels = stocksById.values.mapNotNull { stock ->
        stock.fundProductProfile?.etnIssuerCreditModelParameters
    }.groupBy { parameters -> parameters.issuerId }
    if (issuerModels.values.any { parameters -> parameters.distinct().size != 1 }) {
        return "같은 ETN 발행자 ID에 서로 다른 신용 모델 모수가 저장되었습니다."
    }
    val recordsByProduct = state.etnLedger.groupBy(EtnLedgerEntry::productId)

    fun reconstructObservation(
        observation: EtnIndicativeValueObservation,
    ): EtnIndicativeValueObservation = EtnIndicativeValueObservation(
        observationDate = observation.observationDate,
        indicativeValuePerNote = observation.indicativeValuePerNote,
    )

    fun reconstructEntry(entry: EtnLedgerEntry): EtnLedgerEntry = EtnLedgerEntry(
        id = entry.id,
        productId = entry.productId,
        kind = entry.kind,
        effectiveAt = entry.effectiveAt,
        revision = entry.revision,
        sequenceInBatch = entry.sequenceInBatch,
        settlementCurrency = entry.settlementCurrency,
        referenceLevelBefore = entry.referenceLevelBefore,
        referenceLevelAfter = entry.referenceLevelAfter,
        indicativeValueBefore = entry.indicativeValueBefore,
        indicativeValueAfter = entry.indicativeValueAfter,
        notesOutstandingBefore = entry.notesOutstandingBefore,
        notesOutstandingAfter = entry.notesOutstandingAfter,
        notesIssued = entry.notesIssued,
        notesCancelled = entry.notesCancelled,
        notesSettled = entry.notesSettled,
        notesDelta = entry.notesDelta,
        cashPaidToNoteholders = entry.cashPaidToNoteholders,
        cashReceivedFromNoteholders = entry.cashReceivedFromNoteholders,
        contractEvent = entry.contractEvent,
        settlementIndicativeValueObservations =
        entry.settlementIndicativeValueObservations.map(::reconstructObservation),
    )

    for ((productId, etnState) in state.etnStates) {
        val stock = stocksById.getValue(productId)
        val product = stock.fundProductProfile
            ?: return "ETN 상태 상품에 펀드 상품 프로필이 없습니다."
        val terms = product.etnProductTerms
            ?: return "ETN 상태 상품에 계약 조건이 없습니다."
        val credit = product.etnIssuerCreditModelParameters
            ?: return "ETN 상태 상품에 발행자 신용 모델이 없습니다."
        if (catalog != null) {
            val canonicalProduct = catalog.findById(productId)?.fundProductProfile
                ?: return "ETN 상태 상품이 현재 카탈로그와 다릅니다."
            if (canonicalProduct != product || canonicalProduct.etnProductTerms != terms ||
                canonicalProduct.etnIssuerCreditModelParameters != credit
            ) {
                return "ETN 계약·발행자 신용 모델이 현재 카탈로그와 다릅니다."
            }
        }
        val reconstructed = runCatching {
            EtnState(
                productId = etnState.productId,
                referenceLevel = etnState.referenceLevel,
                feeAdjustedIndicativeValuePerNote = etnState.feeAdjustedIndicativeValuePerNote,
                notesOutstanding = etnState.notesOutstanding,
                accruedCouponPerNote = etnState.accruedCouponPerNote,
                issuerCreditSpread = etnState.issuerCreditSpread,
                issuerHazardRate = etnState.issuerHazardRate,
                issuerRecoveryRate = etnState.issuerRecoveryRate,
                indicativeValueObservationWindow =
                etnState.indicativeValueObservationWindow.map(::reconstructObservation),
                lifecycle = etnState.lifecycle,
                terminalCreditEvent = etnState.terminalCreditEvent,
                asOf = etnState.asOf,
                revision = etnState.revision,
            )
        }.getOrNull()
        val listing = state.listingLifecycleStates[productId]
            ?: return "ETN 상태 상품의 상장 생명주기 상태가 없습니다."
        val freezesAtLastMark = etnState.lifecycle == EtnLifecycle.SETTLED ||
            listing.isSettlementPending || listing.isTerminal
        if (reconstructed != etnState || etnState.productId != productId ||
            terms.productId != productId || terms.referenceId != product.benchmarkRef.benchmarkId ||
            terms.issuerId != credit.issuerId || terms.settlementCurrency.name != stock.currency.name ||
            etnState.asOf !in GameCalendar.startInstant..state.currentTime ||
            !freezesAtLastMark && etnState.asOf != state.currentTime
        ) {
            return "ETN 상태의 ID·계약·발행자·통화·기준 시각이 유효하지 않습니다."
        }

        val localAsOfDate = GameCalendar.marketLocalDateTime(stock.market, etnState.asOf).date
        if (etnState.indicativeValueObservationWindow.any { observation ->
                observation.observationDate > localAsOfDate ||
                    observation.observationDate < GameCalendar.START_LOCAL_DATE_TIME.date ||
                    GameCalendar.isWeekend(observation.observationDate) ||
                    observation.observationDate in
                    DefaultMarketHolidays.closedDates(stock.market, observation.observationDate.year)
            }
        ) {
            return "ETN 지표가치 관측창에 미래일·휴장일 관측이 있습니다."
        }

        var settlementDate = terms.maturityDate
        while (GameCalendar.isWeekend(settlementDate) ||
            settlementDate in DefaultMarketHolidays.closedDates(stock.market, settlementDate.year)
        ) {
            settlementDate = settlementDate.plus(1, DateTimeUnit.DAY)
        }
        val settlementClose = GameCalendar.regularSessionWindow(
            stock.market,
            settlementDate,
            DefaultMarketHolidays.closedDates(stock.market, settlementDate.year),
        )?.closesAt ?: return "ETN 만기 정산 거래소 종가를 계산할 수 없습니다."
        if (etnState.lifecycle == EtnLifecycle.ACTIVE && etnState.asOf >= settlementClose) {
            return "ETN이 계약 만기 후 첫 거래소 종가를 지나도록 ACTIVE로 남아 있습니다."
        }

        val records = recordsByProduct[productId].orEmpty()
        val batches = records.groupBy(EtnLedgerEntry::revision).toSortedMap()
        if (batches.size.toLong() != etnState.revision ||
            batches.keys != (1L..etnState.revision).toSet() ||
            batches.values.zipWithNext().any { (previous, next) ->
                previous.first().effectiveAt > next.first().effectiveAt
            }
        ) {
            return "ETN 원장 batch revision·효력시각 계보가 현재 상태와 다릅니다."
        }
        var replayedNotes = catalog?.findById(productId)?.sharesOutstanding
            ?: stock.sharesOutstanding
        var latestTerminalEvent: EtnCreditEvent? = null
        var latestTerminalSettlement: EtnLedgerEntry? = null
        for ((revision, entries) in batches) {
            if (entries.map(EtnLedgerEntry::sequenceInBatch) != entries.indices.toList() ||
                entries.map(EtnLedgerEntry::kind).distinct().size != entries.size ||
                entries != entries.sortedBy { entry -> entry.kind.ordinal } ||
                entries.any { entry ->
                    entry.revision != revision || entry.effectiveAt != entries.first().effectiveAt ||
                        entry.effectiveAt > etnState.asOf ||
                        entry.settlementCurrency != terms.settlementCurrency ||
                        entry.id != "$productId:$revision:${entry.sequenceInBatch}" ||
                        runCatching { reconstructEntry(entry) }.getOrNull() != entry
                }
            ) {
                return "ETN 원장 batch의 순서·통화·ID·도메인 불변조건이 유효하지 않습니다."
            }
            for (entry in entries) {
                if (entry.notesOutstandingBefore != replayedNotes) {
                    return "ETN 원장의 존속 note 수량 계보가 끊어졌습니다."
                }
                replayedNotes = entry.notesOutstandingAfter
                if (entry.kind == EtnLedgerKind.CONTRACT_SETTLEMENT) {
                    val rule = when (entry.contractEvent) {
                        EtnCreditEvent.NONE -> return "ETN 계약 정산 원장에 사건 종류가 없습니다."
                        EtnCreditEvent.HOLDER_REDEMPTION -> terms.callTerms.holderRedemptionValuationRule
                        EtnCreditEvent.ISSUER_CALL -> terms.callTerms.issuerCallValuationRule
                        EtnCreditEvent.CONTRACTUAL_MATURITY -> terms.maturityValuationRule
                        EtnCreditEvent.ISSUER_ACCELERATION -> if (
                            entry.notesSettled == entry.notesOutstandingBefore
                        ) {
                            terms.accelerationTerms.fullAccelerationValuationRule
                        } else {
                            terms.accelerationTerms.partialAccelerationValuationRule
                        }
                        EtnCreditEvent.CREDIT_DEFAULT -> null
                    }
                    val expectedObservationCount = if (
                        entry.contractEvent == EtnCreditEvent.CREDIT_DEFAULT
                    ) {
                        1
                    } else {
                        rule?.observationCount
                            ?: return "ETN 계약 정산 원장에 해당 사건의 평가 규칙이 없습니다."
                    }
                    if (entry.settlementIndicativeValueObservations.size != expectedObservationCount ||
                        entry.settlementIndicativeValueObservations
                            .map(EtnIndicativeValueObservation::observationDate)
                            .zipWithNext().any { (previous, next) -> previous >= next } ||
                        entry.settlementIndicativeValueObservations.last().observationDate >
                        GameCalendar.marketLocalDateTime(stock.market, entry.effectiveAt).date
                    ) {
                        return "ETN 계약 정산 원장의 canonical 지표가치 관측창이 약관과 다릅니다."
                    }
                    latestTerminalEvent = entry.contractEvent.takeIf { event ->
                        event != EtnCreditEvent.HOLDER_REDEMPTION && entry.notesOutstandingAfter == 0L
                    }
                    if (latestTerminalEvent != null) latestTerminalSettlement = entry
                }
            }
        }
        if (replayedNotes != etnState.notesOutstanding ||
            etnState.lifecycle == EtnLifecycle.SETTLED &&
            (latestTerminalEvent == null || latestTerminalEvent != etnState.terminalCreditEvent) ||
            etnState.lifecycle == EtnLifecycle.ACTIVE && latestTerminalEvent != null
        ) {
            return "ETN 원장 재생 결과와 현재 note 수량·종단 사건이 다릅니다."
        }
        val hasOrderlyListingSettlement =
            listing.activeReason == ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION &&
                listing.status in setOf(
                    ListingLifecycleStatus.LIQUIDATION_PENDING,
                    ListingLifecycleStatus.TERMINATED,
                )
        if ((etnState.lifecycle == EtnLifecycle.SETTLED) != hasOrderlyListingSettlement) {
            return "ETN 계약 정산 상태와 상장 생명주기의 현금청산 단계가 일치하지 않습니다."
        }
        if (hasOrderlyListingSettlement) {
            val occurrenceId = listing.controllingTerminationOccurrenceId
                ?: return "ETN 계약 정산 상태에 지배 종료 공시 ID가 없습니다."
            val notice = state.newsEvents.exactTerminationNotice(occurrenceId, stock)
                ?: return "ETN 계약 정산 상태의 지배 종료 공시를 찾을 수 없습니다."
            val expectedContractEvent = when (notice.terms.kind) {
                InstrumentTerminationKind.CONTRACTUAL_MATURITY ->
                    EtnCreditEvent.CONTRACTUAL_MATURITY
                InstrumentTerminationKind.CREDIT_DEFAULT -> EtnCreditEvent.CREDIT_DEFAULT
                InstrumentTerminationKind.ISSUER_ACCELERATION ->
                    EtnCreditEvent.ISSUER_ACCELERATION
                InstrumentTerminationKind.OPTIONAL_CALL -> EtnCreditEvent.ISSUER_CALL
                InstrumentTerminationKind.FUND_LIQUIDATION ->
                    return "ETN 계약 정산 공시에 펀드 청산 종류를 사용할 수 없습니다."
            }
            val terminalSettlement = latestTerminalSettlement
                ?: return "ETN 현금청산 단계에 계약 정산 원장이 없습니다."
            val cashPerUnit = listing.finalDisposition?.cashPerUnit
                ?: return "ETN 현금청산 단계에 확정 지급 단가가 없습니다."
            if (terminalSettlement.contractEvent != expectedContractEvent ||
                etnState.terminalCreditEvent != expectedContractEvent ||
                terminalSettlement.notesSettled <= 0L ||
                !amountsAreClose(
                    terminalSettlement.cashPaidToNoteholders /
                        terminalSettlement.notesSettled.toDouble(),
                    cashPerUnit,
                )
            ) {
                return "ETN 종료 공시 종류·계약 정산 사건·상장 현금 지급 단가가 일치하지 않습니다."
            }
        }
    }
    return null
}

/** CEF 법적 대차대조표와 분배·자본·조달 batch 원장의 shares/liability 계보를 검증한다. */
private fun validateClosedEndFundPersistenceState(
    state: SimulatorUiState,
    stocksById: Map<String, StockDefinition>,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    val expectedFundIds = stocksById.values.filter { stock ->
        stock.fundProductProfile?.legalStructure == FundLegalStructure.CLOSED_END_FUND
    }.mapTo(linkedSetOf(), StockDefinition::id)
    if (state.closedEndFundStates.keys != expectedFundIds ||
        state.closedEndFundLedger.any { entry -> entry.fundId !in expectedFundIds }
    ) {
        return "CEF 상태·원장은 CEF 법적 구조 상품 집합과 정확히 일치해야 합니다."
    }
    if (state.closedEndFundLedger.map(ClosedEndFundLedgerEntry::id).distinct().size !=
        state.closedEndFundLedger.size
    ) {
        return "CEF 원장 ID가 중복되었습니다."
    }
    val recordsByFund = state.closedEndFundLedger.groupBy(ClosedEndFundLedgerEntry::fundId)

    fun close(left: Double, right: Double): Boolean = amountsAreClose(left, right)

    fun reconstructEntry(entry: ClosedEndFundLedgerEntry): ClosedEndFundLedgerEntry =
        ClosedEndFundLedgerEntry(
            id = entry.id,
            fundId = entry.fundId,
            kind = entry.kind,
            effectiveAt = entry.effectiveAt,
            revision = entry.revision,
            sequenceInBatch = entry.sequenceInBatch,
            settlementCurrency = entry.settlementCurrency,
            capitalActionKind = entry.capitalActionKind,
            financingActionKind = entry.financingActionKind,
            grossAssetsDelta = entry.grossAssetsDelta,
            commonSharesDelta = entry.commonSharesDelta,
            debtLiabilityDelta = entry.debtLiabilityDelta,
            preferredShareLiabilityDelta = entry.preferredShareLiabilityDelta,
            externalCashFlow = entry.externalCashFlow,
            cashToCommonShareholders = entry.cashToCommonShareholders,
            netInvestmentIncomeDistribution = entry.netInvestmentIncomeDistribution,
            realizedGainDistribution = entry.realizedGainDistribution,
            returnOfCapitalDistribution = entry.returnOfCapitalDistribution,
            navPerShareBefore = entry.navPerShareBefore,
            navPerShareAfter = entry.navPerShareAfter,
        )

    for ((fundId, cefState) in state.closedEndFundStates) {
        val stock = stocksById.getValue(fundId)
        val product = stock.fundProductProfile
            ?: return "CEF 상태 상품에 펀드 상품 프로필이 없습니다."
        val terms = product.closedEndFundTerms
            ?: return "CEF 상태 상품에 법적 조건이 없습니다."
        val parameters = product.closedEndFundMarketModelParameters
            ?: return "CEF 상태 상품에 시장 모델 모수가 없습니다."
        if (catalog != null) {
            val canonicalProduct = catalog.findById(fundId)?.fundProductProfile
                ?: return "CEF 상태 상품이 현재 카탈로그와 다릅니다."
            if (canonicalProduct != product || canonicalProduct.closedEndFundTerms != terms ||
                canonicalProduct.closedEndFundMarketModelParameters != parameters
            ) {
                return "CEF 법적 조건·시장 모델이 현재 카탈로그와 다릅니다."
            }
        }
        val reconstructed = runCatching {
            ClosedEndFundState(
                fundId = cefState.fundId,
                grossAssets = cefState.grossAssets,
                commonSharesOutstanding = cefState.commonSharesOutstanding,
                debtLiability = cefState.debtLiability,
                preferredShareLiability = cefState.preferredShareLiability,
                navPerCommonShare = cefState.navPerCommonShare,
                undistributedNetInvestmentIncome = cefState.undistributedNetInvestmentIncome,
                distributionReserve = cefState.distributionReserve,
                marketDiscountRate = cefState.marketDiscountRate,
                cumulativeUnitAdjustmentFactor = cefState.cumulativeUnitAdjustmentFactor,
                lastCorporateActionAccountingSequence =
                cefState.lastCorporateActionAccountingSequence,
                asOf = cefState.asOf,
                revision = cefState.revision,
            )
        }.getOrNull()
        val expectedAdjustment = state.unitAdjustmentLineage(fundId)
        val listing = state.listingLifecycleStates[fundId]
            ?: return "CEF 상태 상품의 상장 생명주기 상태가 없습니다."
        val freezesAtLastMark = listing.isSettlementPending || listing.isTerminal
        if (reconstructed != cefState || cefState.fundId != fundId || terms.fundId != fundId ||
            parameters.fundId != fundId || terms.settlementCurrency.name != stock.currency.name ||
            cefState.asOf !in GameCalendar.startInstant..state.currentTime ||
            !freezesAtLastMark && cefState.asOf != state.currentTime ||
            !terms.allowsDebtLeverage && cefState.debtLiability != 0.0 ||
            !terms.allowsPreferredLeverage && cefState.preferredShareLiability != 0.0 ||
            !unitAdjustmentMarkerMatches(
                actualFactor = cefState.cumulativeUnitAdjustmentFactor,
                actualLastSequence = cefState.lastCorporateActionAccountingSequence,
                expected = expectedAdjustment,
            )
        ) {
            return "CEF 상태의 ID·계약·통화·대차대조표·기준 시각이 유효하지 않습니다."
        }

        val records = recordsByFund[fundId].orEmpty()
        val batches = records.groupBy(ClosedEndFundLedgerEntry::revision).toSortedMap()
        if (batches.size.toLong() != cefState.revision ||
            batches.keys != (1L..cefState.revision).toSet() ||
            batches.values.zipWithNext().any { (previous, next) ->
                previous.first().effectiveAt > next.first().effectiveAt
            }
        ) {
            return "CEF 원장 batch revision·효력시각 계보가 현재 상태와 다릅니다."
        }

        val canonicalStock = catalog?.findById(fundId) ?: stock
        var replayedShares = canonicalStock.sharesOutstanding.toDouble()
        val initialNav = canonicalStock.initialPrice / (1.0 + parameters.targetMarketDiscountRate)
        val commonNetAssets = initialNav * replayedShares
        val netAssetFraction = 1.0 - parameters.initialDebtToGrossAssets -
            parameters.initialPreferredToGrossAssets
        val initialGrossAssets = commonNetAssets / netAssetFraction
        var replayedDebt = initialGrossAssets * parameters.initialDebtToGrossAssets
        var replayedPreferred = initialGrossAssets * parameters.initialPreferredToGrossAssets
        val unitAdjustments = state.corporateActionLedger.filter { action -> action.stockId == fundId }
        if (unitAdjustments.any { action -> action.effectiveAt > cefState.asOf }) {
            return "CEF 누적 좌수조정 원장이 법적 상태의 기준 시각보다 미래입니다."
        }
        var unitAdjustmentIndex = 0
        fun applyUnitAdjustmentsThrough(
            boundary: kotlin.time.Instant,
            inclusive: Boolean,
        ) {
            while (unitAdjustmentIndex < unitAdjustments.size) {
                val adjustment = unitAdjustments[unitAdjustmentIndex]
                val isDue = if (inclusive) {
                    adjustment.effectiveAt <= boundary
                } else {
                    adjustment.effectiveAt < boundary
                }
                if (!isDue) break
                replayedShares *= adjustment.quantityMultiplier
                unitAdjustmentIndex += 1
            }
        }

        val orderedBatches = batches.entries.toList()
        for ((batchIndex, batch) in orderedBatches.withIndex()) {
            val revision = batch.key
            val entries = batch.value
            val batchEffectiveAt = entries.first().effectiveAt
            applyUnitAdjustmentsThrough(batchEffectiveAt, inclusive = false)
            if (entries.map(ClosedEndFundLedgerEntry::sequenceInBatch) != entries.indices.toList() ||
                entries.map(ClosedEndFundLedgerEntry::kind).distinct().size != entries.size ||
                entries != entries.sortedBy { entry -> entry.kind.ordinal } ||
                entries.any { entry ->
                    entry.revision != revision || entry.effectiveAt != entries.first().effectiveAt ||
                        entry.effectiveAt > cefState.asOf ||
                        entry.settlementCurrency != terms.settlementCurrency ||
                        entry.id != "$fundId:$revision:${entry.sequenceInBatch}" ||
                        runCatching { reconstructEntry(entry) }.getOrNull() != entry
                }
            ) {
                return "CEF 원장 batch의 순서·통화·ID·도메인 불변조건이 유효하지 않습니다."
            }
            for (entry in entries) {
                val grossBefore = entry.navPerShareBefore * replayedShares +
                    replayedDebt + replayedPreferred
                val grossAfter = grossBefore + entry.grossAssetsDelta
                val sharesAfter = replayedShares + entry.commonSharesDelta
                val debtAfter = replayedDebt + entry.debtLiabilityDelta
                val preferredAfter = replayedPreferred + entry.preferredShareLiabilityDelta
                if (grossAfter <= 0.0 || sharesAfter <= 0.0 || debtAfter < 0.0 || preferredAfter < 0.0 ||
                    !close(
                        entry.navPerShareAfter,
                        (grossAfter - debtAfter - preferredAfter) / sharesAfter,
                    )
                ) {
                    return "CEF 원장의 총자산·주식수·부채·우선주 변화가 NAV 계정식과 다릅니다."
                }
                when (entry.kind) {
                    ClosedEndFundLedgerKind.DISTRIBUTION -> Unit
                    ClosedEndFundLedgerKind.CAPITAL_ACTION -> when (entry.capitalActionKind) {
                        ClosedEndFundCapitalActionKind.NONE ->
                            return "CEF 자본행동 원장에 사건 종류가 없습니다."
                        ClosedEndFundCapitalActionKind.TENDER_OFFER ->
                            if (!terms.allowsTenderOffers) return "허용되지 않은 CEF 공개매수 원장이 있습니다."
                        ClosedEndFundCapitalActionKind.SHARE_BUYBACK ->
                            if (!terms.allowsShareRepurchases) return "허용되지 않은 CEF 자사주매입 원장이 있습니다."
                        ClosedEndFundCapitalActionKind.RIGHTS_OFFERING ->
                            if (!terms.allowsRightsOfferings) return "허용되지 않은 CEF 권리공모 원장이 있습니다."
                        ClosedEndFundCapitalActionKind.AT_THE_MARKET_OFFERING ->
                            if (!terms.allowsAtTheMarketOfferings) return "허용되지 않은 CEF ATM 발행 원장이 있습니다."
                    }
                    ClosedEndFundLedgerKind.FINANCING -> when (entry.financingActionKind) {
                        ClosedEndFundFinancingActionKind.NONE ->
                            return "CEF 조달 원장에 사건 종류가 없습니다."
                        ClosedEndFundFinancingActionKind.DRAW_DEBT,
                        ClosedEndFundFinancingActionKind.REPAY_DEBT,
                        -> if (!terms.allowsDebtLeverage) return "허용되지 않은 CEF 부채 조달 원장이 있습니다."
                        ClosedEndFundFinancingActionKind.ISSUE_PREFERRED_SHARES,
                        ClosedEndFundFinancingActionKind.REDEEM_PREFERRED_SHARES,
                        -> if (!terms.allowsPreferredLeverage) return "허용되지 않은 CEF 우선주 조달 원장이 있습니다."
                    }
                }
                replayedShares = sharesAfter
                replayedDebt = debtAfter
                replayedPreferred = preferredAfter
            }
            val nextBatchEffectiveAt = orderedBatches.getOrNull(batchIndex + 1)
                ?.value
                ?.first()
                ?.effectiveAt
            if (nextBatchEffectiveAt != batchEffectiveAt) {
                // Runtime boundary adjustments run after same-instant CEF cash/legal batches.
                applyUnitAdjustmentsThrough(batchEffectiveAt, inclusive = true)
            }
        }
        applyUnitAdjustmentsThrough(cefState.asOf, inclusive = true)
        if (!close(replayedShares, cefState.commonSharesOutstanding) ||
            !close(replayedDebt, cefState.debtLiability) ||
            !close(replayedPreferred, cefState.preferredShareLiability)
        ) {
            return "CEF 원장 재생 결과와 현재 주식수·부채·우선주 잔액이 다릅니다."
        }
    }
    return null
}

/** 저장 생성자를 우회한 KOFR fixing·공표·지수 상태를 공식 영업일 시계에 결속한다. */
private fun validateKofrIndexPersistenceState(
    state: SimulatorUiState,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    val definitionsByRef = catalog
        ?.benchmarksInEvaluationOrder
        ?.filter { definition -> definition.engineKind == BenchmarkEngineKind.OVERNIGHT_RATE_INDEX }
        ?.associateBy(BenchmarkDefinition::ref)
    if (definitionsByRef != null && state.kofrIndexStates.keys != definitionsByRef.keys) {
        return "KOFR 지수 상태는 실행 가능한 익일물 금리 벤치마크마다 정확히 하나씩 필요합니다."
    }
    if (state.kofrIndexStates.size > KofrIndexBook.MAX_REFERENCES) {
        return "KOFR 지수 상태 수가 저장 한도를 초과합니다."
    }
    if (state.kofrIndexStates.keys.any { ref ->
            state.fixedIncomeReferenceStates.values.any { it.benchmarkRef == ref } ||
                ref in state.equityReferenceStates || ref in state.commoditySpotReferenceStates ||
                ref in state.futuresReferenceStates || ref in state.fundOfFundsStates ||
                ref in state.alternativeRiskPremiaStates || ref in state.compositeReferenceStates
        }
    ) {
        return "하나의 벤치마크 버전을 KOFR와 다른 기준 엔진이 동시에 소유할 수 없습니다."
    }

    for ((ref, saved) in state.kofrIndexStates) {
        if (saved.benchmarkRef != ref || saved.asOf != state.currentTime) {
            return "KOFR 지수 상태의 벤치마크 키 또는 기준 시각이 현재 캠페인과 다릅니다."
        }
        val reconstructed = runCatching {
            KofrIndexState(
                benchmarkRef = saved.benchmarkRef,
                publishedRateAnnual = saved.publishedRateAnnual,
                publishedRateObservationDate = saved.publishedRateObservationDate,
                indexLevel = saved.indexLevel,
                indexPublicationDate = saved.indexPublicationDate,
                pendingRateAnnual = saved.pendingRateAnnual,
                pendingRateObservationDate = saved.pendingRateObservationDate,
                revision = saved.revision,
                asOf = saved.asOf,
            )
        }.getOrNull()
        if (reconstructed != saved) {
            return "KOFR 지수 상태의 금리·지수·대기 fixing 불변식이 유효하지 않습니다."
        }
        val definition = definitionsByRef?.get(ref) ?: continue
        val profile = definition.kofrIndexProfile
            ?: return "KOFR 지수 벤치마크에 실행 프로필이 없습니다."
        val local = state.currentTime.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE)
        val publicationTime = LocalTime(profile.publicationHourKst, profile.publicationMinuteKst)
        var expectedPublicationDate = local.date
        if (!KofrBusinessCalendar.isBusinessDate(expectedPublicationDate) || local.time < publicationTime) {
            expectedPublicationDate = expectedPublicationDate.minus(1, DateTimeUnit.DAY)
        }
        expectedPublicationDate = KofrBusinessCalendar.latestBusinessDateOnOrBefore(expectedPublicationDate)
        val expectedPublishedObservationDate =
            KofrBusinessCalendar.previousBusinessDate(expectedPublicationDate)
        var latestCapturedDate = local.date
        if (!KofrBusinessCalendar.isBusinessDate(latestCapturedDate) ||
            local.time < LocalTime(profile.observationCaptureHourKst, 0)
        ) {
            latestCapturedDate = latestCapturedDate.minus(1, DateTimeUnit.DAY)
        }
        latestCapturedDate = KofrBusinessCalendar.latestBusinessDateOnOrBefore(latestCapturedDate)
        val expectedPendingDate = latestCapturedDate.takeIf {
            it > expectedPublishedObservationDate
        }
        if (saved.indexPublicationDate != expectedPublicationDate ||
            saved.publishedRateObservationDate != expectedPublishedObservationDate ||
            saved.pendingRateObservationDate != expectedPendingDate ||
            (saved.pendingRateAnnual == null) != (expectedPendingDate == null)
        ) {
            return "KOFR 지수 상태의 공표일·관측일·대기 fixing이 공식 영업일 시계와 다릅니다."
        }
        var expectedRevision = 0L
        var date = GameCalendar.startInstant.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE).date
        while (date <= local.date) {
            if (KofrBusinessCalendar.isBusinessDate(date)) {
                val publicationAt = LocalDateTime(date, publicationTime)
                    .toInstant(GameCalendar.KOREA_TIME_ZONE)
                if (publicationAt > GameCalendar.startInstant && publicationAt <= state.currentTime) {
                    expectedRevision += 1L
                }
            }
            date = date.plus(1, DateTimeUnit.DAY)
        }
        if (saved.revision != expectedRevision) {
            return "KOFR 지수 revision이 캠페인 공표 횟수와 일치하지 않습니다."
        }
        val scale = 10.0.pow(profile.indexDecimalPlaces)
        if (abs(saved.indexLevel * scale - round(saved.indexLevel * scale)) > 1e-6) {
            return "KOFR 지수 수준이 공식 소수점 자릿수로 반올림되지 않았습니다."
        }
        if (state.currentTime == GameCalendar.startInstant &&
            (saved.publishedRateAnnual != profile.initialPublishedRateAnnual ||
                saved.publishedRateObservationDate != profile.initialPublishedRateObservationDate ||
                saved.indexLevel != profile.initialIndexLevel ||
                saved.indexPublicationDate != profile.initialIndexPublicationDate ||
                saved.pendingRateAnnual != profile.initialPendingRateAnnual ||
                saved.pendingRateObservationDate != profile.initialPendingRateObservationDate)
        ) {
            return "KOFR 초기 공식 공표 snapshot이 카탈로그 anchor와 다릅니다."
        }
    }
    if (definitionsByRef != null) {
        val scheduledEventEngine = ScheduledEventEngine(
            DeterministicRandom.mixSeed(state.options.seed, ScheduledEventEngine.STREAM_ID),
        )
        val canonicalBook = runCatching {
            KofrIndexBookEngine(KofrRateModel.forCampaignSeed(state.options.seed)).canonicalBook(
                definitions = definitionsByRef.values,
                at = state.currentTime,
                koreanPolicyRateAnnualAt = { at ->
                    scheduledEventEngine.centralBankRateAnnualAt(ScheduledEventKind.KR_BOK, at)
                },
            )
        }.getOrNull() ?: return "KOFR 공식 anchor에서 현재 시각까지 canonical 상태를 재생할 수 없습니다."
        if (state.kofrIndexStates != canonicalBook.states) {
            return "KOFR 금리·지수·대기 fixing 상태가 공식 anchor와 정책금리 계보를 재생한 원본과 다릅니다."
        }
    }
    return null
}

/** 저장 생성자를 우회한 고정수익 곡선·ladder와 만기 교체 원장을 카탈로그 계보에 결속한다. */
private fun validateFixedIncomeReferencePersistenceState(
    state: SimulatorUiState,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    val definitionsByReferenceId: Map<String, BenchmarkDefinition>? = catalog
        ?.benchmarksInEvaluationOrder
        ?.filter { definition -> definition.engineKind == BenchmarkEngineKind.FIXED_INCOME_CURVE }
        ?.associateBy { definition -> FixedIncomeReferenceState.referenceIdFor(definition.ref) }
    if (definitionsByReferenceId != null &&
        state.fixedIncomeReferenceStates.keys != definitionsByReferenceId.keys
    ) {
        return "고정수익 기준 상태는 실행 가능한 곡선 벤치마크마다 정확히 하나씩 필요합니다."
    }
    if (state.fixedIncomeReferenceStates.values
            .map(FixedIncomeReferenceState::benchmarkRef)
            .distinct().size != state.fixedIncomeReferenceStates.size
    ) {
        return "하나의 고정수익 벤치마크 버전에 둘 이상의 기준 상태가 있습니다."
    }
    if (state.fixedIncomeRollLedger.map(FixedIncomeRollRecord::id).distinct().size !=
        state.fixedIncomeRollLedger.size
    ) {
        return "고정수익 만기 교체 원장 ID가 중복되었습니다."
    }

    val stateByRef = state.fixedIncomeReferenceStates.values.associateBy(
        FixedIncomeReferenceState::benchmarkRef,
    )
    if (state.fixedIncomeRollLedger.any { record -> record.benchmarkRef !in stateByRef }) {
        return "고정수익 만기 교체 원장에 현재 상태가 없는 벤치마크가 있습니다."
    }
    val recordsByRef = state.fixedIncomeRollLedger.groupBy(FixedIncomeRollRecord::benchmarkRef)

    fun parseAssetId(
        assetId: String,
        benchmarkRef: com.amond.kmpbook.domain.model.fund.BenchmarkRef,
    ): Pair<Pair<ReferenceCurrency, Int>, Long>? {
        val groups = FIXED_INCOME_ASSET_ID_PATTERN.matchEntire(assetId)?.groupValues ?: return null
        val version = groups[2].toIntOrNull() ?: return null
        val currency = ReferenceCurrency.entries.firstOrNull { it.name == groups[3] } ?: return null
        val rung = groups[4].toIntOrNull() ?: return null
        val generation = groups[5].toLongOrNull() ?: return null
        if (groups[1] != benchmarkRef.benchmarkId || version != benchmarkRef.version || rung !in 0..3) {
            return null
        }
        return (currency to rung) to generation
    }

    fun expectedInstrumentKind(type: FixedIncomeAssetType): FixedIncomeInstrumentKind = when (type) {
        FixedIncomeAssetType.NOMINAL_GOVERNMENT -> FixedIncomeInstrumentKind.TREASURY
        FixedIncomeAssetType.INFLATION_LINKED -> FixedIncomeInstrumentKind.INFLATION_LINKED
        FixedIncomeAssetType.AGENCY_MBS -> FixedIncomeInstrumentKind.MORTGAGE_BACKED
        FixedIncomeAssetType.SECURITIZED_CREDIT -> FixedIncomeInstrumentKind.SECURITIZED_CREDIT
        FixedIncomeAssetType.MUNICIPAL -> FixedIncomeInstrumentKind.MUNICIPAL
        FixedIncomeAssetType.PREFERRED_HYBRID -> FixedIncomeInstrumentKind.PREFERRED
        FixedIncomeAssetType.INVESTMENT_GRADE,
        FixedIncomeAssetType.HIGH_YIELD,
        FixedIncomeAssetType.MULTI_SECTOR_CREDIT,
        -> FixedIncomeInstrumentKind.CORPORATE
        FixedIncomeAssetType.FLOATING_RATE -> FixedIncomeInstrumentKind.FLOATING_RATE
        FixedIncomeAssetType.CLO -> FixedIncomeInstrumentKind.CLO_TRANCHE
        FixedIncomeAssetType.MONEY_MARKET -> FixedIncomeInstrumentKind.CASH_EQUIVALENT
    }

    fun expectedCreditQuality(bucket: FixedIncomeCreditBucket): CreditQuality = when (bucket) {
        FixedIncomeCreditBucket.GOVERNMENT_BACKED -> CreditQuality.SOVEREIGN
        FixedIncomeCreditBucket.AAA -> CreditQuality.AAA
        FixedIncomeCreditBucket.INVESTMENT_GRADE -> CreditQuality.BBB
        FixedIncomeCreditBucket.HIGH_YIELD -> CreditQuality.BB
        FixedIncomeCreditBucket.MIXED,
        FixedIncomeCreditBucket.UNVERIFIED,
        -> CreditQuality.BBB
    }

    fun reconstructPosition(position: FixedIncomeReferencePosition): FixedIncomeReferencePosition =
        FixedIncomeReferencePosition(
            assetId = position.assetId,
            kind = position.kind,
            currency = position.currency,
            creditQuality = position.creditQuality,
            currentWeight = position.currentWeight,
            targetWeight = position.targetWeight,
            dirtyMarketValue = position.dirtyMarketValue,
            remainingMaturityYears = position.remainingMaturityYears,
            modifiedDurationYears = position.modifiedDurationYears,
            convexityYearsSquared = position.convexityYearsSquared,
            spreadDurationYears = position.spreadDurationYears,
            couponRateAnnual = position.couponRateAnnual,
            floatingSpreadAnnual = position.floatingSpreadAnnual,
            floatingRateFloorAnnual = position.floatingRateFloorAnnual,
            inflationIndexRatio = position.inflationIndexRatio,
        )

    fun reconstructCurve(curve: YieldCurveSnapshot): YieldCurveSnapshot = YieldCurveSnapshot(
        currency = curve.currency,
        annualZeroRates = curve.annualZeroRates.toMap(),
        asOf = curve.asOf,
    )

    fun reconstructSpreads(spreads: CreditSpreadSnapshot): CreditSpreadSnapshot = CreditSpreadSnapshot(
        currency = spreads.currency,
        annualSpreads = spreads.annualSpreads.toMap(),
        asOf = spreads.asOf,
    )

    for ((referenceId, fixedIncomeState) in state.fixedIncomeReferenceStates) {
        val definition = definitionsByReferenceId?.get(referenceId)
        if (fixedIncomeState.referenceId != referenceId ||
            definition?.ref?.let { expected -> expected != fixedIncomeState.benchmarkRef } == true ||
            fixedIncomeState.asOf < GameCalendar.startInstant || fixedIncomeState.asOf > state.currentTime
        ) {
            return "고정수익 기준 상태의 map 키·벤치마크 버전·기준 시각이 유효하지 않습니다."
        }
        val reconstructed = runCatching {
            FixedIncomeReferenceState(
                benchmarkRef = fixedIncomeState.benchmarkRef,
                positions = fixedIncomeState.positions.map(::reconstructPosition),
                nominalCurves = fixedIncomeState.nominalCurves.mapValues { (_, curve) ->
                    reconstructCurve(curve)
                },
                realCurves = fixedIncomeState.realCurves.mapValues { (_, curve) ->
                    reconstructCurve(curve)
                },
                creditSpreads = fixedIncomeState.creditSpreads.mapValues { (_, spreads) ->
                    reconstructSpreads(spreads)
                },
                revision = fixedIncomeState.revision,
                asOf = fixedIncomeState.asOf,
            )
        }.getOrNull()
        if (reconstructed != fixedIncomeState) {
            return "고정수익 기준 상태의 비중·곡선·스프레드 도메인 불변조건이 유효하지 않습니다."
        }

        val profile = definition?.fixedIncomeProfile
        if (definition != null && profile == null) {
            return "곡선 엔진 고정수익 벤치마크에 기준 프로필이 없습니다."
        }
        val expectedCurrencies = profile?.currencies
            ?: fixedIncomeState.positions.mapTo(linkedSetOf(), FixedIncomeReferencePosition::currency)
        if (fixedIncomeState.positions.mapTo(linkedSetOf(), FixedIncomeReferencePosition::currency) !=
            expectedCurrencies ||
            fixedIncomeState.nominalCurves.keys != expectedCurrencies ||
            fixedIncomeState.realCurves.keys != expectedCurrencies ||
            fixedIncomeState.creditSpreads.keys != expectedCurrencies ||
            fixedIncomeState.positions.size != expectedCurrencies.size * 4
        ) {
            return "고정수익 기준 상태의 통화별 4-rung ladder·곡선 집합이 카탈로그와 다릅니다."
        }

        val targetWeight = 1.0 / fixedIncomeState.positions.size.toDouble()
        val expectedCoordinates = expectedCurrencies.flatMapTo(linkedSetOf()) { currency ->
            (0..3).map { rung -> currency to rung }
        }
        val currentCoordinates = linkedSetOf<Pair<ReferenceCurrency, Int>>()
        for (position in fixedIncomeState.positions) {
            val parsed = parseAssetId(position.assetId, fixedIncomeState.benchmarkRef)
                ?: return "고정수익 기준 포지션의 assetId 계보가 유효하지 않습니다."
            val (coordinate, generation) = parsed
            if (coordinate.first != position.currency || generation !in 0L..fixedIncomeState.revision ||
                !currentCoordinates.add(coordinate) ||
                kotlin.math.abs(position.targetWeight - targetWeight) >
                FIXED_INCOME_STATIC_VALUE_EPSILON
            ) {
                return "고정수익 기준 포지션의 통화·rung·generation·목표 비중이 유효하지 않습니다."
            }
            if (profile != null &&
                (position.kind != expectedInstrumentKind(profile.assetType) ||
                    position.creditQuality != expectedCreditQuality(profile.creditQuality))
            ) {
                return "고정수익 기준 포지션의 증권 구조·신용등급이 카탈로그 프로필과 다릅니다."
            }
            val expectedConvexity = position.modifiedDurationYears *
                position.modifiedDurationYears * FIXED_INCOME_CONVEXITY_MULTIPLIER
            val expectedSpreadDuration = if (position.creditQuality == CreditQuality.SOVEREIGN) {
                0.0
            } else {
                position.modifiedDurationYears
            }
            if (kotlin.math.abs(position.convexityYearsSquared - expectedConvexity) >
                FIXED_INCOME_STATIC_VALUE_EPSILON * maxOf(1.0, expectedConvexity) ||
                kotlin.math.abs(position.spreadDurationYears - expectedSpreadDuration) >
                FIXED_INCOME_STATIC_VALUE_EPSILON * maxOf(1.0, expectedSpreadDuration) ||
                position.kind != FixedIncomeInstrumentKind.INFLATION_LINKED &&
                position.inflationIndexRatio != 1.0
            ) {
                return "고정수익 기준 포지션의 듀레이션·볼록성·물가지수 계정이 유효하지 않습니다."
            }
        }
        if (currentCoordinates != expectedCoordinates) {
            return "고정수익 기준 포지션에는 각 통화의 4개 rung이 정확히 한 번씩 필요합니다."
        }

        val records = recordsByRef[fixedIncomeState.benchmarkRef].orEmpty()
        if (records.size.toLong() != fixedIncomeState.revision ||
            records.withIndex().any { (index, record) -> record.revision != index + 1L } ||
            records.zipWithNext().any { (previous, next) ->
                previous.effectiveAt >= next.effectiveAt
            }
        ) {
            return "고정수익 만기 교체 원장의 revision·효력시각 계보가 현재 상태와 다릅니다."
        }
        val replayedIds = expectedCoordinates.mapTo(linkedSetOf()) { (currency, rung) ->
            "FI:${fixedIncomeState.benchmarkRef.benchmarkId}:v${fixedIncomeState.benchmarkRef.version}:" +
                "${currency.name}:r$rung:g0"
        }
        for (record in records) {
            val reconstructedRecord = runCatching {
                FixedIncomeRollRecord(
                    id = record.id,
                    benchmarkRef = record.benchmarkRef,
                    removedAssetIds = record.removedAssetIds.toList(),
                    addedAssetIds = record.addedAssetIds.toList(),
                    effectiveAt = record.effectiveAt,
                    revision = record.revision,
                )
            }.getOrNull()
            val expectedRecordId =
                "fixed-income-roll:${fixedIncomeState.benchmarkRef.benchmarkId}:" +
                    "v${fixedIncomeState.benchmarkRef.version}:${record.revision}:" +
                    record.effectiveAt.epochSeconds
            if (reconstructedRecord != record || record.benchmarkRef != fixedIncomeState.benchmarkRef ||
                record.effectiveAt > fixedIncomeState.asOf || record.id != expectedRecordId ||
                record.removedAssetIds.any { assetId -> assetId !in replayedIds } ||
                record.addedAssetIds.any { assetId -> assetId in replayedIds }
            ) {
                return "고정수익 만기 교체 원장의 ID·시각·편출입 계보가 유효하지 않습니다."
            }
            val removedCoordinates = record.removedAssetIds.associate { assetId ->
                val parsed = parseAssetId(assetId, fixedIncomeState.benchmarkRef)
                    ?: return "고정수익 만기 교체 원장의 편출 assetId가 유효하지 않습니다."
                if (parsed.second >= record.revision) {
                    return "고정수익 만기 교체 원장의 편출 generation이 revision보다 앞서지 않습니다."
                }
                parsed.first to assetId
            }
            val addedCoordinates = record.addedAssetIds.associate { assetId ->
                val parsed = parseAssetId(assetId, fixedIncomeState.benchmarkRef)
                    ?: return "고정수익 만기 교체 원장의 편입 assetId가 유효하지 않습니다."
                if (parsed.second != record.revision) {
                    return "고정수익 만기 교체 원장의 편입 generation이 revision과 다릅니다."
                }
                parsed.first to assetId
            }
            if (removedCoordinates.keys != addedCoordinates.keys ||
                removedCoordinates.keys.any { coordinate -> coordinate !in expectedCoordinates }
            ) {
                return "고정수익 만기 교체 원장은 같은 통화·rung의 sleeve만 교체해야 합니다."
            }
            replayedIds.removeAll(record.removedAssetIds.toSet())
            replayedIds.addAll(record.addedAssetIds)
        }
        if (replayedIds != fixedIncomeState.positions.mapTo(linkedSetOf()) { it.assetId }) {
            return "고정수익 만기 교체 원장 재생 결과가 현재 ladder 구성과 다릅니다."
        }
    }
    return null
}

/** Generic equity representative baskets, factor state and selection/reweight lineage. */
private fun validateEquityReferencePersistenceState(
    state: SimulatorUiState,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    val engine = EquityReferenceBookEngine.forCampaignSeed(state.options.seed)
    val definitions = catalog?.benchmarksInEvaluationOrder
        ?.filter { definition -> definition.engineKind == BenchmarkEngineKind.EQUITY_REFERENCE }
        ?.associateBy(BenchmarkDefinition::ref)
    if (definitions != null && state.equityReferenceStates.keys != definitions.keys) {
        return "일반 주식 기준 상태는 실행 가능한 EQUITY_REFERENCE 벤치마크마다 정확히 하나씩 필요합니다."
    }
    if (state.equityReferenceLedger.map(EquityReferenceRebalanceRecord::id).distinct().size !=
        state.equityReferenceLedger.size
    ) {
        return "일반 주식 기준 재조정 원장 ID가 중복되었습니다."
    }
    if (state.equityReferenceLedger.any { record ->
            record.benchmarkRef !in state.equityReferenceStates.keys
        }
    ) {
        return "일반 주식 기준 원장에 현재 상태가 없는 벤치마크가 있습니다."
    }
    val canonicalLedgerOrder = state.equityReferenceLedger.sortedWith(
        compareBy<EquityReferenceRebalanceRecord>(EquityReferenceRebalanceRecord::effectiveAt)
            .thenBy(EquityReferenceRebalanceRecord::benchmarkRef)
            .thenBy(EquityReferenceRebalanceRecord::revision),
    )
    if (state.equityReferenceLedger != canonicalLedgerOrder) {
        return "일반 주식 기준 원장이 효력시각·benchmark·revision 순서로 정렬되지 않았습니다."
    }

    fun reconstructPosition(value: EquityReferencePosition): EquityReferencePosition =
        EquityReferencePosition(
            assetId = value.assetId,
            region = value.region,
            countryCode = value.countryCode,
            sector = value.sector,
            weight = value.weight,
            targetWeight = value.targetWeight,
            representedConstituentCount = value.representedConstituentCount,
            selectionScore = value.selectionScore,
            indicatedAnnualDividendYield = value.indicatedAnnualDividendYield,
            enteredOn = value.enteredOn,
        )

    fun reconstructFactor(value: EquityReferenceFactorExposure): EquityReferenceFactorExposure =
        EquityReferenceFactorExposure(
            countryWeights = value.countryWeights.toMap(),
            sectorWeights = value.sectorWeights.toMap(),
            styleExposures = value.styleExposures.toMap(),
            idiosyncraticVolatilityWeights = value.idiosyncraticVolatilityWeights.toList(),
            thematicExposure = value.thematicExposure,
            activeManagementExposure = value.activeManagementExposure,
        )

    fun reconstructState(value: EquityReferenceState): EquityReferenceState =
        EquityReferenceState(
            benchmarkRef = value.benchmarkRef,
            region = value.region,
            resolvedCountryCodes = value.resolvedCountryCodes.toSet(),
            themeId = value.themeId,
            positions = value.positions.map(::reconstructPosition),
            factorExposure = reconstructFactor(value.factorExposure),
            revision = value.revision,
            lastSelectionDate = value.lastSelectionDate,
            nextSelectionDate = value.nextSelectionDate,
            lastReweightDate = value.lastReweightDate,
            nextReweightDate = value.nextReweightDate,
            estimatedAnnualIncomeYield = value.estimatedAnnualIncomeYield,
            declaredTargetConstituentCount = value.declaredTargetConstituentCount,
            eligibleCandidateCount = value.eligibleCandidateCount,
            representativeBasketLimit = value.representativeBasketLimit,
            profileFingerprint = value.profileFingerprint,
            universeModelVersion = value.universeModelVersion,
            universeFingerprint = value.universeFingerprint,
            compositionHash = value.compositionHash,
            asOf = value.asOf,
        )

    fun reconstructRecord(
        value: EquityReferenceRebalanceRecord,
    ): EquityReferenceRebalanceRecord = EquityReferenceRebalanceRecord(
        id = value.id,
        benchmarkRef = value.benchmarkRef,
        kind = value.kind,
        selectionDate = value.selectionDate,
        effectiveAt = value.effectiveAt,
        addedAssetIds = value.addedAssetIds.toList(),
        removedAssetIds = value.removedAssetIds.toList(),
        compositionHashBefore = value.compositionHashBefore,
        compositionHashAfter = value.compositionHashAfter,
        turnoverRate = value.turnoverRate,
        resultingPositionCount = value.resultingPositionCount,
        representedConstituentCount = value.representedConstituentCount,
        revision = value.revision,
    )

    fun marketForRegion(region: com.amond.kmpbook.domain.model.fund.EquityReferenceRegion): Market =
        if (region == com.amond.kmpbook.domain.model.fund.EquityReferenceRegion.KOREA) {
            Market.KOSPI
        } else {
            Market.NYSE
        }

    fun mostRecentSnapshotYear(value: EquityReferenceState): Int? {
        val market = marketForRegion(value.region)
        var date = GameCalendar.marketLocalDateTime(market, value.asOf).date
        repeat(62) {
            val closedDates = if (date.year in 2026..2040) {
                DefaultMarketHolidays.closedDates(market, date.year)
            } else {
                emptySet()
            }
            val window = GameCalendar.regularSessionWindow(market, date, closedDates)
            if (window != null && window.closesAt <= value.asOf) {
                var laterDate = date.plus(1, DateTimeUnit.DAY)
                var hasLaterTradingDateInMonth = false
                while (laterDate.month == date.month) {
                    val laterClosedDates = if (laterDate.year in 2026..2040) {
                        DefaultMarketHolidays.closedDates(market, laterDate.year)
                    } else {
                        emptySet()
                    }
                    if (GameCalendar.regularSessionWindow(market, laterDate, laterClosedDates) != null) {
                        hasLaterTradingDateInMonth = true
                        break
                    }
                    laterDate = laterDate.plus(1, DateTimeUnit.DAY)
                }
                if (!hasLaterTradingDateInMonth) return date.year
            }
            date = date.minus(1, DateTimeUnit.DAY)
        }
        return null
    }

    fun recordHasCanonicalSession(value: EquityReferenceRebalanceRecord): Boolean {
        val equityState = state.equityReferenceStates[value.benchmarkRef] ?: return false
        val market = marketForRegion(equityState.region)
        val closedDates = if (value.selectionDate.year in 2026..2040) {
            DefaultMarketHolidays.closedDates(market, value.selectionDate.year)
        } else {
            emptySet()
        }
        val window = GameCalendar.regularSessionWindow(market, value.selectionDate, closedDates)
            ?: return false
        return window.closesAt <= value.effectiveAt &&
            GameCalendar.marketLocalDateTime(market, value.effectiveAt).date == value.selectionDate
    }

    val recordsByRef = state.equityReferenceLedger.groupBy(EquityReferenceRebalanceRecord::benchmarkRef)
    for ((benchmarkRef, equityState) in state.equityReferenceStates) {
        val definition = definitions?.get(benchmarkRef)
        val profile = definition?.equityReferenceProfile
        val records = recordsByRef[benchmarkRef].orEmpty()
            .sortedBy(EquityReferenceRebalanceRecord::revision)
        var canonicalInitialIds: Set<String>? = null
        var canonicalInitialPositions: Map<String, EquityReferencePosition>? = null
        if (definition != null && profile == null) {
            return "EQUITY_REFERENCE 벤치마크에 일반 주식 기준 프로필이 없습니다."
        }
        if (equityState.benchmarkRef != benchmarkRef || equityState.asOf != state.currentTime ||
            runCatching { reconstructState(equityState) }.getOrNull() != equityState ||
            equityState.positions.any { position -> !engine.hasCanonicalReferenceIdentity(position) } ||
            equityState.compositionHash != engine.compositionHash(equityState.positions)
        ) {
            return "일반 주식 기준 상태의 map 키·시각·합성 자산 정체성·구성 해시가 유효하지 않습니다."
        }
        if (profile != null) {
            if (equityState.region != profile.region ||
                equityState.themeId != profile.themeId ||
                equityState.declaredTargetConstituentCount != profile.targetConstituentCount ||
                equityState.profileFingerprint != engine.profileFingerprint(benchmarkRef, profile) ||
                !engine.hasCanonicalUniverseMetadata(equityState, profile)
            ) {
                return "일반 주식 기준 상태의 지역·테마·구성원 수·프로필/유니버스 지문이 카탈로그와 다릅니다."
            }
            val factorYear = mostRecentSnapshotYear(equityState)
                ?: return "일반 주식 기준 상태에서 최근 거래 종가 연도를 결정할 수 없습니다."
            val expectedFactor = runCatching {
                engine.factorExposure(benchmarkRef, profile, equityState.positions, factorYear)
            }.getOrNull() ?: return "일반 주식 기준 요인 노출을 canonical universe에서 재구성할 수 없습니다."
            if (equityState.factorExposure != expectedFactor) {
                return "일반 주식 기준의 국가·섹터·스타일·고유위험 노출이 canonical universe와 다릅니다."
            }
            if (equityState.positions.any { position ->
                    !engine.hasCanonicalPositionSnapshot(position, factorYear)
                }
            ) {
                return "일반 주식 기준 구성원의 배당수익률 스냅샷이 canonical universe와 다릅니다."
            }
            if (!engine.hasCanonicalRepresentativeLayout(equityState, profile)) {
                return "일반 주식 기준의 대표 구성원 수 배치가 방법론의 선언 구성원 수와 다릅니다."
            }
            if (!amountsAreClose(
                    equityState.estimatedAnnualIncomeYield,
                    engine.incomeYield(equityState.positions),
                )
            ) {
                return "일반 주식 기준의 소득수익률이 canonical 배당과 현재 비중에서 재계산되지 않습니다."
            }
            if (profile.individualWeightCap?.let { cap ->
                    equityState.positions.any { position ->
                        position.targetWeight > cap + REFERENCE_WEIGHT_ALLOCATION_EPSILON
                    }
                } == true ||
                profile.sectorWeightCap?.let { cap ->
                    equityState.positions.groupBy(EquityReferencePosition::sector).values.any { positions ->
                        positions.sumOf(EquityReferencePosition::targetWeight) >
                            cap + REFERENCE_WEIGHT_ALLOCATION_EPSILON
                    }
                } == true
            ) {
                return "일반 주식 기준의 마지막 action 목표 비중이 종목·섹터 cap을 초과했습니다."
            }
            if (equityState.nextSelectionDate != engine.nextScheduledDate(
                    benchmarkRef,
                    profile,
                    EquityReferenceActionKind.RECONSTITUTION,
                    equityState.lastSelectionDate,
                ) ||
                equityState.nextReweightDate != engine.nextScheduledDate(
                    benchmarkRef,
                    profile,
                    EquityReferenceActionKind.REWEIGHT,
                    equityState.lastReweightDate,
                )
            ) {
                return "일반 주식 기준의 다음 selection/reweight 일정이 프로필 달력과 다릅니다."
            }
            val canonicalInitialState = runCatching {
                engine.initialBook(
                    definitions = listOf(definition),
                    atDate = GameCalendar.campaignDate(GameCalendar.startInstant),
                    at = GameCalendar.startInstant,
                ).states.getValue(benchmarkRef)
            }.getOrNull() ?: return "일반 주식 기준 bootstrap basket을 재구성할 수 없습니다."
            canonicalInitialIds = canonicalInitialState.positions
                .mapTo(linkedSetOf(), EquityReferencePosition::assetId)
            canonicalInitialPositions = canonicalInitialState.positions.associateBy(
                EquityReferencePosition::assetId,
            )
            val canonicalInitialHash = canonicalInitialState.compositionHash
            if (records.firstOrNull()?.compositionHashBefore?.let { hash ->
                    hash != canonicalInitialHash
                } == true ||
                records.isEmpty() && equityState.compositionHash != canonicalInitialHash
            ) {
                return "일반 주식 기준 원장의 최초 구성 해시가 canonical bootstrap basket과 다릅니다."
            }
            val latestAction = records.lastOrNull()
            val hasInvalidCanonicalPosition = equityState.positions.any { position ->
                val expectedEnteredOn = records.asReversed()
                    .firstOrNull { record -> position.assetId in record.addedAssetIds }
                    ?.selectionDate
                    ?: canonicalInitialPositions.get(position.assetId)?.enteredOn
                    ?: return@any true
                val expectedScore = if (latestAction == null) {
                    canonicalInitialPositions.get(position.assetId)?.selectionScore
                        ?: return@any true
                } else {
                    runCatching {
                        engine.canonicalSelectionScore(
                            ref = benchmarkRef,
                            profile = profile,
                            assetId = position.assetId,
                            scoreYear = latestAction.selectionDate.year,
                            incumbent = latestAction.kind == EquityReferenceActionKind.REWEIGHT ||
                                position.assetId !in latestAction.addedAssetIds,
                        )
                    }.getOrNull() ?: return@any true
                }
                position.enteredOn != expectedEnteredOn ||
                    position.selectionScore.toBits() != expectedScore.toBits()
            }
            if (hasInvalidCanonicalPosition) {
                return "일반 주식 기준 구성원의 편입일 또는 최근 action 선택 점수가 canonical 계보와 다릅니다."
            }
        }
        if (equityState.positions.any { position -> position.enteredOn > equityState.lastSelectionDate }) {
            return "일반 주식 기준 구성원의 편입일이 마지막 selection 날짜보다 늦습니다."
        }

        if (records.size.toLong() != equityState.revision ||
            records.withIndex().any { (index, record) -> record.revision != index + 1L } ||
            records.zipWithNext().any { (previous, next) -> previous.effectiveAt > next.effectiveAt } ||
            records.any { record ->
                record.effectiveAt > equityState.asOf ||
                    runCatching { reconstructRecord(record) }.getOrNull() != record ||
                    record.id != "equity-reference-${record.kind.name.lowercase()}:" +
                    "${benchmarkRef.benchmarkId}:v${benchmarkRef.version}:" +
                    "${record.selectionDate}:r${record.revision}" ||
                    !recordHasCanonicalSession(record) ||
                    profile?.let { canonicalProfile ->
                        engine.nextScheduledDate(
                            benchmarkRef,
                            canonicalProfile,
                            record.kind,
                            record.selectionDate.minus(1, DateTimeUnit.DAY),
                        ) != record.selectionDate
                    } == true
            }
        ) {
            return "일반 주식 기준 원장의 revision·ID·일정·효력시각 계보가 유효하지 않습니다."
        }
        if (records.zipWithNext().any { (previous, next) ->
                previous.compositionHashAfter != next.compositionHashBefore
            } ||
            records.lastOrNull()?.compositionHashAfter?.let { hash -> hash != equityState.compositionHash } == true
        ) {
            return "일반 주식 기준 원장의 구성 해시 계보가 현재 target basket과 끊어졌습니다."
        }
        val latestSelection = records.lastOrNull { record ->
            record.kind == EquityReferenceActionKind.RECONSTITUTION
        }
        val latestReweight = records.lastOrNull { record ->
            record.kind == EquityReferenceActionKind.REWEIGHT
        }
        if (latestSelection != null && latestSelection.selectionDate != equityState.lastSelectionDate ||
            latestReweight != null && latestReweight.selectionDate > equityState.lastReweightDate ||
            equityState.lastReweightDate > (latestReweight?.selectionDate ?: equityState.lastReweightDate) &&
            records.none { record ->
                record.kind == EquityReferenceActionKind.RECONSTITUTION &&
                    record.selectionDate == equityState.lastReweightDate
            }
        ) {
            return "일반 주식 기준의 마지막 selection/reweight 날짜가 원장 action과 다릅니다."
        }

        var currentIds = equityState.positions.mapTo(linkedSetOf(), EquityReferencePosition::assetId)
        for (record in records.asReversed()) {
            if (record.resultingPositionCount != currentIds.size ||
                record.addedAssetIds.any { it !in currentIds } ||
                record.removedAssetIds.any { it in currentIds }
            ) {
                return "일반 주식 기준 원장의 편입·편출 ID를 현재 basket에서 역재생할 수 없습니다."
            }
            currentIds.removeAll(record.addedAssetIds.toSet())
            currentIds.addAll(record.removedAssetIds)
        }
        if (canonicalInitialIds != null && currentIds != canonicalInitialIds) {
            return "일반 주식 기준 원장 역재생 결과가 canonical bootstrap 자산 집합과 다릅니다."
        }
        val latestRecord = records.lastOrNull()
        if (latestRecord != null &&
            (latestRecord.resultingPositionCount != equityState.positions.size ||
                latestRecord.representedConstituentCount !=
                equityState.positions.sumOf(EquityReferencePosition::representedConstituentCount))
        ) {
            return "일반 주식 기준 최신 원장의 대표 basket/구성원 수가 현재 상태와 다릅니다."
        }
    }
    return null
}

/** Fund-of-funds candidate identity, target caps and selection/reweight ledger lineage. */
private fun validateFundOfFundsPersistenceState(
    state: SimulatorUiState,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    val engine = FundOfFundsBookEngine.forCampaignSeed(state.options.seed)
    val definitions = catalog?.benchmarksInEvaluationOrder
        ?.filter { definition ->
            definition.engineKind == BenchmarkEngineKind.FUND_OF_FUNDS_METHODOLOGY
        }
        ?.associateBy(BenchmarkDefinition::ref)
    if (definitions != null && state.fundOfFundsStates.keys != definitions.keys) {
        return "펀드오브펀드 상태는 실행 가능한 FUND_OF_FUNDS_METHODOLOGY 벤치마크마다 정확히 하나씩 필요합니다."
    }
    if (state.fundOfFundsRebalanceLedger.map(FundOfFundsRebalanceRecord::id).distinct().size !=
        state.fundOfFundsRebalanceLedger.size
    ) {
        return "펀드오브펀드 재조정 원장 ID가 중복되었습니다."
    }
    if (state.fundOfFundsRebalanceLedger.any { record ->
            record.benchmarkRef !in state.fundOfFundsStates.keys
        }
    ) {
        return "펀드오브펀드 원장에 현재 상태가 없는 벤치마크가 있습니다."
    }
    val canonicalLedgerOrder = state.fundOfFundsRebalanceLedger.sortedWith(
        compareBy<FundOfFundsRebalanceRecord>(FundOfFundsRebalanceRecord::effectiveAt)
            .thenBy(FundOfFundsRebalanceRecord::benchmarkRef)
            .thenBy(FundOfFundsRebalanceRecord::revision),
    )
    if (state.fundOfFundsRebalanceLedger != canonicalLedgerOrder) {
        return "펀드오브펀드 원장이 효력시각·benchmark·revision 순서로 정렬되지 않았습니다."
    }

    fun reconstructPosition(value: FundOfFundsPosition): FundOfFundsPosition =
        FundOfFundsPosition(
            candidateFundId = value.candidateFundId,
            category = value.category,
            underlyingBenchmarkRef = value.underlyingBenchmarkRef,
            currentWeight = value.currentWeight,
            targetWeight = value.targetWeight,
            marketDiscountRate = value.marketDiscountRate,
            indicatedAnnualDistributionYield = value.indicatedAnnualDistributionYield,
            leverageRatio = value.leverageRatio,
            annualExpenseRate = value.annualExpenseRate,
            annualResidualVolatility = value.annualResidualVolatility,
            liquidityScore = value.liquidityScore,
            selectionScore = value.selectionScore,
            enteredOn = value.enteredOn,
            asOf = value.asOf,
        )

    fun reconstructState(value: FundOfFundsState): FundOfFundsState =
        FundOfFundsState(
            benchmarkRef = value.benchmarkRef,
            universe = value.universe,
            positions = value.positions.map(::reconstructPosition),
            revision = value.revision,
            bootstrapDate = value.bootstrapDate,
            lastSelectionDate = value.lastSelectionDate,
            nextSelectionDate = value.nextSelectionDate,
            lastReweightDate = value.lastReweightDate,
            nextReweightDate = value.nextReweightDate,
            estimatedAnnualIncomeYield = value.estimatedAnnualIncomeYield,
            eligibleCandidateCount = value.eligibleCandidateCount,
            profileFingerprint = value.profileFingerprint,
            universeFingerprint = value.universeFingerprint,
            compositionHash = value.compositionHash,
            asOf = value.asOf,
        )

    fun reconstructRecord(value: FundOfFundsRebalanceRecord): FundOfFundsRebalanceRecord =
        FundOfFundsRebalanceRecord(
            id = value.id,
            benchmarkRef = value.benchmarkRef,
            kind = value.kind,
            effectiveDate = value.effectiveDate,
            effectiveAt = value.effectiveAt,
            addedCandidateFundIds = value.addedCandidateFundIds.toList(),
            removedCandidateFundIds = value.removedCandidateFundIds.toList(),
            compositionHashBefore = value.compositionHashBefore,
            compositionHashAfter = value.compositionHashAfter,
            oneWayTurnoverRate = value.oneWayTurnoverRate,
            resultingFundCount = value.resultingFundCount,
            revision = value.revision,
        )

    fun recordHasCanonicalSession(value: FundOfFundsRebalanceRecord): Boolean {
        val closedDates = if (value.effectiveDate.year in 2026..2040) {
            DefaultMarketHolidays.closedDates(Market.NYSE, value.effectiveDate.year)
        } else {
            emptySet()
        }
        val window = GameCalendar.regularSessionWindow(
            Market.NYSE,
            value.effectiveDate,
            closedDates,
        ) ?: return false
        return value.effectiveAt == window.closesAt
    }

    val canonicalBootstrapDate = GameCalendar.marketLocalDateTime(
        Market.NYSE,
        GameCalendar.startInstant,
    ).date
    val recordsByRef = state.fundOfFundsRebalanceLedger.groupBy(
        FundOfFundsRebalanceRecord::benchmarkRef,
    )
    for ((benchmarkRef, fundOfFundsState) in state.fundOfFundsStates) {
        val definition = definitions?.get(benchmarkRef)
        val profile = definition?.fundOfFundsMethodologyProfile
        if (definition != null && profile == null) {
            return "FUND_OF_FUNDS_METHODOLOGY 벤치마크에 실행 가능한 방법론 프로필이 없습니다."
        }
        if (fundOfFundsState.benchmarkRef != benchmarkRef ||
            fundOfFundsState.asOf != state.currentTime ||
            fundOfFundsState.bootstrapDate != canonicalBootstrapDate ||
            runCatching { reconstructState(fundOfFundsState) }.getOrNull() != fundOfFundsState ||
            fundOfFundsState.positions.any { position ->
                !engine.hasCanonicalCandidate(fundOfFundsState.universe, position)
            } ||
            fundOfFundsState.compositionHash !=
            engine.canonicalCompositionHash(fundOfFundsState.positions) ||
            !amountsAreClose(
                fundOfFundsState.estimatedAnnualIncomeYield,
                engine.canonicalEstimatedAnnualIncomeYield(fundOfFundsState.positions),
            )
        ) {
            return "펀드오브펀드 상태의 키·시각·후보 정체성·구성 해시·소득률이 유효하지 않습니다."
        }

        val records = recordsByRef[benchmarkRef].orEmpty()
            .sortedBy(FundOfFundsRebalanceRecord::revision)
        if (profile != null) {
            if (fundOfFundsState.universe != profile.universe ||
                fundOfFundsState.positions.size != profile.targetFundCount ||
                fundOfFundsState.eligibleCandidateCount !in
                fundOfFundsState.positions.size..profile.candidateUniverseSize ||
                fundOfFundsState.profileFingerprint !=
                engine.canonicalProfileFingerprint(benchmarkRef, profile) ||
                fundOfFundsState.universeFingerprint != engine.universeFingerprint ||
                definition.componentBenchmarkRefs != profile.componentBenchmarkRefs
            ) {
                return "펀드오브펀드 상태의 유니버스·구성 수·프로필/유니버스 지문이 카탈로그와 다릅니다."
            }
            if (fundOfFundsState.positions.any { position ->
                    position.category !in profile.eligibleCategories ||
                        position.underlyingBenchmarkRef !=
                        profile.benchmarkRefFor(position.category) ||
                        catalog.findBenchmark(position.underlyingBenchmarkRef).let { component ->
                            component == null ||
                                component.engineKind == BenchmarkEngineKind.FUND_OF_FUNDS_METHODOLOGY
                        }
                }
            ) {
                return "펀드오브펀드 후보의 카테고리·기초 benchmark 참조가 방법론 DAG와 다릅니다."
            }

            val rankedPositions = fundOfFundsState.positions.sortedWith(
                compareByDescending<FundOfFundsPosition>(FundOfFundsPosition::selectionScore)
                    .thenBy(FundOfFundsPosition::candidateFundId),
            )
            if (rankedPositions.withIndex().any { (index, position) ->
                    position.targetWeight >
                        profile.weightCapAtRank(index + 1) + REFERENCE_WEIGHT_ALLOCATION_EPSILON
                } ||
                fundOfFundsState.positions.groupBy(FundOfFundsPosition::category).values.any { positions ->
                    positions.sumOf(FundOfFundsPosition::targetWeight) >
                        profile.categoryWeightCap + REFERENCE_WEIGHT_ALLOCATION_EPSILON
                }
            ) {
                return "펀드오브펀드의 마지막 action 목표 비중이 rank·category cap을 초과했습니다."
            }

            val expectedNextSelection = engine.canonicalNextScheduledDate(
                profile.selectionMonths,
                fundOfFundsState.lastSelectionDate ?: fundOfFundsState.bootstrapDate,
            )
            val expectedNextReweight = engine.canonicalNextScheduledDate(
                profile.reweightMonths,
                fundOfFundsState.lastReweightDate ?: fundOfFundsState.bootstrapDate,
            )
            if (fundOfFundsState.nextSelectionDate != expectedNextSelection ||
                fundOfFundsState.nextReweightDate != expectedNextReweight
            ) {
                return "펀드오브펀드의 다음 selection/reweight 일정이 방법론 달력과 다릅니다."
            }

            val canonicalSelectionDate = fundOfFundsState.lastSelectionDate
                ?: fundOfFundsState.bootstrapDate
            val canonicalSelectionState = runCatching {
                engine.initialBook(
                    profiles = mapOf(benchmarkRef to profile),
                    atDate = canonicalSelectionDate,
                    at = fundOfFundsState.asOf,
                ).states.getValue(benchmarkRef)
            }.getOrNull() ?: return "펀드오브펀드 canonical 후보 유니버스를 재구성할 수 없습니다."
            if (fundOfFundsState.eligibleCandidateCount !=
                canonicalSelectionState.eligibleCandidateCount
            ) {
                return "펀드오브펀드의 적격 후보 수가 selection 연도 canonical 유니버스와 다릅니다."
            }

            val canonicalInitialState = runCatching {
                engine.initialBook(
                    profiles = mapOf(benchmarkRef to profile),
                    atDate = canonicalBootstrapDate,
                    at = GameCalendar.startInstant,
                ).states.getValue(benchmarkRef)
            }.getOrNull() ?: return "펀드오브펀드 bootstrap basket을 재구성할 수 없습니다."
            val initialHash = canonicalInitialState.compositionHash
            if (records.firstOrNull()?.compositionHashBefore?.let { hash -> hash != initialHash } == true ||
                records.isEmpty() && fundOfFundsState.compositionHash != initialHash
            ) {
                return "펀드오브펀드 원장의 최초 구성 해시가 canonical bootstrap basket과 다릅니다."
            }

            var currentIds = fundOfFundsState.positions.mapTo(linkedSetOf()) {
                position -> position.candidateFundId
            }
            for (record in records.asReversed()) {
                if (record.resultingFundCount != currentIds.size ||
                    record.addedCandidateFundIds.any { candidateId -> candidateId !in currentIds } ||
                    record.removedCandidateFundIds.any { candidateId -> candidateId in currentIds }
                ) {
                    return "펀드오브펀드 원장의 편입·편출 후보를 현재 basket에서 역재생할 수 없습니다."
                }
                currentIds.removeAll(record.addedCandidateFundIds.toSet())
                currentIds.addAll(record.removedCandidateFundIds)
            }
            if (currentIds != canonicalInitialState.positions.mapTo(linkedSetOf()) { position ->
                    position.candidateFundId
                }
            ) {
                return "펀드오브펀드 원장 역재생 결과가 canonical bootstrap 후보 집합과 다릅니다."
            }
        }

        val latestPermittedSelectionDate = fundOfFundsState.lastSelectionDate
            ?: fundOfFundsState.bootstrapDate
        if (fundOfFundsState.positions.any { position ->
                position.enteredOn > latestPermittedSelectionDate
            }
        ) {
            return "펀드오브펀드 후보의 편입일이 bootstrap/마지막 selection 날짜보다 늦습니다."
        }
        if (records.size.toLong() != fundOfFundsState.revision ||
            records.withIndex().any { (index, record) -> record.revision != index + 1L } ||
            records.zipWithNext().any { (previous, next) -> previous.effectiveAt >= next.effectiveAt } ||
            records.any { record ->
                record.effectiveAt > fundOfFundsState.asOf ||
                    runCatching { reconstructRecord(record) }.getOrNull() != record ||
                    record.id != "fund-of-funds-${record.kind.name.lowercase()}:" +
                    "${benchmarkRef.benchmarkId}:v${benchmarkRef.version}:" +
                    "${record.effectiveDate}:r${record.revision}" ||
                    !recordHasCanonicalSession(record) ||
                    record.resultingFundCount != fundOfFundsState.positions.size ||
                    record.kind == FundOfFundsActionKind.RECONSTITUTION &&
                    record.addedCandidateFundIds.size != record.removedCandidateFundIds.size ||
                    profile?.let { canonicalProfile ->
                        val months = when (record.kind) {
                            FundOfFundsActionKind.RECONSTITUTION -> canonicalProfile.selectionMonths
                            FundOfFundsActionKind.REWEIGHT -> canonicalProfile.reweightMonths
                        }
                        engine.canonicalNextScheduledDate(
                            months,
                            record.effectiveDate.minus(1, DateTimeUnit.DAY),
                        ) != record.effectiveDate
                    } == true
            }
        ) {
            return "펀드오브펀드 원장의 revision·ID·일정·효력시각 계보가 유효하지 않습니다."
        }
        if (records.zipWithNext().any { (previous, next) ->
                previous.compositionHashAfter != next.compositionHashBefore
            } ||
            records.lastOrNull()?.compositionHashAfter?.let { hash ->
                hash != fundOfFundsState.compositionHash
            } == true
        ) {
            return "펀드오브펀드 원장의 구성 해시 계보가 현재 target basket과 끊어졌습니다."
        }

        val latestSelection = records.lastOrNull { record ->
            record.kind == FundOfFundsActionKind.RECONSTITUTION
        }
        val latestExplicitReweight = records.lastOrNull { record ->
            record.kind == FundOfFundsActionKind.REWEIGHT
        }
        val latestSelectionAlsoReweight = profile?.let { canonicalProfile ->
            records.lastOrNull { record ->
                record.kind == FundOfFundsActionKind.RECONSTITUTION &&
                    engine.canonicalNextScheduledDate(
                        canonicalProfile.reweightMonths,
                        record.effectiveDate.minus(1, DateTimeUnit.DAY),
                    ) == record.effectiveDate
            }
        }
        val expectedLastReweight = listOfNotNull(
            latestExplicitReweight?.effectiveDate,
            latestSelectionAlsoReweight?.effectiveDate,
        ).maxOrNull()
        val intrinsicLastReweightIsLinked = profile != null || when {
            fundOfFundsState.lastReweightDate == latestExplicitReweight?.effectiveDate -> true
            fundOfFundsState.lastReweightDate == null -> latestExplicitReweight == null
            else -> records.any { record ->
                record.kind == FundOfFundsActionKind.RECONSTITUTION &&
                    record.effectiveDate == fundOfFundsState.lastReweightDate &&
                    (latestExplicitReweight == null ||
                        record.effectiveDate >= latestExplicitReweight.effectiveDate)
            }
        }
        if (fundOfFundsState.lastSelectionDate != latestSelection?.effectiveDate ||
            !intrinsicLastReweightIsLinked ||
            profile != null && fundOfFundsState.lastReweightDate != expectedLastReweight
        ) {
            return "펀드오브펀드의 마지막 selection/reweight 날짜가 원장 action과 다릅니다."
        }

        val latestRecord = records.lastOrNull()
        if (latestRecord != null) {
            val latestAddedTargetWeight = fundOfFundsState.positions
                .filter { position ->
                    position.candidateFundId in latestRecord.addedCandidateFundIds
                }
                .sumOf(FundOfFundsPosition::targetWeight)
            if (latestRecord.resultingFundCount != fundOfFundsState.positions.size ||
                latestRecord.oneWayTurnoverRate + REFERENCE_WEIGHT_ALLOCATION_EPSILON <
                latestAddedTargetWeight
            ) {
                return "펀드오브펀드 최신 원장의 펀드 수·회전율이 현재 target basket과 다릅니다."
            }
        }
    }
    return null
}

/** Alternative-risk-premia and composite books, source registry and rebalance lineage. */
private fun validateStructuredReferencePersistenceState(
    state: SimulatorUiState,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    val alternativeEngine = AlternativeRiskPremiaBookEngine.forCampaignSeed(state.options.seed)
    val compositeEngine = CompositeReferenceBookEngine.forCampaignSeed(state.options.seed)
    val allDefinitions = catalog?.benchmarksInEvaluationOrder?.associateBy(BenchmarkDefinition::ref)
    val alternativeDefinitions = allDefinitions?.filterValues { definition ->
        definition.engineKind == BenchmarkEngineKind.ALTERNATIVE_RISK_PREMIA
    }
    val compositeDefinitions = allDefinitions?.filterValues { definition ->
        definition.engineKind == BenchmarkEngineKind.COMPOSITE_REFERENCE
    }
    if (alternativeDefinitions != null &&
        state.alternativeRiskPremiaStates.keys != alternativeDefinitions.keys
    ) {
        return "대체위험 프리미엄 상태는 실행 가능한 ALT 벤치마크마다 정확히 하나씩 필요합니다."
    }
    if (compositeDefinitions != null && state.compositeReferenceStates.keys != compositeDefinitions.keys) {
        return "복합 기준 상태는 실행 가능한 COMPOSITE 벤치마크마다 정확히 하나씩 필요합니다."
    }

    val sourceCatalog = catalog?.let { snapshot ->
        runCatching {
            ReferenceSourceCatalog(
                benchmarkDefinitions = snapshot.benchmarks.associateBy(BenchmarkDefinition::ref),
                operatingCompanyCurrencies = state.stocks
                    .filter { stock -> stock.behavior.strategy == InstrumentStrategy.OPERATING_COMPANY }
                    .associate { stock ->
                        stock.id to ReferenceCurrency.valueOf(stock.currency.name)
                    },
            )
        }.getOrNull()
    }
    if (catalog != null && sourceCatalog == null) {
        return "ALT/복합 기준의 기업·benchmark source registry를 재구성할 수 없습니다."
    }

    val canonicalBootstrapHashes = if (catalog == null || sourceCatalog == null) {
        null
    } else {
        runCatching {
            val definitions = catalog.benchmarksInEvaluationOrder
            val campaignStart = GameCalendar.startInstant
            val campaignStartDate = GameCalendar.campaignDate(campaignStart)
            val initialDynamics = MarketDynamicsEngine(
                seed = DeterministicRandom.mixSeed(
                    state.options.seed,
                    SimulatorRuntime.DYNAMICS_STREAM_ID,
                ),
                initialForces = state.options.initialExternalMarketForces,
            ).snapshot()
            val initialFxRates = SimulatorRuntime.initialFxRates(state.options.initialUsdKrw)
            val initialMacro = MacroEnvironment(
                koreanPolicyRate = SimulatorRuntime.INITIAL_KOREAN_POLICY_RATE,
                koreanPolicyRateChange = 0.0,
                usdKrw = state.options.initialUsdKrw,
                fxRatesToKrw = initialFxRates,
                previousFxRatesToKrw = initialFxRates,
                volatilityRegime = initialDynamics.resolvedVolatilityRegime,
                retailOrderFlow = initialDynamics.retailFlow,
                institutionalOrderFlow = initialDynamics.institutionalFlow,
                liquidityStress = initialDynamics.liquidityStress,
                newsIntensity = initialDynamics.newsIntensity,
            )

            val initialIncomeYields = definitions.associate { definition ->
                definition.ref to 0.0
            }.toMutableMap()
            val initialDurations = definitions.associate { definition ->
                definition.ref to 0.0
            }.toMutableMap()

            val methodologyDefinitions = definitions.filter { definition ->
                definition.engineKind == BenchmarkEngineKind.EQUITY_METHODOLOGY
            }
            if (methodologyDefinitions.isNotEmpty()) {
                val methodologyEngine = ReferencePortfolioEngine.forCampaignSeed(
                    state.options.seed,
                    catalog.equityMethodologyRegistry,
                )
                methodologyDefinitions.forEach { definition ->
                    val compiled = BenchmarkMethodologyCompiler.compile(
                        definition,
                        catalog.equityMethodologyRegistry,
                    )
                    val initial = methodologyEngine.initialState(
                        portfolioId = ReferencePortfolioEngine.portfolioIdFor(definition.ref),
                        definition = definition,
                        atDate = compiled.schedule.marketDate(campaignStart),
                        at = campaignStart,
                    )
                    initialIncomeYields[initial.benchmarkRef] = initial.estimatedAnnualIncomeYield
                }
            }

            val equityDefinitions = definitions.filter { definition ->
                definition.engineKind == BenchmarkEngineKind.EQUITY_REFERENCE
            }
            if (equityDefinitions.isNotEmpty()) {
                EquityReferenceBookEngine.forCampaignSeed(state.options.seed).initialBook(
                    definitions = equityDefinitions,
                    atDate = campaignStartDate,
                    at = campaignStart,
                ).states.forEach { (ref, initial) ->
                    initialIncomeYields[ref] = initial.estimatedAnnualIncomeYield
                }
            }

            val fixedDefinitions = definitions.filter { definition ->
                definition.engineKind == BenchmarkEngineKind.FIXED_INCOME_CURVE
            }
            val initialFixedStates = if (fixedDefinitions.isEmpty()) {
                emptyList()
            } else {
                FixedIncomeReferenceBookEngine().initialBook(
                    definitions = fixedDefinitions,
                    macro = initialMacro,
                    at = campaignStart,
                ).states.values.toList()
            }
            initialFixedStates.forEach { initial ->
                initialIncomeYields[initial.benchmarkRef] = initial.estimatedAnnualIncomeYield
                initialDurations[initial.benchmarkRef] = initial.positions.sumOf { position ->
                    position.currentWeight * position.modifiedDurationYears
                }
            }

            val fundOfFundsDefinitions = definitions.filter { definition ->
                definition.engineKind == BenchmarkEngineKind.FUND_OF_FUNDS_METHODOLOGY
            }
            if (fundOfFundsDefinitions.isNotEmpty()) {
                FundOfFundsBookEngine.forCampaignSeed(state.options.seed).initialBook(
                    profiles = fundOfFundsDefinitions.associate { definition ->
                        definition.ref to requireNotNull(definition.fundOfFundsMethodologyProfile)
                    },
                    atDate = GameCalendar.marketLocalDateTime(Market.NYSE, campaignStart).date,
                    at = campaignStart,
                ).states.forEach { (ref, initial) ->
                    initialIncomeYields[ref] = initial.estimatedAnnualIncomeYield
                }
            }

            val directlyReferencedInstrumentIds = buildSet {
                state.stocks.forEach { stock ->
                    val product = stock.fundProductProfile
                    listOfNotNull(
                        product?.dailyResetTerms?.reference?.instrumentId,
                        product?.optionStrategyTerms?.reference?.instrumentId,
                        product?.cashCollateralizedPutSpreadTerms?.optionReference?.instrumentId,
                    ).forEach(::add)
                }
                alternativeDefinitions.orEmpty().values.forEach { definition ->
                    addAll(requireNotNull(definition.alternativeRiskPremiaProfile).componentInstrumentIds)
                }
                compositeDefinitions.orEmpty().values.forEach { definition ->
                    addAll(requireNotNull(definition.compositeReferenceProfile).componentInstrumentIds)
                }
            }
            val instrumentsByIdAtCampaignStart = state.stocks.associateBy(StockDefinition::id)
            val initialInstrumentIncomeYields = directlyReferencedInstrumentIds.associateWith { id ->
                instrumentsByIdAtCampaignStart.getValue(id).dividendYield
            }
            val initialInstrumentDurations = directlyReferencedInstrumentIds.associateWith { 0.0 }
            val initialInstrumentAvailability = directlyReferencedInstrumentIds.associateWith { true }
            val initialMortgageRate = (
                initialFixedStates.asSequence()
                    .mapNotNull { initial -> initial.nominalCurves[ReferenceCurrency.USD] }
                    .firstOrNull()
                    ?.rateAtYears(10.0)
                    ?: initialMacro.policyRate
                ).plus(STRUCTURED_REFERENCE_MORTGAGE_SPREAD).coerceIn(0.0, 1.0)

            val baseSourceSnapshot = ReferenceSourceSnapshot(
                benchmarkAnnualIncomeYields = initialIncomeYields,
                benchmarkDurationsYears = initialDurations,
                instrumentAnnualIncomeYields = initialInstrumentIncomeYields,
                instrumentDurationsYears = initialInstrumentDurations,
                instrumentAvailability = initialInstrumentAvailability,
                mortgageRateAnnual = initialMortgageRate,
            )
            val initialAlternativeStates = if (alternativeDefinitions.isNullOrEmpty()) {
                emptyMap()
            } else {
                alternativeEngine.initialBook(
                    definitions = alternativeDefinitions.values,
                    sourceCatalog = sourceCatalog,
                    sourceSnapshot = baseSourceSnapshot,
                    atDate = campaignStartDate,
                    at = campaignStart,
                ).states
            }
            val canonicalAlternativeHashes = initialAlternativeStates.mapValues { (_, initial) ->
                initial.bootstrapCompositionHash
            }
            initialAlternativeStates.forEach { (ref, initial) ->
                initialIncomeYields[ref] = initial.estimatedAnnualIncomeYield
                initialDurations[ref] = initial.effectiveDurationYears
            }
            val componentFirstSourceSnapshot = ReferenceSourceSnapshot(
                benchmarkAnnualIncomeYields = initialIncomeYields,
                benchmarkDurationsYears = initialDurations,
                instrumentAnnualIncomeYields = initialInstrumentIncomeYields,
                instrumentDurationsYears = initialInstrumentDurations,
                instrumentAvailability = initialInstrumentAvailability,
                mortgageRateAnnual = initialMortgageRate,
            )
            val initialCompositeStates = if (compositeDefinitions.isNullOrEmpty()) {
                emptyMap()
            } else {
                compositeEngine.initialBook(
                    definitions = compositeDefinitions.values,
                    sourceCatalog = sourceCatalog,
                    sourceSnapshot = componentFirstSourceSnapshot,
                    atDate = campaignStartDate,
                    at = campaignStart,
                ).states
            }
            val canonicalCompositeHashes = initialCompositeStates.mapValues { (_, initial) ->
                initial.bootstrapCompositionHash
            }
            val canonicalCompositeActiveSleeveIds = initialCompositeStates.mapValues { (_, initial) ->
                initial.positions.asSequence()
                    .filter { position ->
                        position.targetWeightMagnitude > REFERENCE_WEIGHT_ALLOCATION_EPSILON
                    }
                    .map(CompositeReferenceSleevePosition::sleeveId)
                    .toSet()
            }
            Triple(
                canonicalAlternativeHashes,
                canonicalCompositeHashes,
                canonicalCompositeActiveSleeveIds,
            )
        }.getOrNull()
    }
    if (catalog != null && canonicalBootstrapHashes == null) {
        return "ALT/복합 기준의 canonical campaign-start 구성 해시를 재구성할 수 없습니다."
    }

    val benchmarkIncomeYields = linkedMapOf<BenchmarkRef, Double>().apply {
        state.referencePortfolioStates.values.forEach { portfolio ->
            put(portfolio.benchmarkRef, portfolio.estimatedAnnualIncomeYield)
        }
        state.equityReferenceStates.forEach { (ref, value) -> put(ref, value.estimatedAnnualIncomeYield) }
        state.fixedIncomeReferenceStates.values.forEach { value ->
            put(value.benchmarkRef, value.estimatedAnnualIncomeYield)
        }
        state.kofrIndexStates.keys.forEach { ref -> put(ref, 0.0) }
        state.commoditySpotReferenceStates.keys.forEach { ref -> put(ref, 0.0) }
        state.futuresReferenceStates.keys.forEach { ref -> put(ref, 0.0) }
        state.fundOfFundsStates.forEach { (ref, value) -> put(ref, value.estimatedAnnualIncomeYield) }
        state.alternativeRiskPremiaStates.forEach { (ref, value) ->
            put(ref, value.estimatedAnnualIncomeYield)
        }
    }
    val benchmarkDurations = linkedMapOf<BenchmarkRef, Double>().apply {
        state.fixedIncomeReferenceStates.values.forEach { value ->
            put(
                value.benchmarkRef,
                value.positions.sumOf { position ->
                    position.currentWeight * position.modifiedDurationYears
                },
            )
        }
        state.kofrIndexStates.keys.forEach { ref -> put(ref, 0.0) }
        state.alternativeRiskPremiaStates.forEach { (ref, value) ->
            put(ref, value.effectiveDurationYears)
        }
    }
    val instrumentsById = state.stocks.associateBy(StockDefinition::id)

    fun expectedSourceIncome(
        source: com.amond.kmpbook.domain.model.fund.CompositeReferenceSource,
    ): Double? = when (source.kind) {
        CompositeReferenceSourceKind.BENCHMARK ->
            source.benchmarkRef?.let(benchmarkIncomeYields::get)
        CompositeReferenceSourceKind.INSTRUMENT ->
            source.instrumentId?.let(instrumentsById::get)?.dividendYield
    }

    fun expectedSourceDuration(
        source: com.amond.kmpbook.domain.model.fund.CompositeReferenceSource,
    ): Double? = when (source.kind) {
        CompositeReferenceSourceKind.BENCHMARK ->
            source.benchmarkRef?.let { ref -> benchmarkDurations[ref] ?: 0.0 }
        CompositeReferenceSourceKind.INSTRUMENT -> source.instrumentId?.let { 0.0 }
    }

    fun hasCanonicalSourceAndHedge(
        source: com.amond.kmpbook.domain.model.fund.CompositeReferenceSource,
        baseCurrency: ReferenceCurrency,
        hedgeRatio: Double?,
    ): Boolean {
        val registry = sourceCatalog ?: return true
        if (!registry.contains(source)) return false
        val sourceCurrency = runCatching { registry.currencyOf(source) }.getOrNull() ?: return false
        return if (sourceCurrency == baseCurrency) hedgeRatio == null else hedgeRatio != null
    }

    fun reconstructAlternativePosition(
        value: AlternativeRiskPremiaDriverPosition,
    ): AlternativeRiskPremiaDriverPosition = AlternativeRiskPremiaDriverPosition(
        driverId = value.driverId,
        strategyFamily = value.strategyFamily,
        currentSignedWeight = value.currentSignedWeight,
        targetSignedWeight = value.targetSignedWeight,
        annualizedVariance = value.annualizedVariance,
        trendSignal = value.trendSignal,
        lastSourceLogReturn = value.lastSourceLogReturn,
        sourceAvailable = value.sourceAvailable,
        sourceAnnualIncomeYield = value.sourceAnnualIncomeYield,
        sourceDurationYears = value.sourceDurationYears,
    )

    fun reconstructAlternativeState(value: AlternativeRiskPremiaState): AlternativeRiskPremiaState =
        AlternativeRiskPremiaState(
            benchmarkRef = value.benchmarkRef,
            positions = value.positions.map(::reconstructAlternativePosition),
            revision = value.revision,
            lastReweightDate = value.lastReweightDate,
            nextReweightDate = value.nextReweightDate,
            estimatedAnnualIncomeYield = value.estimatedAnnualIncomeYield,
            grossExposure = value.grossExposure,
            netExposure = value.netExposure,
            effectiveDurationYears = value.effectiveDurationYears,
            bootstrapCompositionHash = value.bootstrapCompositionHash,
            profileFingerprint = value.profileFingerprint,
            compositionHash = value.compositionHash,
            asOf = value.asOf,
        )

    fun reconstructAlternativeRecord(
        value: AlternativeRiskPremiaRebalanceRecord,
    ): AlternativeRiskPremiaRebalanceRecord = AlternativeRiskPremiaRebalanceRecord(
        id = value.id,
        benchmarkRef = value.benchmarkRef,
        kind = value.kind,
        effectiveDate = value.effectiveDate,
        effectiveAt = value.effectiveAt,
        cashSubstitutedDriverIds = value.cashSubstitutedDriverIds,
        compositionHashBefore = value.compositionHashBefore,
        compositionHashAfter = value.compositionHashAfter,
        turnoverRate = value.turnoverRate,
        resultingGrossExposure = value.resultingGrossExposure,
        resultingNetExposure = value.resultingNetExposure,
        resultingDurationYears = value.resultingDurationYears,
        revision = value.revision,
    )

    fun reconstructCompositePosition(
        value: CompositeReferenceSleevePosition,
    ): CompositeReferenceSleevePosition = CompositeReferenceSleevePosition(
        sleeveId = value.sleeveId,
        direction = value.direction,
        currentWeightMagnitude = value.currentWeightMagnitude,
        targetWeightMagnitude = value.targetWeightMagnitude,
        annualizedVariance = value.annualizedVariance,
        trendSignal = value.trendSignal,
        lastSourceLogReturn = value.lastSourceLogReturn,
        sourceAvailable = value.sourceAvailable,
        sourceAnnualIncomeYield = value.sourceAnnualIncomeYield,
        sourceDurationYears = value.sourceDurationYears,
        conditionalPrepaymentRateAnnual = value.conditionalPrepaymentRateAnnual,
    )

    fun reconstructCompositeState(value: CompositeReferenceState): CompositeReferenceState =
        CompositeReferenceState(
            benchmarkRef = value.benchmarkRef,
            positions = value.positions.map(::reconstructCompositePosition),
            revision = value.revision,
            lastSelectionDate = value.lastSelectionDate,
            nextSelectionDate = value.nextSelectionDate,
            lastReweightDate = value.lastReweightDate,
            nextReweightDate = value.nextReweightDate,
            estimatedAnnualIncomeYield = value.estimatedAnnualIncomeYield,
            grossExposure = value.grossExposure,
            netExposure = value.netExposure,
            effectiveDurationYears = value.effectiveDurationYears,
            lastMortgageRateAnnual = value.lastMortgageRateAnnual,
            bootstrapCompositionHash = value.bootstrapCompositionHash,
            profileFingerprint = value.profileFingerprint,
            compositionHash = value.compositionHash,
            asOf = value.asOf,
        )

    fun reconstructCompositeRecord(
        value: CompositeReferenceRebalanceRecord,
    ): CompositeReferenceRebalanceRecord = CompositeReferenceRebalanceRecord(
        id = value.id,
        benchmarkRef = value.benchmarkRef,
        kind = value.kind,
        effectiveDate = value.effectiveDate,
        effectiveAt = value.effectiveAt,
        addedSleeveIds = value.addedSleeveIds,
        removedSleeveIds = value.removedSleeveIds,
        cashSubstitutedSleeveIds = value.cashSubstitutedSleeveIds,
        compositionHashBefore = value.compositionHashBefore,
        compositionHashAfter = value.compositionHashAfter,
        turnoverRate = value.turnoverRate,
        resultingGrossExposure = value.resultingGrossExposure,
        resultingNetExposure = value.resultingNetExposure,
        resultingDurationYears = value.resultingDurationYears,
        revision = value.revision,
    )

    fun closeMatches(definition: BenchmarkDefinition, date: LocalDate, at: kotlin.time.Instant): Boolean =
        CompositeScheduleResolver.closeAt(definition.baseCurrency, date) == at

    fun scheduledDatesThrough(
        schedule: com.amond.kmpbook.domain.model.fund.CompositeRebalanceSchedule,
        currency: ReferenceCurrency,
        through: kotlin.time.Instant,
    ): List<LocalDate> = buildList {
        var next = CompositeScheduleResolver.nextDateAfterInstant(
            schedule,
            currency,
            GameCalendar.startInstant,
        )
        while (next != null && CompositeScheduleResolver.closeAt(currency, next) <= through) {
            add(next)
            next = CompositeScheduleResolver.nextDate(schedule, currency, next)
        }
    }

    fun canonicalSourceAvailability(
        source: com.amond.kmpbook.domain.model.fund.CompositeReferenceSource,
    ): Boolean? = when (source.kind) {
        CompositeReferenceSourceKind.BENCHMARK -> true
        CompositeReferenceSourceKind.INSTRUMENT -> source.instrumentId?.let { instrumentId ->
            state.listingLifecycleStates[instrumentId]?.isIndexEligible
        }
    }

    fun canonicalInstrumentUnavailabilityAt(instrumentId: String): kotlin.time.Instant? {
        val instrument = instrumentsById[instrumentId] ?: return null
        val firstUnavailableTransition = state.listingLifecycleLedger
            .asSequence()
            .filter { ledgerEvent ->
                ledgerEvent.stockId == instrumentId &&
                    ledgerEvent.toStatus in setOf(
                        ListingLifecycleStatus.LIQUIDATION_PENDING,
                        ListingLifecycleStatus.DELISTED,
                        ListingLifecycleStatus.TERMINATED,
                    )
            }
            .minByOrNull(ListingLifecycleLedgerEvent::sequence)
            ?: return null
        val venueClose = GameCalendar.regularSessionWindow(
            instrument.market,
            firstUnavailableTransition.tradingDate,
            DefaultMarketHolidays.closedDates(
                instrument.market,
                firstUnavailableTransition.tradingDate.year,
            ),
        )?.closesAt ?: return null
        val elapsedHours = (venueClose - GameCalendar.startInstant).inWholeHours
        val floor = GameCalendar.startInstant + elapsedHours.hours
        return if (floor == venueClose) floor else floor + 1.hours
    }

    fun canonicalExtraordinaryBatches(
        sourcesByPositionId: List<
            Pair<String, com.amond.kmpbook.domain.model.fund.CompositeReferenceSource>
            >,
        through: kotlin.time.Instant,
    ): List<Pair<kotlin.time.Instant, List<String>>>? {
        val positionIdsByEffectiveAt = linkedMapOf<kotlin.time.Instant, MutableList<String>>()
        for ((positionId, source) in sourcesByPositionId) {
            if (source.kind != CompositeReferenceSourceKind.INSTRUMENT) continue
            val instrumentId = source.instrumentId ?: return null
            val available = canonicalSourceAvailability(source) ?: return null
            if (available) continue
            val effectiveAt = canonicalInstrumentUnavailabilityAt(instrumentId) ?: return null
            if (effectiveAt > through) return null
            positionIdsByEffectiveAt.getOrPut(effectiveAt, ::mutableListOf).add(positionId)
        }
        return positionIdsByEffectiveAt.entries
            .sortedBy(Map.Entry<kotlin.time.Instant, MutableList<String>>::key)
            .map { (effectiveAt, positionIds) -> effectiveAt to positionIds.sorted() }
    }

    fun satisfiesExposureConstraint(
        value: Double,
        constraint: com.amond.kmpbook.domain.model.fund.CompositeExposureConstraint,
    ): Boolean = value in
        (constraint.minimum - STRUCTURED_REFERENCE_EPSILON)..
        (constraint.maximum + STRUCTURED_REFERENCE_EPSILON) &&
        constraint.target?.let { target ->
            abs(value - target) <= STRUCTURED_REFERENCE_EPSILON
        } != false

    val alternativeRecordsByRef = state.alternativeRiskPremiaRebalanceLedger.groupBy(
        AlternativeRiskPremiaRebalanceRecord::benchmarkRef,
    )
    if (state.alternativeRiskPremiaRebalanceLedger.map(AlternativeRiskPremiaRebalanceRecord::id)
            .distinct().size != state.alternativeRiskPremiaRebalanceLedger.size ||
        state.alternativeRiskPremiaRebalanceLedger.any { record ->
            record.benchmarkRef !in state.alternativeRiskPremiaStates
        }
    ) {
        return "대체위험 프리미엄 원장의 ID 또는 benchmark 소유권이 유효하지 않습니다."
    }
    val canonicalAlternativeOrder = state.alternativeRiskPremiaRebalanceLedger.sortedWith(
        compareBy<AlternativeRiskPremiaRebalanceRecord>(AlternativeRiskPremiaRebalanceRecord::effectiveAt)
            .thenBy(AlternativeRiskPremiaRebalanceRecord::benchmarkRef)
            .thenBy(AlternativeRiskPremiaRebalanceRecord::revision),
    )
    if (canonicalAlternativeOrder != state.alternativeRiskPremiaRebalanceLedger) {
        return "대체위험 프리미엄 원장이 효력시각·benchmark·revision 순서로 정렬되지 않았습니다."
    }
    for ((ref, alternativeState) in state.alternativeRiskPremiaStates) {
        val definition = alternativeDefinitions?.get(ref)
        val profile = definition?.alternativeRiskPremiaProfile
        val records = alternativeRecordsByRef[ref].orEmpty().sortedBy(
            AlternativeRiskPremiaRebalanceRecord::revision,
        )
        val currentGross = alternativeState.positions.sumOf { position -> abs(position.currentSignedWeight) }
        val currentNet = alternativeState.positions.sumOf(AlternativeRiskPremiaDriverPosition::currentSignedWeight)
        val currentIncome = alternativeState.positions.sumOf { position ->
            position.currentSignedWeight.coerceAtLeast(0.0) * position.sourceAnnualIncomeYield
        }.coerceIn(0.0, 1.0)
        val currentDuration = alternativeState.positions.sumOf { position ->
            position.currentSignedWeight * position.sourceDurationYears
        }
        if (alternativeState.benchmarkRef != ref || alternativeState.asOf != state.currentTime ||
            runCatching { reconstructAlternativeState(alternativeState) }.getOrNull() != alternativeState ||
            alternativeState.compositionHash != alternativeEngine.compositionHash(alternativeState.positions) ||
            abs(alternativeState.grossExposure - currentGross) > STRUCTURED_REFERENCE_EPSILON ||
            abs(alternativeState.netExposure - currentNet) > STRUCTURED_REFERENCE_EPSILON ||
            abs(alternativeState.estimatedAnnualIncomeYield - currentIncome) > STRUCTURED_REFERENCE_EPSILON ||
            abs(alternativeState.effectiveDurationYears - currentDuration) > STRUCTURED_REFERENCE_EPSILON
        ) {
            return "대체위험 프리미엄 상태의 키·시각·constructor·해시 또는 회계 합계가 유효하지 않습니다."
        }
        if (definition != null && profile == null) {
            return "ALT 벤치마크에 대체위험 프리미엄 프로필이 없습니다."
        }
        if (profile != null) {
            val driversById = profile.drivers.associateBy { driver -> driver.driverId }
            if (alternativeState.bootstrapCompositionHash != canonicalBootstrapHashes?.first?.get(ref) ||
                alternativeState.positions.map { position -> position.driverId }.toSet() != driversById.keys ||
                alternativeState.profileFingerprint != alternativeEngine.profileFingerprint(ref, profile)
            ) {
                return "대체위험 프리미엄 bootstrap 해시·driver 집합 또는 프로필 지문이 카탈로그와 다릅니다."
            }
            if (alternativeState.positions.any { position ->
                    val driver = driversById[position.driverId] ?: return@any true
                    val canonicalAvailable = canonicalSourceAvailability(driver.source)
                        ?: return@any true
                    val expectedIncome = if (position.sourceAvailable) {
                        expectedSourceIncome(driver.source)
                    } else {
                        0.0
                    }
                    val expectedDuration = if (position.sourceAvailable) {
                        expectedSourceDuration(driver.source)
                    } else {
                        0.0
                    }
                    val violatesDirectionPolicy = when (driver.signalDirectionPolicy) {
                        AlternativeRiskPremiaSignalDirectionPolicy.LONG_ONLY ->
                            position.currentSignedWeight < -STRUCTURED_REFERENCE_EPSILON ||
                                position.targetSignedWeight < -STRUCTURED_REFERENCE_EPSILON
                        AlternativeRiskPremiaSignalDirectionPolicy.SHORT_ONLY ->
                            position.currentSignedWeight > STRUCTURED_REFERENCE_EPSILON ||
                                position.targetSignedWeight > STRUCTURED_REFERENCE_EPSILON
                        AlternativeRiskPremiaSignalDirectionPolicy.DYNAMIC_LONG_SHORT -> false
                    }
                    val violatesCurrentTargetSign = when {
                        position.targetSignedWeight > STRUCTURED_REFERENCE_EPSILON ->
                            position.currentSignedWeight < -STRUCTURED_REFERENCE_EPSILON
                        position.targetSignedWeight < -STRUCTURED_REFERENCE_EPSILON ->
                            position.currentSignedWeight > STRUCTURED_REFERENCE_EPSILON
                        else -> abs(position.currentSignedWeight) > STRUCTURED_REFERENCE_EPSILON
                    }
                    position.strategyFamily != driver.strategyFamily ||
                        position.sourceAvailable != canonicalAvailable ||
                        position.lastSourceLogReturn !in -3.0..3.0 ||
                        violatesDirectionPolicy || violatesCurrentTargetSign ||
                        !hasCanonicalSourceAndHedge(
                            driver.source,
                            definition.baseCurrency,
                            driver.hedgeRatioToProfileBaseCurrency,
                        ) ||
                        expectedIncome?.let { expected ->
                            abs(position.sourceAnnualIncomeYield - expected) > STRUCTURED_REFERENCE_EPSILON
                        } != false ||
                        expectedDuration?.let { expected ->
                            abs(position.sourceDurationYears - expected) > STRUCTURED_REFERENCE_EPSILON
                        } != false
                }
            ) {
                return "대체위험 프리미엄 driver의 전략·source·FX·소득·듀레이션이 카탈로그와 다릅니다."
            }
            val targetLong = alternativeState.positions.sumOf { position ->
                position.targetSignedWeight.coerceAtLeast(0.0)
            }
            val targetShort = alternativeState.positions.sumOf { position ->
                (-position.targetSignedWeight).coerceAtLeast(0.0)
            }
            val targetNet = targetLong - targetShort
            if (!satisfiesExposureConstraint(targetLong, profile.longGrossExposureConstraint) ||
                !satisfiesExposureConstraint(targetShort, profile.shortGrossExposureConstraint) ||
                !satisfiesExposureConstraint(targetNet, profile.netExposureConstraint) ||
                alternativeState.nextReweightDate != CompositeScheduleResolver.nextDateAfterInstant(
                    profile.rebalanceSchedule,
                    definition.baseCurrency,
                    alternativeState.asOf,
                )
            ) {
                return "대체위험 프리미엄 목표 노출 또는 다음 재가중 일정이 프로필과 다릅니다."
            }
        }
        if (records.size.toLong() != alternativeState.revision ||
            records.withIndex().any { (index, record) -> record.revision != index + 1L } ||
            records.any { record ->
                record.effectiveAt > alternativeState.asOf ||
                    runCatching { reconstructAlternativeRecord(record) }.getOrNull() != record
            } ||
            records.zipWithNext().any { (previous, next) ->
                previous.compositionHashAfter != next.compositionHashBefore ||
                    previous.effectiveAt > next.effectiveAt
            } ||
            records.lastOrNull()?.compositionHashAfter?.let { hash ->
                hash != alternativeState.compositionHash
            } == true || records.firstOrNull()?.compositionHashBefore?.let { hash ->
                hash != alternativeState.bootstrapCompositionHash
            } == true || records.isEmpty() &&
            alternativeState.compositionHash != alternativeState.bootstrapCompositionHash
        ) {
            return "대체위험 프리미엄 원장의 revision·일정·ID·구성 해시 계보가 유효하지 않습니다."
        }
        if (profile != null) {
            val scheduledRecords = records.filter { record ->
                record.kind == AlternativeRiskPremiaActionKind.REWEIGHT
            }
            val extraordinaryRecords = records.filter { record ->
                record.kind == AlternativeRiskPremiaActionKind.EXTRAORDINARY_SOURCE_TO_CASH
            }
            val expectedScheduledDates = scheduledDatesThrough(
                profile.rebalanceSchedule,
                definition.baseCurrency,
                alternativeState.asOf,
            )
            if (scheduledRecords.map(AlternativeRiskPremiaRebalanceRecord::effectiveDate) !=
                expectedScheduledDates || scheduledRecords.any { record ->
                    val resultLong = (record.resultingGrossExposure + record.resultingNetExposure) / 2.0
                    val resultShort = (record.resultingGrossExposure - record.resultingNetExposure) / 2.0
                    record.id != "alternative-reweight:${ref.benchmarkId}:v${ref.version}:" +
                    "${record.effectiveDate}:r${record.revision}" ||
                        !closeMatches(definition, record.effectiveDate, record.effectiveAt) ||
                        !satisfiesExposureConstraint(resultLong, profile.longGrossExposureConstraint) ||
                        !satisfiesExposureConstraint(resultShort, profile.shortGrossExposureConstraint) ||
                        !satisfiesExposureConstraint(record.resultingNetExposure, profile.netExposureConstraint)
                } || extraordinaryRecords.any { record ->
                    record.id != "alternative-extraordinary-source-to-cash:${ref.benchmarkId}:" +
                    "v${ref.version}:${record.effectiveAt.epochSeconds}:r${record.revision}" ||
                        record.effectiveDate != CompositeScheduleResolver.localDateAt(
                            definition.baseCurrency,
                            record.effectiveAt,
                        )
                }
            ) {
                return "대체위험 프리미엄의 정기·비상 action 원장이 canonical 일정·ID·노출과 다릅니다."
            }
            val canonicalExtraordinaryBatches = canonicalExtraordinaryBatches(
                profile.drivers.map { driver -> driver.driverId to driver.source },
                alternativeState.asOf,
            )
            val persistedExtraordinaryBatches = extraordinaryRecords.map { record ->
                record.effectiveAt to record.cashSubstitutedDriverIds
            }
            if (canonicalExtraordinaryBatches == null ||
                persistedExtraordinaryBatches != canonicalExtraordinaryBatches
            ) {
                return "대체위험 프리미엄 source-to-cash 원장이 기초종목의 최초 비적격 전이와 다릅니다."
            }
            val substitutedIds = extraordinaryRecords.flatMap(
                AlternativeRiskPremiaRebalanceRecord::cashSubstitutedDriverIds,
            )
            val currentlyUnavailableIds = alternativeState.positions
                .filterNot(AlternativeRiskPremiaDriverPosition::sourceAvailable)
                .map(AlternativeRiskPremiaDriverPosition::driverId)
            if (substitutedIds.distinct().size != substitutedIds.size ||
                substitutedIds.toSet() != currentlyUnavailableIds.toSet() ||
                substitutedIds.any { driverId -> profile.drivers.none { it.driverId == driverId } }
            ) {
                return "대체위험 프리미엄의 sticky source-to-cash 계보가 현재 driver 상태와 다릅니다."
            }
            val latestScheduled = scheduledRecords.lastOrNull()
            val targetGross = alternativeState.positions.sumOf { position ->
                abs(position.targetSignedWeight)
            }
            val targetNet = alternativeState.positions.sumOf(
                AlternativeRiskPremiaDriverPosition::targetSignedWeight,
            )
            if (alternativeState.lastReweightDate != latestScheduled?.effectiveDate ||
                latestScheduled?.let { record ->
                    abs(record.resultingGrossExposure - targetGross) > STRUCTURED_REFERENCE_EPSILON ||
                        abs(record.resultingNetExposure - targetNet) > STRUCTURED_REFERENCE_EPSILON
                } == true
            ) {
                return "대체위험 프리미엄 최신 정기 원장의 날짜·목표 노출이 현재 target book과 다릅니다."
            }
        }
    }

    val compositeRecordsByRef = state.compositeReferenceRebalanceLedger.groupBy(
        CompositeReferenceRebalanceRecord::benchmarkRef,
    )
    if (state.compositeReferenceRebalanceLedger.map(CompositeReferenceRebalanceRecord::id)
            .distinct().size != state.compositeReferenceRebalanceLedger.size ||
        state.compositeReferenceRebalanceLedger.any { record ->
            record.benchmarkRef !in state.compositeReferenceStates
        }
    ) {
        return "복합 기준 원장의 ID 또는 benchmark 소유권이 유효하지 않습니다."
    }
    val canonicalCompositeOrder = state.compositeReferenceRebalanceLedger.sortedWith(
        compareBy<CompositeReferenceRebalanceRecord>(CompositeReferenceRebalanceRecord::effectiveAt)
            .thenBy(CompositeReferenceRebalanceRecord::benchmarkRef)
            .thenBy(CompositeReferenceRebalanceRecord::revision),
    )
    if (canonicalCompositeOrder != state.compositeReferenceRebalanceLedger) {
        return "복합 기준 원장이 효력시각·benchmark·revision 순서로 정렬되지 않았습니다."
    }
    val expectedMortgageRate = (
        state.fixedIncomeReferenceStates.values.asSequence()
            .mapNotNull { fixed -> fixed.nominalCurves[ReferenceCurrency.USD] }
            .firstOrNull()
            ?.rateAtYears(10.0)
            ?: state.macro.policyRate
        ).plus(STRUCTURED_REFERENCE_MORTGAGE_SPREAD).coerceIn(0.0, 1.0)
    for ((ref, compositeState) in state.compositeReferenceStates) {
        val definition = compositeDefinitions?.get(ref)
        val profile = definition?.compositeReferenceProfile
        val records = compositeRecordsByRef[ref].orEmpty().sortedBy(
            CompositeReferenceRebalanceRecord::revision,
        )
        val currentGross = compositeState.positions.sumOf(
            CompositeReferenceSleevePosition::currentWeightMagnitude,
        )
        val currentNet = compositeState.positions.sumOf(CompositeReferenceSleevePosition::signedCurrentWeight)
        val currentIncome = compositeState.positions.sumOf { position ->
            if (position.direction == CompositeSleeveDirection.LONG) {
                position.currentWeightMagnitude * position.sourceAnnualIncomeYield
            } else {
                0.0
            }
        }.coerceIn(0.0, 1.0)
        val currentDuration = compositeState.positions.sumOf { position ->
            position.signedCurrentWeight * position.sourceDurationYears
        }
        if (compositeState.benchmarkRef != ref || compositeState.asOf != state.currentTime ||
            runCatching { reconstructCompositeState(compositeState) }.getOrNull() != compositeState ||
            compositeState.compositionHash != compositeEngine.compositionHash(compositeState.positions) ||
            abs(compositeState.grossExposure - currentGross) > STRUCTURED_REFERENCE_EPSILON ||
            abs(compositeState.netExposure - currentNet) > STRUCTURED_REFERENCE_EPSILON ||
            abs(compositeState.estimatedAnnualIncomeYield - currentIncome) > STRUCTURED_REFERENCE_EPSILON ||
            abs(compositeState.effectiveDurationYears - currentDuration) > STRUCTURED_REFERENCE_EPSILON ||
            abs(compositeState.lastMortgageRateAnnual - expectedMortgageRate) >
            STRUCTURED_REFERENCE_EPSILON
        ) {
            return "복합 기준 상태의 키·시각·constructor·해시·회계 또는 모기지 금리가 유효하지 않습니다."
        }
        if (definition != null && profile == null) {
            return "COMPOSITE 벤치마크에 복합 기준 프로필이 없습니다."
        }
        if (profile != null) {
            val sleevesById = profile.sleeves.associateBy { sleeve -> sleeve.sleeveId }
            if (compositeState.bootstrapCompositionHash != canonicalBootstrapHashes?.second?.get(ref) ||
                compositeState.positions.map { position -> position.sleeveId }.toSet() != sleevesById.keys ||
                compositeState.profileFingerprint != compositeEngine.profileFingerprint(ref, profile)
            ) {
                return "복합 기준 bootstrap 해시·sleeve 집합 또는 프로필 지문이 카탈로그와 다릅니다."
            }
            if (compositeState.positions.any { position ->
                    val sleeve = sleevesById[position.sleeveId] ?: return@any true
                    val canonicalAvailable = canonicalSourceAvailability(sleeve.source)
                        ?: return@any true
                    val expectedIncome = if (!position.sourceAvailable) {
                        0.0
                    } else {
                        sleeve.mbsInterestOnlyTerms?.modelParameters
                            ?.couponStripYieldAnnual ?: expectedSourceIncome(sleeve.source)
                    }
                    val expectedDuration = if (!position.sourceAvailable) {
                        0.0
                    } else {
                        sleeve.mbsInterestOnlyTerms?.modelParameters
                            ?.effectiveDurationYears ?: expectedSourceDuration(sleeve.source)
                    }
                    position.direction != sleeve.direction ||
                        position.sourceAvailable != canonicalAvailable ||
                        position.lastSourceLogReturn !in -3.0..3.0 ||
                        !hasCanonicalSourceAndHedge(
                            sleeve.source,
                            definition.baseCurrency,
                            sleeve.hedgeRatioToCompositeBaseCurrency,
                        ) ||
                        expectedIncome?.let { expected ->
                            abs(position.sourceAnnualIncomeYield - expected) > STRUCTURED_REFERENCE_EPSILON
                        } != false ||
                        expectedDuration?.let { expected ->
                            abs(position.sourceDurationYears - expected) > STRUCTURED_REFERENCE_EPSILON
                        } != false ||
                        (position.conditionalPrepaymentRateAnnual != null) !=
                        (position.sourceAvailable && sleeve.mbsInterestOnlyTerms != null) ||
                        sleeve.minimumWeight?.let { minimum ->
                            position.targetWeightMagnitude < minimum - STRUCTURED_REFERENCE_EPSILON
                        } == true ||
                        sleeve.maximumWeight?.let { maximum ->
                            position.targetWeightMagnitude > maximum + STRUCTURED_REFERENCE_EPSILON
                        } == true
                }
            ) {
                return "복합 기준 sleeve의 방향·source·FX·소득·듀레이션·MBS/목표 비중이 다릅니다."
            }
            val targetGross = compositeState.positions.sumOf(
                CompositeReferenceSleevePosition::targetWeightMagnitude,
            )
            val targetNet = compositeState.positions.sumOf(CompositeReferenceSleevePosition::signedTargetWeight)
            if (!satisfiesExposureConstraint(targetGross, profile.grossExposureConstraint) ||
                !satisfiesExposureConstraint(targetNet, profile.netExposureConstraint) ||
                compositeState.nextSelectionDate != CompositeScheduleResolver.nextDateAfterInstant(
                    profile.selectionSchedule,
                    definition.baseCurrency,
                    compositeState.asOf,
                ) ||
                compositeState.nextReweightDate != CompositeScheduleResolver.nextDateAfterInstant(
                    profile.reweightSchedule,
                    definition.baseCurrency,
                    compositeState.asOf,
                )
            ) {
                return "복합 기준 목표 노출 또는 다음 selection/reweight 일정이 프로필과 다릅니다."
            }
        }
        if (records.size.toLong() != compositeState.revision ||
            records.withIndex().any { (index, record) -> record.revision != index + 1L } ||
            records.any { record ->
                record.effectiveAt > compositeState.asOf ||
                    runCatching { reconstructCompositeRecord(record) }.getOrNull() != record
            } ||
            records.zipWithNext().any { (previous, next) ->
                previous.compositionHashAfter != next.compositionHashBefore ||
                    previous.effectiveAt > next.effectiveAt
            } ||
            records.lastOrNull()?.compositionHashAfter?.let { hash ->
                hash != compositeState.compositionHash
            } == true || records.firstOrNull()?.compositionHashBefore?.let { hash ->
                hash != compositeState.bootstrapCompositionHash
            } == true || records.isEmpty() &&
            compositeState.compositionHash != compositeState.bootstrapCompositionHash
        ) {
            return "복합 기준 원장의 revision·일정·ID·구성 해시 계보가 유효하지 않습니다."
        }
        if (profile != null) {
            val selectionRecords = records.filter { record ->
                record.kind == CompositeReferenceActionKind.SELECTION
            }
            val reweightRecords = records.filter { record ->
                record.kind == CompositeReferenceActionKind.REWEIGHT
            }
            val extraordinaryRecords = records.filter { record ->
                record.kind == CompositeReferenceActionKind.EXTRAORDINARY_SOURCE_TO_CASH
            }
            val expectedSelectionDates = scheduledDatesThrough(
                profile.selectionSchedule,
                definition.baseCurrency,
                compositeState.asOf,
            )
            val expectedExplicitReweightDates = scheduledDatesThrough(
                profile.reweightSchedule,
                definition.baseCurrency,
                compositeState.asOf,
            ).filterNot(expectedSelectionDates.toSet()::contains)
            val actualReweightDates = reweightRecords.map(CompositeReferenceRebalanceRecord::effectiveDate)
            if (selectionRecords.map(CompositeReferenceRebalanceRecord::effectiveDate) !=
                expectedSelectionDates ||
                if (profile.driftThreshold == null) {
                    actualReweightDates != expectedExplicitReweightDates
                } else {
                    actualReweightDates.any { date -> date !in expectedExplicitReweightDates }
                }
            ) {
                return "복합 기준의 selection/reweight 원장 일정이 프로필의 전체 action 계보와 다릅니다."
            }
            val scheduledRecords = selectionRecords + reweightRecords
            if (scheduledRecords.any { record ->
                    val schedule = when (record.kind) {
                        CompositeReferenceActionKind.SELECTION -> profile.selectionSchedule
                        CompositeReferenceActionKind.REWEIGHT -> profile.reweightSchedule
                        CompositeReferenceActionKind.EXTRAORDINARY_SOURCE_TO_CASH -> return@any true
                    }
                    record.id != "composite-${record.kind.name.lowercase()}:" +
                    "${ref.benchmarkId}:v${ref.version}:${record.effectiveDate}:r${record.revision}" ||
                        !closeMatches(definition, record.effectiveDate, record.effectiveAt) ||
                        CompositeScheduleResolver.nextDate(
                            schedule,
                            definition.baseCurrency,
                            record.effectiveDate.minus(1, DateTimeUnit.DAY),
                        ) != record.effectiveDate ||
                        !satisfiesExposureConstraint(
                            record.resultingGrossExposure,
                            profile.grossExposureConstraint,
                        ) || !satisfiesExposureConstraint(
                            record.resultingNetExposure,
                            profile.netExposureConstraint,
                        ) || profile.durationConstraint?.let { constraint ->
                            record.resultingDurationYears !in
                                (constraint.minimumYears - STRUCTURED_REFERENCE_EPSILON)..
                                (constraint.maximumYears + STRUCTURED_REFERENCE_EPSILON)
                        } == true
                } || extraordinaryRecords.any { record ->
                    record.id != "composite-extraordinary-source-to-cash:${ref.benchmarkId}:" +
                    "v${ref.version}:${record.effectiveAt.epochSeconds}:r${record.revision}" ||
                        record.effectiveDate != CompositeScheduleResolver.localDateAt(
                            definition.baseCurrency,
                            record.effectiveAt,
                        )
                }
            ) {
                return "복합 기준의 정기·비상 action ID·시각·결과 노출이 canonical 규칙과 다릅니다."
            }

            val canonicalExtraordinaryBatches = canonicalExtraordinaryBatches(
                profile.sleeves.map { sleeve -> sleeve.sleeveId to sleeve.source },
                compositeState.asOf,
            )
            val persistedExtraordinaryBatches = extraordinaryRecords.map { record ->
                record.effectiveAt to record.cashSubstitutedSleeveIds
            }
            if (canonicalExtraordinaryBatches == null ||
                persistedExtraordinaryBatches != canonicalExtraordinaryBatches
            ) {
                return "복합 기준 source-to-cash 원장이 기초종목의 최초 비적격 전이와 다릅니다."
            }

            val cashSubstitutedIds = extraordinaryRecords.flatMap(
                CompositeReferenceRebalanceRecord::cashSubstitutedSleeveIds,
            )
            val currentlyUnavailableIds = compositeState.positions
                .filterNot(CompositeReferenceSleevePosition::sourceAvailable)
                .map(CompositeReferenceSleevePosition::sleeveId)
            if (cashSubstitutedIds.distinct().size != cashSubstitutedIds.size ||
                cashSubstitutedIds.toSet() != currentlyUnavailableIds.toSet() ||
                cashSubstitutedIds.any { sleeveId -> profile.sleeves.none { it.sleeveId == sleeveId } }
            ) {
                return "복합 기준의 sticky source-to-cash 계보가 현재 sleeve 상태와 다릅니다."
            }

            var activeSleeveIds = compositeState.positions
                .filter { position ->
                    position.targetWeightMagnitude > REFERENCE_WEIGHT_ALLOCATION_EPSILON
                }
                .mapTo(linkedSetOf(), CompositeReferenceSleevePosition::sleeveId)
            for (record in selectionRecords.asReversed()) {
                if (!activeSleeveIds.containsAll(record.addedSleeveIds) ||
                    record.removedSleeveIds.any(activeSleeveIds::contains) ||
                    (record.addedSleeveIds + record.removedSleeveIds).any { sleeveId ->
                        profile.sleeves.none { sleeve -> sleeve.sleeveId == sleeveId }
                    }
                ) {
                    return "복합 기준 selection 원장의 편입·편출을 현재 active sleeve에서 역재생할 수 없습니다."
                }
                activeSleeveIds.removeAll(record.addedSleeveIds.toSet())
                activeSleeveIds.addAll(record.removedSleeveIds)
            }
            val canonicalBootstrapActiveSleeveIds = canonicalBootstrapHashes?.third?.get(ref)
            if (canonicalBootstrapActiveSleeveIds == null ||
                activeSleeveIds != canonicalBootstrapActiveSleeveIds
            ) {
                return "복합 기준 selection 역재생 결과가 canonical campaign-start active sleeve와 다릅니다."
            }

            val latestSelection = selectionRecords.lastOrNull()
            val latestReweight = reweightRecords.lastOrNull()
            val latestCoincidentSelection = records.lastOrNull { record ->
                record.kind == CompositeReferenceActionKind.SELECTION &&
                    CompositeScheduleResolver.nextDate(
                        profile.reweightSchedule,
                        definition.baseCurrency,
                        record.effectiveDate.minus(1, DateTimeUnit.DAY),
                    ) == record.effectiveDate
            }
            val expectedLastReweight = listOfNotNull(
                latestReweight?.effectiveDate,
                latestCoincidentSelection?.effectiveDate,
            ).maxOrNull()
            val latestScheduled = records.lastOrNull { record ->
                record.kind != CompositeReferenceActionKind.EXTRAORDINARY_SOURCE_TO_CASH
            }
            val targetGross = compositeState.positions.sumOf(
                CompositeReferenceSleevePosition::targetWeightMagnitude,
            )
            val targetNet = compositeState.positions.sumOf(
                CompositeReferenceSleevePosition::signedTargetWeight,
            )
            if (compositeState.lastSelectionDate != latestSelection?.effectiveDate ||
                compositeState.lastReweightDate != expectedLastReweight ||
                latestScheduled?.let { record ->
                    abs(record.resultingGrossExposure - targetGross) > STRUCTURED_REFERENCE_EPSILON ||
                        abs(record.resultingNetExposure - targetNet) > STRUCTURED_REFERENCE_EPSILON
                } == true
            ) {
                return "복합 기준의 마지막 action 날짜·결과 노출이 현재 target book과 다릅니다."
            }
        }
    }
    return null
}

/** 원자재 현물·선물 공유 book과 roll·배분 revision을 카탈로그 약관에 결속한다. */
private fun validateCommodityReferencePersistenceState(
    state: SimulatorUiState,
    catalog: InstrumentCatalogSnapshot?,
): String? {
    val spotDefinitions = catalog?.benchmarksInEvaluationOrder
        ?.filter { definition -> definition.engineKind == BenchmarkEngineKind.COMMODITY_SPOT }
        ?.associateBy(BenchmarkDefinition::ref)
    val futuresDefinitions = catalog?.benchmarksInEvaluationOrder
        ?.filter { definition -> definition.engineKind == BenchmarkEngineKind.FUTURES_CURVE }
        ?.associateBy(BenchmarkDefinition::ref)
    if (spotDefinitions != null && state.commoditySpotReferenceStates.keys != spotDefinitions.keys) {
        return "원자재 현물 상태는 실행 가능한 현물 벤치마크마다 정확히 하나씩 필요합니다."
    }
    if (futuresDefinitions != null && state.futuresReferenceStates.keys != futuresDefinitions.keys) {
        return "선물 상태는 실행 가능한 선물 벤치마크마다 정확히 하나씩 필요합니다."
    }
    if (state.commoditySpotReferenceStates.keys.any(state.futuresReferenceStates::containsKey)) {
        return "하나의 벤치마크 버전을 현물 상태와 선물 상태가 동시에 소유할 수 없습니다."
    }
    if (state.futuresRollLedger.map(FuturesRollRecord::id).distinct().size !=
        state.futuresRollLedger.size ||
        state.futuresAllocationLedger.map(FuturesAllocationRecord::id).distinct().size !=
        state.futuresAllocationLedger.size
    ) {
        return "선물 roll·배분 원장 ID가 중복되었습니다."
    }
    val futuresRefs = state.futuresReferenceStates.keys
    if (state.futuresRollLedger.any { record -> record.benchmarkRef !in futuresRefs } ||
        state.futuresAllocationLedger.any { record -> record.benchmarkRef !in futuresRefs }
    ) {
        return "선물 원장에 현재 상태가 없는 벤치마크가 있습니다."
    }
    val allAsOf = state.commoditySpotReferenceStates.values.map(CommoditySpotReferenceState::asOf) +
        state.futuresReferenceStates.values.map(FuturesReferenceState::asOf)
    if (allAsOf.any { asOf -> asOf != state.currentTime }) {
        return "원자재·선물 공유 기준 상태는 현재 게임 시각까지 매 tick 전개되어야 합니다."
    }

    fun closeEnough(left: Double, right: Double): Boolean =
        kotlin.math.abs(left - right) <=
            COMMODITY_REFERENCE_VALUE_EPSILON * maxOf(1.0, kotlin.math.abs(left), kotlin.math.abs(right))

    fun reconstructSpot(value: CommoditySpotReferenceState): CommoditySpotReferenceState =
        CommoditySpotReferenceState(
            benchmarkRef = value.benchmarkRef,
            assetClass = value.assetClass,
            baseCurrency = value.baseCurrency,
            currentSpotLevel = value.currentSpotLevel,
            currentReferenceLevel = value.currentReferenceLevel,
            currentSpotWeight = value.currentSpotWeight,
            currentCollateralWeight = value.currentCollateralWeight,
            annualizedNetCarryRate = value.annualizedNetCarryRate,
            asOf = value.asOf,
        )

    for ((benchmarkRef, spotState) in state.commoditySpotReferenceStates) {
        val definition = spotDefinitions?.get(benchmarkRef)
        val terms = definition?.commoditySpotTerms
        if (definition != null && terms == null) {
            return "현물 엔진 벤치마크에 원자재 현물 약관이 없습니다."
        }
        if (spotState.benchmarkRef != benchmarkRef ||
            runCatching { reconstructSpot(spotState) }.getOrNull() != spotState ||
            terms?.let { canonical ->
                canonical.benchmarkRef != benchmarkRef ||
                    canonical.assetClass != spotState.assetClass ||
                    canonical.baseCurrency != spotState.baseCurrency
            } == true
        ) {
            return "원자재 현물 상태의 map 키·자산군·통화·도메인 불변조건이 유효하지 않습니다."
        }
        if (terms != null) {
            val cashRate = state.macro.policyRate.coerceIn(-0.10, 1.0)
            val expectedCarry = spotState.currentSpotWeight * (
                terms.annualConvenienceYieldRate - terms.annualStorageCostRate -
                    terms.annualCustodyAndInsuranceCostRate
                ) + spotState.currentCollateralWeight * cashRate *
                terms.collateralYieldParticipation
            if (!closeEnough(spotState.annualizedNetCarryRate, expectedCarry)) {
                return "원자재 현물 상태의 보관비·편익수익·담보이자 carry 계정이 약관과 다릅니다."
            }
        }
    }

    fun reconstructSleeve(value: FuturesSleeveState): FuturesSleeveState = FuturesSleeveState(
        sleeveId = value.sleeveId,
        curveId = value.curveId,
        assetClass = value.assetClass,
        rollCalendar = value.rollCalendar,
        priceReturnConvention = value.priceReturnConvention,
        fixedPriceReturnNotional = value.fixedPriceReturnNotional,
        currentWeight = value.currentWeight,
        targetWeight = value.targetWeight,
        currentSpotLevel = value.currentSpotLevel,
        frontContractId = value.frontContractId,
        frontExpiryDate = value.frontExpiryDate,
        frontPrice = value.frontPrice,
        frontContractWeight = value.frontContractWeight,
        nextContractId = value.nextContractId,
        nextExpiryDate = value.nextExpiryDate,
        nextPrice = value.nextPrice,
        nextContractWeight = value.nextContractWeight,
        lastRollTradingDate = value.lastRollTradingDate,
    )

    fun reconstructFutures(value: FuturesReferenceState): FuturesReferenceState =
        FuturesReferenceState(
            benchmarkRef = value.benchmarkRef,
            baseCurrency = value.baseCurrency,
            portfolioStyle = value.portfolioStyle,
            allocationMode = value.allocationMode,
            currentReferenceLevel = value.currentReferenceLevel,
            sleeves = value.sleeves.map(::reconstructSleeve),
            revision = value.revision,
            asOf = value.asOf,
        )

    fun reconstructRoll(value: FuturesRollRecord): FuturesRollRecord = FuturesRollRecord(
        id = value.id,
        benchmarkRef = value.benchmarkRef,
        sleeveId = value.sleeveId,
        rollTradingDate = value.rollTradingDate,
        fromContractId = value.fromContractId,
        toContractId = value.toContractId,
        transferredContractWeight = value.transferredContractWeight,
        frontWeightBefore = value.frontWeightBefore,
        frontWeightAfter = value.frontWeightAfter,
        normalizedCurveBasis = value.normalizedCurveBasis,
        promotedDeferredToFront = value.promotedDeferredToFront,
        successorContractId = value.successorContractId,
        effectiveAt = value.effectiveAt,
        revision = value.revision,
    )

    fun reconstructAllocation(value: FuturesAllocationRecord): FuturesAllocationRecord =
        FuturesAllocationRecord(
            id = value.id,
            benchmarkRef = value.benchmarkRef,
            weightsBefore = value.weightsBefore.toMap(),
            weightsAfter = value.weightsAfter.toMap(),
            effectiveAt = value.effectiveAt,
            revision = value.revision,
        )

    fun localTradingDate(calendar: FuturesRollCalendar, at: kotlin.time.Instant): LocalDate =
        GameCalendar.marketLocalDateTime(
            when (calendar) {
                FuturesRollCalendar.US_FUTURES_FULL_DAY_APPROXIMATION -> Market.NYSE
                FuturesRollCalendar.KRX_DERIVATIVES_FULL_DAY_APPROXIMATION -> Market.KOSPI
            },
            at,
        ).date

    fun isFirstTradingDateOfMonth(calendar: FuturesRollCalendar, date: LocalDate): Boolean {
        if (!calendar.isTradingDate(date)) return false
        var previous = date.minus(1, DateTimeUnit.DAY)
        repeat(14) {
            if (calendar.isTradingDate(previous)) return previous.month != date.month
            previous = previous.minus(1, DateTimeUnit.DAY)
        }
        return false
    }

    fun expectedContractId(curveId: String, expiryDate: LocalDate): String =
        "cm:${DeterministicRandom.stableHash64(curveId).toULong()}:" +
            "${expiryDate.year}${expiryDate.month.number.toString().padStart(2, '0')}"

    val rollsByRef = state.futuresRollLedger.groupBy(FuturesRollRecord::benchmarkRef)
    val allocationsByRef = state.futuresAllocationLedger.groupBy(FuturesAllocationRecord::benchmarkRef)
    for ((benchmarkRef, futuresState) in state.futuresReferenceStates) {
        val definition = futuresDefinitions?.get(benchmarkRef)
        val terms = definition?.futuresReferenceTerms
        if (definition != null && terms == null) {
            return "선물 엔진 벤치마크에 선물 기준 약관이 없습니다."
        }
        if (futuresState.benchmarkRef != benchmarkRef ||
            runCatching { reconstructFutures(futuresState) }.getOrNull() != futuresState ||
            terms?.let { canonical ->
                canonical.benchmarkRef != benchmarkRef ||
                    canonical.baseCurrency != futuresState.baseCurrency ||
                    canonical.portfolioStyle != futuresState.portfolioStyle ||
                    canonical.allocationMode != futuresState.allocationMode
            } == true
        ) {
            return "선물 상태의 map 키·약관 정체성·도메인 불변조건이 유효하지 않습니다."
        }
        val stateSleeves = futuresState.sleeves.associateBy(FuturesSleeveState::sleeveId)
        val termSleeves = terms?.sleeves?.associateBy { sleeve -> sleeve.sleeveId }
        if (termSleeves != null && stateSleeves.keys != termSleeves.keys) {
            return "선물 상태의 sleeve 집합이 카탈로그 약관과 다릅니다."
        }
        for (sleeve in futuresState.sleeves) {
            val sleeveTerms = termSleeves?.get(sleeve.sleeveId)
            if (sleeveTerms != null &&
                (sleeve.curveId != sleeveTerms.curveId ||
                    sleeve.assetClass != sleeveTerms.assetClass ||
                    sleeve.rollCalendar != sleeveTerms.rollCalendar ||
                    sleeve.priceReturnConvention != sleeveTerms.priceReturnConvention ||
                    sleeve.fixedPriceReturnNotional != sleeveTerms.fixedPriceReturnNotional ||
                    sleeve.frontExpiryDate.month.number !in sleeveTerms.eligibleDeliveryMonths ||
                    sleeve.nextExpiryDate.month.number !in sleeveTerms.eligibleDeliveryMonths)
            ) {
                return "선물 sleeve의 curve·자산군·달력·가격규칙·인도월이 약관과 다릅니다."
            }
            if (sleeve.frontContractId != expectedContractId(sleeve.curveId, sleeve.frontExpiryDate) ||
                sleeve.nextContractId != expectedContractId(sleeve.curveId, sleeve.nextExpiryDate) ||
                sleeve.lastRollTradingDate?.let { date ->
                    date > localTradingDate(sleeve.rollCalendar, futuresState.asOf)
                } == true
            ) {
                return "선물 sleeve의 계약 ID·만기월·마지막 roll 날짜 계보가 유효하지 않습니다."
            }
        }

        val rolls = rollsByRef[benchmarkRef].orEmpty()
        val allocations = allocationsByRef[benchmarkRef].orEmpty()
        val revisions = buildList {
            rolls.mapTo(this) { record -> record.revision to record.effectiveAt }
            allocations.mapTo(this) { record -> record.revision to record.effectiveAt }
        }.sortedBy { (revision, _) -> revision }
        if (revisions.size.toLong() != futuresState.revision ||
            revisions.withIndex().any { (index, pair) -> pair.first != index + 1L } ||
            revisions.zipWithNext().any { (previous, next) -> previous.second > next.second } ||
            revisions.any { (_, effectiveAt) -> effectiveAt > futuresState.asOf }
        ) {
            return "선물 roll·배분 원장의 통합 revision·효력시각 계보가 현재 상태와 다릅니다."
        }

        for (record in rolls) {
            val sleeve = stateSleeves[record.sleeveId]
                ?: return "선물 roll 원장에 현재 상태가 없는 sleeve가 있습니다."
            val expectedId = "futures-roll:${benchmarkRef.benchmarkId}:v${benchmarkRef.version}:" +
                "${record.sleeveId}:${record.rollTradingDate}:r${record.revision}"
            if (record.benchmarkRef != benchmarkRef || record.id != expectedId ||
                runCatching { reconstructRoll(record) }.getOrNull() != record ||
                !record.rollTradingDate.let(sleeve.rollCalendar::isTradingDate) ||
                localTradingDate(sleeve.rollCalendar, record.effectiveAt) != record.rollTradingDate
            ) {
                return "선물 roll 원장의 ID·달력·시각·도메인 불변조건이 유효하지 않습니다."
            }
        }
        for (record in allocations) {
            val expectedId = "futures-allocation:${benchmarkRef.benchmarkId}:v${benchmarkRef.version}:" +
                "${record.effectiveAt.epochSeconds}:r${record.revision}"
            val calendars = futuresState.sleeves.mapTo(linkedSetOf(), FuturesSleeveState::rollCalendar)
            val allocationCalendar = calendars.singleOrNull()
                ?: return "다중 달력 선물 basket에는 명시적인 단일 배분 달력이 필요합니다."
            val allocationDate = localTradingDate(allocationCalendar, record.effectiveAt)
            if (record.benchmarkRef != benchmarkRef || record.id != expectedId ||
                runCatching { reconstructAllocation(record) }.getOrNull() != record ||
                record.weightsBefore.keys != stateSleeves.keys ||
                record.weightsAfter.keys != stateSleeves.keys ||
                !isFirstTradingDateOfMonth(allocationCalendar, allocationDate)
            ) {
                return "선물 배분 원장의 ID·sleeve 집합·월초 거래일·도메인 불변조건이 유효하지 않습니다."
            }
            if (terms?.allocationMode == FuturesAllocationMode.STATIC_TARGETS &&
                terms.sleeves.any { sleeveTerms ->
                    !closeEnough(
                        record.weightsAfter.getValue(sleeveTerms.sleeveId),
                        sleeveTerms.targetWeight,
                    )
                }
            ) {
                return "정적 선물 basket의 배분 원장이 약관 목표 비중과 다릅니다."
            }
        }

        for (sleeve in futuresState.sleeves) {
            val sleeveRolls = rolls.filter { record -> record.sleeveId == sleeve.sleeveId }
                .sortedBy(FuturesRollRecord::revision)
            if (sleeveRolls.isEmpty()) {
                if (sleeve.lastRollTradingDate != null ||
                    !closeEnough(sleeve.frontContractWeight, 1.0) ||
                    !closeEnough(sleeve.nextContractWeight, 0.0)
                ) {
                    return "roll 원장이 없는 선물 sleeve의 계약 비중·마지막 날짜가 bootstrap과 다릅니다."
                }
            } else {
                var frontId = sleeveRolls.first().fromContractId
                var nextId = sleeveRolls.first().toContractId
                var frontWeight = 1.0
                var previousRollDate: LocalDate? = null
                for (record in sleeveRolls) {
                    if (record.fromContractId != frontId || record.toContractId != nextId ||
                        !closeEnough(record.frontWeightBefore, frontWeight) ||
                        previousRollDate?.let { previous -> record.rollTradingDate <= previous } == true
                    ) {
                        return "선물 sleeve roll 원장의 계약·비중·거래일 계보가 끊어졌습니다."
                    }
                    if (record.promotedDeferredToFront) {
                        frontId = record.toContractId
                        nextId = requireNotNull(record.successorContractId)
                        frontWeight = 1.0
                    } else {
                        frontWeight = record.frontWeightAfter
                    }
                    previousRollDate = record.rollTradingDate
                }
                if (sleeve.frontContractId != frontId || sleeve.nextContractId != nextId ||
                    !closeEnough(sleeve.frontContractWeight, frontWeight) ||
                    !closeEnough(sleeve.nextContractWeight, 1.0 - frontWeight) ||
                    sleeve.lastRollTradingDate != previousRollDate
                ) {
                    return "선물 sleeve roll 원장 재생 결과가 현재 계약·비중 상태와 다릅니다."
                }
            }
        }

        val latestAllocation = allocations.maxByOrNull(FuturesAllocationRecord::revision)
        val expectedTargets = latestAllocation?.weightsAfter ?: terms?.sleeves?.associate { sleeve ->
            sleeve.sleeveId to sleeve.targetWeight
        }
        if (expectedTargets != null && futuresState.sleeves.any { sleeve ->
                !closeEnough(sleeve.targetWeight, expectedTargets.getValue(sleeve.sleeveId))
            }
        ) {
            return "선물 상태의 목표 비중이 최신 배분 원장 또는 bootstrap 약관과 다릅니다."
        }
    }

    if (state.futuresRollLedger.zipWithNext().any { (previous, next) ->
            previous.effectiveAt > next.effectiveAt ||
                previous.effectiveAt == next.effectiveAt &&
                compareValuesBy(previous, next, FuturesRollRecord::benchmarkRef, FuturesRollRecord::revision) > 0
        } ||
        state.futuresAllocationLedger.zipWithNext().any { (previous, next) ->
            previous.effectiveAt > next.effectiveAt ||
                previous.effectiveAt == next.effectiveAt &&
                compareValuesBy(
                    previous,
                    next,
                    FuturesAllocationRecord::benchmarkRef,
                    FuturesAllocationRecord::revision,
                ) > 0
        }
    ) {
        return "선물 roll·배분 원장의 저장 순서가 효력시각·벤치마크·revision 순서와 다릅니다."
    }
    return null
}

/** 런타임과 같은 순서·반올림 규칙으로 카탈로그의 동적 발행주식 수를 복원한다. */
private fun expectedStocks(
    state: SimulatorUiState,
    catalog: InstrumentCatalogSnapshot,
): List<StockDefinition> {
    val baseStocks = if (state.options.usFractionalTrading) {
        catalog.withUsFractionalTrading()
    } else {
        catalog.definitions
    }
    val stocks = baseStocks.toMutableList()
    val stockIndexById = stocks.mapIndexed { index, stock -> stock.id to index }.toMap()
    state.corporateActionLedger.forEach { action ->
        val index = stockIndexById[action.stockId] ?: return@forEach
        val stock = stocks[index]
        val shares = round(stock.sharesOutstanding.toDouble() * action.quantityMultiplier)
            .toLong()
            .coerceAtLeast(1L)
        stocks[index] = stock.copy(sharesOutstanding = shares)
    }
    return stocks
}

/**
 * 실적 뉴스의 EPS는 발표 당시 주식수에 의존한다. 현재(분할 후) 종목으로 과거 뉴스를
 * 재생성하지 않고, 런타임과 동일한 순차 반올림으로 각 발표 시점의 종목 스냅샷을 복원한다.
 */
private fun canonicalStockSnapshotsAtScheduledReleases(
    state: SimulatorUiState,
    catalog: InstrumentCatalogSnapshot,
): Map<String, List<StockDefinition>> {
    val releases = state.newsEvents
        .filter { event -> event.recordKind == EventRecordKind.SCHEDULED_RELEASE }
        .sortedWith(compareBy(GameEvent::startsAt, GameEvent::id))
    if (releases.isEmpty()) return emptyMap()

    val baseStocks = if (state.options.usFractionalTrading) {
        catalog.withUsFractionalTrading()
    } else {
        catalog.definitions
    }
    val sharesByStockId = baseStocks.associateTo(linkedMapOf()) { stock ->
        stock.id to stock.sharesOutstanding
    }
    val appliedActions = state.corporateActionLedger
        .withIndex()
        .sortedWith(
            compareBy<IndexedValue<CorporateActionRecord>>(
                { indexed -> indexed.value.effectiveAt },
                { indexed -> indexed.index },
            ),
        )
        .map(IndexedValue<CorporateActionRecord>::value)

    val result = linkedMapOf<String, List<StockDefinition>>()
    var actionIndex = 0
    var stockSnapshot = baseStocks
    for (release in releases) {
        var sharesChanged = false
        while (actionIndex < appliedActions.size &&
            appliedActions[actionIndex].effectiveAt <= release.startsAt
        ) {
            val action = appliedActions[actionIndex++]
            val previousShares = sharesByStockId[action.stockId] ?: continue
            sharesByStockId[action.stockId] = round(previousShares.toDouble() * action.quantityMultiplier)
                .toLong()
                .coerceAtLeast(1L)
            sharesChanged = true
        }
        if (sharesChanged) {
            stockSnapshot = baseStocks.map { stock ->
                val shares = sharesByStockId.getValue(stock.id)
                if (shares == stock.sharesOutstanding) stock else stock.copy(sharesOutstanding = shares)
            }
        }
        result[release.id] = stockSnapshot
    }
    return result
}

private fun validateCurrentCorporateReportLineage(
    state: SimulatorUiState,
    scheduledEventEngine: ScheduledEventEngine,
    canonicalStocksByScheduledEventId: Map<String, List<StockDefinition>>,
): String? {
    val earningsNewsByStockId = state.newsEvents
        .filter { event -> event.scheduledEventReference?.kind == ScheduledEventKind.EARNINGS }
        .groupBy { event -> event.affectedStockIds.single() }
        .mapValues { (_, events) -> events.sortedWith(compareBy(GameEvent::startsAt, GameEvent::id)) }

    for (fundamentals in state.corporateFundamentals.values) {
        val canonicalNews = earningsNewsByStockId[fundamentals.stockId].orEmpty()
        val expectedAppliedIds = canonicalNews.map(GameEvent::id)
        if (fundamentals.appliedEarningsOccurrenceIds != expectedAppliedIds) {
            return "${fundamentals.stockId}의 적용 실적 발표 ID가 저장된 canonical 뉴스 시간순과 다릅니다."
        }
        val expectedAsOf = canonicalNews.lastOrNull()?.startsAt ?: GameCalendar.startInstant
        if (fundamentals.asOf != expectedAsOf) {
            return "${fundamentals.stockId}의 재무 기준 시각이 최신 실적 발표 시각과 다릅니다."
        }

        val sourcedReports = fundamentals.quarters.filter { report -> report.sourceOccurrenceId != null }
        val expectedSourceIds = expectedAppliedIds.takeLast(minOf(fundamentals.quarters.size, expectedAppliedIds.size))
        if (sourcedReports.mapNotNull { report -> report.sourceOccurrenceId } != expectedSourceIds) {
            return "${fundamentals.stockId}의 현재 4개 분기 출처가 적용 실적 발표 원장의 최신 tail과 다릅니다."
        }
        val openingReports = fundamentals.quarters.dropLast(sourcedReports.size)
        val expectedOpeningIds = ((4 - openingReports.size + 1)..4)
            .takeIf { openingReports.isNotEmpty() }
            ?.map { index -> "opening-$index" }
            .orEmpty()
        if (openingReports.any { report -> report.sourceOccurrenceId != null } ||
            openingReports.map { report -> report.periodId } != expectedOpeningIds
        ) {
            return "${fundamentals.stockId}의 초기 synthetic 분기와 실제 발표 분기의 위치·ID가 다릅니다."
        }

        for (report in sourcedReports) {
            val occurrenceId = requireNotNull(report.sourceOccurrenceId)
            val news = canonicalNews.singleOrNull { event -> event.id == occurrenceId }
                ?: return "${fundamentals.stockId}의 분기 출처 '$occurrenceId'에 대응하는 실적 뉴스가 없습니다."
            val stocksAtRelease = canonicalStocksByScheduledEventId[occurrenceId]
                ?: return "실적 발표 '$occurrenceId'의 발표 시점 종목 스냅샷이 없습니다."
            val occurrence = scheduledEventEngine.occurrencesBetween(
                from = news.startsAt,
                to = news.startsAt + 1.nanoseconds,
                stocks = stocksAtRelease,
            ).singleOrNull { candidate ->
                candidate.id == occurrenceId && candidate.kind == ScheduledEventKind.EARNINGS
            } ?: return "실적 발표 '$occurrenceId'를 canonical 일정에서 찾을 수 없습니다."
            val emission = scheduledEventEngine.emissionFor(occurrence, stocksAtRelease)
            val eps = emission.outcome.metrics.singleOrNull { metric ->
                metric.kind == ScheduledEventMetricKind.EARNINGS_DILUTED_EPS
            } ?: return "실적 발표 '$occurrenceId'의 canonical EPS가 없습니다."
            val revenue = emission.outcome.metrics.singleOrNull { metric ->
                metric.kind == ScheduledEventMetricKind.EARNINGS_REVENUE
            } ?: return "실적 발표 '$occurrenceId'의 canonical 매출이 없습니다."
            val sharesAtRelease = stocksAtRelease.single { stock -> stock.id == fundamentals.stockId }
                .sharesOutstanding
                .toDouble()
            if (report.periodId != (occurrence.referencePeriod ?: occurrence.id) ||
                report.reportedAt != occurrence.scheduledAt ||
                report.dilutedShares != sharesAtRelease ||
                report.revenue != revenue.actualInBaseUnits.coerceAtLeast(0.0) ||
                report.netIncome != eps.actualInBaseUnits * sharesAtRelease
            ) {
                return "${fundamentals.stockId}의 분기 '$occurrenceId'가 canonical 발표의 기간·시각·주식수·매출·순이익과 다릅니다."
            }
        }
    }
    return null
}

/**
 * 기업행동의 상태는 뉴스 만료 시각이 아니라 공시 → 적용/취소 계보로 검증한다.
 * 모든 대기 항목에는 공시가, 모든 적용 원장에는 공시와 완료 전이가, 상장 종료로
 * 취소된 항목에는 공시와 해당 상장 원장에 묶인 취소 전이가 정확히 하나씩 있어야 한다.
 */
private fun validateCorporateActionNewsLineage(
    state: SimulatorUiState,
    stocksById: Map<String, StockDefinition>,
): String? {
    val pendingById = state.pendingCorporateActions.associateBy { action -> action.id }
    val appliedById = state.corporateActionLedger.associateBy { action -> action.id }
    if (pendingById.keys.any(appliedById::containsKey)) {
        return "같은 기업행동이 대기 원장과 적용 원장에 동시에 존재합니다."
    }

    val references = state.newsEvents.mapNotNull { event ->
        event.corporateActionReference?.let { reference -> event to reference }
    }
    if (references.any { (_, reference) ->
            stocksById[reference.stockId]
                ?.fundProductProfile
                ?.legalStructure == FundLegalStructure.EXCHANGE_TRADED_NOTE
        }
    ) {
        return "현 ETN 계약에는 기업행동 공시·적용·취소 뉴스 계보가 존재할 수 없습니다."
    }
    references.forEach { (event, reference) ->
        reference.semanticInvariantViolation()?.let { violation ->
            return "${event.id}의 기업행동 원장 참조가 유효하지 않습니다: $violation"
        }
    }
    val transitionKeys = references.map { (_, reference) ->
        reference.occurrenceId to reference.transition
    }
    if (transitionKeys.distinct().size != transitionKeys.size) {
        return "같은 기업행동 발생 ID에 동일한 뉴스 전이가 중복되었습니다."
    }

    val referencesByOccurrence = references.groupBy { (_, reference) -> reference.occurrenceId }
    val occurrenceIds = buildSet {
        addAll(pendingById.keys)
        addAll(appliedById.keys)
        addAll(referencesByOccurrence.keys)
    }
    occurrenceIds.forEach { occurrenceId ->
        val lineage = referencesByOccurrence[occurrenceId].orEmpty()
        val announcements = lineage.filter { (_, reference) ->
            reference.transition == CorporateActionNewsTransition.ANNOUNCED
        }
        val applications = lineage.filter { (_, reference) ->
            reference.transition == CorporateActionNewsTransition.APPLIED
        }
        val cancellations = lineage.filter { (_, reference) ->
            reference.transition == CorporateActionNewsTransition.CANCELLED
        }
        if (announcements.size != 1) {
            return "기업행동 $occurrenceId 계보에는 선행 공시 전이가 정확히 하나 필요합니다."
        }
        val (announcementEvent, announcement) = announcements.single()
        val stock = stocksById[announcement.stockId]
            ?: return "기업행동 $occurrenceId 공시에 알 수 없는 종목이 있습니다."
        if (lineage.any { (event, reference) ->
                reference.stockId != stock.id ||
                    event.affectedMarkets != setOf(stock.market) ||
                    event.affectedSectors != setOf(stock.sector) ||
                    event.affectedStockIds != setOf(stock.id) ||
                    event.sourceLabel != reference.source.displayName
            }
        ) {
            return "기업행동 $occurrenceId 뉴스의 종목·시장·산업·출처가 원장과 일치하지 않습니다."
        }
        if (announcementEvent.startsAt != announcement.announcedAt) {
            return "기업행동 $occurrenceId 선행 공시 시각이 원장 참조와 다릅니다."
        }

        val pending = pendingById[occurrenceId]
        val applied = appliedById[occurrenceId]
        when {
            pending != null -> {
                if (applications.isNotEmpty() || cancellations.isNotEmpty() || lineage.size != 1) {
                    return "대기 기업행동 $occurrenceId 계보에 적용 또는 취소 전이가 함께 있습니다."
                }
                announcement.pendingLineageViolation(pending)?.let { violation ->
                    return "기업행동 $occurrenceId 공시가 대기 원장과 일치하지 않습니다: $violation"
                }
            }
            applied != null -> {
                if (applications.size != 1 || cancellations.isNotEmpty() || lineage.size != 2) {
                    return "적용 기업행동 $occurrenceId 계보에는 공시와 완료 전이가 정확히 하나씩 필요합니다."
                }
                announcement.announcementLineageViolation(applied)?.let { violation ->
                    return "기업행동 $occurrenceId 공시가 적용 원장과 일치하지 않습니다: $violation"
                }
                val (applicationEvent, application) = applications.single()
                application.appliedLineageViolation(applied)?.let { violation ->
                    return "기업행동 $occurrenceId 완료 전이가 적용 원장과 일치하지 않습니다: $violation"
                }
                if (applicationEvent.startsAt != applied.effectiveAt) {
                    return "기업행동 $occurrenceId 완료 뉴스가 실제 적용과 동시에 생성되지 않았습니다."
                }
            }
            else -> {
                if (applications.isNotEmpty() || cancellations.size != 1 || lineage.size != 2) {
                    return "원장에서 종료된 기업행동 $occurrenceId 계보에는 취소 전이가 필요합니다."
                }
                val (cancellationEvent, cancellation) = cancellations.single()
                when (cancellation.cancellationReason) {
                    CorporateActionCancellationReason.LISTING_LIFECYCLE -> {
                        val listingEvent = state.listingLifecycleLedger.singleOrNull { ledgerEvent ->
                            ledgerEvent.id == cancellation.cancellingListingEventId &&
                                ledgerEvent.sequence == cancellation.cancellingListingLedgerSequence
                        } ?: return "기업행동 $occurrenceId 취소 전이가 가리키는 상장 원장 이벤트가 없습니다."
                        cancellation.cancellationLineageViolation(
                            announcement,
                            listingEvent,
                        )?.let { violation ->
                            return "기업행동 $occurrenceId 상장 취소 계보가 유효하지 않습니다: $violation"
                        }
                    }
                    CorporateActionCancellationReason.PRODUCT_STATE_INELIGIBLE -> {
                        cancellation.productStateCancellationLineageViolation(announcement)
                            ?.let { violation ->
                                return "기업행동 $occurrenceId 상품 상태 취소 계보가 유효하지 않습니다: $violation"
                            }
                        val productProfile = stock.fundProductProfile
                        val hasIneligibleProductState = when {
                            productProfile?.dailyResetTerms != null ->
                                state.dailyResetStates[stock.id]?.lifecycle != DailyResetLifecycle.ACTIVE
                            productProfile?.optionStrategyTerms != null ->
                                state.optionStrategyStates[stock.id]?.lifecycle != OptionStrategyLifecycle.ACTIVE
                            productProfile?.cashCollateralizedPutSpreadTerms != null ->
                                state.cashCollateralizedPutSpreadStates[stock.id]?.lifecycle !=
                                    CashCollateralizedPutSpreadLifecycle.ACTIVE
                            else -> false
                        }
                        if (!hasIneligibleProductState) {
                            return "기업행동 $occurrenceId 상품 상태 취소에 취소 가능한 종단 운용 상태가 없습니다."
                        }
                    }
                    null -> return "기업행동 $occurrenceId 취소 전이에 취소 사유가 없습니다."
                }
                if (cancellationEvent.startsAt != cancellation.cancelledAt) {
                    return "기업행동 $occurrenceId 취소 뉴스 시각이 취소 전이와 다릅니다."
                }
            }
        }
    }
    return null
}

private fun validateGameEvent(
    event: GameEvent,
    stocksById: Map<String, StockDefinition>,
    listingLifecycleLedger: List<ListingLifecycleLedgerEvent>,
): String? {
    val stockIds = stocksById.keys
    if (event.id.isBlank() || event.title.isBlank() || event.description.isBlank()) {
        return "ID·제목·본문은 비어 있을 수 없습니다."
    }
    if (event.generatorTemplateId?.isBlank() == true) {
        return "${event.id}의 생성 템플릿 ID는 비어 있을 수 없습니다."
    }
    if (event.generatorTemplateId?.let { it !in CURRENT_EVENT_TEMPLATE_IDS } == true) {
        return "${event.id}가 현재 카탈로그에 없는 생성 템플릿을 참조합니다."
    }
    val scope = event.scope as EventScope?
        ?: return "${event.id}의 범위 enum이 유효하지 않습니다."
    val type = event.type as EventType?
        ?: return "${event.id}의 뉴스 유형 enum이 유효하지 않습니다."
    val severity = event.severity as EventSeverity?
        ?: return "${event.id}의 뉴스 중요도 enum이 유효하지 않습니다."
    val recordKind = event.recordKind as EventRecordKind?
        ?: return "${event.id}의 뉴스 기록 유형 enum이 유효하지 않습니다."
    val coveragePolicy = event.impactCoveragePolicy as EventImpactCoveragePolicy?
        ?: return "${event.id}의 영향 커버리지 enum이 유효하지 않습니다."
    if (type !in EventType.entries || severity !in EventSeverity.entries ||
        recordKind !in EventRecordKind.entries || coveragePolicy !in EventImpactCoveragePolicy.entries
    ) {
        return "${event.id}의 뉴스 유형·중요도·기록 유형·영향 커버리지가 유효하지 않습니다."
    }
    if ((recordKind == EventRecordKind.MARKET_ACTION) != (event.marketAction != null)) {
        return "${event.id}의 뉴스 기록 유형과 시장조치 참조가 일치하지 않습니다."
    }
    if ((recordKind == EventRecordKind.SCHEDULED_RELEASE) != (event.scheduledEventReference != null)) {
        return "${event.id}의 정기 발표 기록 유형과 일정 발생 참조가 일치하지 않습니다."
    }
    if ((recordKind == EventRecordKind.CORPORATE_ACTION) != (event.corporateActionReference != null)) {
        return "${event.id}의 기업행동 기록 유형과 원장 참조가 일치하지 않습니다."
    }
    if (event.generatorTemplateId != null && recordKind == EventRecordKind.MARKET_ACTION) {
        return "${event.id}의 런타임 시장조치에 확률 이벤트 템플릿이 연결되었습니다."
    }
    event.corporateActionReference?.let { reference ->
        val transition = reference.transition as CorporateActionNewsTransition?
            ?: return "${event.id}의 기업행동 전이 enum이 유효하지 않습니다."
        val kind = reference.kind as CorporateActionKind?
            ?: return "${event.id}의 기업행동 종류 enum이 유효하지 않습니다."
        val source = reference.source as CorporateActionSource?
            ?: return "${event.id}의 기업행동 출처 enum이 유효하지 않습니다."
        if (transition !in CorporateActionNewsTransition.entries || kind !in CorporateActionKind.entries ||
            source !in CorporateActionSource.entries
        ) {
            return "${event.id}의 기업행동 전이·종류·출처 enum이 유효하지 않습니다."
        }
        reference.semanticInvariantViolation()?.let { violation ->
            return "${event.id}의 기업행동 원장 참조가 유효하지 않습니다: $violation"
        }
        val expectedStart = when (transition) {
            CorporateActionNewsTransition.ANNOUNCED -> reference.announcedAt
            CorporateActionNewsTransition.APPLIED -> reference.appliedAt
            CorporateActionNewsTransition.CANCELLED -> reference.cancelledAt
        }
        if (type != EventType.CORPORATE_ACTION || scope != EventScope.STOCK ||
            event.affectedStockIds != setOf(reference.stockId) || event.startsAt != expectedStart ||
            event.sourceLabel != source.displayName || event.impact != GameEventImpact(ImpactDirection.NEUTRAL)
        ) {
            return "${event.id}의 기업행동 참조와 뉴스 대상·시각·출처·가치중립 영향이 일치하지 않습니다."
        }
    }
    event.instrumentTermination?.let { terms ->
        val terminationKind = terms.kind as InstrumentTerminationKind?
            ?: return "${event.id}의 상품 종료 종류 enum이 유효하지 않습니다."
        val valuationMethod = terms.valuationMethod as InstrumentTerminationValuationMethod?
            ?: return "${event.id}의 상품 종료 평가 방식 enum이 유효하지 않습니다."
        if (terminationKind !in InstrumentTerminationKind.entries ||
            valuationMethod !in InstrumentTerminationValuationMethod.entries
        ) {
            return "${event.id}의 상품 종료 종류·평가 방식이 유효하지 않습니다."
        }
        terms.semanticInvariantViolation()?.let { violation ->
            return "${event.id}의 상품 종료 조건이 유효하지 않습니다: $violation"
        }
        val targetStock = event.affectedStockIds.singleOrNull()?.let(stocksById::get)
        if (recordKind != EventRecordKind.INSTRUMENT_LIFECYCLE ||
            scope != EventScope.STOCK ||
            targetStock == null ||
            !terms.isEligibleFor(targetStock) ||
            terms.effectiveNotBefore?.let { it < event.startsAt } == true ||
            event.listingFinalDispositionHint != null ||
            terms.listingRiskTag in event.listingRiskTags
        ) {
            return "${event.id}의 상품 종료 조건과 뉴스 대상·기록 유형이 일치하지 않습니다."
        }
    }
    event.tradingHaltDirective?.let { directive ->
        val haltKind = directive.kind as EventTradingHaltKind?
            ?: return "${event.id}의 이벤트 거래정지 종류 enum이 유효하지 않습니다."
        val haltReason = directive.reason as TradingHaltReason?
            ?: return "${event.id}의 이벤트 거래정지 사유 enum이 유효하지 않습니다."
        val eligibleMarkets = directive.eligibleMarkets as Set<Market>?
            ?: return "${event.id}의 이벤트 거래정지 대상 시장이 필요합니다."
        val detail = directive.detail as String?
            ?: return "${event.id}의 이벤트 거래정지 안내가 필요합니다."
        if (haltKind !in EventTradingHaltKind.entries || haltReason !in TradingHaltReason.entries ||
            eligibleMarkets.any { (it as Market?) == null } || detail.isBlank()
        ) {
            return "${event.id}의 이벤트 거래정지 지시자 값이 유효하지 않습니다."
        }
        directive.semanticInvariantViolation()?.let { violation ->
            return "${event.id}의 이벤트 거래정지 지시자가 유효하지 않습니다: $violation"
        }
        if (scope != EventScope.STOCK || event.affectedStockIds.isEmpty() ||
            event.affectedStockIds.any { stockId ->
                stocksById[stockId]?.market !in directive.eligibleMarkets
            }
        ) {
            return "${event.id}의 이벤트 거래정지 지시자와 뉴스 대상이 일치하지 않습니다."
        }
    }
    val direction = event.impact.direction as ImpactDirection?
        ?: return "${event.id}의 영향 방향 enum이 유효하지 않습니다."
    if (direction !in ImpactDirection.entries) return "${event.id}의 영향 방향이 유효하지 않습니다."
    if (event.durationHours <= 0 || event.effectDurationHours <= 0) {
        return "${event.id}의 뉴스·실제 반영 기간은 양수여야 합니다."
    }
    if (event.sourceLabel.isBlank()) return "${event.id}의 출처 표시는 비어 있을 수 없습니다."
    val marketRegimeSnapshot = event.marketRegimeSnapshot as CausalMarketRegimeSnapshot?
        ?: return "${event.id}의 시장 국면 스냅샷이 없습니다."
    val marketHourlyReturns: Map<*, *>? = marketRegimeSnapshot.marketHourlyReturns
    if (marketHourlyReturns == null) return "${event.id}의 시장별 시간 수익률이 없습니다."
    val marketChangeFromPreviousClose: Map<*, *>? = marketRegimeSnapshot.marketChangeFromPreviousClose
    if (marketChangeFromPreviousClose == null) {
        return "${event.id}의 시장별 전일 종가 대비 수익률이 없습니다."
    }
    if (!marketRegimeSnapshot.riskSentiment.isFinite() ||
        marketRegimeSnapshot.riskSentiment !in -1.0..1.0 ||
        !marketRegimeSnapshot.volatilityRegime.isFinite() ||
        marketRegimeSnapshot.volatilityRegime !in 0.1..10.0 ||
        !marketRegimeSnapshot.usdKrwChangeRate.isFinite() ||
        marketRegimeSnapshot.usdKrwChangeRate !in -0.25..0.25 ||
        marketHourlyReturns.any { (market, value) ->
            market !is Market || market !in Market.entries || value !is Double || !value.isFinite()
        } ||
        marketChangeFromPreviousClose.any { (market, value) ->
            market !is Market || market !in Market.entries || value !is Double || !value.isFinite()
        }
    ) {
        return "${event.id}의 시장 국면 스냅샷 값이 유효하지 않습니다."
    }
    if (coveragePolicy == EventImpactCoveragePolicy.EXPLICIT_PATHS_ONLY &&
        event.impactInsights.isEmpty() && event.causalSignals.isEmpty()
    ) {
        return "${event.id}의 명시 경로 전용 정책에는 영향 분석 또는 인과 신호가 필요합니다."
    }
    if (event.effectStartsAt < event.startsAt) {
        return "${event.id}의 실제 반영 시작이 발표보다 빠릅니다."
    }
    val effectWindowValid = runCatching { event.effectEndsAt <= event.endsAt }.getOrDefault(false)
    if (!effectWindowValid) return "${event.id}의 실제 반영 구간이 뉴스 구간을 벗어납니다."
    if (event.impact.shockReturn <= -1.0 || !event.impact.shockReturn.isFinite() ||
        !event.impact.hourlyDrift.isFinite() ||
        !event.impact.volatilityMultiplier.isFinite() || event.impact.volatilityMultiplier < 0.0 ||
        !event.impact.volumeMultiplier.isFinite() || event.impact.volumeMultiplier < 0.0 ||
        !event.impact.liquidityMultiplier.isFinite() || event.impact.liquidityMultiplier < 0.0 ||
        !event.impact.sentiment.isFinite() || event.impact.sentiment !in -1.0..1.0
    ) {
        return "${event.id}의 가격 영향 계수가 유효하지 않습니다."
    }
    if (event.affectedStockIds.any { it.isBlank() || it !in stockIds }) {
        return "${event.id}에 알 수 없거나 빈 대상 종목 ID가 있습니다."
    }
    if (scope in setOf(EventScope.COUNTRY, EventScope.MARKET) && event.affectedMarkets.isEmpty() ||
        scope == EventScope.COUNTRY && event.affectedMarkets.map(Market::countryName).distinct().size != 1 ||
        scope == EventScope.SECTOR && event.affectedSectors.isEmpty() ||
        scope == EventScope.STOCK && event.affectedStockIds.isEmpty()
    ) {
        return "${event.id}의 범위와 대상이 일치하지 않습니다."
    }
    event.impactInsights.forEachIndexed { index, insight ->
        val targetKind = insight.targetKind as EventImpactTargetKind?
            ?: return "${event.id} 분석 $index 대상 enum이 유효하지 않습니다."
        val insightDirection = insight.direction as ImpactDirection?
            ?: return "${event.id} 분석 $index 방향 enum이 유효하지 않습니다."
        val horizon = insight.horizon as EventImpactHorizon?
            ?: return "${event.id} 분석 $index 시간축 enum이 유효하지 않습니다."
        if (insightDirection !in ImpactDirection.entries || horizon !in EventImpactHorizon.entries) {
            return "${event.id} 분석 $index enum이 유효하지 않습니다."
        }
        if (insight.targetLabel.isBlank() || insight.rationale.isBlank() ||
            !insight.relativeSensitivity.isFinite() || insight.relativeSensitivity !in 0.0..3.0 ||
            insight.relativeSensitivity == 0.0
        ) {
            return "${event.id} 분석 ${index}의 이름·근거·민감도가 유효하지 않습니다."
        }
        if (insight.stockId?.let { it.isBlank() || it !in stockIds } == true) {
            return "${event.id} 분석 ${index}에 알 수 없는 종목 ID가 있습니다."
        }
        val targetIsValid = when (targetKind) {
            EventImpactTargetKind.MARKET -> insight.markets.isNotEmpty() && insight.sector == null &&
                insight.industrySegment == null && insight.stockId == null
            EventImpactTargetKind.INDUSTRY -> insight.sector != null && insight.industrySegment == null &&
                insight.stockId == null
            EventImpactTargetKind.INDUSTRY_SEGMENT -> insight.sector != null &&
                insight.industrySegment?.parentSector == insight.sector && insight.stockId == null
            EventImpactTargetKind.STOCK -> insight.stockId != null && insight.industrySegment == null
        }
        if (!targetIsValid) return "${event.id} 분석 ${index}의 대상 조합이 유효하지 않습니다."
    }
    if (event.causalSignals.map { it.factor }.distinct().size != event.causalSignals.size) {
        return "${event.id}에 같은 경제 요인의 인과 신호가 중복되었습니다."
    }
    event.causalSignals.forEachIndexed { index, signal ->
        val factor = signal.factor as CausalEconomicFactor?
            ?: return "${event.id} 인과 신호 $index 경제 요인 enum이 유효하지 않습니다."
        val signalDirection = signal.direction as CausalSignalDirection?
            ?: return "${event.id} 인과 신호 $index 방향 enum이 유효하지 않습니다."
        val transmissionProfile = signal.transmissionProfile as CausalTransmissionProfile?
            ?: return "${event.id} 인과 신호 $index 전염 프로필 enum이 유효하지 않습니다."
        if (
            factor !in CausalEconomicFactor.entries ||
            signalDirection !in CausalSignalDirection.entries ||
            transmissionProfile !in CausalTransmissionProfile.entries
        ) {
            return "${event.id} 인과 신호 $index enum이 유효하지 않습니다."
        }
        if (!signal.strength.isFinite() || signal.strength !in MIN_CAUSAL_SIGNAL_STRENGTH..1.0 ||
            !signal.confidence.isFinite() || signal.confidence <= 0.0 || signal.confidence > 1.0
        ) {
            return "${event.id} 인과 신호 $index 강도·신뢰도가 유효하지 않습니다."
        }
    }
    if (
        event.scope in setOf(EventScope.COUNTRY, EventScope.MARKET) &&
        event.causalSignals.map { it.transmissionProfile }.distinct().size > 1
    ) {
        return "${event.id} 국가·시장 이벤트에 서로 다른 전염 프로필이 혼합되었습니다."
    }
    if (event.reportedFacts.any { fact ->
            fact.label.isBlank() || fact.actual.isBlank() || fact.comparison?.isBlank() == true
        }
    ) {
        return "${event.id}의 발표 수치 이름·실제값·비교값이 유효하지 않습니다."
    }
    event.marketAction?.let { action ->
        val kind = action.kind as MarketActionKind?
            ?: return "${event.id}의 시장조치 종류 enum이 유효하지 않습니다."
        val transition = action.transition as MarketActionTransition?
            ?: return "${event.id}의 시장조치 전이 enum이 유효하지 않습니다."
        if (kind !in MarketActionKind.entries || transition !in MarketActionTransition.entries) {
            return "${event.id}의 시장조치 enum이 유효하지 않습니다."
        }
        action.semanticInvariantViolation()?.let { violation ->
            return "${event.id}의 시장조치가 유효하지 않습니다: $violation"
        }
        if (action.announcedAt != event.startsAt ||
            action.stockId?.let { it !in event.affectedStockIds } == true ||
            !event.affectedMarkets.containsAll(action.markets)
        ) {
            return "${event.id}의 시장조치 참조가 뉴스의 발표 시각·직접 대상과 일치하지 않습니다."
        }
        val actionStock = action.stockId?.let(stocksById::get)
        if (actionStock != null && actionStock.market !in action.markets) {
            return "${event.id}의 시장조치 시장이 실제 종목 상장시장과 일치하지 않습니다."
        }
        if (action.stockId?.let { it.isBlank() || it !in stockIds } == true) {
            return "${event.id}의 시장조치 대상 종목이 유효하지 않습니다."
        }
        if (kind == MarketActionKind.LISTING_LIFECYCLE && listingLifecycleLedger.none { ledgerEvent ->
                ledgerEvent.id == action.occurrenceId &&
                    ledgerEvent.stockId == action.stockId &&
                    ledgerEvent.sequence == action.listingLedgerSequence &&
                    ledgerEvent.toStatus == action.listingStatus
            }
        ) {
            return "${event.id}의 상장 원장 참조와 실제 원장 전이가 일치하지 않습니다."
        }
    }
    return null
}
