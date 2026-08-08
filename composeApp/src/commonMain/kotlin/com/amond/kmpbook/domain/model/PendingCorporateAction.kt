package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.time.Instant

/**
 * [quantityMultiplier]는 기존 수량에 곱하는 배수다. 2-for-1 분할은 2.0,
 * 1-for-10 병합은 0.1이며 가격과 주당원가는 이 값으로 나눈다.
 */
data class PendingCorporateAction(
    val id: String,
    val stockId: String,
    val kind: CorporateActionKind,
    val announcedAt: Instant,
    val effectiveNotBefore: Instant,
    val quantityMultiplier: Double,
    val source: CorporateActionSource = CorporateActionSource.CAMPAIGN_RULE,
    val rationale: String,
) {
    init {
        require(id.isNotBlank() && stockId.isNotBlank() && rationale.isNotBlank())
        require(effectiveNotBefore > announcedAt)
        require(quantityMultiplier > 0.0 && quantityMultiplier.isFinite())
        require(abs(quantityMultiplier - 1.0) > 1e-9)
        require(
            (kind == CorporateActionKind.FORWARD_SPLIT && quantityMultiplier > 1.0) ||
                (kind == CorporateActionKind.REVERSE_SPLIT && quantityMultiplier < 1.0),
        ) { "분할·병합 방향과 수량 배수가 일치해야 합니다." }
    }
}
