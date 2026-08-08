package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.time.Instant

enum class CorporateActionSource(val displayName: String) {
    CAMPAIGN_RULE("캠페인 가상 공시"),
    OFFICIAL_FIXTURE("기준일 실제 공시"),
}
