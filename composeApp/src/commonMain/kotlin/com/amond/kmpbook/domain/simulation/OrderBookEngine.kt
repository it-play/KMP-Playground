package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.MarketVenueProfiles
import com.amond.kmpbook.domain.model.OrderBook
import com.amond.kmpbook.domain.model.OrderBookLevel
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.StockDefinition
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Instant

data class OrderBookDepthLevel(
    val price: Double,
    val quantity: Double,
    val cumulativeQuantity: Double,
    val orderCount: Int,
) {
    init {
        require(price > 0.0 && price.isFinite()) { "Order price must be positive and finite" }
        require(quantity > 0.0 && quantity.isFinite()) { "Order quantity must be positive and finite" }
        require(cumulativeQuantity >= quantity && cumulativeQuantity.isFinite())
        require(orderCount > 0) { "Order count must be positive" }
    }
}

data class OrderBookSnapshot(
    val stockId: String,
    val timestamp: Instant,
    val session: MarketSession,
    val lastPrice: Double,
    val bids: List<OrderBookDepthLevel>,
    val asks: List<OrderBookDepthLevel>,
) {
    init {
        require(stockId.isNotBlank())
        require(lastPrice > 0.0 && lastPrice.isFinite())
        require(bids.size == asks.size) { "Bid and ask depth must match" }
        require(bids.size == 10 || bids.isEmpty()) { "An executable book must contain 10 levels" }
        require(bids.zipWithNext().all { (near, far) -> near.price > far.price }) {
            "Bid prices must strictly decrease away from the market"
        }
        require(asks.zipWithNext().all { (near, far) -> near.price < far.price }) {
            "Ask prices must strictly increase away from the market"
        }
        if (bids.isNotEmpty()) {
            require(bids.first().price < asks.first().price) { "Order book cannot be crossed" }
        }
    }

    val bestBid: OrderBookDepthLevel? get() = bids.firstOrNull()
    val bestAsk: OrderBookDepthLevel? get() = asks.firstOrNull()
    val spread: Double? get() = if (bestBid != null && bestAsk != null) bestAsk!!.price - bestBid!!.price else null
    val midpoint: Double? get() = if (bestBid != null && bestAsk != null) (bestBid!!.price + bestAsk!!.price) / 2.0 else null
    val totalBidQuantity: Double get() = bids.lastOrNull()?.cumulativeQuantity ?: 0.0
    val totalAskQuantity: Double get() = asks.lastOrNull()?.cumulativeQuantity ?: 0.0
    val imbalance: Double
        get() {
            val total = totalBidQuantity + totalAskQuantity
            return if (total == 0.0) 0.0 else (totalBidQuantity - totalAskQuantity) / total
        }
    val microPrice: Double?
        get() {
            val bid = bestBid ?: return null
            val ask = bestAsk ?: return null
            val topQuantity = bid.quantity + ask.quantity
            return if (topQuantity == 0.0) midpoint else {
                (ask.price * bid.quantity + bid.price * ask.quantity) / topQuantity
            }
        }

    fun applyTopOfBook(quote: Quote): Quote {
        require(quote.stockId == stockId) { "Quote and order book stock ids must match" }
        return quote.copy(
            bidPrice = bestBid?.price,
            askPrice = bestAsk?.price,
            bidQuantity = bestBid?.quantity ?: 0.0,
            askQuantity = bestAsk?.quantity ?: 0.0,
        )
    }

    /** Lossless executable-depth view for the shared domain order model. */
    fun toOrderBook(): OrderBook = OrderBook(
        stockId = stockId,
        timestamp = timestamp,
        bids = bids.map { OrderBookLevel(it.price, it.quantity, it.orderCount) },
        asks = asks.map { OrderBookLevel(it.price, it.quantity, it.orderCount) },
    )
}

data class OrderBookGenerationInput(
    val stock: StockDefinition,
    val timestamp: Instant,
    val lastPrice: Double,
    val dailyBasePrice: Double = lastPrice,
    val averageDailyVolume: Long = PriceGenerationInput.defaultAverageDailyVolume(stock),
    val session: MarketSession = MarketSession.REGULAR,
    /** Positive values produce stronger bid depth; range is deliberately bounded. */
    val buyPressure: Double = 0.0,
    /** 0 is calm and 1 is severe stress. Stress widens spreads and thins depth. */
    val marketStress: Double = 0.0,
) {
    init {
        require(lastPrice > 0.0 && lastPrice.isFinite())
        require(dailyBasePrice > 0.0 && dailyBasePrice.isFinite())
        require(averageDailyVolume >= 0L)
        require(buyPressure in -1.0..1.0)
        require(marketStress in 0.0..1.0)
    }
}

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
        val market = input.stock.market
        val reference = MarketMicrostructure.roundNearest(market, input.lastPrice)
        val tick = MarketMicrostructure.tickSize(market, reference)
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
            market,
            reference - tick * (spreadTicks + 1) / 2.0,
        )
        var bestAsk = MarketMicrostructure.roundUp(
            market,
            reference + tick * (spreadTicks + 1) / 2.0,
        )
        if (bestAsk <= bestBid) bestAsk = nextPrice(market, bestBid)

        val limits = MarketMicrostructure.dailyPriceLimits(market, input.dailyBasePrice)
        if (limits != null) {
            val minimumBestBid = advanceUp(market, limits.lower, BOOK_DEPTH - 1)
            val maximumBestAsk = advanceDown(market, limits.upper, BOOK_DEPTH - 1)
            bestBid = max(bestBid, minimumBestBid)
            bestAsk = minOf(bestAsk, maximumBestAsk)
            if (bestAsk <= bestBid) {
                bestBid = previousPrice(market, bestAsk)
            }
        }

        val bids = buildList(BOOK_DEPTH) {
            var price = bestBid
            repeat(BOOK_DEPTH) {
                add(price)
                price = previousPrice(market, price)
            }
        }
        val asks = buildList(BOOK_DEPTH) {
            var price = bestAsk
            repeat(BOOK_DEPTH) {
                add(price)
                price = nextPrice(market, price)
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

    private fun nextPrice(market: Market, current: Double): Double {
        val probe = current + MarketMicrostructure.minimumPrice(market) * 0.5
        val tick = MarketMicrostructure.tickSize(market, probe)
        return MarketMicrostructure.roundNearest(market, current + tick)
    }

    private fun previousPrice(market: Market, current: Double): Double {
        val minimum = MarketMicrostructure.minimumPrice(market)
        val probe = (current - minimum * 0.5).coerceAtLeast(minimum)
        val tick = MarketMicrostructure.tickSize(market, probe)
        return MarketMicrostructure.roundNearest(market, (current - tick).coerceAtLeast(minimum))
    }

    private fun advanceUp(market: Market, start: Double, count: Int): Double {
        var value = start
        repeat(count) { value = nextPrice(market, value) }
        return value
    }

    private fun advanceDown(market: Market, start: Double, count: Int): Double {
        var value = start
        repeat(count) { value = previousPrice(market, value) }
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
