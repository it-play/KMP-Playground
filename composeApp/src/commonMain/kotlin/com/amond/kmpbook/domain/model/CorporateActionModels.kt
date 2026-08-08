package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.time.Instant

enum class CorporateActionKind(val displayName: String) {
    FORWARD_SPLIT("주식분할"),
    REVERSE_SPLIT("주식병합"),
}

enum class CorporateActionSource(val displayName: String) {
    CAMPAIGN_RULE("캠페인 가상 공시"),
    OFFICIAL_FIXTURE("기준일 실제 공시"),
}

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

data class CorporateActionRecord(
    val id: String,
    val stockId: String,
    val kind: CorporateActionKind,
    val announcedAt: Instant,
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
        require(effectiveAt >= announcedAt)
        require(quantityMultiplier > 0.0 && quantityMultiplier.isFinite())
        require(preActionPrice > 0.0 && postActionPrice > 0.0)
        require(accountingSequence > 0L)
        require(abs(preActionPrice / quantityMultiplier - postActionPrice) <= maxOf(0.02, postActionPrice * 0.02)) {
            "기업행동 전후 가격이 비율과 일치하지 않습니다."
        }
    }

    val isValueNeutral: Boolean get() = true
}

object CorporateActionMath {
    private val allowedRatios = listOf(2.0, 3.0, 4.0, 5.0, 10.0, 20.0)

    fun forwardMultiplier(price: Double, targetPrice: Double): Double {
        require(price > targetPrice && targetPrice > 0.0)
        val desired = price / targetPrice
        return allowedRatios.minBy { kotlin.math.abs(it - desired) }
    }

    fun reverseMultiplier(price: Double, targetPrice: Double): Double {
        require(price < targetPrice && price > 0.0)
        val desired = targetPrice / price
        return 1.0 / allowedRatios.minBy { kotlin.math.abs(it - desired) }
    }
}
