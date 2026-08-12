package com.amond.kmpbook.domain.model.fundstructure

import kotlin.math.abs
import kotlin.math.max

internal const val MAX_FUND_STRUCTURE_ID_LENGTH: Int = 256
internal const val MAX_FUND_STRUCTURE_URL_LENGTH: Int = 2_048
internal const val MAX_FUND_STRUCTURE_VALUE: Double = 1.0e18
internal const val MIN_FUND_STRUCTURE_VALUE: Double = 1.0e-9
internal const val MAX_EXACT_INTEGER_QUANTITY: Long = 9_000_000_000_000_000L
internal const val MAX_RATE: Double = 100.0
internal const val MAX_YEAR_FRACTION: Double = 100.0
internal const val ACCOUNTING_EPSILON: Double = 1.0e-9

internal fun requireFundStructureId(value: String, label: String) {
    require(value.isNotBlank() && value.length <= MAX_FUND_STRUCTURE_ID_LENGTH) {
        "$label must be non-blank and at most $MAX_FUND_STRUCTURE_ID_LENGTH characters."
    }
}

internal fun requireOfficialSourceUrl(value: String) {
    require(value.startsWith("https://") && value.length <= MAX_FUND_STRUCTURE_URL_LENGTH) {
        "An HTTPS official source URL is required."
    }
}

internal fun requireNonNegativeAmount(value: Double, label: String) {
    require(value.isFinite() && value in 0.0..MAX_FUND_STRUCTURE_VALUE) {
        "$label must be a finite, non-negative amount."
    }
}

internal fun requirePositiveAmount(value: Double, label: String) {
    require(value.isFinite() && value in MIN_FUND_STRUCTURE_VALUE..MAX_FUND_STRUCTURE_VALUE) {
        "$label must be a finite, positive amount."
    }
}

internal fun amountsAreClose(left: Double, right: Double): Boolean {
    val scale = max(1.0, max(abs(left), abs(right)))
    return abs(left - right) <= scale * ACCOUNTING_EPSILON
}
