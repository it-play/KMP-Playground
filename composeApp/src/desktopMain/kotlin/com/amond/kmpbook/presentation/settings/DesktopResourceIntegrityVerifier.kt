package com.amond.kmpbook.presentation.settings

import com.amond.kmpbook.audio.resolveBackgroundMusicPlaylist
import com.amond.kmpbook.domain.history.DesktopHistoricalScenarioParser
import java.security.MessageDigest
import kmpbook.composeapp.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class ResourceIntegrityVerifier actual constructor() {
    actual suspend fun verify(): String? = withContext(Dispatchers.IO) {
        try {
            EXPECTED_COMPOSE_RESOURCES.forEach { (label, path, expectedHash) ->
                val bytes = Res.readBytes(path)
                if (bytes.sha256() != expectedHash) {
                    return@withContext "$label 리소스가 원본과 일치하지 않습니다."
                }
            }
            val manifestBytes = Res.readBytes(HISTORICAL_SCENARIO_MANIFEST_PATH)
            DesktopHistoricalScenarioParser.resourceReferences(manifestBytes).forEach { reference ->
                if (Res.readBytes(reference.path).sha256() != reference.contentSha256) {
                    return@withContext "역사 시나리오 조각이 manifest와 일치하지 않습니다: ${reference.path}"
                }
            }
            resolveBackgroundMusicPlaylist()
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            "리소스 검사를 완료하지 못했습니다: " +
                (error.message?.take(220)?.takeIf(String::isNotBlank) ?: "알 수 없는 오류")
        }
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

private val EXPECTED_COMPOSE_RESOURCES: List<Triple<String, String, String>> = listOf(
    Triple(
        "종목 카탈로그",
        "files/instruments/market_instrument_catalog_v6.json",
        "9b4eb7f1143a7f28217dac46f657a6ef269a511c6d8011d77bca89d66e0f1bdd",
    ),
    Triple(
        "2026년 8월 역사 시나리오 manifest",
        HISTORICAL_SCENARIO_MANIFEST_PATH,
        "08c11bd8d186693888b6ce3f6b922897707e79f79bb8ace8f2eabc748d2a834a",
    ),
    Triple(
        "금융 사전",
        "files/dictionary/index.json",
        "4401b5e95a14cff9d70d27de99b03c664b245b1fa61712a7e45172dbf7f53678",
    ),
    Triple(
        "차트 호스트",
        "files/charts/market_chart_host.js",
        "3cfb87c955b85dc030180eb27e0f1f3e99421706be36dd540eba5ae2c22dc3ef",
    ),
    Triple(
        "차트 런타임",
        "files/charts/tradingview_lightweight_charts_library.js",
        "3744c536efc9717bd911193b82aa2bc55741b1cfa46ee0d946dbb34307123f62",
    ),
    Triple(
        "앱 아이콘",
        "drawable/app_icon_market_ledger.png",
        "98a948f1b9fd90ad8701a3c4a197961ce902f9002891346e02b0d24986124f36",
    ),
    Triple(
        "기본 글꼴",
        "font/pretendard_regular.otf",
        "3ffbacde6ab8411f1d2db54bb9b1f0b3ee2a738932033722cf0388c06aed1c93",
    ),
    Triple(
        "중간 굵기 글꼴",
        "font/pretendard_medium.otf",
        "d39e50e4bb52b4993b6a4eeb821a171254745bd824446af01e1f616b89fface0",
    ),
    Triple(
        "강조 글꼴",
        "font/pretendard_semibold.otf",
        "c89bc43027dc7cde5726e96223376f8eec09302b2fc1f8147fd5b57cfc376118",
    ),
    Triple(
        "굵은 글꼴",
        "font/pretendard_bold.otf",
        "2e91915fab54df71cc9598ebf608b2bdb54c6fe3c066ac61dff0bc44fca71cc7",
    ),
)

private const val HISTORICAL_SCENARIO_MANIFEST_PATH: String =
    "files/scenarios/august_2026/historical_scenario_v2.json"
