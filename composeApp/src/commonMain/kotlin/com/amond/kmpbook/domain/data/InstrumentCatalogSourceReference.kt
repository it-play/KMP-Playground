package com.amond.kmpbook.domain.data

/** 저장 데이터가 참조한 종목팩 한 개의 안정 식별자와 원본 콘텐츠 해시다. */
data class InstrumentCatalogSourceReference(
    val sourceId: String,
    val contentSha256: String,
) {
    init {
        require(sourceId.isNotBlank()) { "종목 카탈로그 sourceId는 비어 있을 수 없습니다." }
        require(sourceId == sourceId.trim()) { "종목 카탈로그 sourceId 앞뒤에는 공백을 둘 수 없습니다." }
        require(sourceId.length <= InstrumentPack.MAX_SOURCE_ID_LENGTH) {
            "종목 카탈로그 sourceId가 너무 깁니다."
        }
        require(sourceId.none(Char::isISOControl)) { "종목 카탈로그 sourceId에 제어 문자를 사용할 수 없습니다." }
        require(SHA_256_PATTERN.matches(contentSha256)) {
            "종목 카탈로그 콘텐츠 해시는 소문자 SHA-256 64자리여야 합니다."
        }
    }

    private companion object {
        val SHA_256_PATTERN: Regex = Regex("[0-9a-f]{64}")
    }
}
