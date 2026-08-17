package com.amond.kmpbook.ui.charts.trading

import androidx.compose.ui.Modifier
import dev.nucleusframework.window.tao.consumeOverlayPointerEvents
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

private const val WEBVIEW2_CLIENT_ID = "{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}"
private val webViewVersionPattern = Regex("""\d+(?:\.\d+){3}""")

internal actual fun isNativeChartRuntimeAvailable(): Boolean {
    if (!isWindows()) return true

    return listOf(
        "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\EdgeUpdate\\Clients\\$WEBVIEW2_CLIENT_ID",
        "HKLM\\SOFTWARE\\Microsoft\\EdgeUpdate\\Clients\\$WEBVIEW2_CLIENT_ID",
        "HKCU\\Software\\Microsoft\\EdgeUpdate\\Clients\\$WEBVIEW2_CLIENT_ID",
    ).any(::hasUsableWebView2Runtime)
}

internal actual fun nativeChartDataDirectory(): String? {
    if (!isWindows()) return null

    val preferred = preferredChartDataDirectory()
    val fallback = runCatching {
        System.getProperty("java.io.tmpdir")
            ?.takeIf(String::isNotBlank)
            ?.let { tempDirectory -> Paths.get(tempDirectory, "MarketLedger2040", "WebView") }
    }.getOrNull()
    return preferred?.let(::createWritableDirectory)
        ?: fallback?.let(::createWritableDirectory)
}

internal actual fun Modifier.consumeNativeChartOverlayPointerEvents(): Modifier =
    consumeOverlayPointerEvents()

private fun isWindows(): Boolean =
    System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

private fun preferredChartDataDirectory(): Path? = runCatching {
    val userHome = System.getProperty("user.home")?.takeIf(String::isNotBlank)
    val appDataRoot = System.getenv("LOCALAPPDATA")
        ?.takeIf(String::isNotBlank)
        ?.let(Paths::get)
        ?: userHome?.let { home -> Paths.get(home, "AppData", "Local") }
        ?: return@runCatching null
    appDataRoot.resolve("MarketLedger2040").resolve("WebView")
}.getOrNull()

private fun createWritableDirectory(path: Path): String? = runCatching {
    Files.createDirectories(path)
        .takeIf(Files::isWritable)
        ?.toAbsolutePath()
        ?.normalize()
        ?.toString()
}.getOrNull()

private fun hasUsableWebView2Runtime(registryKey: String): Boolean = runCatching {
    val process = ProcessBuilder("reg.exe", "query", registryKey, "/v", "pv")
        .redirectErrorStream(true)
        .start()
    if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroy()
        return@runCatching false
    }
    if (process.exitValue() != 0) return@runCatching false

    val version = webViewVersionPattern.find(process.inputStream.bufferedReader().use { it.readText() })
        ?.value
    version != null && version != "0.0.0.0"
}.getOrDefault(false)
