package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

/** 대한민국 거주자의 일반 증권계좌를 기준으로 한 ETF 세무 분류. */
enum class EtfTaxCategory(val displayName: String) {
    /** 국내 주식지수를 1:1로 추종하는 국내 상장 주식형 ETF. */
    KOREAN_DOMESTIC_EQUITY("국내주식형 ETF"),

    /** 해외지수·채권·상품·파생형 등 보유기간 과세 대상인 국내 상장 ETF. */
    KOREAN_OTHER("국내상장 기타 ETF"),

    /** 미국 거래소에 상장되어 국외주식 양도소득 규칙을 적용하는 ETF. */
    FOREIGN_LISTED("미국상장 ETF"),
}
