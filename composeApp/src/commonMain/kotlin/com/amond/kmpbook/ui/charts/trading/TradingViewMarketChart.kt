package com.amond.kmpbook.ui.charts.trading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.pricing.PriceBar
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.Trade
import com.amond.kmpbook.presentation.news.NewsStoryUi
import com.amond.kmpbook.ui.components.LoadingFinancialFact
import com.amond.kmpbook.ui.format.formatQuantity
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketType
import dev.nucleusframework.webview.jsbridge.IJsMessageHandler
import dev.nucleusframework.webview.jsbridge.JsMessage
import dev.nucleusframework.webview.jsbridge.rememberWebViewJsBridge
import dev.nucleusframework.webview.web.LoadingState
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewStateWithHTMLData
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kmpbook.composeapp.generated.resources.Res
import kotlin.math.abs
import kotlin.math.round

private const val READY_TITLE = "market-ledger-chart-ready"
private const val LIGHTWEIGHT_CHARTS_RESOURCE =
    "files/charts/lightweight-charts-5.2.1.standalone.production.js"
private const val CHART_HOST_RESOURCE = "files/charts/trading-chart-host.js"
private val chartAssetMutex = Mutex()
private var cachedChartHtml: String? = null
private val chartJson = Json {
    encodeDefaults = true
    explicitNulls = false
}
private class PreparedChartPayload(
    val injectionScript: String,
    val validEventIds: Set<String>,
)

