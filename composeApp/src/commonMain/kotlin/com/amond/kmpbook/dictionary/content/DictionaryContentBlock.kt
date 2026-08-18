package com.amond.kmpbook.dictionary.content

data class DictionaryContentBlock(
    val kind: DictionaryBlockKind,
    val text: String,
    val level: Int? = null,
) {
    init {
        when (kind) {
            DictionaryBlockKind.HEADING -> {
                require(level in 1..3) { "사전 제목 블록의 level은 1~3이어야 합니다." }
                require(text.isNotBlank()) { "사전 제목 블록의 text는 비어 있을 수 없습니다." }
            }

            DictionaryBlockKind.DIVIDER -> {
                require(level == null) { "사전 구분선 블록은 level을 가질 수 없습니다." }
                require(text.isEmpty()) { "사전 구분선 블록의 text는 빈 문자열이어야 합니다." }
            }

            else -> {
                require(level == null) { "${kind.name} 사전 블록은 level을 가질 수 없습니다." }
                require(text.isNotBlank()) { "${kind.name} 사전 블록의 text는 비어 있을 수 없습니다." }
            }
        }
    }
}
