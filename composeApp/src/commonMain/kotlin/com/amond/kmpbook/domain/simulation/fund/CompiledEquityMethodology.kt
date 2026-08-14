package com.amond.kmpbook.domain.simulation.fund

import com.amond.kmpbook.domain.methodology.EquityMethodologyPolicy
import com.amond.kmpbook.domain.methodology.EquityMethodologyPortfolioConstraints
import com.amond.kmpbook.domain.methodology.EquityMethodologySchedule
import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile

/** A catalog definition bound to one immutable, registered executable policy. */
internal class CompiledEquityMethodology(
    val definition: BenchmarkDefinition,
    val profile: EquityMethodologyProfile,
    val policy: EquityMethodologyPolicy,
    val schedule: EquityMethodologySchedule,
    val constraints: EquityMethodologyPortfolioConstraints,
    val requiredDecimalSignalIds: Set<String>,
    val requiredIntegerSignalIds: Set<String>,
    val requiredBooleanSignalIds: Set<String>,
    val requiredTextSignalIds: Set<String>,
)
