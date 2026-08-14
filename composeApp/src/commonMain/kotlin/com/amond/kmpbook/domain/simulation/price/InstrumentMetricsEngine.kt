package com.amond.kmpbook.domain.simulation.price

import com.amond.kmpbook.domain.model.corporateaction.CorporateFundamentalState
import com.amond.kmpbook.domain.model.instrument.FundFinancialState
import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.MAX_FUND_REFERENCE_VALUE
import com.amond.kmpbook.domain.model.instrument.MIN_FUND_REFERENCE_VALUE
import com.amond.kmpbook.domain.model.instrument.QuarterlyFinancialReport
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.schedule.ScheduledEventEmission
import com.amond.kmpbook.domain.model.schedule.ScheduledEventKind
import com.amond.kmpbook.domain.model.schedule.ScheduledEventMetricKind
import com.amond.kmpbook.presentation.metrics.InstrumentMetricsProjection
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * 느린 원시 회계 상태를 갱신하며 시장가격과 화면용 파생 지표는 소유하지 않는다.
 *
 * 기업은 분기 원장과 자본, 개방형 ETF는 좌당 기준가·좌수·최근 순유량만
 * 저장한다. PER·ROE·AUM·괴리율은 [InstrumentMetricsProjection]이 이 상태와 현재
 * 호가를 결합해 다시 계산한다. ETF 기준가는 추적오차를 포함한 공정가치 기여분만 소비하며,
 * 미시구조와 시장가격 괴리는 별도 가격 계층에 남는다.
 *
 * 실적은 발생 ID 원장으로 재적용을 무해화하고, 좌수 조정은 엄격히 증가하는
 * 기업행동 회계 순번으로 재실행을 차단한다. 일반 ETF 유량 난수는 종목과 시각으로
 * 키를 고정하며, 이벤트 유량은 런타임이 휴장 중 보관했다가 다음 거래 구간에
 * 한 번 소비한다. 현재 런타임의 이 유량 경로는 개방형 ETF만 대상으로 하며,
 * ETN의 일반 발행·취소·상환 유량을 생성하지 않는다.
 */
class InstrumentMetricsEngine(private val seed: Long) {
    fun initialCorporateState(stock: StockDefinition, at: Instant): CorporateFundamentalState {
        require(stock.hasCorporateEarnings)
        val (initialPer, initialPsr, initialRoe) = valuationAnchors(stock.sector)
        val initialMarketCapitalization = stock.initialPrice * stock.sharesOutstanding.toDouble()
        val trailingNetIncome = initialMarketCapitalization / initialPer
        val trailingRevenue = initialMarketCapitalization / initialPsr
        val bookEquity = trailingNetIncome / initialRoe
        val quarterWeights = listOf(0.24, 0.25, 0.24, 0.27)
        val quarters = quarterWeights.mapIndexed { index, weight ->
            QuarterlyFinancialReport(
                periodId = "opening-${index + 1}",
                reportedAt = at - (360 - index * 90).days,
                revenue = trailingRevenue * weight,
                netIncome = trailingNetIncome * weight,
                dilutedShares = stock.sharesOutstanding.toDouble(),
            )
        }
        return CorporateFundamentalState(
            stockId = stock.id,
            quarters = quarters,
            bookEquity = bookEquity,
            equityAtTtmStart = bookEquity,
            appliedEarningsOccurrenceIds = emptyList(),
            asOf = at,
        )
    }

    fun initialFundState(stock: StockDefinition, at: Instant): FundFinancialState {
        require(stock.isFundLike)
        return FundFinancialState(
            stockId = stock.id,
            navPerUnit = stock.initialPrice,
            indicativeValuePerUnit = stock.initialPrice,
            unitsOrNotesOutstanding = stock.sharesOutstanding.toDouble(),
            lastNetFlow = 0.0,
            asOf = at,
        )
    }

