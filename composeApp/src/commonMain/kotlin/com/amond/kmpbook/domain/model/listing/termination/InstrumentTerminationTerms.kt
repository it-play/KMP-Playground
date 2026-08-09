package com.amond.kmpbook.domain.model.listing.termination

import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRiskTag
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationTerms
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationValuationMethod
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 하나의 상품 종료 공시에 고정되는 불변 계약 조건이다.
 *
 * 계약상 만기는 [contractualDate]를, 나머지 종료 사유는 [effectiveNotBefore]를 사용한다.
 * 발행사 가속상환의 [accelerationRecoveryRate]는 이벤트 생성 시 한 번 결정해 저장하므로
 * 저장·재개 이후에도 효력일, 우선순위, 지급 단가가 같은 공시에서 파생된다.
 */
data class InstrumentTerminationTerms(
    val kind: InstrumentTerminationKind,
    val contractualDate: LocalDate? = null,
    val effectiveNotBefore: Instant? = null,
    val valuationMethod: InstrumentTerminationValuationMethod,
    val accelerationRecoveryRate: Double? = null,
) {
    init {
        val violation = semanticInvariantViolation()
        require(violation == null) { requireNotNull(violation) }
    }

    /** Gson처럼 생성자를 우회하는 저장 복원 경계에서도 재사용할 의미 검증이다. */
    fun semanticInvariantViolation(): String? {
        val hasContractualDate = contractualDate != null
        val hasNotBeforeInstant = effectiveNotBefore != null
        if (hasContractualDate == hasNotBeforeInstant) {
            return "상품 종료 조건에는 계약일 또는 최소 효력 시각 중 정확히 하나가 필요합니다."
        }
        if (accelerationRecoveryRate != null &&
            (!accelerationRecoveryRate.isFinite() || accelerationRecoveryRate !in 0.40..0.80)
        ) {
            return "발행사 가속상환 회수율은 40% 이상 80% 이하여야 합니다."
        }
        return when (kind) {
            InstrumentTerminationKind.CONTRACTUAL_MATURITY -> when {
                contractualDate == null -> "계약상 만기에는 정확한 계약일이 필요합니다."
                valuationMethod != InstrumentTerminationValuationMethod.FINAL_INDICATIVE_VALUE_PROXY ->
                    "계약상 만기는 종료일 최종 지표가치로 평가해야 합니다."
                accelerationRecoveryRate != null -> "계약상 만기에는 가속상환 회수율을 둘 수 없습니다."
                else -> null
            }
            InstrumentTerminationKind.ISSUER_ACCELERATION -> when {
                effectiveNotBefore == null -> "발행사 가속상환에는 최소 효력 시각이 필요합니다."
                valuationMethod !=
                    InstrumentTerminationValuationMethod.TRAILING_FIVE_SESSION_AVERAGE_WITH_RECOVERY ->
                    "발행사 가속상환에는 회수율을 반영한 5거래일 평가 방식이 필요합니다."
                accelerationRecoveryRate == null -> "발행사 가속상환에는 공시에 고정된 회수율이 필요합니다."
                else -> null
            }
            InstrumentTerminationKind.OPTIONAL_CALL -> when {
                effectiveNotBefore == null -> "선택적 조기상환에는 최소 효력 시각이 필요합니다."
                valuationMethod != InstrumentTerminationValuationMethod.FINAL_INDICATIVE_VALUE_PROXY ->
                    "선택적 조기상환은 종료일 최종 지표가치로 평가해야 합니다."
                accelerationRecoveryRate != null -> "선택적 조기상환에는 가속상환 회수율을 둘 수 없습니다."
                else -> null
            }
            InstrumentTerminationKind.FUND_LIQUIDATION -> when {
                effectiveNotBefore == null -> "펀드 청산에는 최소 효력 시각이 필요합니다."
                valuationMethod != InstrumentTerminationValuationMethod.FINAL_NET_ASSET_VALUE_PROXY ->
                    "펀드 청산은 종료일 최종 순자산가치로 평가해야 합니다."
                accelerationRecoveryRate != null -> "펀드 청산에는 가속상환 회수율을 둘 수 없습니다."
                else -> null
            }
        }
    }

    val listingRiskTag: ListingRiskTag
        get() = when (kind) {
            InstrumentTerminationKind.FUND_LIQUIDATION -> ListingRiskTag.ETF_LIQUIDATION_APPROVED
            else -> ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION
        }

    val finalDisposition: ListingFinalDispositionType
        get() = ListingFinalDispositionType.CASH_LIQUIDATION

    /** ID나 제목이 아니라 상품 계약 속성으로 공시 적용 가능성을 판정한다. */
    fun isEligibleFor(stock: StockDefinition): Boolean = when (kind) {
        InstrumentTerminationKind.CONTRACTUAL_MATURITY ->
            stock.instrumentType == InstrumentType.ETN &&
                stock.identityProfile?.maturityDate == contractualDate.toString()
        InstrumentTerminationKind.ISSUER_ACCELERATION -> stock.instrumentType == InstrumentType.ETN
        InstrumentTerminationKind.OPTIONAL_CALL ->
            stock.instrumentType == InstrumentType.ETN && stock.identityProfile?.callable == true
        InstrumentTerminationKind.FUND_LIQUIDATION ->
            stock.instrumentType in setOf(InstrumentType.ETF, InstrumentType.CLOSED_END_FUND)
    }
}