@Composable
internal fun TradingViewMarketChart(
    symbol: String,
    resolution: String,
    market: Market,
    priceMinMove: Double,
    rangeKey: String,
    bars: List<PriceBar>,
    visibleDurationSeconds: Long?,
    averagePrice: Double?,
    trades: List<Trade>,
    relatedNews: List<NewsStoryUi>,
    onOpenEvent: (String) -> Unit,
    onShowAll: () -> Unit,
    isObscured: Boolean = false,
    onObscuredClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) {
        ChartMessage(
            text = "첫 거래 시간이 지나면 OHLCV 차트가 생성됩니다.",
            modifier = modifier,
        )
        return
    }

    val nativeChartRuntime by produceState<NativeChartRuntimeConfiguration?>(initialValue = null) {
        value = prepareNativeChartRuntime()
    }
    val chartHtmlResult by produceState<Result<String>?>(initialValue = null) {
        value = try {
            Result.success(withContext(Dispatchers.IO) { loadChartHtml() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
    val preparedPayloadResult by produceState<Result<PreparedChartPayload>?>(
        null,
        symbol,
        resolution,
        market,
        priceMinMove,
        rangeKey,
        bars,
        visibleDurationSeconds,
        averagePrice,
        trades,
        relatedNews,
    ) {
        value = try {
            Result.success(
                withContext(Dispatchers.Default) {
                    prepareChartPayload(
                        symbol = symbol,
                        resolution = resolution,
                        market = market,
                        priceMinMove = priceMinMove,
                        rangeKey = rangeKey,
                        bars = bars,
                        visibleDurationSeconds = visibleDurationSeconds,
                        averagePrice = averagePrice,
                        trades = trades,
                        relatedNews = relatedNews,
                    )
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    val runtimeConfiguration = nativeChartRuntime
    when {
        runtimeConfiguration == null -> {
            ChartLoading(modifier)
            return
        }
        !runtimeConfiguration.isAvailable -> {
            MissingNativeChartRuntime(modifier)
            return
        }
        chartHtmlResult == null -> {
            ChartLoading(modifier)
            return
        }
        chartHtmlResult?.isFailure == true -> {
            ChartMessage(
                text = "차트 리소스를 불러오지 못했습니다.",
                modifier = modifier,
            )
            return
        }
        preparedPayloadResult == null -> {
            ChartLoading(modifier)
            return
        }
        preparedPayloadResult?.isFailure == true -> {
            ChartMessage(
                text = "차트 데이터를 준비하지 못했습니다.",
                modifier = modifier,
            )
            return
        }
    }

    val chartHtml = chartHtmlResult?.getOrNull() ?: return
    val preparedPayload = preparedPayloadResult?.getOrNull() ?: return
    val payloadInjectionScript = preparedPayload.injectionScript
    val currentValidEventIds = rememberUpdatedState(preparedPayload.validEventIds)
    val currentOnOpenEvent = rememberUpdatedState(onOpenEvent)
    val currentOnShowAll = rememberUpdatedState(onShowAll)

    val navigator = rememberWebViewNavigator()
    val webViewState = rememberWebViewStateWithHTMLData(
        data = chartHtml,
        baseUrl = "https://market-ledger.local/chart/",
        mimeType = "text/html",
    ).also { state ->
        state.webSettings.apply {
            isJavaScriptEnabled = true
            supportZoom = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            backgroundColor = MarketColors.Paper
            desktopWebSettings.apply {
                transparent = false
                dataDirectory = runtimeConfiguration.dataDirectory
                incognito = runtimeConfiguration.dataDirectory == null
                enableClipboard = false
                enableDevtools = false
                enableNavigationGestures = false
            }
        }
    }
    val jsBridge = rememberWebViewJsBridge(navigator)
    var readinessTimedOut by remember { mutableStateOf(false) }

    DisposableEffect(jsBridge) {
        val eventHandler = object : IJsMessageHandler {
            override fun methodName(): String = "openChartEvent"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit,
            ) {
                val eventId = runCatching {
                    chartJson.parseToJsonElement(message.params)
                        .jsonObject.getValue("id")
                        .jsonPrimitive.content
                }.getOrNull()
                val accepted = eventId != null && eventId in currentValidEventIds.value
                if (accepted) currentOnOpenEvent.value(eventId)
                callback(if (accepted) "{\"ok\":true}" else "{\"ok\":false}")
            }
        }
        val showAllHandler = object : IJsMessageHandler {
            override fun methodName(): String = "showAllChartData"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit,
            ) {
                currentOnShowAll.value()
                callback("{\"ok\":true}")
            }
        }
        jsBridge.register(eventHandler)
        jsBridge.register(showAllHandler)
        onDispose {
            jsBridge.unregister(eventHandler)
            jsBridge.unregister(showAllHandler)
        }
    }

    LaunchedEffect(webViewState.pageTitle, payloadInjectionScript) {
        if (webViewState.pageTitle != READY_TITLE) return@LaunchedEffect
        readinessTimedOut = false
        navigator.evaluateJavaScript(payloadInjectionScript)
    }
    LaunchedEffect(webViewState.loadingState, webViewState.pageTitle) {
        if (webViewState.loadingState !is LoadingState.Finished || webViewState.pageTitle == READY_TITLE) {
            readinessTimedOut = false
            return@LaunchedEffect
        }
        delay(1_500)
        readinessTimedOut = webViewState.pageTitle != READY_TITLE
    }

    WebView(
        state = webViewState,
        modifier = modifier,
        navigator = navigator,
        webViewJsBridge = jsBridge,
    ) {
        when {
            isObscured -> Box(
                Modifier
                    .fillMaxSize()
                    .background(MarketColors.Scrim)
                    .consumeNativeChartOverlayPointerEvents()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onObscuredClick,
                    ),
            )
            readinessTimedOut -> ChartMessage(
                text = "네이티브 차트 엔진을 시작하지 못했습니다. WebView2 Runtime 설치 상태를 확인해 주세요.",
                modifier = Modifier.fillMaxSize(),
            )
            webViewState.pageTitle != READY_TITLE -> ChartLoading(Modifier.fillMaxSize())
        }
    }
}

private suspend fun prepareChartPayload(
    symbol: String,
    resolution: String,
    market: Market,
    priceMinMove: Double,
    rangeKey: String,
    bars: List<PriceBar>,
    visibleDurationSeconds: Long?,
    averagePrice: Double?,
    trades: List<Trade>,
    relatedNews: List<NewsStoryUi>,
): PreparedChartPayload {
    val orderedBars = bars.sortedBy(PriceBar::startTime)
    currentCoroutineContext().ensureActive()
    val payload = createPayload(
        symbol = symbol,
        resolution = resolution,
        market = market,
        priceMinMove = priceMinMove,
        rangeKey = rangeKey,
        bars = orderedBars,
        visibleDurationSeconds = visibleDurationSeconds,
        averagePrice = averagePrice,
        trades = trades,
        relatedNews = relatedNews,
    )
    currentCoroutineContext().ensureActive()
    val javaScriptArgument = payload.toJavaScriptArgument()
    currentCoroutineContext().ensureActive()
    return PreparedChartPayload(
        injectionScript =
            "window.__MARKET_LEDGER_PENDING_PAYLOAD__=$javaScriptArgument;" +
                "window.marketLedgerChart?.receiveBase64(window.__MARKET_LEDGER_PENDING_PAYLOAD__);",
        validEventIds = payload.markers.mapNotNullTo(linkedSetOf()) { marker ->
            marker.id.removePrefix("event:").takeIf { marker.id.startsWith("event:") }
        },
    )
}

@Composable
private fun MissingNativeChartRuntime(modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = modifier.background(MarketColors.Paper).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "이 PC에는 차트에 필요한 WebView2 Runtime이 없습니다.",
                style = MarketType.label,
                color = MarketColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Microsoft WebView2 설치 ↗",
                modifier = Modifier
                    .clickable(role = Role.Button) {
                        uriHandler.openUri("https://developer.microsoft.com/microsoft-edge/webview2/")
                    }
                    .padding(vertical = 4.dp),
                style = MarketType.label,
                color = MarketColors.Primary,
            )
        }
    }
}

@Composable
private fun ChartLoading(modifier: Modifier) {
    Box(
        modifier = modifier.background(MarketColors.Paper),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MarketColors.Signal)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "차트를 준비하고 있습니다.",
                style = MarketType.label,
                color = MarketColors.InkMuted,
            )
            Spacer(Modifier.height(14.dp))
            LoadingFinancialFact(
                factKey = "trading-chart",
                modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 480.dp),
                compact = true,
            )
        }
    }
}

