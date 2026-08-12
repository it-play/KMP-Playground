package com.amond.kmpbook.domain.model.fundproduct

/** 일일 목표배율 상품 한 구간의 공정가치 변화다. */
data class DailyResetAdvance(
    val state: DailyResetState,
    val productLogReturn: Double,
    val resetApplied: Boolean,
) {
    init {
        require(productLogReturn.isFinite())
        require(!resetApplied || state.lifecycle == DailyResetLifecycle.ACTIVE)
    }
}
