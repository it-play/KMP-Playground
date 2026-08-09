package com.amond.kmpbook.domain.simulation.order

import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.venue.MarketVenueProfiles
import com.amond.kmpbook.domain.simulation.market.MarketMicrostructure
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import com.amond.kmpbook.domain.simulation.price.PriceEngine
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Deterministic synthetic level-10 order book for the selected instrument. */
class OrderBookEngine(private val seed: Long) {
    fun generate(input: OrderBookGenerationInput): OrderBookSnapshot {
        if (!input.session.isTradable) {
            return OrderBookSnapshot(
                stockId = input.stock.id,
                timestamp = input.timestamp,
                session = input.session,
                lastPrice = input.lastPrice,
                bids = emptyList(),
                asks = emptyList(),
            )
        }

        val random = DeterministicRandom(
            DeterministicRandom.mixSeed(
                seed,
                PriceEngine.stableHash64(input.stock.id),
                input.timestamp.epochSeconds,
                input.lastPrice.toBits(),
            ),
        )
        val prices = generatePrices(input)
        val baselineQuantity = baselineQuantity(input)
        val bidLevels = generateLevels(
            prices = prices.first,
            baselineQuantity = baselineQuantity,
            sidePressure = 1.0 + input.buyPressure * PRESSURE_DEPTH_EFFECT,
            quantityStep = input.stock.quantityStep,
            random = random,
        )
        val askLevels = generateLevels(
            prices = prices.second,
            baselineQuantity = baselineQuantity,
            sidePressure = 1.0 - input.buyPressure * PRESSURE_DEPTH_EFFECT,
            quantityStep = input.stock.quantityStep,
            random = random,
        )
        return OrderBookSnapshot(
            stockId = input.stock.id,
            timestamp = input.timestamp,
            session = input.session,
            lastPrice = input.lastPrice,
            bids = bidLevels,
            asks = askLevels,
        )
    }

    private fun generatePrices(input: OrderBookGenerationInput): Pair<List<Double>, List<Double>> {
        val stock = input.stock
        val market = stock.market
        val reference = MarketMicrostructure.roundNearest(stock, input.lastPrice)
        val tick = MarketMicrostructure.tickSize(stock, reference)
        val sizeScale = normalizedSize(input.stock)
        val liquidityScale = ln(1.0 + input.averageDailyVolume.toDouble()).coerceAtLeast(1.0)
        val venueProfile = MarketVenueProfiles.forMarket(market)
        val rawSpreadTicks = 1.0 +
            input.stock.volatility * VOLATILITY_SPREAD_TICKS +
            input.marketStress * STRESS_SPREAD_TICKS -
            sizeScale * SIZE_SPREAD_DISCOUNT -
            liquidityScale * LIQUIDITY_SPREAD_DISCOUNT
        val spreadTicks = (rawSpreadTicks.coerceAtLeast(1.0) * venueProfile.spreadMultiplier)
            .roundToInt()
            .coerceIn(1, MAX_SPREAD_TICKS)

        var bestBid = MarketMicrostructure.roundDown(
            stock,
            reference - tick * (spreadTicks + 1) / 2.0,
        )
        var bestAsk = MarketMicrostructure.roundUp(
            stock,
            reference + tick * (spreadTicks + 1) / 2.0,
        )
        if (bestAsk <= bestBid) bestAsk = nextPrice(stock, bestBid)

        val limits = MarketMicrostructure.dailyPriceLimits(stock, input.dailyBasePrice)
        if (limits != null) {
            val minimumBestBid = advanceUp(stock, limits.lower, BOOK_DEPTH - 1)
            val maximumBestAsk = advanceDown(stock, limits.upper, BOOK_DEPTH - 1)
            bestBid = max(bestBid, minimumBestBid)
            bestAsk = minOf(bestAsk, maximumBestAsk)
            if (bestAsk <= bestBid) {
                bestBid = previousPrice(stock, bestAsk)
            }
        }

        val bids = buildList(BOOK_DEPTH) {
            var price = bestBid
            repeat(BOOK_DEPTH) {
                add(price)
                price = previousPrice(stock, price)
            }
        }
        val asks = buildList(BOOK_DEPTH) {
            var price = bestAsk
            repeat(BOOK_DEPTH) {
                add(price)
                price = nextPrice(stock, price)
            }
        }
        return bids to asks
    }

