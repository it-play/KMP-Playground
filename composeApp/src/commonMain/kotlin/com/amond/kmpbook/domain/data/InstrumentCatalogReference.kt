package com.amond.kmpbook.domain.data

/** 저장·재개 시 같은 순서와 콘텐츠의 종목팩을 사용했는지 확인하는 카탈로그 참조다. */
class InstrumentCatalogReference(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    orderedSources: Iterable<InstrumentCatalogSourceReference>,
) {
    val orderedSources: List<InstrumentCatalogSourceReference> = orderedSources.toList()

    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "지원하지 않는 종목 카탈로그 참조 schemaVersion입니다: $schemaVersion"
        }
        require(this.orderedSources.isNotEmpty()) { "종목 카탈로그 참조에는 하나 이상의 출처가 필요합니다." }
        require(this.orderedSources.size <= MAX_SOURCES) {
            "종목 카탈로그 참조에는 최대 ${MAX_SOURCES}개 출처만 허용됩니다."
        }
        require(
            this.orderedSources.distinctBy(InstrumentCatalogSourceReference::sourceId).size ==
                this.orderedSources.size,
        ) {
            "종목 카탈로그 참조에 중복된 sourceId가 있습니다."
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is InstrumentCatalogReference &&
            schemaVersion == other.schemaVersion &&
            orderedSources == other.orderedSources

    override fun hashCode(): Int = 31 * schemaVersion + orderedSources.hashCode()

    override fun toString(): String =
        "InstrumentCatalogReference(schemaVersion=$schemaVersion, orderedSources=$orderedSources)"

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
        /** 기본팩 하나와 캠페인당 최대 128개 활성 모드팩을 합친 상한이다. */
        const val MAX_SOURCES: Int = 129
    }
}
