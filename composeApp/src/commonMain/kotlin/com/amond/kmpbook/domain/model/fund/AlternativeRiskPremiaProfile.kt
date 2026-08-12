package com.amond.kmpbook.domain.model.fund

import kotlin.math.abs

/** Executable, explicitly calibrated long/short alternative-risk-premia reference. */
class AlternativeRiskPremiaProfile(
    strategyFamilies: Set<AlternativeRiskPremiaStrategyFamily>,
    drivers: List<AlternativeRiskPremiaDriver>,
    val signalModel: AlternativeRiskPremiaSignalModel,
    val longGrossExposureConstraint: CompositeExposureConstraint,
    val shortGrossExposureConstraint: CompositeExposureConstraint,
    val netExposureConstraint: CompositeExposureConstraint,
    val targetVolatilityAnnual: Double?,
    val targetVolatilityOrigin: CompositeParameterOrigin?,
    val signalLookbackTradingDays: Int,
    val signalLookbackOrigin: CompositeParameterOrigin,
    val rebalanceSchedule: CompositeRebalanceSchedule,
    val annualFinancingSpread: Double,
    val annualFinancingSpreadOrigin: CompositeParameterOrigin,
    val annualShortBorrowSpread: Double,
    val annualShortBorrowSpreadOrigin: CompositeParameterOrigin,
    val annualImplementationCostRate: Double,
    val annualImplementationCostOrigin: CompositeParameterOrigin,
    val supportLevel: BenchmarkSupportLevel,
    val provenance: CompositeRuleProvenance,
    val confidence: CompositeConfidence,
    officialSourceUrls: Set<String>,
    val assumptionId: String?,
) {
    val strategyFamilies: Set<AlternativeRiskPremiaStrategyFamily> = strategyFamilies
        .sortedBy(AlternativeRiskPremiaStrategyFamily::ordinal)
        .toCollection(linkedSetOf())
        .toSet()
    val drivers: List<AlternativeRiskPremiaDriver> = drivers.sortedBy(AlternativeRiskPremiaDriver::driverId).toList()
    val officialSourceUrls: Set<String> = officialSourceUrls.sorted().toCollection(linkedSetOf()).toSet()
    val componentBenchmarkRefs: Set<BenchmarkRef> = this.drivers.mapNotNullTo(linkedSetOf()) {
        it.source.benchmarkRef
    }.sorted().toCollection(linkedSetOf()).toSet()
    val componentInstrumentIds: Set<String> = this.drivers.mapNotNullTo(linkedSetOf()) {
        it.source.instrumentId
    }.sorted().toCollection(linkedSetOf()).toSet()

    init {
        require(this.strategyFamilies.isNotEmpty())
        require(this.drivers.isNotEmpty() && this.drivers.size <= MAX_DRIVERS)
        require(this.drivers.map(AlternativeRiskPremiaDriver::driverId).distinct().size == this.drivers.size)
        require(this.strategyFamilies == this.drivers.mapTo(linkedSetOf()) { it.strategyFamily })
        require(this.drivers.all { driver ->
            driver.hedgeRatioToProfileBaseCurrency == null ||
                driver.hedgeRatioToProfileBaseCurrency == 0.0
        }) { "헤지 비용 모델이 추가되기 전에는 대안 위험프리미엄 driver의 환헤지를 지원하지 않습니다." }
        require(longGrossExposureConstraint.minimum >= 0.0)
        require(shortGrossExposureConstraint.minimum >= 0.0)
        validateDirectionFeasibility()
        require(abs(netExposureConstraint.minimum) <=
            longGrossExposureConstraint.maximum + shortGrossExposureConstraint.maximum + EPSILON)
        require(abs(netExposureConstraint.maximum) <=
            longGrossExposureConstraint.maximum + shortGrossExposureConstraint.maximum + EPSILON)
        require((targetVolatilityAnnual != null) == (targetVolatilityOrigin != null))
        targetVolatilityAnnual?.let { require(it.isFinite() && it > 0.0 && it <= 5.0) }
        require(signalLookbackTradingDays in CompositeReferenceProfile.MIN_LOOKBACK_DAYS..
            CompositeReferenceProfile.MAX_LOOKBACK_DAYS)
        requireRate(annualFinancingSpread)
        requireRate(annualShortBorrowSpread)
        requireRate(annualImplementationCostRate)
        require(this.drivers.all { it.targetRiskBudget != null })
        require(abs(this.drivers.sumOf { requireNotNull(it.targetRiskBudget) } - 1.0) <= EPSILON)
        if (this.strategyFamilies.size > 1) {
            require(signalModel == AlternativeRiskPremiaSignalModel.MULTI_SIGNAL)
        }
        require(this.officialSourceUrls.size <= MAX_OFFICIAL_SOURCE_URLS)
        require(this.officialSourceUrls.all(::isValidHttpsUrl))
        when (provenance) {
            CompositeRuleProvenance.VERIFIED_INDEX_METHODOLOGY,
            CompositeRuleProvenance.VERIFIED_PRODUCT_DISCLOSURE,
            -> {
                require(this.officialSourceUrls.isNotEmpty())
                require(assumptionId == null)
            }
            CompositeRuleProvenance.MODEL_ASSUMPTION -> {
                requireNotNull(assumptionId)
                require(ASSUMPTION_ID_PATTERN.matches(assumptionId))
            }
        }
        if (supportLevel == BenchmarkSupportLevel.VERIFIED_RULES) {
            require(provenance == CompositeRuleProvenance.VERIFIED_INDEX_METHODOLOGY)
        }
    }

    private fun requireRate(value: Double) {
        require(value.isFinite() && value in 0.0..1.0)
    }

    private fun validateDirectionFeasibility() {
        val needsLong = longGrossExposureConstraint.maximum > EPSILON
        val needsShort = shortGrossExposureConstraint.maximum > EPSILON
        val longCapable = drivers.filter { driver ->
            driver.signalDirectionPolicy != AlternativeRiskPremiaSignalDirectionPolicy.SHORT_ONLY
        }
        val shortCapable = drivers.filter { driver ->
            driver.signalDirectionPolicy != AlternativeRiskPremiaSignalDirectionPolicy.LONG_ONLY
        }
        if (needsLong) require(longCapable.isNotEmpty()) {
            "양의 long 노출에는 LONG_ONLY 또는 DYNAMIC_LONG_SHORT driver가 필요합니다."
        }
        if (needsShort) require(shortCapable.isNotEmpty()) {
            "양의 short 노출에는 SHORT_ONLY 또는 DYNAMIC_LONG_SHORT driver가 필요합니다."
        }
        if (needsLong && needsShort) {
            require(longCapable.any { longDriver ->
                shortCapable.any { shortDriver -> shortDriver.driverId != longDriver.driverId }
            }) { "long·short 동시 노출에는 방향이 호환되는 서로 다른 driver 두 개가 필요합니다." }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is AlternativeRiskPremiaProfile &&
            strategyFamilies == other.strategyFamilies && drivers == other.drivers &&
            signalModel == other.signalModel && longGrossExposureConstraint == other.longGrossExposureConstraint &&
            shortGrossExposureConstraint == other.shortGrossExposureConstraint &&
            netExposureConstraint == other.netExposureConstraint &&
            targetVolatilityAnnual == other.targetVolatilityAnnual &&
            targetVolatilityOrigin == other.targetVolatilityOrigin &&
            signalLookbackTradingDays == other.signalLookbackTradingDays &&
            signalLookbackOrigin == other.signalLookbackOrigin && rebalanceSchedule == other.rebalanceSchedule &&
            annualFinancingSpread == other.annualFinancingSpread &&
            annualFinancingSpreadOrigin == other.annualFinancingSpreadOrigin &&
            annualShortBorrowSpread == other.annualShortBorrowSpread &&
            annualShortBorrowSpreadOrigin == other.annualShortBorrowSpreadOrigin &&
            annualImplementationCostRate == other.annualImplementationCostRate &&
            annualImplementationCostOrigin == other.annualImplementationCostOrigin &&
            supportLevel == other.supportLevel && provenance == other.provenance && confidence == other.confidence &&
            officialSourceUrls == other.officialSourceUrls && assumptionId == other.assumptionId

    override fun hashCode(): Int {
        var result = strategyFamilies.hashCode()
        result = 31 * result + drivers.hashCode()
        result = 31 * result + signalModel.hashCode()
        result = 31 * result + longGrossExposureConstraint.hashCode()
        result = 31 * result + shortGrossExposureConstraint.hashCode()
        result = 31 * result + netExposureConstraint.hashCode()
        result = 31 * result + (targetVolatilityAnnual?.hashCode() ?: 0)
        result = 31 * result + (targetVolatilityOrigin?.hashCode() ?: 0)
        result = 31 * result + signalLookbackTradingDays
        result = 31 * result + signalLookbackOrigin.hashCode()
        result = 31 * result + rebalanceSchedule.hashCode()
        result = 31 * result + annualFinancingSpread.hashCode()
        result = 31 * result + annualFinancingSpreadOrigin.hashCode()
        result = 31 * result + annualShortBorrowSpread.hashCode()
        result = 31 * result + annualShortBorrowSpreadOrigin.hashCode()
        result = 31 * result + annualImplementationCostRate.hashCode()
        result = 31 * result + annualImplementationCostOrigin.hashCode()
        result = 31 * result + supportLevel.hashCode()
        result = 31 * result + provenance.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        result = 31 * result + (assumptionId?.hashCode() ?: 0)
        return result
    }

    private fun isValidHttpsUrl(value: String): Boolean =
        value.length in 9..2_048 && value.startsWith("https://") && value.none(Char::isISOControl)

    companion object {
        const val MAX_DRIVERS: Int = 64
        const val MAX_OFFICIAL_SOURCE_URLS: Int = 16
        private const val EPSILON: Double = 1e-8
        private val ASSUMPTION_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
