package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

enum class RoundingDirection {
    DOWN,
    HALF_UP,
    UP,
}
