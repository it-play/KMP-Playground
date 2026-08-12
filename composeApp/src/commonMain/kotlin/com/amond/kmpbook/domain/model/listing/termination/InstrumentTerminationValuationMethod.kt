package com.amond.kmpbook.domain.model.listing.termination

import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationValuationMethod
import kotlinx.datetime.plus

/** 종료일 현금 지급 단가를 산정하는 캠페인 평가 규칙이다. */
enum class InstrumentTerminationValuationMethod {
    /** ETN 계약의 관측창·배수·미지급 쿠폰을 구조 엔진이 확정한 상환액이다. */
    ETN_CONTRACT_SETTLEMENT,

    /** 발행사 신용사건 시 최종 지표가치와 공시 회수율로 확정한 무담보채권 상환액이다. */
    ETN_CREDIT_DEFAULT_RECOVERY,

    /** ETF·폐쇄형 펀드의 회계 상태가 확정한 최종 순자산가치다. */
    FINAL_NET_ASSET_VALUE,

}
