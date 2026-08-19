package com.amond.kmpbook.domain.model.history

/** 저장 게임을 정확히 하나의 검증된 역사 시나리오 콘텐츠에 결박하는 불변 참조다. */
data class HistoricalScenarioReference(
    val scenarioId: String,
    val scenarioVersion: Int,
    val contentSha256: String,
) {
    init {
        require(SCENARIO_ID_PATTERN.matches(scenarioId)) {
            "역사 시나리오 참조 ID 형식이 올바르지 않습니다."
        }
        require(scenarioVersion > 0) {
            "역사 시나리오 참조 버전은 1 이상이어야 합니다."
        }
        require(SHA_256_PATTERN.matches(contentSha256)) {
            "역사 시나리오 참조 콘텐츠 해시는 소문자 SHA-256 64자리여야 합니다."
        }
    }

    companion object {
        fun from(pack: HistoricalScenarioPack): HistoricalScenarioReference =
            HistoricalScenarioReference(
                scenarioId = pack.definition.id,
                scenarioVersion = pack.definition.version,
                contentSha256 = pack.contentSha256,
            )

        private val SCENARIO_ID_PATTERN: Regex = Regex("[a-z0-9][a-z0-9._:-]{2,127}")
        private val SHA_256_PATTERN: Regex = Regex("[0-9a-f]{64}")
    }
}
