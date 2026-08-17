package com.amond.kmpbook.domain.model.instrument

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * 분배락 시점의 보유권과 좌당 금액이 어느 회계 경계에서 생겼는지 보존하는 영구 원장이다.
 * 보유 수량과 ROC 원가조정 결과를 이 원장에 동결하고, [accountingSequence] 전
 * 거래·기업행동·FIFO lot을 재생해 그 복제값을 독립 검증한다.
 */
data class DistributionEntitlementOrigin(
    val id: String,
    val stockId: String,
    val exDate: LocalDate,
    val establishedAt: Instant,
    val amountBasis: DistributionAmountBasis,
    val grossPerUnit: Double,
    /** ex-date에 권리가 확정된 보유 수량. 이 시점 FIFO lot 집합의 canonical 합계다. */
    val entitledQuantity: Double,
    /**
     * gross 중 배당소득으로 분류되는 비율. 미국 ETF·CEF만 1보다 작을 수 있고,
     * 한국 상장 ETF는 국내 과세표준 규칙과 분리해 항상 1이다.
     */
    val taxableCoverageRatio: Double,
    /** ROC 원가 조정에만 사용하는 ex-date 관측 원화환산율. 지급일 환율과 분리한다. */
    val taxBasisExchangeRateToKrw: Double,
    /** ex-date gross와 coverage에서 통화 최소단위로 확정한 ROC 금액이다. */
    val returnOfCapitalAmount: Double,
    /** ex-date FIFO 원가를 모두 소진하고 남아 지급연도 양도이득으로 이월할 원화 금액이다. */
    val excessReturnOfCapitalGainKrw: Long,
    val accruedDistributionPerUnitBeforeEx: Double,
    val navPerUnitBeforeEx: Double,
    val navPerUnitAfterEx: Double,
    val accountingSequence: Long,
) {
    init {
        require(id.isNotBlank() && stockId.isNotBlank())
        require(grossPerUnit.isFinite() && grossPerUnit in 0.0..MAX_FUND_REFERENCE_VALUE && grossPerUnit != 0.0)
        require(
            entitledQuantity.isFinite() &&
                entitledQuantity in 0.0..MAX_FUND_REFERENCE_VALUE && entitledQuantity != 0.0,
        )
        require(taxableCoverageRatio.isFinite() && taxableCoverageRatio in 0.0..1.0)
        require(taxBasisExchangeRateToKrw.isFinite() && taxBasisExchangeRateToKrw > 0.0)
        require(
            returnOfCapitalAmount.isFinite() &&
                returnOfCapitalAmount in 0.0..MAX_FUND_REFERENCE_VALUE,
        )
        require(excessReturnOfCapitalGainKrw >= 0L)
        require(taxableCoverageRatio < 1.0 || returnOfCapitalAmount == 0.0)
        require(
            accruedDistributionPerUnitBeforeEx.isFinite() &&
                accruedDistributionPerUnitBeforeEx in 0.0..MAX_FUND_REFERENCE_VALUE,
        )
        require(navPerUnitBeforeEx.isFinite() && navPerUnitBeforeEx in MIN_FUND_REFERENCE_VALUE..MAX_FUND_REFERENCE_VALUE)
        require(navPerUnitAfterEx.isFinite() && navPerUnitAfterEx in MIN_FUND_REFERENCE_VALUE..MAX_FUND_REFERENCE_VALUE)
        require(
            navPerUnitAfterEx.toBits() ==
                (navPerUnitBeforeEx - grossPerUnit).coerceAtLeast(MIN_FUND_REFERENCE_VALUE).toBits(),
        )
        require(accountingSequence > 0L)
    }
}