    private fun generateLevels(
        prices: List<Double>,
        baselineQuantity: Double,
        sidePressure: Double,
        quantityStep: Double,
        random: DeterministicRandom,
    ): List<OrderBookDepthLevel> {
        var cumulative = 0.0
        return prices.mapIndexed { index, price ->
            val depthMultiplier = 1.0 + index * DEPTH_GROWTH + index * index * DEPTH_CURVE
            val noise = exp(LEVEL_QUANTITY_NOISE * random.nextGaussian())
            val rawQuantity = baselineQuantity * depthMultiplier * sidePressure * noise
            val quantity = floor(rawQuantity / quantityStep).coerceAtLeast(1.0) * quantityStep
            cumulative += quantity
            val typicalOrder = max(quantityStep, sqrt(baselineQuantity * quantityStep) * 2.0)
            OrderBookDepthLevel(
                price = price,
                quantity = quantity,
                cumulativeQuantity = cumulative,
                orderCount = (quantity / typicalOrder).roundToInt().coerceAtLeast(1),
            )
        }
    }

    private fun baselineQuantity(input: OrderBookGenerationInput): Double {
        val dailyParticipation = input.averageDailyVolume.toDouble() * BOOK_PARTICIPATION_RATE
        val sizeSupport = sqrt(input.stock.sharesOutstanding.toDouble()) * SHARE_COUNT_SUPPORT
        val stressDepth = 1.0 - input.marketStress * STRESS_DEPTH_REDUCTION
        val venueDepth = MarketVenueProfiles.forMarket(input.stock.market).depthMultiplier
        return max(input.stock.quantityStep, (dailyParticipation + sizeSupport) * stressDepth * venueDepth)
    }

    private fun normalizedSize(stock: StockDefinition): Double {
        val referenceCapitalization = if (stock.market.isKorean) 1_000_000_000_000.0 else 1_000_000_000.0
        return ln(1.0 + stock.marketCap / referenceCapitalization).coerceIn(0.0, 12.0)
    }

    private fun nextPrice(stock: StockDefinition, current: Double): Double {
        val probe = current + MarketMicrostructure.minimumPrice(stock.market) * 0.5
        val tick = MarketMicrostructure.tickSize(stock, probe)
        return MarketMicrostructure.roundNearest(stock, current + tick)
    }

    private fun previousPrice(stock: StockDefinition, current: Double): Double {
        val minimum = MarketMicrostructure.minimumPrice(stock.market)
        val probe = (current - minimum * 0.5).coerceAtLeast(minimum)
        val tick = MarketMicrostructure.tickSize(stock, probe)
        return MarketMicrostructure.roundNearest(stock, (current - tick).coerceAtLeast(minimum))
    }

    private fun advanceUp(stock: StockDefinition, start: Double, count: Int): Double {
        var value = start
        repeat(count) { value = nextPrice(stock, value) }
        return value
    }

    private fun advanceDown(stock: StockDefinition, start: Double, count: Int): Double {
        var value = start
        repeat(count) { value = previousPrice(stock, value) }
        return value
    }

    companion object {
        private const val BOOK_DEPTH: Int = 10
        private const val MAX_SPREAD_TICKS: Int = 12
        private const val VOLATILITY_SPREAD_TICKS: Double = 9.0
        private const val STRESS_SPREAD_TICKS: Double = 6.0
        private const val SIZE_SPREAD_DISCOUNT: Double = 0.18
        private const val LIQUIDITY_SPREAD_DISCOUNT: Double = 0.035
        private const val PRESSURE_DEPTH_EFFECT: Double = 0.55
        private const val DEPTH_GROWTH: Double = 0.13
        private const val DEPTH_CURVE: Double = 0.008
        private const val LEVEL_QUANTITY_NOISE: Double = 0.28
        private const val BOOK_PARTICIPATION_RATE: Double = 0.00045
        private const val SHARE_COUNT_SUPPORT: Double = 0.002
        private const val STRESS_DEPTH_REDUCTION: Double = 0.65
    }
}
