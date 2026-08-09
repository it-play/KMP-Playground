package com.amond.kmpbook.presentation.market

import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private const val MAX_MARKET_RESPONSE_CHARACTERS: Int = 5 * 1024 * 1024

actual class MarketIndexDataSource actual constructor() {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    actual suspend fun loadThreeMonthSeries(): List<MarketIndexSeries> = coroutineScope {
        MARKET_INDEX_SPECS.map { (symbol, name) ->
            async(Dispatchers.IO) { loadSeries(symbol, name) }
        }.awaitAll()
    }

    private fun loadSeries(symbol: String, name: String): MarketIndexSeries = runCatching {
        val encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8)
        val uri = URI.create(
            "https://query1.finance.yahoo.com/v8/finance/chart/$encodedSymbol" +
                "?range=3mo&interval=1d&events=history",
        )
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("User-Agent", "MarketLedger2040/1.0")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        require(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}" }
        require(response.body().length <= MAX_MARKET_RESPONSE_CHARACTERS) { "응답 크기 초과" }

        val result = JsonParser.parseString(response.body())
            .asJsonObject["chart"].asJsonObject["result"].asJsonArray
            .firstOrNull()?.asJsonObject
            ?: error("시계열 없음")
        val timestamps = result["timestamp"].asJsonArray
        val closes = result["indicators"].asJsonObject["quote"].asJsonArray[0]
            .asJsonObject["close"].asJsonArray
        val points = buildList {
            val count = minOf(timestamps.size(), closes.size())
            repeat(count) { index ->
                val close = closes[index]
                if (!close.isJsonNull) {
                    val value = close.asDouble
                    if (value.isFinite()) {
                        add(MarketIndexPoint(timestamps[index].asLong, value))
                    }
                }
            }
        }
        require(points.size >= 2) { "유효한 시세 부족" }
        MarketIndexSeries(symbol = symbol, name = name, points = points)
    }.getOrElse { error ->
        MarketIndexSeries(
            symbol = symbol,
            name = name,
            points = emptyList(),
            errorMessage = error.message ?: "데이터를 불러오지 못했습니다.",
        )
    }

    private companion object {
        val MARKET_INDEX_SPECS: List<Pair<String, String>> = listOf(
            "^DJI" to "다우존스",
            "^IXIC" to "나스닥 종합",
            "^GSPC" to "S&P 500",
            "^VIX" to "VIX",
            "^KS11" to "코스피",
            "^KQ11" to "코스닥",
        )
    }
}