    /** 발생 ID가 이미 원장에 있으면 아무것도 하지 않아 저장 직후 재진입도 정확히 한 번만 반영한다. */
    fun applyEarnings(
        state: CorporateFundamentalState,
        stock: StockDefinition,
        emission: ScheduledEventEmission,
    ): CorporateFundamentalState {
        require(state.stockId == stock.id && stock.hasCorporateEarnings)
        require(emission.occurrence.kind == ScheduledEventKind.EARNINGS)
        require(emission.occurrence.affectedStockIds == setOf(stock.id))
        val occurrenceId = emission.occurrence.id
        if (occurrenceId in state.appliedEarningsOccurrenceIds) return state
        require(emission.occurrence.scheduledAt >= state.asOf) {
            "기업 재무 원장보다 과거인 실적 발표를 적용할 수 없습니다."
        }

        val eps = emission.outcome.metrics.singleOrNull {
            it.kind == ScheduledEventMetricKind.EARNINGS_DILUTED_EPS
        } ?: error("실적 발표에 희석 EPS가 없습니다: $occurrenceId")
        val revenue = emission.outcome.metrics.singleOrNull {
            it.kind == ScheduledEventMetricKind.EARNINGS_REVENUE
        } ?: error("실적 발표에 매출이 없습니다: $occurrenceId")
        val dilutedShares = stock.sharesOutstanding.toDouble()
        val report = QuarterlyFinancialReport(
            periodId = emission.occurrence.referencePeriod ?: occurrenceId,
            reportedAt = emission.occurrence.scheduledAt,
            revenue = revenue.actualInBaseUnits.coerceAtLeast(0.0),
            netIncome = eps.actualInBaseUnits * dilutedShares,
            dilutedShares = dilutedShares,
            sourceOccurrenceId = occurrenceId,
        )
        val removedQuarter = state.quarters.first()
        val payoutRatio = (stock.dividendYield * valuationAnchors(stock.sector).first)
            .coerceIn(0.0, MAX_PAYOUT_RATIO)
        val retainedIncome = report.netIncome * (1.0 - payoutRatio)
        val equityFloor = stock.marketCap * MIN_EQUITY_TO_INITIAL_MARKET_CAP
        val nextBookEquity = (state.bookEquity + retainedIncome).coerceAtLeast(equityFloor)
        val nextTtmStartEquity = (
            state.equityAtTtmStart + removedQuarter.netIncome * (1.0 - payoutRatio)
            ).coerceIn(equityFloor, nextBookEquity.coerceAtLeast(equityFloor))
        return CorporateFundamentalState(
            stockId = stock.id,
            quarters = state.quarters.drop(1) + report,
            bookEquity = nextBookEquity,
            equityAtTtmStart = nextTtmStartEquity,
            appliedEarningsOccurrenceIds = state.appliedEarningsOccurrenceIds + occurrenceId,
            asOf = emission.occurrence.scheduledAt,
        )
    }

