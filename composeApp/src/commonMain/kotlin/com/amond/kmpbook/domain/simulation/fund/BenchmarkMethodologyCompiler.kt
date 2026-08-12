package com.amond.kmpbook.domain.simulation.fund

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkEngineKind
import com.amond.kmpbook.domain.model.fund.BenchmarkSupportLevel
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.market.ReferenceCurrency

/** Resolves a versioned benchmark definition to the one exact policy this engine supports today. */
internal object BenchmarkMethodologyCompiler {
    fun compileSchd(definition: BenchmarkDefinition): EquityMethodologyProfile {
        require(definition.engineKind == BenchmarkEngineKind.EQUITY_METHODOLOGY) {
            "Benchmark ${definition.ref} is not an executable equity methodology."
        }
        require(definition.supportLevel == BenchmarkSupportLevel.VERIFIED_RULES) {
            "Benchmark ${definition.ref} does not have verified executable rules."
        }
        require(definition.componentBenchmarkRefs.isEmpty()) {
            "Composite benchmarks are not supported by the SCHD reference-portfolio policy."
        }
        require(definition.baseCurrency == ReferenceCurrency.USD) {
            "The SCHD reference-portfolio policy requires a USD benchmark."
        }
        val profile = requireNotNull(definition.equityMethodology) {
            "Benchmark ${definition.ref} has no equity methodology."
        }
        SchdDividend100Policy.validate(profile)
        return profile
    }
}
