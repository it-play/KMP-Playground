package com.amond.kmpbook.domain.simulation.listing

import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRecoveryCondition
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

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
