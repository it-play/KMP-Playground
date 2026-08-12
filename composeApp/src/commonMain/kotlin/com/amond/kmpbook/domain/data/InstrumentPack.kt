package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.instrument.StockDefinition

/**
 * 한 출처에서 읽은 종목 정의와 원본 콘텐츠의 식별 정보를 묶는다.
 *
 * [definitions]는 생성 시 복사하므로 호출자가 넘긴 가변 목록의 이후 변경에 영향을 받지 않는다.
 */
class InstrumentPack(
    val sourceId: String,
    val fingerprint: String,
    benchmarks: Iterable<BenchmarkDefinition>,
    definitions: Iterable<StockDefinition>,
) {
    val benchmarks: List<BenchmarkDefinition> = benchmarks.toList()
    val definitions: List<StockDefinition> = definitions.toList()
    val benchmarkCount: Int get() = benchmarks.size
    val instrumentCount: Int get() = definitions.size

    init {
        require(sourceId.isNotBlank()) { "종목팩 sourceId는 비어 있을 수 없습니다." }
        require(sourceId == sourceId.trim()) { "종목팩 sourceId 앞뒤에는 공백을 둘 수 없습니다." }
        require(sourceId.length <= MAX_SOURCE_ID_LENGTH) {
            "종목팩 sourceId는 ${MAX_SOURCE_ID_LENGTH}자 이하여야 합니다."
        }
        require(sourceId.none(Char::isISOControl)) { "종목팩 sourceId에 제어 문자를 사용할 수 없습니다." }
        require(SHA_256_PATTERN.matches(fingerprint)) {
            "종목팩 fingerprint는 소문자 SHA-256 64자리여야 합니다."
        }
        require(this.benchmarks.size <= MAX_BENCHMARKS) {
            "종목팩에는 최대 ${MAX_BENCHMARKS}개 벤치마크만 포함할 수 있습니다."
        }
        require(this.benchmarks.distinctBy(BenchmarkDefinition::ref).size == this.benchmarks.size) {
            "종목팩에 같은 (benchmarkId, version) 벤치마크가 중복 정의되었습니다."
        }
        require(this.definitions.isNotEmpty()) { "종목팩에는 하나 이상의 종목이 필요합니다." }
        require(this.definitions.size <= MAX_INSTRUMENTS) {
            "종목팩에는 최대 ${MAX_INSTRUMENTS}개 종목만 포함할 수 있습니다."
        }
        require(this.definitions.distinctBy(StockDefinition::id).size == this.definitions.size) {
            "종목팩에 중복된 종목 ID가 있습니다."
        }
        require(
            this.definitions.distinctBy { definition ->
                definition.market to definition.symbol.trim().uppercase()
            }.size == this.definitions.size,
        ) { "종목팩의 같은 시장에 중복된 종목 코드가 있습니다." }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is InstrumentPack &&
            sourceId == other.sourceId &&
            fingerprint == other.fingerprint &&
            benchmarks == other.benchmarks &&
            definitions == other.definitions

    override fun hashCode(): Int {
        var result = sourceId.hashCode()
        result = 31 * result + fingerprint.hashCode()
        result = 31 * result + benchmarks.hashCode()
        result = 31 * result + definitions.hashCode()
        return result
    }

    override fun toString(): String =
        "InstrumentPack(sourceId=$sourceId, fingerprint=$fingerprint, " +
            "benchmarkCount=$benchmarkCount, instrumentCount=$instrumentCount)"

    companion object {
        const val MAX_INSTRUMENTS: Int = 1_024
        const val MAX_BENCHMARKS: Int = 1_024
        const val MAX_SOURCE_ID_LENGTH: Int = 256

        private val SHA_256_PATTERN: Regex = Regex("[0-9a-f]{64}")
    }
}
