package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

enum class EtfAssetClass(val displayName: String) {
    BROAD_EQUITY("주식시장"),
    SECTOR_EQUITY("주식 섹터·테마"),
    FIXED_INCOME("채권"),
    MONEY_MARKET("단기금융"),
    COMMODITY("원자재"),
    REAL_ESTATE("리츠·인프라"),
    MULTI_ASSET("혼합자산"),
    ALTERNATIVE("대체·전략"),
}
