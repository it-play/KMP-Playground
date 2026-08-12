package com.amond.kmpbook.domain.model.fund

import kotlin.math.abs

/** Executable multi-source reference policy with explicit leverage, currency, and evidence boundaries. */
class CompositeReferenceProfile(
    sleeves: List<CompositeReferenceSleeve>,
    val allocationModel: CompositeAllocationModel,
    val grossExposureConstraint: CompositeExposureConstraint,
    val netExposureConstraint: CompositeExposureConstraint,
    val annualFinancingSpread: Double?,
    val annualFinancingSpreadOrigin: CompositeParameterOrigin?,
    val targetVolatilityAnnual: Double?,
    val targetVolatilityOrigin: CompositeParameterOrigin?,
    val riskLookbackTradingDays: Int?,
    val riskLookbackOrigin: CompositeParameterOrigin?,
    val durationConstraint: CompositeDurationConstraint?,
    val driftThreshold: Double?,
    val driftThresholdOrigin: CompositeParameterOrigin?,
    val selectionSchedule: CompositeRebalanceSchedule,
    val reweightSchedule: CompositeRebalanceSchedule,
    val supportLevel: BenchmarkSupportLevel,
    val provenance: CompositeRuleProvenance,
    val confidence: CompositeConfidence,
    officialSourceUrls: Set<String>,
    val assumptionId: String?,
) {
    val sleeves: List<CompositeReferenceSleeve> = sleeves.sortedBy(CompositeReferenceSleeve::sleeveId).toList()
    val officialSourceUrls: Set<String> = officialSourceUrls.sorted().toCollection(linkedSetOf()).toSet()
    val componentBenchmarkRefs: Set<BenchmarkRef> = this.sleeves.mapNotNullTo(linkedSetOf()) {
        it.source.benchmarkRef
    }.sorted().toCollection(linkedSetOf()).toSet()
    val componentInstrumentIds: Set<String> = this.sleeves.mapNotNullTo(linkedSetOf()) {
        it.source.instrumentId
    }.sorted().toCollection(linkedSetOf()).toSet()

    init {
        require(this.sleeves.isNotEmpty() && this.sleeves.size <= MAX_SLEEVES)
        require(this.sleeves.map(CompositeReferenceSleeve::sleeveId).distinct().size == this.sleeves.size)
        require(this.sleeves.all { sleeve ->
            sleeve.hedgeRatioToCompositeBaseCurrency == null ||
                sleeve.hedgeRatioToCompositeBaseCurrency == 0.0
        }) { "헤지 비용 모델이 추가되기 전에는 합성 sleeve의 환헤지를 지원하지 않습니다." }
        require(grossExposureConstraint.minimum >= 0.0)
        require(grossExposureConstraint.maximum <= CompositeExposureConstraint.MAX_ABSOLUTE_EXPOSURE)
        require(abs(netExposureConstraint.minimum) <= grossExposureConstraint.maximum + EPSILON)
        require(abs(netExposureConstraint.maximum) <= grossExposureConstraint.maximum + EPSILON)
        validateExposureFeasibility()
        require((annualFinancingSpread != null) == (annualFinancingSpreadOrigin != null))
        if (grossExposureConstraint.maximum > 1.0 + EPSILON) {
            requireNotNull(annualFinancingSpread)
        } else {
            require(annualFinancingSpread == null)
        }
        annualFinancingSpread?.let { require(it.isFinite() && it in 0.0..MAX_ANNUAL_RATE) }
        require((targetVolatilityAnnual != null) == (targetVolatilityOrigin != null))
        targetVolatilityAnnual?.let { require(it.isFinite() && it > 0.0 && it <= MAX_TARGET_VOLATILITY) }
        require((riskLookbackTradingDays != null) == (riskLookbackOrigin != null))
        riskLookbackTradingDays?.let { require(it in MIN_LOOKBACK_DAYS..MAX_LOOKBACK_DAYS) }
        require((driftThreshold != null) == (driftThresholdOrigin != null))
        driftThreshold?.let { require(it.isFinite() && it > 0.0 && it <= 1.0) }
        validateAllocationModel()
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
        if (supportLevel != BenchmarkSupportLevel.PROVISIONAL_PROXY) {
            require(this.officialSourceUrls.isNotEmpty())
        }
    }

    private fun validateExposureFeasibility() {
        val minimumGross = sleeves.sumOf { it.minimumWeight ?: 0.0 }
        val maximumGross = sleeves.sumOf { it.maximumWeight ?: grossExposureConstraint.maximum }
        require(minimumGross <= grossExposureConstraint.maximum + EPSILON)
        require(maximumGross + EPSILON >= grossExposureConstraint.minimum)
        val longMinimum = sleeves.filter { it.direction == CompositeSleeveDirection.LONG }
            .sumOf { it.minimumWeight ?: 0.0 }
        val longMaximum = sleeves.filter { it.direction == CompositeSleeveDirection.LONG }
            .sumOf { it.maximumWeight ?: grossExposureConstraint.maximum }
        val shortMinimum = sleeves.filter { it.direction == CompositeSleeveDirection.SHORT }
            .sumOf { it.minimumWeight ?: 0.0 }
        val shortMaximum = sleeves.filter { it.direction == CompositeSleeveDirection.SHORT }
            .sumOf { it.maximumWeight ?: grossExposureConstraint.maximum }
        require(longMinimum - shortMaximum <= netExposureConstraint.maximum + EPSILON)
        require(longMaximum - shortMinimum + EPSILON >= netExposureConstraint.minimum)
    }

    private fun validateAllocationModel() {
        val returnSeeking = sleeves.filter { it.role == CompositeSleeveRole.RETURN_SEEKING }
        when (allocationModel) {
            CompositeAllocationModel.STATIC_TARGET -> {
                require(sleeves.all { sleeve ->
                    sleeve.targetWeight != null && sleeve.minimumWeight == sleeve.targetWeight &&
                        sleeve.maximumWeight == sleeve.targetWeight && sleeve.riskBudget == null
                })
                val gross = sleeves.sumOf { requireNotNull(it.targetWeight) }
                val net = sleeves.sumOf { requireNotNull(it.signedTargetWeight) }
                require(grossExposureConstraint.target != null && abs(grossExposureConstraint.target - gross) <= EPSILON)
                require(netExposureConstraint.target != null && abs(netExposureConstraint.target - net) <= EPSILON)
            }
            CompositeAllocationModel.EQUAL_RISK_CONTRIBUTION -> {
                require(returnSeeking.isNotEmpty())
                require(returnSeeking.all { it.riskBudget != null })
                require(sleeves.filterNot { it.role == CompositeSleeveRole.RETURN_SEEKING }.all { it.riskBudget == null })
                require(abs(returnSeeking.sumOf { requireNotNull(it.riskBudget) } - 1.0) <= EPSILON)
                requireNotNull(riskLookbackTradingDays)
            }
            CompositeAllocationModel.ACTIVE_LONG_SHORT ->
                require(sleeves.any { it.direction == CompositeSleeveDirection.SHORT })
            CompositeAllocationModel.DURATION_HEDGE -> {
                requireNotNull(durationConstraint)
                require(sleeves.size == 2 && sleeves.all {
                    it.direction == CompositeSleeveDirection.LONG
                }) { "DURATION_HEDGE에는 정확히 두 개의 LONG sleeve가 필요합니다." }
                require(sleeves.any { it.role == CompositeSleeveRole.HEDGE })
                require(sleeves.any { it.mbsInterestOnlyTerms != null })
            }
            CompositeAllocationModel.TACTICAL_ALLOCATION,
            CompositeAllocationModel.SYSTEMATIC_ALTERNATIVE,
            -> requireNotNull(riskLookbackTradingDays)
            CompositeAllocationModel.TARGET_BAND -> require(sleeves.all { it.minimumWeight != null })
        }
        if (allocationModel != CompositeAllocationModel.EQUAL_RISK_CONTRIBUTION) {
            require(sleeves.all { it.riskBudget == null })
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is CompositeReferenceProfile &&
            sleeves == other.sleeves && allocationModel == other.allocationModel &&
            grossExposureConstraint == other.grossExposureConstraint &&
            netExposureConstraint == other.netExposureConstraint &&
            annualFinancingSpread == other.annualFinancingSpread &&
            annualFinancingSpreadOrigin == other.annualFinancingSpreadOrigin &&
            targetVolatilityAnnual == other.targetVolatilityAnnual &&
            targetVolatilityOrigin == other.targetVolatilityOrigin &&
            riskLookbackTradingDays == other.riskLookbackTradingDays &&
            riskLookbackOrigin == other.riskLookbackOrigin && durationConstraint == other.durationConstraint &&
            driftThreshold == other.driftThreshold && driftThresholdOrigin == other.driftThresholdOrigin &&
            selectionSchedule == other.selectionSchedule && reweightSchedule == other.reweightSchedule &&
            supportLevel == other.supportLevel && provenance == other.provenance && confidence == other.confidence &&
            officialSourceUrls == other.officialSourceUrls && assumptionId == other.assumptionId

    override fun hashCode(): Int {
        var result = sleeves.hashCode()
        result = 31 * result + allocationModel.hashCode()
        result = 31 * result + grossExposureConstraint.hashCode()
        result = 31 * result + netExposureConstraint.hashCode()
        result = 31 * result + (annualFinancingSpread?.hashCode() ?: 0)
        result = 31 * result + (annualFinancingSpreadOrigin?.hashCode() ?: 0)
        result = 31 * result + (targetVolatilityAnnual?.hashCode() ?: 0)
        result = 31 * result + (targetVolatilityOrigin?.hashCode() ?: 0)
        result = 31 * result + (riskLookbackTradingDays ?: 0)
        result = 31 * result + (riskLookbackOrigin?.hashCode() ?: 0)
        result = 31 * result + (durationConstraint?.hashCode() ?: 0)
        result = 31 * result + (driftThreshold?.hashCode() ?: 0)
        result = 31 * result + (driftThresholdOrigin?.hashCode() ?: 0)
        result = 31 * result + selectionSchedule.hashCode()
        result = 31 * result + reweightSchedule.hashCode()
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
        const val MAX_SLEEVES: Int = 64
        const val MAX_OFFICIAL_SOURCE_URLS: Int = 16
        const val MAX_ANNUAL_RATE: Double = 1.0
        const val MAX_TARGET_VOLATILITY: Double = 5.0
        const val MIN_LOOKBACK_DAYS: Int = 2
        const val MAX_LOOKBACK_DAYS: Int = 2_520
        private const val EPSILON: Double = 1e-8
        private val ASSUMPTION_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
