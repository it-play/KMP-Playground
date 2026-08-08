package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

enum class ShareholderRelation {
    SELF,
    RELATIVE,
    CONTROLLED_ENTITY,
}
