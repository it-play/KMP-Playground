package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.ListingLifecycleReason
import com.amond.kmpbook.domain.model.ListingLifecycleState
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.ListingRecoveryCondition
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class ListingRemediationDecisionStatus {
    NOT_APPLICABLE,
    PENDING,
    CURED,
    NOT_CURED,
}

/**
 * 질적 상장 사유의 개선 공시를 결정하는 저장 독립적 결과다. 가격·시가총액·유동성처럼
 * 관측값으로 직접 회복을 증명할 수 있는 사유는 상장 생애주기 엔진이 자체 판단한다.
 */
data class ListingRemediationDecision(
    val status: ListingRemediationDecisionStatus,
    val dueOn: LocalDate? = null,
    val recoveryCondition: ListingRecoveryCondition? = null,
    val successProbability: Double? = null,
) {
    init {
        require(status == ListingRemediationDecisionStatus.CURED || recoveryCondition == null)
        require(successProbability == null || successProbability in 0.0..1.0)
    }
}

/**
 * 이벤트 기간 만료를 곧바로 결함 해소로 보지 않는다. 소스 위험이 끝난 뒤에도 별도
 * 개선 심사일과 결정론적 성공·미해소 결과를 거쳐야 회복 조건을 발행한다.
 */
object ListingRemediationPolicy {
    fun evaluate(
        state: ListingLifecycleState,
        tradingDate: LocalDate,
        stageStartedOn: LocalDate,
        campaignSeed: Long,
        sourceRiskActive: Boolean,
        alreadyDecidedForStage: Boolean,
    ): ListingRemediationDecision {
        val reason = state.activeReason ?: return notApplicable()
        val recovery = recoveryCondition(reason) ?: return notApplicable()
        if (state.status !in REMEDIABLE_STATUSES) return notApplicable()

        val dueOn = stageStartedOn.plus(delayDays(state.status), DateTimeUnit.DAY)
        if (tradingDate < dueOn || sourceRiskActive || alreadyDecidedForStage) {
            return ListingRemediationDecision(
                status = ListingRemediationDecisionStatus.PENDING,
                dueOn = dueOn,
            )
        }

        val probability = successProbability(reason, state.status)
        val key = buildString {
            append(state.stockId)
            append(':')
            append(reason.name)
            append(':')
            append(state.designatedOn)
            append(':')
            append(state.status.name)
            append(':')
            append(stageStartedOn)
        }
        val cured = DeterministicRandom.keyed(campaignSeed, key).nextBoolean(probability)
        return ListingRemediationDecision(
            status = if (cured) {
                ListingRemediationDecisionStatus.CURED
            } else {
                ListingRemediationDecisionStatus.NOT_CURED
            },
            dueOn = dueOn,
            recoveryCondition = recovery.takeIf { cured },
            successProbability = probability,
        )
    }

    fun recoveryCondition(reason: ListingLifecycleReason): ListingRecoveryCondition? = when (reason) {
        ListingLifecycleReason.KRX_LISTING_MAINTENANCE,
        ListingLifecycleReason.KRX_ADMINISTRATIVE_ISSUE,
        ListingLifecycleReason.US_LISTING_MAINTENANCE,
        -> ListingRecoveryCondition.FINANCIAL_DEFICIENCY_RESOLVED

        ListingLifecycleReason.AUDIT_OR_DISCLOSURE_FAILURE ->
            ListingRecoveryCondition.AUDIT_OR_DISCLOSURE_CURED

        ListingLifecycleReason.SERIOUS_COMPLIANCE_EVENT ->
            ListingRecoveryCondition.REGULATORY_CLEARANCE

        ListingLifecycleReason.CORE_BUSINESS_SUSPENSION -> ListingRecoveryCondition.BUSINESS_RESUMED
        ListingLifecycleReason.ISSUER_ELIGIBILITY_FAILURE ->
            ListingRecoveryCondition.ISSUER_ELIGIBILITY_RESTORED

        ListingLifecycleReason.UNDERLYING_INDEX_UNAVAILABLE ->
            ListingRecoveryCondition.UNDERLYING_INDEX_RESTORED

        ListingLifecycleReason.LIQUIDITY_PROVIDER_FAILURE ->
            ListingRecoveryCondition.LIQUIDITY_PROVIDER_REPLACED

        ListingLifecycleReason.US_MINIMUM_BID_PRICE,
        ListingLifecycleReason.US_MARKET_CAPITALIZATION,
        ListingLifecycleReason.LOW_TRADING_LIQUIDITY,
        ListingLifecycleReason.BANKRUPTCY_OR_INSOLVENCY,
        ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION,
        ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION,
        -> null
    }

    private fun delayDays(status: ListingLifecycleStatus): Int = when (status) {
        ListingLifecycleStatus.DEFICIENCY_NOTICE -> 5
        ListingLifecycleStatus.UNDER_REVIEW,
        ListingLifecycleStatus.TRADING_SUSPENDED,
        -> 2

        ListingLifecycleStatus.DELISTING_SCHEDULED -> 1
        else -> 0
    }

    private fun successProbability(
        reason: ListingLifecycleReason,
        status: ListingLifecycleStatus,
    ): Double {
        val initial = when (reason) {
            ListingLifecycleReason.KRX_LISTING_MAINTENANCE,
            ListingLifecycleReason.KRX_ADMINISTRATIVE_ISSUE,
            ListingLifecycleReason.US_LISTING_MAINTENANCE,
            -> 0.58

            ListingLifecycleReason.AUDIT_OR_DISCLOSURE_FAILURE -> 0.42
            ListingLifecycleReason.SERIOUS_COMPLIANCE_EVENT -> 0.25
            ListingLifecycleReason.CORE_BUSINESS_SUSPENSION -> 0.48
            ListingLifecycleReason.ISSUER_ELIGIBILITY_FAILURE -> 0.40
            ListingLifecycleReason.UNDERLYING_INDEX_UNAVAILABLE -> 0.70
            ListingLifecycleReason.LIQUIDITY_PROVIDER_FAILURE -> 0.75
            else -> 0.0
        }
        val stageMultiplier = when (status) {
            ListingLifecycleStatus.DEFICIENCY_NOTICE -> 1.0
            ListingLifecycleStatus.UNDER_REVIEW -> 0.75
            ListingLifecycleStatus.TRADING_SUSPENDED -> 0.55
            ListingLifecycleStatus.DELISTING_SCHEDULED -> 0.25
            else -> 0.0
        }
        return initial * stageMultiplier
    }

    private fun notApplicable() = ListingRemediationDecision(ListingRemediationDecisionStatus.NOT_APPLICABLE)

    private val REMEDIABLE_STATUSES = setOf(
        ListingLifecycleStatus.DEFICIENCY_NOTICE,
        ListingLifecycleStatus.UNDER_REVIEW,
        ListingLifecycleStatus.TRADING_SUSPENDED,
        ListingLifecycleStatus.DELISTING_SCHEDULED,
    )
}
