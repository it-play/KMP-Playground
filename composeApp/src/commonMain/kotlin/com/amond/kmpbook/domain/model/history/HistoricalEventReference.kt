package com.amond.kmpbook.domain.model.history

/** 저장된 게임 뉴스가 번들 역사 시나리오의 정확한 사건을 가리키는 불변 참조다. */
data class HistoricalEventReference(
    val scenarioId: String,
    val scenarioVersion: Int,
    val scenarioContentSha256: String,
    val occurrenceId: String,
) {
    init {
        require(scenarioId.isNotBlank()) { "역사 사건 참조의 시나리오 ID는 비어 있을 수 없습니다." }
        require(scenarioVersion > 0) { "역사 사건 참조의 시나리오 버전은 1 이상이어야 합니다." }
        require(SHA_256_PATTERN.matches(scenarioContentSha256)) {
            "역사 사건 참조의 콘텐츠 해시는 소문자 SHA-256 64자리여야 합니다."
        }
        require(occurrenceId.isNotBlank()) { "역사 사건 참조의 발생 ID는 비어 있을 수 없습니다." }
    }

    companion object {
        fun from(
            pack: HistoricalScenarioPack,
            occurrence: HistoricalEventOccurrence,
        ): HistoricalEventReference = pack.eventReference(occurrence.id)

        private val SHA_256_PATTERN: Regex = Regex("[0-9a-f]{64}")
    }
}
