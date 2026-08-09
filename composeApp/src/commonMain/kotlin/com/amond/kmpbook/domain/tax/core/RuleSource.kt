package com.amond.kmpbook.domain.tax.core

import com.amond.kmpbook.domain.tax.policy.TaxPolicyPack2026
import kotlinx.datetime.LocalDate

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
