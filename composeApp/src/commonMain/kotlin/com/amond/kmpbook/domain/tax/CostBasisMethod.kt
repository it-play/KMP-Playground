package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

enum class CostBasisMethod(val displayName: String) {
    FIFO("선입선출법"),
    MOVING_AVERAGE("이동평균법"),
}
