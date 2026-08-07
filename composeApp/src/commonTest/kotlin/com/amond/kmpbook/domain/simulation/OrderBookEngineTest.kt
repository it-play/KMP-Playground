package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.Quote
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderBookEngineTest {
    private val time = Instant.parse("2026-08-07T02:00:00Z")

    @Test
    fun openMarketBuildsDeterministicUncrossedLevelTenBook() {
        val stock = testStock(initialPrice = 70_000.0)
        val input = OrderBookGenerationInput(
            stock = stock,
            timestamp = time,
            lastPrice = 70_000.0,
            dailyBasePrice = 70_000.0,
            averageDailyVolume = 8_000_000L,
            session = MarketSession.REGULAR,
            buyPressure = 0.25,
        )

        val first = OrderBookEngine(123L).generate(input)
        val second = OrderBookEngine(123L).generate(input)

        assertEquals(first, second)
        assertEquals(10, first.bids.size)
        assertEquals(10, first.asks.size)
        assertTrue(first.bids.zipWithNext().all { (near, far) -> near.price > far.price })
        assertTrue(first.asks.zipWithNext().all { (near, far) -> near.price < far.price })
        assertTrue(first.bestBid!!.price < first.bestAsk!!.price)
        assertTrue(first.bids.all { isOnTick(stock.market, it.price) })
        assertTrue(first.asks.all { isOnTick(stock.market, it.price) })
        assertTrue(first.bids.zipWithNext().all { (a, b) -> b.cumulativeQuantity > a.cumulativeQuantity })
        assertTrue(first.asks.zipWithNext().all { (a, b) -> b.cumulativeQuantity > a.cumulativeQuantity })
        assertTrue(first.microPrice!! in first.bestBid!!.price..first.bestAsk!!.price)
    }

    @Test
    fun fractionalUsStockDepthUsesQuantityStep() {
        val step = 0.000001
        val stock = testStock(
            market = Market.NASDAQ,
            initialPrice = 187.25,
            quantityStep = step,
        )
        val book = OrderBookEngine(8L).generate(
            OrderBookGenerationInput(
                stock = stock,
                timestamp = time,
                lastPrice = 187.25,
                session = MarketSession.REGULAR,
            ),
        )

        assertTrue((book.bids + book.asks).all { level ->
            val steps = level.quantity / step
            abs(steps - round(steps)) < 1e-5
        })
        assertTrue((book.bids + book.asks).all { isOnTick(stock.market, it.price) })
    }

    @Test
    fun americanBookIsWiderAndShallowerThanArcaForEquivalentFixtures() {
        val arca = testStock(
            symbol = "VENUE",
            market = Market.NYSE_ARCA,
            initialPrice = 100.0,
            volatility = 0.25,
        )
        val american = arca.copy(market = Market.NYSE_AMERICAN)
        val common = OrderBookGenerationInput(
            stock = arca,
            timestamp = time,
            lastPrice = 100.0,
            dailyBasePrice = 100.0,
            averageDailyVolume = 8_000_000L,
            session = MarketSession.REGULAR,
            marketStress = 0.0,
        )
        val engine = OrderBookEngine(20260807L)

        val arcaBook = engine.generate(common)
        val americanBook = engine.generate(common.copy(stock = american))

        assertTrue(americanBook.spread!! > arcaBook.spread!!)
        assertTrue(americanBook.totalBidQuantity < arcaBook.totalBidQuantity)
        assertTrue(americanBook.totalAskQuantity < arcaBook.totalAskQuantity)
        assertTrue((americanBook.bids + americanBook.asks).all { isOnTick(Market.NYSE_AMERICAN, it.price) })
    }

    @Test
    fun closedMarketHasNoExecutableDepth() {
        val stock = testStock()
        val book = OrderBookEngine(8L).generate(
            OrderBookGenerationInput(
                stock = stock,
                timestamp = time,
                lastPrice = stock.initialPrice,
                session = MarketSession.CLOSED,
            ),
        )

        assertTrue(book.bids.isEmpty())
        assertTrue(book.asks.isEmpty())
        assertNull(book.spread)
        assertNull(book.microPrice)
    }

    @Test
    fun topOfBookCanBeAttachedToQuote() {
        val stock = testStock()
        val book = OrderBookEngine(9L).generate(
            OrderBookGenerationInput(stock, time, stock.initialPrice),
        )
        val quote = Quote(
            stockId = stock.id,
            timestamp = time,
            price = stock.initialPrice,
            previousClose = stock.initialPrice,
            session = MarketSession.REGULAR,
        )

        val enriched = book.applyTopOfBook(quote)

        assertEquals(book.bestBid!!.price, enriched.bidPrice)
        assertEquals(book.bestAsk!!.price, enriched.askPrice)
        assertEquals(book.bestBid!!.quantity, enriched.bidQuantity)
        assertEquals(book.bestAsk!!.quantity, enriched.askQuantity)
    }

    private fun isOnTick(market: Market, price: Double): Boolean {
        val tick = MarketMicrostructure.tickSize(market, price)
        val units = price / tick
        return abs(units - round(units)) < 1e-7
    }
}
