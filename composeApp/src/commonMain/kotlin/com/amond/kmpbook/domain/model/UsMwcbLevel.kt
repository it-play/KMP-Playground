package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class UsMwcbLevel(val declineRate: Double) {
    LEVEL_1(0.07),
    LEVEL_2(0.13),
    LEVEL_3(0.20),
}
