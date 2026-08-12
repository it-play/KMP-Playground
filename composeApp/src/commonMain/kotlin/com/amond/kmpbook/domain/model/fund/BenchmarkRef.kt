package com.amond.kmpbook.domain.model.fund

/** 종목팩과 저장 상태가 공유하는 안정적인 벤치마크 버전 키다. */
data class BenchmarkRef(
    val benchmarkId: String,
    val version: Int,
) : Comparable<BenchmarkRef> {
    init {
        require(ID_PATTERN.matches(benchmarkId)) { "벤치마크 ID 형식이 올바르지 않습니다." }
        require(version in 1..MAX_VERSION) { "벤치마크 버전은 1~$MAX_VERSION 사이여야 합니다." }
    }

    override fun compareTo(other: BenchmarkRef): Int =
        compareValuesBy(this, other, BenchmarkRef::benchmarkId, BenchmarkRef::version)

    companion object {
        const val MAX_ID_LENGTH: Int = 160
        const val MAX_VERSION: Int = 1_000_000

        private val ID_PATTERN: Regex = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
