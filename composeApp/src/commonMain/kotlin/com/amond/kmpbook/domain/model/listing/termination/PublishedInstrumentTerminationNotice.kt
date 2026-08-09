package com.amond.kmpbook.domain.model.listing.termination

import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationTerms
import com.amond.kmpbook.domain.model.listing.termination.PublishedInstrumentTerminationNotice
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 발표된 상품 종료 공시를 상장 감시와 뉴스 화면이 함께 소비하는 판정 결과다.
 * [rawEffectiveOn]은 현재일 clamp나 계약상 만기 hard cap을 적용하기 전 공시 자체의 효력 거래일이다.
 */
data class PublishedInstrumentTerminationNotice(
    val event: GameEvent,
    val terms: InstrumentTerminationTerms,
    val rawEffectiveOn: LocalDate,
)
