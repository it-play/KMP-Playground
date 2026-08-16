package com.amond.kmpbook.domain.model.fund

import com.amond.kmpbook.domain.model.methodology.EquityMethodologyParameters
import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef
import kotlinx.datetime.LocalDate

/**
 * Provider-neutral declaration for one executable equity methodology.
 *
 * Product-specific constituent counts, caps, calendars and trigger thresholds belong to the
 * registered provider's typed [parameters], rather than becoming mandatory SCHD-shaped fields.
 *
 * @property effectiveFrom Provider bootstrap action date used to construct deterministic state;
 * it is not necessarily the methodology publication date or an official rule-effective date.
 */
class EquityMethodologyProfile(
    val methodologyRef: EquityMethodologyRef,
    val effectiveFrom: LocalDate,
    val referenceUniverse: FundReferenceUniverse,
    val decisionModel: EquityMethodologyDecisionModel,
    val modelAssumptionId: String?,
    val parameters: EquityMethodologyParameters,
) {
    init {
        when (decisionModel) {
            EquityMethodologyDecisionModel.RULE_BASED -> require(modelAssumptionId == null) {
                "규칙 기반 주식 방법론에는 재량 결정 모델 가정을 지정할 수 없습니다."
            }
            EquityMethodologyDecisionModel.DISCRETIONARY_PROXY -> require(
                modelAssumptionId != null && ASSUMPTION_ID.matches(modelAssumptionId),
            ) { "재량 대리 모델에는 버전된 modelAssumptionId가 필요합니다." }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is EquityMethodologyProfile &&
            methodologyRef == other.methodologyRef &&
            effectiveFrom == other.effectiveFrom &&
            referenceUniverse == other.referenceUniverse &&
            decisionModel == other.decisionModel &&
            modelAssumptionId == other.modelAssumptionId &&
            parameters == other.parameters

    override fun hashCode(): Int {
        var result = methodologyRef.hashCode()
        result = 31 * result + effectiveFrom.hashCode()
        result = 31 * result + referenceUniverse.hashCode()
        result = 31 * result + decisionModel.hashCode()
        result = 31 * result + (modelAssumptionId?.hashCode() ?: 0)
        result = 31 * result + parameters.hashCode()
        return result
    }

    override fun toString(): String =
        "EquityMethodologyProfile(methodologyRef=$methodologyRef, effectiveFrom=$effectiveFrom, " +
            "referenceUniverse=$referenceUniverse, decisionModel=$decisionModel, " +
            "modelAssumptionId=$modelAssumptionId, parameters=$parameters)"

    companion object {
        const val MAX_MODEL_ASSUMPTION_ID_LENGTH: Int = 160
        private val ASSUMPTION_ID = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
