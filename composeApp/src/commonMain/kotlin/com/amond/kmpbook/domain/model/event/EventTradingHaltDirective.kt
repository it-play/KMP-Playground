package com.amond.kmpbook.domain.model.event

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.protection.core.TradingHaltReason

/**
 * 이벤트 ID를 해석하지 않고도 거래정지 규칙을 재현하기 위한 불변 지시자다.
 * 현재 중요정보 공시 정지는 KRX 상장 종목에 정확히 30분 동안만 적용한다.
 */
data class EventTradingHaltDirective(
    val kind: EventTradingHaltKind,
    val reason: TradingHaltReason,
    val eligibleMarkets: Set<Market>,
    val durationMinutes: Int,
    val detail: String,
) {
    init {
        val violation = semanticInvariantViolation()
        require(violation == null) { violation.orEmpty() }
    }

    fun semanticInvariantViolation(): String? = when {
        eligibleMarkets.isEmpty() -> "이벤트 거래정지에는 적용 시장이 필요합니다."
        durationMinutes <= 0 -> "이벤트 거래정지 기간은 양수여야 합니다."
        detail.isBlank() -> "이벤트 거래정지 안내는 비어 있을 수 없습니다."
        kind == EventTradingHaltKind.MATERIAL_DISCLOSURE &&
            reason != TradingHaltReason.MATERIAL_DISCLOSURE ->
            "중요정보 공시 거래정지는 중요정보 공시 사유를 사용해야 합니다."
        kind == EventTradingHaltKind.MATERIAL_DISCLOSURE &&
            eligibleMarkets != setOf(Market.KOSPI, Market.KOSDAQ) ->
            "중요정보 공시 거래정지는 KRX 시장에만 적용해야 합니다."
        kind == EventTradingHaltKind.MATERIAL_DISCLOSURE && durationMinutes != 30 ->
            "중요정보 공시 거래정지는 정확히 30분이어야 합니다."
        else -> null
    }
}