@Composable
private fun ChartMessage(
    text: String,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.background(MarketColors.Paper).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MarketType.label,
            color = MarketColors.InkMuted,
        )
    }
}

private fun createPayload(
    symbol: String,
    resolution: String,
    market: Market,
    priceMinMove: Double,
    rangeKey: String,
    bars: List<PriceBar>,
    visibleDurationSeconds: Long?,
    averagePrice: Double?,
    trades: List<Trade>,
    relatedNews: List<NewsStoryUi>,
): MarketChartPayload {
    val firstTime = bars.first().startTime.epochSeconds
    val lastTime = bars.last().startTime.epochSeconds
    val visibleFrom = visibleDurationSeconds?.let { duration ->
        maxOf(firstTime, lastTime - duration)
    }
    return MarketChartPayload(
        instrumentId = "${market.name}:$symbol",
        symbol = symbol,
        resolution = resolution,
        timeZone = market.timeZoneId,
        pricePrecision = pricePrecision(priceMinMove),
        priceMinMove = priceMinMove,
        rangeKey = rangeKey,
        visibleFrom = visibleFrom,
        averagePrice = averagePrice?.takeIf(Double::isFinite),
        bars = bars.map { bar ->
            MarketChartBarPayload(
                time = bar.startTime.epochSeconds,
                open = bar.open,
                high = bar.high,
                low = bar.low,
                close = bar.close,
                volume = bar.volume,
                volumeText = bar.volume.toString(),
            )
        },
        markers = createMarkers(bars, trades, relatedNews),
    )
}

private fun pricePrecision(minMove: Double): Int {
    var scaled = minMove
    var precision = 0
    while (precision < 8 && abs(scaled - round(scaled)) > 1e-9) {
        scaled *= 10.0
        precision += 1
    }
    return precision
}

private fun createMarkers(
    bars: List<PriceBar>,
    trades: List<Trade>,
    relatedNews: List<NewsStoryUi>,
): List<MarketChartMarkerPayload> = buildList {
    trades.forEach { trade ->
        val time = bars.containingBarTime(trade.executedAt.epochSeconds) ?: return@forEach
        val isBuy = trade.side == OrderSide.BUY
        add(
            MarketChartMarkerPayload(
                time = time,
                position = if (isBuy) "belowBar" else "aboveBar",
                shape = if (isBuy) "arrowUp" else "arrowDown",
                color = if (isBuy) "#625CF6" else "#E08719",
                id = "trade:${trade.id}",
                text = "${if (isBuy) "B" else "S"} ${formatQuantity(trade.quantity)}",
            ),
        )
    }
    relatedNews.distinctBy { story -> story.event.id }.forEach { story ->
        val time = bars.snapEventToBarTime(story.event.startsAt.epochSeconds) ?: return@forEach
        val isCorporateAction = story.event.corporateActionReference != null
        val color = when (story.personalDirection) {
            ImpactDirection.POSITIVE -> "#E34D5B"
            ImpactDirection.NEGATIVE -> "#2F73D2"
            ImpactDirection.MIXED -> "#625CF6"
            ImpactDirection.NEUTRAL -> "#687480"
        }
        add(
            MarketChartMarkerPayload(
                time = time,
                position = "aboveBar",
                shape = if (isCorporateAction) "square" else "circle",
                color = color,
                id = "event:${story.event.id}",
                text = if (isCorporateAction) "CA" else "N",
                size = if (isCorporateAction) 1.2 else 1.0,
            ),
        )
    }
}.sortedWith(compareBy(MarketChartMarkerPayload::time, MarketChartMarkerPayload::id))

