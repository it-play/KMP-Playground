package com.amond.kmpbook.persistence

import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.presentation.SimulatorUiState

private val TERMINAL_LISTING_STATUSES: Set<ListingLifecycleStatus> = setOf(
    ListingLifecycleStatus.DELISTED,
    ListingLifecycleStatus.TERMINATED,
)

internal fun validateSimulatorUiState(state: SimulatorUiState): String? {
    if (state.turn < 0L) return "턴 번호가 음수입니다."
    if (state.nextSequence < 0L) return "다음 원장 시퀀스가 음수입니다."
    if (state.eventEngineSnapshot.sequence < 0L) return "이벤트 엔진 시퀀스가 음수입니다."
    if (state.stocks.map { it.id }.distinct().size != state.stocks.size) return "종목 ID가 중복되었습니다."
    val stocksById = state.stocks.associateBy { it.id }
    val stockIds = stocksById.keys
    if (state.selectedStockId != null && state.stocks.none { it.id == state.selectedStockId }) {
        return "선택 종목이 종목 목록에 없습니다."
    }
    if (state.cashByCurrency.values.any { !it.isFinite() || it < 0.0 }) return "현금 잔액이 유효하지 않습니다."
    if (state.holdings.any { (id, holding) -> id != holding.stockId }) return "보유 종목 맵 키가 일치하지 않습니다."
    if (state.quotes.any { (id, quote) -> id != quote.stockId }) return "시세 맵 키가 일치하지 않습니다."
    if (state.priceHistory.any { (id, bars) -> bars.any { it.stockId != id } }) {
        return "가격 히스토리 종목 키가 일치하지 않습니다."
    }
    if (state.portfolioSnapshots.any { snapshot ->
            snapshot.holdingCostBasisKrw.keys != snapshot.holdings.mapTo(linkedSetOf()) { it.stockId } ||
                snapshot.holdingCostBasisKrw.values.any { !it.isFinite() || it < 0.0 }
        }
    ) {
        return "포트폴리오 스냅샷에 모든 보유 종목의 FIFO 원가가 필요합니다."
    }
    if (state.pendingEtfReferenceReturns.any { (stockId, returnRate) ->
            stockId !in stockIds || !returnRate.isFinite()
        }
    ) {
        return "ETF 기초시장 이월 수익률이 유효하지 않습니다."
    }
    if (state.pendingClosedEventLogReturns.any { (stockId, logReturn) ->
            stockId !in stockIds || !logReturn.isFinite()
        }
    ) {
        return "폐장 중 이벤트 이월 수익률이 유효하지 않습니다."
    }
    val requiredIndexIds = MarketIndexId.entries.toSet()
    if (state.marketIndices.keys != requiredIndexIds ||
        state.marketIndices.any { (id, snapshot) -> id != snapshot.id }
    ) {
        return "대표 지수 현재값에 필수 지수가 없거나 맵 키와 지수 ID가 일치하지 않습니다."
    }
    if (state.marketIndexHistory.keys != requiredIndexIds ||
        state.marketIndexHistory.any { (id, values) ->
            values.isEmpty() || values.any { it.id != id || it.timestamp > state.currentTime } ||
                values.zipWithNext().any { (previous, next) -> previous.timestamp >= next.timestamp }
        }
    ) {
        return "대표 지수 이력에 필수 지수가 없거나 시간·ID 순서가 올바르지 않습니다."
    }
    if (state.annualTaxLedgers.any { (year, ledger) -> year != ledger.taxYear }) {
        return "연간 세금 원장의 연도 키가 일치하지 않습니다."
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
    if (state.pendingTaxSettlementTradeIds.any { it !in tradeIds }) {
        return "미결제 세무 환율 원장에 알 수 없는 체결이 있습니다."
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
        state.corporateActionLedger.any { it.stockId !in stockIds }
    ) {
        return "적용 기업행동 원장의 ID 또는 종목이 유효하지 않습니다."
    }
    if (state.dividendLedger.map { it.id }.distinct().size != state.dividendLedger.size ||
        state.dividendLedger.any { entry ->
            entry.stockId !in stockIds || !entry.taxableIncomeAmount.isFinite() ||
                !entry.returnOfCapitalAmount.isFinite() || entry.taxableIncomeAmount < 0.0 ||
                entry.returnOfCapitalAmount < 0.0 || entry.excessReturnOfCapitalGainKrw < 0L
        }
    ) {
        return "분배 원장의 ID·종목·과세소득·원금환급 금액이 유효하지 않습니다."
    }
    val accountingSequences = buildList {
        state.trades.mapTo(this) { it.accountingSequence }
        state.dividendLedger.mapTo(this) { it.accountingSequence }
        state.corporateActionLedger.mapTo(this) { it.accountingSequence }
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

    val lifecycleLedger = state.listingLifecycleLedger
    if (lifecycleLedger.any { it.stockId !in stockIds }) {
        return "상장 생명주기 원장에 알 수 없는 종목 ID가 있습니다."
    }
    if (lifecycleLedger.any { it.sequence <= 0L }) return "상장 생명주기 원장 시퀀스가 양수가 아닙니다."
    if (lifecycleLedger.map { it.id }.distinct().size != lifecycleLedger.size) {
        return "상장 생명주기 원장 이벤트 ID가 중복되었습니다."
    }
    val ledgerByStock = lifecycleLedger.groupBy { it.stockId }
    if (ledgerByStock.values.any { events ->
            events.map { it.sequence }.distinct().size != events.size ||
                events.zipWithNext().any { (previous, next) -> previous.sequence >= next.sequence }
        }
    ) {
        return "상장 생명주기 원장 시퀀스가 종목별로 중복되었거나 순서가 잘못되었습니다."
    }
    if (listings.any { (stockId, listing) ->
            val lastLedgerEvent = ledgerByStock[stockId]?.lastOrNull()
            listing.ledgerSequence != (lastLedgerEvent?.sequence ?: 0L) ||
                lastLedgerEvent != null && listing.status != lastLedgerEvent.toStatus
        }
    ) {
        return "상장 생명주기 상태와 원장의 마지막 시퀀스·상태가 일치하지 않습니다."
    }

    val protection = state.tradingProtectionSnapshot
    val krxMarkets = Market.entries.filter(Market::isKorean).toSet()
    val krxStockIds = stocksById.filterValues { it.market.isKorean }.keys
    val usStockIds = stocksById.filterValues { it.market.isUnitedStates }.keys
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
            stockId != protectionState.stockId || stockId !in stockIds
        }
    ) {
        return "종목 거래정지 맵 키와 종목 ID가 일치하지 않습니다."
    }
    if (protection.scheduledInstrumentTradingHalts.any { (scheduleId, protectionState) ->
            scheduleId.isBlank() || protectionState.stockId !in stockIds ||
                protectionState.scheduledReleaseAt == null
        }
    ) {
        return "예정 종목 거래정지의 ID·종목·해제 시각이 올바르지 않습니다."
    }
    if (protection.investmentAlerts.any { (stockId, protectionState) ->
            stockId != protectionState.stockId || stocksById[stockId]?.market?.isKorean != true
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
        return "미국 LULD에 필수 종목 상태가 없거나 맵 키·종목·주 상장시장이 일치하지 않습니다."
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

    if (state.dailyTradingSurveillance.keys != stockIds ||
        state.dailyTradingSurveillance.any { (_, points) ->
                points.zipWithNext().any { (previous, next) -> previous.date >= next.date } ||
                points.any { it.date > state.currentDate }
        }
    ) {
        return "일별 시장감시 이력의 종목·날짜 순서가 올바르지 않습니다."
    }
    return null
}
