package com.amond.kmpbook.domain.model.fund

/**
 * 종목팩과 저장 상태가 공유하는 안정적인 벤치마크 버전 키다.
 *
 * 참조한 실행 방법론 코드는 JSON fingerprint 밖에 있으므로 그 동작이 바뀌면 방법론 버전과 이를
 * 감싸는 [BenchmarkRef]의 [version]을 함께 새로 발급해야 한다.
 *
 * @property version 선언 데이터와 참조한 실행 동작을 함께 식별하는 벤치마크 계약 버전.
 */
data class BenchmarkRef(
    val benchmarkId: String,
    val version: Int,
) : Comparable<BenchmarkRef> {
    init {
        require(ID_PATTERN.matches(benchmarkId)) { "벤치마크 ID 형식이 올바르지 않습니다." }
        require(version in 1..MAX_VERSION) { "벤치마크 버전은 1~$MAX_VERSION 사이여야 합니다." }
    }

    override fun compareTo(other: BenchmarkRef): Int {
        val idComparison = benchmarkId.compareTo(other.benchmarkId)
        return if (idComparison != 0) idComparison else version.compareTo(other.version)
    }

    companion object {
        const val MAX_ID_LENGTH: Int = 160
        const val MAX_VERSION: Int = 1_000_000

        private val ID_PATTERN: Regex = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