private fun List<PriceBar>.containingBarTime(epochSeconds: Long): Long? {
    var low = 0
    var high = lastIndex
    var candidate = -1
    while (low <= high) {
        val middle = (low + high).ushr(1)
        if (this[middle].startTime.epochSeconds <= epochSeconds) {
            candidate = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    val bar = getOrNull(candidate) ?: return null
    return bar.startTime.epochSeconds.takeIf { epochSeconds < bar.endTime.epochSeconds }
}

private fun List<PriceBar>.snapEventToBarTime(epochSeconds: Long): Long? {
    if (isEmpty() || epochSeconds < first().startTime.epochSeconds) return null
    var low = 0
    var high = lastIndex
    var candidate = -1
    while (low <= high) {
        val middle = (low + high).ushr(1)
        if (epochSeconds < this[middle].endTime.epochSeconds) {
            candidate = middle
            high = middle - 1
        } else {
            low = middle + 1
        }
    }
    return getOrNull(candidate)?.startTime?.epochSeconds
}

@OptIn(ExperimentalEncodingApi::class)
private fun MarketChartPayload.toJavaScriptArgument(): String {
    val encoded = Base64.Default.encode(chartJson.encodeToString(this).encodeToByteArray())
    return chartJson.encodeToString(encoded)
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadChartHtml(): String = chartAssetMutex.withLock {
    cachedChartHtml ?: run {
        val lightweightCharts = Res.readBytes(LIGHTWEIGHT_CHARTS_RESOURCE).decodeToString()
        val chartHost = Res.readBytes(CHART_HOST_RESOURCE).decodeToString()
        buildChartHtml(lightweightCharts, chartHost).also { cachedChartHtml = it }
    }
}

private fun buildChartHtml(
    lightweightCharts: String,
    chartHost: String,
): String = """
    <!doctype html>
    <html lang="ko">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-market-ledger-chart'; style-src 'nonce-market-ledger-chart'; img-src data:; font-src 'none'; connect-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'">
        <title>market-ledger-chart-loading</title>
        <style nonce="market-ledger-chart">
            :root { color-scheme: light; font-family: Inter, Pretendard, 'Noto Sans KR', system-ui, sans-serif; }
            * { box-sizing: border-box; }
            html, body { width: 100%; height: 100%; margin: 0; overflow: hidden; background: #FCFDFE; }
            body { user-select: none; -webkit-user-select: none; }
            #chart { position: absolute; inset: 0; }
            #legend {
                position: absolute; z-index: 4; top: 8px; left: 10px; right: 78px;
                display: flex; flex-wrap: wrap; align-items: center; gap: 5px 10px;
                min-height: 28px; padding: 5px 8px; pointer-events: none;
                color: #687480; background: rgba(252, 253, 254, 0.90);
                border: 1px solid rgba(221, 227, 232, 0.82); border-radius: 5px;
                font-size: 11px; line-height: 1.2; font-variant-numeric: tabular-nums;
                backdrop-filter: blur(5px);
            }
            #symbol { color: #17222D; font-weight: 700; letter-spacing: 0.02em; }
            #time { color: #687480; }
            .metric b { color: #17222D; font-weight: 650; }
            .metric.rise b, #change[data-tone='rise'] { color: #E34D5B; }
            .metric.fall b, #change[data-tone='fall'] { color: #2F73D2; }
            .metric.amber b { color: #E08719; }
            .metric.signal b { color: #625CF6; }
            #empty-state {
                position: absolute; z-index: 3; inset: 0; display: grid; place-items: center;
                color: #687480; background: #FCFDFE; font-size: 12px;
            }
            #empty-state[hidden] { display: none; }
        </style>
    </head>
    <body>
        <div id="chart" aria-label="주식 OHLCV 차트"></div>
        <div id="legend" aria-live="polite">
            <span id="symbol">MARKET</span><span id="time">준비 중</span>
            <span class="metric">O <b id="open">-</b></span>
            <span class="metric rise">H <b id="high">-</b></span>
            <span class="metric fall">L <b id="low">-</b></span>
            <span class="metric">C <b id="close">-</b></span>
            <span class="metric">Δ <b id="change">-</b></span>
            <span class="metric">V <b id="volume">-</b></span>
            <span class="metric amber">MA5 <b id="ma5">-</b></span>
            <span class="metric signal">MA20 <b id="ma20">-</b></span>
        </div>
        <div id="empty-state">가격 데이터가 없습니다.</div>
        <script nonce="market-ledger-chart">$lightweightCharts</script>
        <script nonce="market-ledger-chart">$chartHost</script>
    </body>
    </html>
""".trimIndent()
