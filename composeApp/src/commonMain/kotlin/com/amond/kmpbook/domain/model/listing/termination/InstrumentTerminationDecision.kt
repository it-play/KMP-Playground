package com.amond.kmpbook.domain.model.listing.termination

import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationDecision
import com.amond.kmpbook.domain.model.listing.termination.PublishedInstrumentTerminationNotice
import com.amond.kmpbook.domain.model.listing.termination.scheduledTerminationOn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 한 거래일의 정규장 마감 감시에서 확정한 상품 종료 의사결정이다.
 *
 * winner 공시, 공시 자체의 원효력일, 현재 감시일을 반영한 실제 예정일을 한 값으로 묶어
 * 상장 사유·평가 방식·현금 지급 조건이 서로 다른 공시에서 섞이지 않게 한다.
 */
data class InstrumentTerminationDecision(
    val notice: PublishedInstrumentTerminationNotice,
    val scheduledTerminationOn: LocalDate,
) {
    /** 별도 복제 없이 winner 공시가 가진 원효력일을 그대로 노출한다. */
    val rawEffectiveOn: LocalDate
        get() = notice.rawEffectiveOn
}
