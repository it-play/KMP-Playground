package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

data class ListingFinalDisposition(
    val type: ListingFinalDispositionType,
    val effectiveOn: LocalDate,
    val settlementDueOn: LocalDate? = null,
    val cashPerUnit: Double? = null,
    /** 청산 효력일에 확정된 권리 수량·취득원가. 이후 분할·배당과 분리해 지급한다. */
    val entitledQuantity: Double? = null,
    val entitledCostBasis: Double? = null,
) {
    init {
        semanticInvariantViolation()?.let { throw IllegalArgumentException(it) }
    }

    /**
     * Gson처럼 생성자를 거치지 않는 저장 경계에서도 같은 처분 규칙을 적용한다.
     * 현금 청산의 0원 회수와 무보유 계좌의 0 권리 수량·원가는 유효한 런타임 상태다.
     * 권리 수량과 원가를 언제 확정해야 하는지는 상태·원장 검증기가 판단한다.
     */
    fun semanticInvariantViolation(): String? {
        val dispositionType = type as ListingFinalDispositionType?
            ?: return "잔고 처분 유형이 필요합니다."
        if (dispositionType !in ListingFinalDispositionType.entries) {
            return "잔고 처분 유형이 유효하지 않습니다."
        }
        val dispositionEffectiveOn = effectiveOn as LocalDate?
            ?: return "잔고 처분 효력일이 필요합니다."
        if (cashPerUnit?.let { !it.isFinite() || it < 0.0 } == true) {
            return "청산 단가는 유한한 0 이상 값이어야 합니다."
        }
        if (entitledQuantity?.let { !it.isFinite() || it < 0.0 } == true) {
            return "확정 권리 수량은 유한한 0 이상 값이어야 합니다."
        }
        if (entitledCostBasis?.let { !it.isFinite() || it < 0.0 } == true) {
            return "확정 취득원가는 유한한 0 이상 값이어야 합니다."
        }
        if ((entitledQuantity == null) != (entitledCostBasis == null)) {
            return "확정 권리 수량과 취득원가는 함께 있거나 함께 없어야 합니다."
        }

        return when (dispositionType) {
            ListingFinalDispositionType.CASH_LIQUIDATION -> when {
                settlementDueOn == null || cashPerUnit == null ->
                    "현금 청산에는 지급일과 확정 단가가 필요합니다."
                settlementDueOn < dispositionEffectiveOn ->
                    "청산금 지급일은 처분 효력일보다 빠를 수 없습니다."
                else -> null
            }

            ListingFinalDispositionType.WORTHLESS_DISPOSITION -> when {
                settlementDueOn != null -> "무가치 처분에는 지급 예정일을 둘 수 없습니다."
                cashPerUnit != 0.0 -> "무가치 처분의 회수 단가는 0이어야 합니다."
                entitledQuantity != null -> "무가치 처분에는 현금 청산 권리를 둘 수 없습니다."
                else -> null
            }

            ListingFinalDispositionType.MARKET_SALE,
            ListingFinalDispositionType.OTC_TRANSFER,
            -> when {
                settlementDueOn != null -> "시장 매도·장외 이전에는 지급 예정일을 둘 수 없습니다."
                cashPerUnit != null -> "시장 매도·장외 이전에는 청산 단가를 둘 수 없습니다."
                entitledQuantity != null -> "시장 매도·장외 이전에는 현금 청산 권리를 둘 수 없습니다."
                else -> null
            }
        }
    }
}
