package com.amond.kmpbook.domain.simulation.fund

import com.amond.kmpbook.domain.methodology.EquityMethodologyRegistry
import com.amond.kmpbook.domain.methodology.StandardEquityMethodologySignalIds
import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkEngineKind
import com.amond.kmpbook.domain.model.fund.BenchmarkSupportLevel
import com.amond.kmpbook.domain.model.fund.FundReferenceUniverse
import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
import com.amond.kmpbook.domain.model.market.ReferenceCurrency

/** Resolves a versioned benchmark definition to its immutable, registered executable policy. */
internal object BenchmarkMethodologyCompiler {
    private val supportedDecimalSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP,
        StandardEquityMethodologySignalIds.AVERAGE_DAILY_VALUE_TRADED,
        StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD,
        StandardEquityMethodologySignalIds.FREE_CASH_FLOW_TO_DEBT,
        StandardEquityMethodologySignalIds.RETURN_ON_EQUITY,
        StandardEquityMethodologySignalIds.FIVE_YEAR_DIVIDEND_GROWTH,
    )
    private val supportedIntegerSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.DIVIDEND_PAYMENT_YEARS,
    )
    private val supportedBooleanSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.DIVIDEND_PROGRAM_SUSPENDED,
    )
    private val supportedTextSignalIds: Set<String> = emptySet()

    fun compile(
        definition: BenchmarkDefinition,
        registry: EquityMethodologyRegistry,
    ): CompiledEquityMethodology {
        require(definition.engineKind == BenchmarkEngineKind.EQUITY_METHODOLOGY) {
            "Benchmark ${definition.ref} is not an executable equity methodology."
        }
        require(definition.supportLevel == BenchmarkSupportLevel.VERIFIED_RULES) {
            "Benchmark ${definition.ref} does not have verified executable rules."
        }
        require(definition.componentBenchmarkRefs.isEmpty()) {
            "Executable equity methodologies cannot depend on component benchmarks."
        }
        val profile = requireNotNull(definition.equityMethodology) {
            "Benchmark ${definition.ref} has no equity methodology."
        }
        val policy = registry.require(profile.methodologyRef).policy
        val schedule = policy.schedule
        val requiredDecimalSignalIds = buildSet { addAll(policy.requiredDecimalSignalIds) }
        val requiredIntegerSignalIds = buildSet { addAll(policy.requiredIntegerSignalIds) }
        val requiredBooleanSignalIds = buildSet { addAll(policy.requiredBooleanSignalIds) }
        val requiredTextSignalIds = buildSet { addAll(policy.requiredTextSignalIds) }
        require(profile.referenceUniverse == FundReferenceUniverse.US_BROAD_EQUITY) {
            "The current reference-portfolio host supports the US broad-equity universe only."
        }
        require(definition.baseCurrency == ReferenceCurrency.USD) {
            "The current reference-portfolio host supports USD benchmarks only."
        }
        require(schedule.market.isUnitedStates) {
            "The current reference-portfolio host requires a US methodology market."
        }
        require(schedule.exposureRegion == EtfExposureRegion.UNITED_STATES) {
            "The current reference-portfolio host requires United States regional exposure."
        }
        requireSupportedSignals(
            valueType = "decimal",
            required = requiredDecimalSignalIds,
            supported = supportedDecimalSignalIds,
        )
        requireSupportedSignals(
            valueType = "integer",
            required = requiredIntegerSignalIds,
            supported = supportedIntegerSignalIds,
        )
        requireSupportedSignals(
            valueType = "boolean",
            required = requiredBooleanSignalIds,
            supported = supportedBooleanSignalIds,
        )
        requireSupportedSignals(
            valueType = "text",
            required = requiredTextSignalIds,
            supported = supportedTextSignalIds,
        )
        policy.validate(definition, profile)
        val constraints = policy.portfolioConstraints(profile)
        return CompiledEquityMethodology(
            definition = definition,
            profile = profile,
            policy = policy,
            schedule = schedule,
            constraints = constraints,
            requiredDecimalSignalIds = requiredDecimalSignalIds,
            requiredIntegerSignalIds = requiredIntegerSignalIds,
            requiredBooleanSignalIds = requiredBooleanSignalIds,
            requiredTextSignalIds = requiredTextSignalIds,
        )
    }

    private fun requireSupportedSignals(
        valueType: String,
        required: Set<String>,
        supported: Set<String>,
    ) {
        val unsupported = required - supported
        require(unsupported.isEmpty()) {
            "The current reference-portfolio host does not provide required $valueType signals: " +
                unsupported.sorted().joinToString()
        }
    }
}
