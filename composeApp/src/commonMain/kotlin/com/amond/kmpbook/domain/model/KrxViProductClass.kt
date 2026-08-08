package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class KrxViProductClass {
    KOSPI200_CONSTITUENT,
    OTHER_EQUITY,
    CORE_INDEX_INVERSE_OR_BOND_ETP,
    OTHER_ETP,
    ;

    val usesLowerDynamicRate: Boolean
        get() = this == KOSPI200_CONSTITUENT || this == CORE_INDEX_INVERSE_OR_BOND_ETP
}
