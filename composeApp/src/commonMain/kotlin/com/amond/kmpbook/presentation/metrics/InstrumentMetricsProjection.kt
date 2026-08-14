package com.amond.kmpbook.presentation.metrics

import com.amond.kmpbook.domain.model.corporateaction.CorporateFundamentalState
import com.amond.kmpbook.domain.model.instrument.FundFinancialState
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundState
import com.amond.kmpbook.domain.model.fundstructure.EtnState
import com.amond.kmpbook.domain.model.pricing.Quote

/**
 * 저장된 원시 회계·계약 상태와 현재 호가를 화면용 파생 지표로 투영한다.
 *
 * 기업 지표는 분기 원장에서 TTM 손익과 매출을 집계하고, 개방형 ETF의 AUM은
 * `NAV × 좌수`, ETN 발행잔액은 `지표가치 × notes outstanding`, 폐쇄형 펀드의
 * 순자산은 `좌당 NAV × 보통주수`로 매번 계산한다. 시장가격은 이 공정가치를
 * 덮어쓰지 않고 시가총액·가격 배수와 상품의 프리미엄·디스카운트 계산에 사용된다. 비율과
 * 시가총액은 저장하지 않으며 `asOf`는 호가와 원시 상태 중 더 늦은 기준 시각이다.
 *
 * ETN은 [com.amond.kmpbook.domain.model.fundstructure.EtnState]의 주어진 발행잔량을 읽을 뿐이다.
 * 이 투영 계층과 현재 런타임은 일반 ETN 발행·취소·상환 유량을 만들지 않으며,
 * 그러므로 ETN의 `lastNetFlow`는 0으로 투영한다.
 */
object InstrumentMetricsProjection {
    fun project(
        stock: StockDefinition,
        quote: Quote,
        corporateState: CorporateFundamentalState?,
        fundState: FundFinancialState?,
        etnState: EtnState?,
        closedEndFundState: ClosedEndFundState?,
    ): InstrumentMetricsSnapshot? = when {
        stock.hasCorporateEarnings -> projectCorporate(
            stock,
            quote,
            requireNotNull(corporateState) { "${stock.id} 기업 재무 상태가 없습니다." },
        )
        fundState != null -> projectOpenEndFund(
            stock,
            quote,
            fundState,
        )
        etnState != null -> projectEtn(stock, quote, etnState)
        closedEndFundState != null -> projectClosedEndFund(stock, quote, closedEndFundState)
        stock.isFundLike -> error("${stock.id} 상품의 법적 구조 상태가 없습니다.")
        else -> null
    }

    private fun projectCorporate(
        stock: StockDefinition,
        quote: Quote,
        state: CorporateFundamentalState,
    ): CorporateMetricsSnapshot {
        require(state.stockId == stock.id && quote.stockId == stock.id)
        val shares = stock.sharesOutstanding.toDouble()
        val marketCapitalization = quote.price * shares
        val trailingRevenue = state.quarters.sumOf { it.revenue }
        val trailingNetIncome = state.quarters.sumOf { it.netIncome }
        val averageEquity = (state.equityAtTtmStart + state.bookEquity) / 2.0
        return CorporateMetricsSnapshot(
            stockId = stock.id,
            asOf = maxOf(state.asOf, quote.timestamp),
            currency = stock.currency,
            marketCapitalization = marketCapitalization,
            sharesOutstanding = shares,
            ttmRevenue = trailingRevenue,
            ttmNetIncome = trailingNetIncome,
            bookEquity = state.bookEquity,
            earningsPerShare = trailingNetIncome.takeIf(Double::isFinite)?.div(shares),
            priceEarningsRatio = trailingNetIncome.takeIf { it > 0.0 }
                ?.let { marketCapitalization / it },
            priceSalesRatio = trailingRevenue.takeIf { it > 0.0 }
                ?.let { marketCapitalization / it },
            returnOnEquity = averageEquity.takeIf { it > 0.0 }
                ?.let { trailingNetIncome / it },
            priceToBookRatio = state.bookEquity.takeIf { it > 0.0 }
                ?.let { marketCapitalization / it },
        )
    }

    private fun projectOpenEndFund(
        stock: StockDefinition,
        quote: Quote,
        state: FundFinancialState,
    ): FundMetricsSnapshot {
        require(state.stockId == stock.id && quote.stockId == stock.id)
        val profile = requireNotNull(stock.etfProfile)
        val referenceValue = state.navPerUnit
        val referenceNotional = referenceValue * state.unitsOrNotesOutstanding
        return FundMetricsSnapshot(
            stockId = stock.id,
            asOf = maxOf(state.asOf, quote.timestamp),
            currency = stock.currency,
            marketCapitalization = quote.price * state.unitsOrNotesOutstanding,
            targetLeverage = profile.leverage,
            annualExpenseRatio = profile.annualExpenseRatio,
            unitsOrNotesOutstanding = state.unitsOrNotesOutstanding,
            navPerUnit = state.navPerUnit,
            indicativeValuePerUnit = state.indicativeValuePerUnit,
            assetsUnderManagement = referenceNotional,
            outstandingNotional = null,
            premiumDiscountRate = referenceValue.takeIf { it > 0.0 }
                ?.let { quote.price / it - 1.0 },
            lastNetFlow = state.lastNetFlow,
        )
    }

    private fun projectEtn(
        stock: StockDefinition,
        quote: Quote,
        state: EtnState,
    ): FundMetricsSnapshot {
        require(state.productId == stock.id && quote.stockId == stock.id)
        val profile = requireNotNull(stock.etfProfile)
        val indicativeValue = state.feeAdjustedIndicativeValuePerNote
        val notes = state.notesOutstanding.toDouble()
        return FundMetricsSnapshot(
            stockId = stock.id,
            asOf = maxOf(state.asOf, quote.timestamp),
            currency = stock.currency,
            marketCapitalization = quote.price * notes,
            targetLeverage = profile.leverage,
            annualExpenseRatio = profile.annualExpenseRatio,
            unitsOrNotesOutstanding = notes,
            navPerUnit = indicativeValue,
            indicativeValuePerUnit = indicativeValue,
            assetsUnderManagement = null,
            outstandingNotional = indicativeValue * notes,
            premiumDiscountRate = indicativeValue.takeIf { it > 0.0 }
                ?.let { quote.price / it - 1.0 },
            lastNetFlow = 0.0,
        )
    }

    private fun projectClosedEndFund(
        stock: StockDefinition,
        quote: Quote,
        state: ClosedEndFundState,
    ): FundMetricsSnapshot {
        require(state.fundId == stock.id && quote.stockId == stock.id)
        val profile = requireNotNull(stock.etfProfile)
        val commonNetAssets = state.navPerCommonShare * state.commonSharesOutstanding
        return FundMetricsSnapshot(
            stockId = stock.id,
            asOf = maxOf(state.asOf, quote.timestamp),
            currency = stock.currency,
            marketCapitalization = quote.price * state.commonSharesOutstanding,
            targetLeverage = profile.leverage,
            annualExpenseRatio = profile.annualExpenseRatio,
            unitsOrNotesOutstanding = state.commonSharesOutstanding,
            navPerUnit = state.navPerCommonShare,
            indicativeValuePerUnit = state.navPerCommonShare,
            assetsUnderManagement = commonNetAssets,
            outstandingNotional = null,
            premiumDiscountRate = quote.price / state.navPerCommonShare - 1.0,
            lastNetFlow = 0.0,
        )
    }
}
