package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

internal fun isOrderlyProductTerminationStage(
    reason: ListingLifecycleReason?,
    status: ListingLifecycleStatus,
): Boolean = reason in setOf(
    ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION,
    ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION,
) && status in setOf(
    ListingLifecycleStatus.DELISTING_SCHEDULED,
    ListingLifecycleStatus.LIQUIDATION_PENDING,
    ListingLifecycleStatus.TERMINATED,
)

internal fun Iterable<ListingRiskTag>.hasOrderlyProductTerminationSignal(): Boolean = any { tag ->
    tag == ListingRiskTag.ETF_LIQUIDATION_APPROVED ||
        tag == ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION
}

/**
 * 계약상 만기·자진 청산보다 먼저 거래소의 강제 심사·정지 절차를 유지해야 하는 사유다.
 * 이 상태가 해제되기 전에는 새로운 orderly termination 공시가 기존 강제 절차를 덮지 않는다.
 */
internal fun ListingLifecycleReason.blocksOrderlyProductTermination(): Boolean = this in setOf(
    ListingLifecycleReason.BANKRUPTCY_OR_INSOLVENCY,
    ListingLifecycleReason.ISSUER_ELIGIBILITY_FAILURE,
    ListingLifecycleReason.UNDERLYING_INDEX_UNAVAILABLE,
    ListingLifecycleReason.LIQUIDITY_PROVIDER_FAILURE,
    ListingLifecycleReason.AUDIT_OR_DISCLOSURE_FAILURE,
    ListingLifecycleReason.SERIOUS_COMPLIANCE_EVENT,
    ListingLifecycleReason.CORE_BUSINESS_SUSPENSION,
)

/** 현재 관측된 공시 중요도까지 반영해 orderly termination보다 먼저 새 강제 절차를 시작할지 판정한다. */
internal fun ListingLifecycleReason.preemptsOrderlyProductTermination(
    severity: ListingRiskSeverity,
): Boolean = when (this) {
    ListingLifecycleReason.BANKRUPTCY_OR_INSOLVENCY -> true
    ListingLifecycleReason.ISSUER_ELIGIBILITY_FAILURE,
    ListingLifecycleReason.UNDERLYING_INDEX_UNAVAILABLE,
    ListingLifecycleReason.AUDIT_OR_DISCLOSURE_FAILURE,
    ListingLifecycleReason.SERIOUS_COMPLIANCE_EVENT,
    ListingLifecycleReason.CORE_BUSINESS_SUSPENSION,
    -> severity.level >= ListingRiskSeverity.HIGH.level
    ListingLifecycleReason.LIQUIDITY_PROVIDER_FAILURE -> severity == ListingRiskSeverity.CRITICAL
    else -> false
}
