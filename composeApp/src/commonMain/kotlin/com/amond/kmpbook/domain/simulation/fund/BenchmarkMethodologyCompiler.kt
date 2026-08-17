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
        StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP,
        StandardEquityMethodologySignalIds.INVESTABLE_WEIGHT_FACTOR,
        StandardEquityMethodologySignalIds.FLOAT_ADJUSTED_LIQUIDITY_RATIO,
        StandardEquityMethodologySignalIds.MINIMUM_SIX_MONTH_MONTHLY_SHARE_VOLUME,
        StandardEquityMethodologySignalIds.AVERAGE_DAILY_VALUE_TRADED,
        StandardEquityMethodologySignalIds.MEDIAN_DAILY_VALUE_TRADED,
        StandardEquityMethodologySignalIds.TRAILING_125_TRADING_DAY_AVERAGE_DAILY_VALUE_TRADED,
        StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD,
        StandardEquityMethodologySignalIds.FREE_CASH_FLOW_TO_DEBT,
        StandardEquityMethodologySignalIds.RETURN_ON_EQUITY,
        StandardEquityMethodologySignalIds.FIVE_YEAR_DIVIDEND_GROWTH,
        StandardEquityMethodologySignalIds.THREE_YEAR_AVERAGE_DIVIDEND_PAYOUT_RATIO,
        StandardEquityMethodologySignalIds.THREE_YEAR_AVERAGE_RETURN_ON_EQUITY,
        StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_DAILY_VALUE_TRADED,
        StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_PRICE_TO_BOOK_RATIO,
        StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_DIVIDEND_YIELD,
        StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_MARKET_CAP,
        StandardEquityMethodologySignalIds.TRAILING_FOUR_QUARTER_TOTAL_CASH_DIVIDENDS,
        StandardEquityMethodologySignalIds.BOOK_TO_PRICE,
        StandardEquityMethodologySignalIds.FUTURE_EARNINGS_TO_PRICE,
        StandardEquityMethodologySignalIds.HISTORICAL_EARNINGS_TO_PRICE,
        StandardEquityMethodologySignalIds.DIVIDEND_TO_PRICE,
        StandardEquityMethodologySignalIds.SALES_TO_PRICE,
        StandardEquityMethodologySignalIds.FUTURE_LONG_TERM_EARNINGS_GROWTH,
        StandardEquityMethodologySignalIds.FUTURE_SHORT_TERM_EARNINGS_GROWTH,
        StandardEquityMethodologySignalIds.THREE_YEAR_HISTORICAL_EARNINGS_GROWTH,
        StandardEquityMethodologySignalIds.THREE_YEAR_HISTORICAL_SALES_GROWTH,
        StandardEquityMethodologySignalIds.CURRENT_INVESTMENT_TO_ASSETS,
        StandardEquityMethodologySignalIds.RETURN_ON_ASSETS,
    )
    private val supportedIntegerSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.GICS_CLASSIFICATION_CODE,
        StandardEquityMethodologySignalIds.DIVIDEND_PAYMENT_YEARS,
        StandardEquityMethodologySignalIds.LISTING_AGE_YEARS,
    )
    private val supportedBooleanSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.ZERO_TOTAL_DEBT,
        StandardEquityMethodologySignalIds.NEGATIVE_BOOK_VALUE_PER_SHARE,
        StandardEquityMethodologySignalIds.SCHEDULED_DIVIDEND_PAYMENT_OMITTED,
        StandardEquityMethodologySignalIds.DIVIDEND_PROGRAM_CEASED_INDEFINITELY,
        StandardEquityMethodologySignalIds.LATEST_QUARTER_GAAP_NET_INCOME_POSITIVE,
        StandardEquityMethodologySignalIds.TRAILING_FOUR_QUARTER_GAAP_NET_INCOME_POSITIVE,
        StandardEquityMethodologySignalIds.KOSPI200_FINANCIAL_MEMBER,
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
        when (profile.referenceUniverse) {
            FundReferenceUniverse.US_BROAD_EQUITY -> {
                require(definition.baseCurrency == ReferenceCurrency.USD)
                require(schedule.market.isUnitedStates)
                require(schedule.exposureRegion == EtfExposureRegion.UNITED_STATES)
            }
            FundReferenceUniverse.KOREA_BROAD_EQUITY -> {
                require(definition.baseCurrency == ReferenceCurrency.KRW)
                require(schedule.market.isKorean)
                require(schedule.exposureRegion == EtfExposureRegion.KOREA)
            }
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
