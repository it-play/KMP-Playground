package com.amond.kmpbook.domain.model.venue


/** 시세창에 표시하는 거래 세션 상태. 정규장 체결 가능 여부는 [isTradable]로 판단한다. */
enum class MarketSession(
    val displayName: String,
    val isTradable: Boolean,
) {
    CLOSED("장 마감", false),
    PRE_MARKET("프리마켓", false),
    REGULAR("정규장", true),
    AFTER_HOURS("애프터마켓", false),
}
