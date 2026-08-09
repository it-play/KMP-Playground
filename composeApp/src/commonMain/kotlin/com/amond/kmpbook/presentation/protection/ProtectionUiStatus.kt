package com.amond.kmpbook.presentation.protection

import com.amond.kmpbook.domain.model.market.Market
import kotlin.time.Instant
import kotlinx.datetime.plus

/**
 * 엔진 상태를 화면 문구로 바꾼 불변 모델이다. UI가 거래소 enum을 다시 해석하지 않도록
 * 주문 영향과 재개 조건까지 한곳에서 결정한다.
 */
data class ProtectionUiStatus(
    val id: String,
    val badgeLabel: String,
    val title: String,
    val summary: String,
    val orderImpact: String,
    val resumeGuidance: String,
    val ruleExplanation: String,
    val tone: ProtectionUiTone,
    val emphasis: ProtectionBadgeEmphasis,
    val priority: Int,
    val stockId: String? = null,
    val markets: Set<Market> = emptySet(),
    val endsAt: Instant? = null,
) {
    init {
        require(id.isNotBlank())
        require(badgeLabel.isNotBlank() && title.isNotBlank() && summary.isNotBlank())
        require(orderImpact.isNotBlank() && resumeGuidance.isNotBlank() && ruleExplanation.isNotBlank())
        require(priority >= 0)
    }
}
