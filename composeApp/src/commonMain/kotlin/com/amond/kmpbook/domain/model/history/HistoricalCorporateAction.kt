package com.amond.kmpbook.domain.model.history

import com.amond.kmpbook.domain.model.market.Currency
import kotlin.time.Instant

/** 외부 역사 자료에서 확인한 배당 또는 주식분할 발생이다. */
data class HistoricalCorporateAction(
    val id: String,
    val stockId: String,
    val effectiveAt: Instant,
    val kind: HistoricalCorporateActionKind,
    val cashAmount: Double? = null,
    val currency: Currency? = null,
    val splitNumerator: Long? = null,
    val splitDenominator: Long? = null,
    val sourceId: String,
) {
    init {
        require(ID_PATTERN.matches(id)) { "역사 기업행동 ID 형식이 올바르지 않습니다." }
        require(STOCK_ID_PATTERN.matches(stockId)) {
            "역사 기업행동 종목 ID 형식이 올바르지 않습니다."
        }
        require(sourceId.isNotBlank() && sourceId == sourceId.trim()) {
            "역사 기업행동 출처 ID는 비어 있거나 앞뒤 공백을 가질 수 없습니다."
        }
        when (kind) {
            HistoricalCorporateActionKind.CASH_DIVIDEND -> {
                require(cashAmount != null && cashAmount.isFinite() && cashAmount > 0.0) {
                    "현금배당에는 유한한 양수 금액이 필요합니다."
                }
                require(currency != null) { "현금배당에는 지급 통화가 필요합니다." }
                require(splitNumerator == null && splitDenominator == null) {
                    "현금배당에는 분할 비율을 지정할 수 없습니다."
                }
            }

            HistoricalCorporateActionKind.STOCK_SPLIT -> {
                require(cashAmount == null && currency == null) {
                    "주식분할에는 현금 금액이나 통화를 지정할 수 없습니다."
                }
                require(splitNumerator != null && splitNumerator > 0L) {
                    "주식분할 분자는 양수여야 합니다."
                }
                require(splitDenominator != null && splitDenominator > 0L) {
                    "주식분할 분모는 양수여야 합니다."
                }
                require(splitNumerator != splitDenominator) { "1:1 주식분할은 기록하지 않습니다." }
            }
        }
    }

    companion object {
        private val ID_PATTERN: Regex = Regex("[a-z0-9][a-z0-9._:-]{2,191}")
        private val STOCK_ID_PATTERN: Regex = Regex("[A-Z_]+:[^\\s:]+")
    }
}
