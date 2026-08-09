package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.CorporateActionRecord
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GamePhase
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.domain.model.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.ListingLifecycleState
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.Order
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PortfolioSnapshot
import com.amond.kmpbook.domain.model.PendingCorporateAction
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.model.ScheduledEventOccurrence
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.domain.model.Trade
import com.amond.kmpbook.domain.model.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.simulation.EventEngineSnapshot
import com.amond.kmpbook.domain.simulation.ExternalMarketForces
import com.amond.kmpbook.domain.simulation.MacroEnvironment
import com.amond.kmpbook.domain.simulation.OrderBookSnapshot
import com.amond.kmpbook.domain.simulation.DeterministicRandom
import com.amond.kmpbook.domain.simulation.ScheduledEventEngine
import com.amond.kmpbook.domain.tax.AnnualTaxLedger
import com.amond.kmpbook.domain.tax.FeeBreakdown
import com.amond.kmpbook.domain.tax.FifoCostBasisBook
import com.amond.kmpbook.domain.tax.TaxBreakdown
import com.amond.kmpbook.domain.tax.TaxLiabilityStatus
import com.amond.kmpbook.domain.tax.StockGainTaxTreatment
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round
import kotlin.time.Instant

data class NewGameOptions(
    val initialCapitalKrw: Double = 100_000_000.0,
    val seed: Long = DEFAULT_SEED,
    val usFractionalTrading: Boolean = false,
    val autoExchange: Boolean = true,
    val initialUsdKrw: Double = 1_350.0,
    /** 2026년 8월 기본 시나리오이자 게임 시작 시 동역학 엔진에 주입할 목표 환경. */
    val initialExternalMarketForces: ExternalMarketForces = ExternalMarketForces(),
) {
    init {
        require(initialCapitalKrw >= MIN_INITIAL_CAPITAL_KRW && initialCapitalKrw.isFinite()) {
            "초기 자금은 100만원 이상이어야 합니다."
        }
        require(initialUsdKrw > 0.0 && initialUsdKrw.isFinite()) {
            "초기 원·달러 환율은 0보다 커야 합니다."
        }
    }

    companion object {
        const val MIN_INITIAL_CAPITAL_KRW: Double = 1_000_000.0
        const val DEFAULT_SEED: Long = 20_260_807L
    }
}
