package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.time.Instant

/** 만기 접근 증권·sleeve를 새 만기군으로 교체한 구성 원장이다. */
data class FixedIncomeRollRecord(
    val id: String,
    val benchmarkRef: BenchmarkRef,
    val removedAssetIds: List<String>,
    val addedAssetIds: List<String>,
    val effectiveAt: Instant,
    val revision: Long,
) {
    init {
        require(id.isNotBlank() && id.length <= 512)
        require(removedAssetIds.isNotEmpty())
        require(removedAssetIds.size == addedAssetIds.size)
        require(removedAssetIds == removedAssetIds.distinct().sorted())
        require(addedAssetIds == addedAssetIds.distinct().sorted())
        require(removedAssetIds.none(addedAssetIds::contains))
        require(revision > 0L)
    }
}
