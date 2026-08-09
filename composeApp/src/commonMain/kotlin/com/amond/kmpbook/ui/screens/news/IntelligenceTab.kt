package com.amond.kmpbook.ui.screens.news

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal enum class IntelligenceTab(val label: String, val eyebrow: String) {
    IMPACT("영향 경로", "IMPACT TRACE"),
    NEWS("관련 뉴스", "CONNECTED SIGNALS"),
    STRUCTURE("상품 구조", "INSTRUMENT PROFILE"),
}