    /**
     * 공정가치 수익은 좌당 NAV·지표가치에, 설정·환매는 좌수·발행수량에만 반영한다.
     * [externalFlowRate]는 ETF 자금 유입·환매 사건의 일회성 비율이며 일반 시간당 흐름과 합성한다.
     * 현재 런타임은 이 메서드를 개방형 ETF에만 호출한다. ETN은 별도 계약 상태를
     * 사용하며 평상시 notes outstanding 발행·취소 입력은 런타임에서 주입되지 않는다.
     */
    fun advanceFund(
        state: FundFinancialState,
        stock: StockDefinition,
        /** 이번 유효 거래 구간에서 새로 발생한 공정가치 로그수익률. */
        fairValueLogReturn: Double,
        /** 거래정지·폐장 중 이미 시간별 제한을 거쳐 원장에 쌓인 공정가치 로그수익률. */
        carriedFairValueLogReturn: Double = 0.0,
        tradingFraction: Double,
        riskSentiment: Double,
        externalFlowRate: Double,
        at: Instant,
    ): FundFinancialState {
        require(state.stockId == stock.id && stock.isFundLike)
        require(fairValueLogReturn.isFinite())
        require(carriedFairValueLogReturn.isFinite())
        require(tradingFraction in 0.0..1.0)
        require(riskSentiment in -1.0..1.0)
        require(externalFlowRate.isFinite())
        require(at >= state.asOf)

        val isEtn = stock.instrumentType == InstrumentType.ETN
        val previousReference = if (isEtn) state.indicativeValuePerUnit else state.navPerUnit
        val totalFairValueLogReturn =
            fairValueLogReturn.coerceIn(-MAX_REFERENCE_LOG_MOVE, MAX_REFERENCE_LOG_MOVE) +
                carriedFairValueLogReturn
        val boundedReferenceLog = (ln(previousReference) + totalFairValueLogReturn)
            .coerceIn(ln(MIN_FUND_REFERENCE_VALUE), ln(MAX_FUND_REFERENCE_VALUE))
        // 시장가격에는 미시구조·추적오차가 섞인다. 이를 NAV/지표가치에 역산하면 괴리가
        // 공정가치로 누적되므로, 기준가 계층은 분리된 fair-value attribution만 소비한다.
        val nextReference = exp(boundedReferenceLog)

        val previousAssets = previousReference * state.unitsOrNotesOutstanding
        val anchorAssets = stock.marketCap
        val sizeReversion = ln(anchorAssets / previousAssets) * SIZE_REVERSION_PER_TRADING_HOUR
        val momentumFlow = totalFairValueLogReturn.coerceIn(-0.15, 0.15) *
            NAV_MOMENTUM_FLOW_LOADING
        val leverage = stock.etfProfile?.leverage ?: 1.0
        val riskLoading = if (leverage < 0.0) -0.35 else stock.beta.coerceIn(0.1, 1.8)
        val sentimentFlow = riskSentiment * riskLoading * SENTIMENT_FLOW_SCALE
        val expenseRatio = requireNotNull(stock.etfProfile).annualExpenseRatio
        val feeAttractiveness = (FEE_NEUTRAL_RATE - expenseRatio) * FEE_FLOW_LOADING
        val keyed = DeterministicRandom.keyed(seed, "fund-flow:${stock.id}:${at.epochSeconds}")
        val noise = keyed.nextGaussian() * FLOW_NOISE * sqrt(tradingFraction)
        val organicRate = if (
            tradingFraction == 0.0 || stock.instrumentType == InstrumentType.CLOSED_END_FUND
        ) {
            0.0
        } else {
            (
                (sizeReversion + sentimentFlow + feeAttractiveness) * tradingFraction +
                    momentumFlow + noise
                )
                .coerceIn(-MAX_ORGANIC_FLOW_RATE, MAX_ORGANIC_FLOW_RATE)
        }
        val totalFlowRate = (organicRate + externalFlowRate)
            .coerceIn(-MAX_TOTAL_FLOW_RATE, MAX_TOTAL_FLOW_RATE)
        val unboundedUnits = state.unitsOrNotesOutstanding * exp(totalFlowRate)
        val unitAnchor = stock.sharesOutstanding.toDouble()
        val nextUnits = unboundedUnits.coerceIn(
            unitAnchor * MIN_UNITS_TO_CATALOG_FLOAT,
            unitAnchor * MAX_UNITS_TO_CATALOG_FLOAT,
        )
        val netFlow = (nextUnits - state.unitsOrNotesOutstanding) * nextReference
        return FundFinancialState(
            stockId = stock.id,
            navPerUnit = if (isEtn) state.navPerUnit else nextReference,
            indicativeValuePerUnit = if (isEtn) nextReference else nextReference,
            unitsOrNotesOutstanding = nextUnits,
            lastNetFlow = netFlow,
            cumulativeUnitAdjustmentFactor = state.cumulativeUnitAdjustmentFactor,
            lastCorporateActionAccountingSequence = state.lastCorporateActionAccountingSequence,
            asOf = at,
        )
    }

    /** 분할·병합은 좌당 가치와 좌수를 반대로 조정해 AUM·발행잔액을 보존한다. */
    fun applyFundUnitAdjustment(
        state: FundFinancialState,
        quantityMultiplier: Double,
        corporateActionAccountingSequence: Long,
        at: Instant,
    ): FundFinancialState {
        require(quantityMultiplier.isFinite() && quantityMultiplier > 0.0)
        require(corporateActionAccountingSequence > 0L)
        require(
            state.lastCorporateActionAccountingSequence == null ||
                corporateActionAccountingSequence > state.lastCorporateActionAccountingSequence,
        )
        require(at >= state.asOf)
        return state.copy(
            navPerUnit = state.navPerUnit / quantityMultiplier,
            indicativeValuePerUnit = state.indicativeValuePerUnit / quantityMultiplier,
            unitsOrNotesOutstanding = state.unitsOrNotesOutstanding * quantityMultiplier,
            lastNetFlow = 0.0,
            cumulativeUnitAdjustmentFactor =
                state.cumulativeUnitAdjustmentFactor * quantityMultiplier,
            lastCorporateActionAccountingSequence = corporateActionAccountingSequence,
            asOf = at,
        )
    }

