package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.reference.EquityReferenceActionKind
import kotlinx.datetime.LocalDate

/** Exact, interval-local key for sharing pure selection work across equivalent references. */
internal data class EquityReferenceSelectionCacheKey(
    val policyKey: String,
    val benchmarkScope: BenchmarkRef?,
    val kind: EquityReferenceActionKind,
    val year: Int,
    val effectiveDate: LocalDate?,
    val incumbentIdentities: List<String>,
)
