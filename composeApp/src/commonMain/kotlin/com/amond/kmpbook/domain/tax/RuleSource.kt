package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

data class RuleSource(
    val title: String,
    val url: String? = null,
    val accessedOn: LocalDate = TaxPolicyPack2026.FROZEN_AS_OF,
) {
    init {
        require(title.isNotBlank()) { "A rule source needs a title." }
        require(url == null || url.startsWith("https://")) { "A source URL must use HTTPS." }
    }
}