    /** 현금 분배락은 시장가격과 같은 좌당 금액만큼 NAV·ETN 지표가치에서도 제거한다. */
    fun applyFundDistribution(
        state: FundFinancialState,
        grossPerUnit: Double,
        at: Instant,
    ): FundFinancialState {
        require(grossPerUnit.isFinite() && grossPerUnit >= 0.0)
        require(at >= state.asOf)
        return state.copy(
            navPerUnit = (state.navPerUnit - grossPerUnit).coerceAtLeast(MIN_FUND_REFERENCE_VALUE),
            indicativeValuePerUnit = (state.indicativeValuePerUnit - grossPerUnit)
                .coerceAtLeast(MIN_FUND_REFERENCE_VALUE),
            asOf = at,
        )
    }

    private fun valuationAnchors(sector: Sector): Triple<Double, Double, Double> = when (sector) {
        Sector.SEMICONDUCTOR -> Triple(24.0, 5.0, 0.19)
        Sector.INFORMATION_TECHNOLOGY, Sector.INTERNET_PLATFORM -> Triple(26.0, 5.5, 0.18)
        Sector.COMMUNICATION_SERVICES -> Triple(20.0, 3.2, 0.15)
        Sector.CONSUMER_DISCRETIONARY -> Triple(22.0, 2.0, 0.15)
        Sector.CONSUMER_STAPLES -> Triple(20.0, 1.8, 0.16)
        Sector.FINANCIALS -> Triple(11.0, 2.2, 0.12)
        Sector.HEALTHCARE_BIO -> Triple(28.0, 5.0, 0.14)
        Sector.AUTOMOTIVE -> Triple(12.0, 0.8, 0.13)
        Sector.INDUSTRIALS -> Triple(18.0, 1.6, 0.14)
        Sector.AEROSPACE_DEFENSE -> Triple(22.0, 2.5, 0.15)
        Sector.ENERGY -> Triple(13.0, 1.4, 0.15)
        Sector.MATERIALS_CHEMICALS -> Triple(15.0, 1.2, 0.12)
        Sector.BATTERY -> Triple(28.0, 3.8, 0.12)
        Sector.ROBOTICS -> Triple(30.0, 4.0, 0.13)
        Sector.ENTERTAINMENT -> Triple(24.0, 3.0, 0.14)
        Sector.GAMING -> Triple(25.0, 4.0, 0.15)
        Sector.RETAIL_ECOMMERCE -> Triple(18.0, 0.8, 0.14)
        Sector.TRANSPORTATION_LOGISTICS -> Triple(14.0, 1.1, 0.13)
        Sector.UTILITIES -> Triple(15.0, 1.6, 0.10)
        Sector.REAL_ESTATE -> Triple(16.0, 4.0, 0.11)
        Sector.CONGLOMERATE -> Triple(13.0, 0.9, 0.10)
        Sector.OTHER -> Triple(18.0, 1.5, 0.12)
    }

    companion object {
        const val STREAM_ID: Long = 0x4D45545249435331L
        private const val MAX_PAYOUT_RATIO: Double = 0.85
        private const val MIN_EQUITY_TO_INITIAL_MARKET_CAP: Double = 0.03
        private const val MAX_REFERENCE_LOG_MOVE: Double = 0.35
        private const val SIZE_REVERSION_PER_TRADING_HOUR: Double = 0.00020
        private const val NAV_MOMENTUM_FLOW_LOADING: Double = 0.030
        private const val SENTIMENT_FLOW_SCALE: Double = 0.00006
        private const val FEE_NEUTRAL_RATE: Double = 0.003
        private const val FEE_FLOW_LOADING: Double = 0.002
        private const val FLOW_NOISE: Double = 0.00012
        private const val MAX_ORGANIC_FLOW_RATE: Double = 0.0015
        private const val MAX_TOTAL_FLOW_RATE: Double = 0.20
        private const val MIN_UNITS_TO_CATALOG_FLOAT: Double = 0.02
        private const val MAX_UNITS_TO_CATALOG_FLOAT: Double = 50.0
    }
}
