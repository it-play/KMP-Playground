package com.amond.kmpbook.domain.model.instrument

enum class DistributionFrequency(val displayName: String, val periodsPerYear: Int) {
    NONE("미분배", 0),
    WEEKLY("주간", 52),
    MONTHLY("월간", 12),
    QUARTERLY("분기", 4),
    SEMIANNUAL("반기", 2),
    ANNUAL("연간", 1),
}
