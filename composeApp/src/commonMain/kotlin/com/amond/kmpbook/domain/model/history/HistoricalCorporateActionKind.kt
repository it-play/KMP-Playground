package com.amond.kmpbook.domain.model.history

/** 역사 가격을 원시 체결가로 유지할 때 별도로 재생해야 하는 기업행동 종류다. */
enum class HistoricalCorporateActionKind {
    CASH_DIVIDEND,
    STOCK_SPLIT,
}
