package com.amond.kmpbook.domain.model.history

/** 시나리오 manifest가 참조하는 불변 번들 리소스와 예상 콘텐츠 해시다. */
data class HistoricalScenarioResourceReference(
    val kind: HistoricalScenarioResourceKind,
    val path: String,
    val contentSha256: String,
    val recordCount: Int,
) {
    init {
        require(path.startsWith(RESOURCE_PATH_PREFIX) && path.endsWith(".json")) {
            "역사 시나리오 리소스는 $RESOURCE_PATH_PREFIX 아래 JSON이어야 합니다."
        }
        require(path == path.trim() && '\\' !in path && ".." !in path.split('/')) {
            "역사 시나리오 리소스 경로가 안전하지 않습니다."
        }
        require(SHA_256_PATTERN.matches(contentSha256)) {
            "역사 시나리오 리소스 해시는 소문자 SHA-256 64자리여야 합니다."
        }
        require(recordCount in 0..MAX_RECORDS_PER_RESOURCE) {
            "역사 시나리오 리소스 레코드 수가 허용 범위를 벗어났습니다."
        }
    }

    companion object {
        const val RESOURCE_PATH_PREFIX: String = "files/scenarios/"
        const val MAX_RECORDS_PER_RESOURCE: Int = 100_000

        private val SHA_256_PATTERN: Regex = Regex("[0-9a-f]{64}")
    }
}
