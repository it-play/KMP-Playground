package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition

internal fun testStock(
    symbol: String = "TEST",
    market: Market = Market.KOSPI,
    sector: Sector = Sector.SEMICONDUCTOR,
    initialPrice: Double = if (market.isKorean) 70_000.0 else 100.0,
    volatility: Double = 0.25,
    beta: Double = 1.0,
    quantityStep: Double = 1.0,
): StockDefinition = StockDefinition(
    symbol = symbol,
    name = "테스트 $symbol",
    englishName = "Test $symbol",
    market = market,
    sector = sector,
    initialPrice = initialPrice,
    volatility = volatility,
    dividendYield = 0.02,
    marketCap = if (market.isKorean) 50_000_000_000_000.0 else 100_000_000_000.0,
    sharesOutstanding = 500_000_000L,
    description = "시뮬레이션 단위 테스트 종목",
    beta = beta,
    quantityStep = quantityStep,
)
