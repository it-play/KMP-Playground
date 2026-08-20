package com.amond.kmpbook.domain.history

import com.amond.kmpbook.domain.model.history.HistoricalScenarioPack
import kmpbook.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Compose 번들에서 기본 2026년 8월 역사 시나리오와 해시 연결 리소스를 읽는다. */
object DesktopHistoricalScenarioLoader {
    const val AUGUST_2026_MANIFEST_PATH: String =
        "files/scenarios/august_2026/historical_scenario_v2.json"

    suspend fun loadAugust2026(): HistoricalScenarioPack {
        val manifestBytes = withContext(Dispatchers.IO) {
            Res.readBytes(AUGUST_2026_MANIFEST_PATH)
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
}
