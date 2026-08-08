package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

/**
 * 공개 규칙을 그대로 적용할 수 있는지, 데이터가 없어 게임 근사를 쓰는지를 구분한다.
 * 재무제표·유통주식수·감사의견 원문이 필요한 기준은 반드시 [GAME_APPROXIMATION]을 사용한다.
 */
enum class ListingRuleBasis(val displayName: String) {
    OFFICIAL_PUBLIC_RULE_SUMMARY("공식 공개 규칙 요약"),
    HYBRID_PUBLIC_RULE_AND_GAME_APPROXIMATION("공식 규칙·게임 근사 혼합"),
    GAME_APPROXIMATION("게임 근사"),
}
