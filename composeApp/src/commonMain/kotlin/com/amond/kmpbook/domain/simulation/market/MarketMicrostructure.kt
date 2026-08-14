package com.amond.kmpbook.domain.simulation.market

import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Market
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** Exchange quotation rules shared by the price and order-book engines. */
object MarketMicrostructure {
    /**
     * 2026 KRX equity quotation units. KOSPI and KOSDAQ intentionally diverge
     * above KRW 50,000; fund-like exchange products use the instrument overload.
     */
    fun tickSize(market: Market, price: Double): Double {
        require(price >= 0.0 && price.isFinite()) { "Price must be finite and non-negative" }
        return when (market) {
            Market.KOSPI -> when {
                price < 1_000.0 -> 1.0
                price < 5_000.0 -> 5.0
                price < 10_000.0 -> 10.0
                price < 50_000.0 -> 50.0
                price < 100_000.0 -> 100.0
                price < 500_000.0 -> 500.0
                else -> 1_000.0
            }

            Market.KOSDAQ -> when {
                price < 1_000.0 -> 1.0
                price < 5_000.0 -> 5.0
                price < 10_000.0 -> 10.0
                price < 50_000.0 -> 50.0
                else -> 100.0
            }

            Market.NASDAQ,
            Market.NYSE,
            Market.NYSE_ARCA,
            Market.CBOE_BZX,
            Market.NYSE_AMERICAN,
            -> if (price < 1.0) 0.0001 else 0.01
        }
    }

    /** KRX-listed ETF/ETN quotation unit is KRW 5 at every price level. */
    fun tickSize(stock: StockDefinition, price: Double): Double = when {
        stock.market.isKorean &&
            (stock.instrumentType == InstrumentType.ETF || stock.instrumentType == InstrumentType.ETN) -> 5.0

        else -> tickSize(stock.market, price)
    }

    fun roundDown(market: Market, price: Double): Double {
        val safePrice = price.coerceAtLeast(minimumPrice(market))
        val tick = tickSize(market, safePrice)
        return normalize(market, floor((safePrice + EPSILON) / tick) * tick)
    }

    fun roundDown(stock: StockDefinition, price: Double): Double {
        val safePrice = price.coerceAtLeast(minimumPrice(stock.market))
        val tick = tickSize(stock, safePrice)
        return normalize(stock.market, floor((safePrice + EPSILON) / tick) * tick)
    }

    fun roundUp(market: Market, price: Double): Double {
        val safePrice = price.coerceAtLeast(minimumPrice(market))
        val tick = tickSize(market, safePrice)
        return normalize(market, ceil((safePrice - EPSILON) / tick) * tick)
    }

    fun roundUp(stock: StockDefinition, price: Double): Double {
        val safePrice = price.coerceAtLeast(minimumPrice(stock.market))
        val tick = tickSize(stock, safePrice)
        return normalize(stock.market, ceil((safePrice - EPSILON) / tick) * tick)
    }

    fun roundNearest(market: Market, price: Double): Double {
        val safePrice = price.coerceAtLeast(minimumPrice(market))
        val tick = tickSize(market, safePrice)
        return normalize(market, round(safePrice / tick) * tick)
    }

    fun roundNearest(stock: StockDefinition, price: Double): Double {
        val safePrice = price.coerceAtLeast(minimumPrice(stock.market))
        val tick = tickSize(stock, safePrice)
        return normalize(stock.market, round(safePrice / tick) * tick)
    }

    fun minimumPrice(market: Market): Double = if (market.isKorean) 1.0 else 0.0001

    /**
     * KRX's ordinary-stock daily limit is +/-30% of the daily base price. The
     * 30% amount is first truncated to the base-price quotation unit.
     */
    fun dailyPriceLimits(market: Market, basePrice: Double): DailyPriceLimits? {
        require(basePrice > 0.0 && basePrice.isFinite()) { "Base price must be positive and finite" }
        if (!market.isKorean) return null

        val baseTick = tickSize(market, basePrice)
        val limitAmount = floor(basePrice * KRX_LIMIT_RATE / baseTick) * baseTick
        val upper = roundDown(market, basePrice + limitAmount)
        val lower = roundUp(market, basePrice - limitAmount)
            .coerceAtLeast(minimumPrice(market))
        return DailyPriceLimits(lower = lower, upper = upper)
    }

    fun dailyPriceLimits(stock: StockDefinition, basePrice: Double): DailyPriceLimits? {
        require(basePrice > 0.0 && basePrice.isFinite()) { "Base price must be positive and finite" }
        if (!stock.market.isKorean) return null

        val baseTick = tickSize(stock, basePrice)
        val limitAmount = floor(basePrice * KRX_LIMIT_RATE / baseTick) * baseTick
        val upper = roundDown(stock, basePrice + limitAmount)
        val lower = roundUp(stock, basePrice - limitAmount)
            .coerceAtLeast(minimumPrice(stock.market))
        return DailyPriceLimits(lower = lower, upper = upper)
    }

    fun clampToDailyLimits(market: Market, price: Double, basePrice: Double): Double {
        val limits = dailyPriceLimits(market, basePrice)
            ?: return roundNearest(market, price)
        return roundNearest(market, price.coerceIn(limits.lower, limits.upper))
            .coerceIn(limits.lower, limits.upper)
    }

    fun clampToDailyLimits(stock: StockDefinition, price: Double, basePrice: Double): Double {
        val limits = dailyPriceLimits(stock, basePrice)
            ?: return roundNearest(stock, price)
        return roundNearest(stock, price.coerceIn(limits.lower, limits.upper))
            .coerceIn(limits.lower, limits.upper)
    }

    private fun normalize(market: Market, price: Double): Double {
        val scale = if (market.isKorean) 1.0 else if (price < 1.0) 10_000.0 else 100.0
        return round(price * scale) / scale
    }

    private const val KRX_LIMIT_RATE: Double = 0.30
    private const val EPSILON: Double = 1e-10
}
