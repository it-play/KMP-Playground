package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.protection.krx.KrxViProductClass

enum class KrxViProductClass {
    KOSPI200_CONSTITUENT,
    OTHER_EQUITY,
    CORE_INDEX_INVERSE_OR_BOND_ETP,
    OTHER_ETP,
    ;

    val usesLowerDynamicRate: Boolean
        get() = this == KOSPI200_CONSTITUENT || this == CORE_INDEX_INVERSE_OR_BOND_ETP
}
