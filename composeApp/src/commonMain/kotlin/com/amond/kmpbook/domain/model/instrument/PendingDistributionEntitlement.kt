package com.amond.kmpbook.domain.model.instrument

import com.amond.kmpbook.domain.model.market.Currency
import kotlinx.datetime.LocalDate

/** 분배락일 보유량으로 확정되어 지급일까지 매매와 독립적으로 존속하는 현금 권리다. */
data class PendingDistributionEntitlement(
    val id: String,
    val originId: String,
    val stockId: String,
    val exDate: LocalDate,
    val recordDate: LocalDate,
    val payDate: LocalDate,
    val currency: Currency,
    val grossPerUnit: Double,
    val entitledQuantity: Double,
    val taxableCoverageRatio: Double,
) {
    init {
        require(id.isNotBlank() && originId.isNotBlank() && stockId.isNotBlank())
        require(exDate <= recordDate && recordDate <= payDate)
        require(grossPerUnit.isFinite() && grossPerUnit in 0.0..MAX_FUND_REFERENCE_VALUE && grossPerUnit != 0.0)
        require(entitledQuantity.isFinite() && entitledQuantity in 0.0..MAX_FUND_REFERENCE_VALUE && entitledQuantity != 0.0)
        require(taxableCoverageRatio.isFinite() && taxableCoverageRatio in 0.0..1.0)
        require(
            grossReceivableAmount().let { gross ->
                gross.isFinite() && gross in 0.0..MAX_FUND_REFERENCE_VALUE && gross != 0.0
            },
        )
    }
}
