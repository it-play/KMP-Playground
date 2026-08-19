package com.amond.kmpbook.domain.history

import com.amond.kmpbook.domain.model.history.HistoricalScenarioPack
import java.security.MessageDigest
import kmpbook.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Compose 번들에서 기본 2026년 8월 역사 시나리오와 해시 연결 리소스를 읽는다. */
object DesktopHistoricalScenarioLoader {
    const val AUGUST_2026_MANIFEST_PATH: String =
        "files/scenarios/august_2026/historical_scenario_v2.json"
    const val AUGUST_2026_MANIFEST_SHA256: String =
        "08c11bd8d186693888b6ce3f6b922897707e79f79bb8ace8f2eabc748d2a834a"

    suspend fun loadAugust2026(): HistoricalScenarioPack {
        val manifestBytes = withContext(Dispatchers.IO) {
            Res.readBytes(AUGUST_2026_MANIFEST_PATH)
        }
        require(manifestBytes.sha256() == AUGUST_2026_MANIFEST_SHA256) {
            "2026년 8월 역사 시나리오 manifest가 앱에 고정된 원본과 일치하지 않습니다."
        }
        val references = withContext(Dispatchers.Default) {
            DesktopHistoricalScenarioParser.resourceReferences(manifestBytes)
        }
        val resourceBytes = withContext(Dispatchers.IO) {
            references.associate { reference -> reference.path to Res.readBytes(reference.path) }
        }
        return withContext(Dispatchers.Default) {
            DesktopHistoricalScenarioParser.parse(manifestBytes, resourceBytes)
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
