package com.amond.kmpbook.domain.model.corporateaction

import kotlin.math.abs
import kotlin.time.Instant

data class CorporateActionRecord(
    val id: String,
    val stockId: String,
    val kind: CorporateActionKind,
    val announcedAt: Instant,
    /** 공시 당시 확정한 최초 효력 가능 시각. 적용 후에도 공시 계보를 정확히 보존한다. */
    val effectiveNotBefore: Instant,
    val effectiveAt: Instant,
    val quantityMultiplier: Double,
    val preActionPrice: Double,
    val postActionPrice: Double,
    val source: CorporateActionSource,
    val rationale: String,
    /** 같은 시각의 체결·분배와 저장 전 순서를 보존하는 전역 회계 순번. */
    val accountingSequence: Long,
) {
    init {
        require(id.isNotBlank() && stockId.isNotBlank() && rationale.isNotBlank())
        require(effectiveNotBefore > announcedAt)
        require(effectiveAt >= effectiveNotBefore)
        require(quantityMultiplier > 0.0 && quantityMultiplier.isFinite())
        require(preActionPrice > 0.0 && postActionPrice > 0.0)
        require(accountingSequence > 0L)
        require(abs(preActionPrice / quantityMultiplier - postActionPrice) <= maxOf(0.02, postActionPrice * 0.02)) {
            "기업행동 전후 가격이 비율과 일치하지 않습니다."
        }
    }

    val isValueNeutral: Boolean get() = true
}
