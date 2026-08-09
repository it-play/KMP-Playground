package com.amond.kmpbook.domain.model.schedule

enum class ScheduledOutcomeComparison(val displayName: String) {
    ABOVE("시장 예상 대비 상회"),
    INLINE("시장 예상 부합"),
    BELOW("시장 예상 대비 하회"),
}
