package com.amond.kmpbook.domain.model.fund

/**
 * Executable selection policy for an ETF whose constituents are themselves managed funds.
 *
 * The profile never claims to predict future real holdings. It applies verified public rules or an
 * explicitly versioned model assumption to a campaign-seeded, non-tradable candidate universe.
 */
class FundOfFundsMethodologyProfile(
    val universe: FundOfFundsUniverse,
    val selectionModel: FundOfFundsSelectionModel,
    val weightingModel: FundOfFundsWeightingModel,
    val targetFundCount: Int,
    val candidateUniverseSize: Int,
    eligibleCategories: Set<FundOfFundsCategory>,
    categoryReferences: List<FundOfFundsCategoryReference>,
    val minimumDistributionYield: Double,
    val maximumAbsoluteDiscount: Double,
    val minimumLiquidityScore: Double,
    val individualWeightCap: Double,
    val categoryWeightCap: Double,
    rankedWeightCapTiers: List<FundOfFundsRankedWeightCapTier> = emptyList(),
    val selectionCalendar: FundOfFundsRebalanceCalendar,
    selectionMonths: Set<Int>,
    val reweightCalendar: FundOfFundsRebalanceCalendar,
    reweightMonths: Set<Int>,
    val supportLevel: BenchmarkSupportLevel,
    val provenance: FundOfFundsRuleProvenance,
    val confidence: FundOfFundsConfidence,
    officialSourceUrls: Set<String>,
    val assumptionId: String?,
) {
    val eligibleCategories: Set<FundOfFundsCategory> = eligibleCategories
        .sortedBy(FundOfFundsCategory::ordinal)
        .toCollection(linkedSetOf())
        .toSet()
    val categoryReferences: List<FundOfFundsCategoryReference> = categoryReferences
        .sortedBy { reference -> reference.category.ordinal }
        .toList()
    val selectionMonths: Set<Int> = selectionMonths.sorted().toCollection(linkedSetOf()).toSet()
    val reweightMonths: Set<Int> = reweightMonths.sorted().toCollection(linkedSetOf()).toSet()
    val officialSourceUrls: Set<String> = officialSourceUrls.sorted().toCollection(linkedSetOf()).toSet()
    val rankedWeightCapTiers: List<FundOfFundsRankedWeightCapTier> =
        rankedWeightCapTiers.sortedBy(FundOfFundsRankedWeightCapTier::lastRankInclusive).toList()

    init {
        require(targetFundCount in 1..MAX_TARGET_FUNDS)
        require(candidateUniverseSize in targetFundCount..MAX_CANDIDATE_FUNDS)
        require(this.eligibleCategories.isNotEmpty())
        require(this.categoryReferences.isNotEmpty())
        require(this.categoryReferences.map(FundOfFundsCategoryReference::category).distinct().size ==
            this.categoryReferences.size)
        require(this.categoryReferences.map(FundOfFundsCategoryReference::category).toSet() ==
            this.eligibleCategories) {
            "Every eligible underlying-fund category needs exactly one executable benchmark reference."
        }
        require(minimumDistributionYield.isFinite() && minimumDistributionYield in 0.0..1.0)
        require(maximumAbsoluteDiscount.isFinite() && maximumAbsoluteDiscount in 0.0..MAX_DISCOUNT)
        require(minimumLiquidityScore.isFinite() && minimumLiquidityScore in 0.0..1.0)
        require(individualWeightCap.isFinite() && individualWeightCap in MIN_WEIGHT..1.0)
        require(categoryWeightCap.isFinite() && categoryWeightCap in individualWeightCap..1.0)
        require(
            if (this.rankedWeightCapTiers.isEmpty()) {
                targetFundCount * individualWeightCap >= 1.0 - WEIGHT_EPSILON
            } else {
                this.rankedWeightCapTiers.last().lastRankInclusive == targetFundCount &&
                    this.rankedWeightCapTiers.zipWithNext().all { (left, right) ->
                        left.lastRankInclusive < right.lastRankInclusive
                    } &&
                    this.rankedWeightCapTiers.all { tier ->
                        tier.maximumWeight <= individualWeightCap
                    } && rankedCapCapacity() >= 1.0 - WEIGHT_EPSILON
            },
        ) { "Ranked constituent caps cannot allocate a complete fund-of-funds portfolio." }
        require(this.eligibleCategories.size * categoryWeightCap >= 1.0 - WEIGHT_EPSILON) {
            "Eligible category caps cannot allocate a complete fund-of-funds portfolio."
        }
        validateCalendar(selectionCalendar, this.selectionMonths, "selection")
        validateCalendar(reweightCalendar, this.reweightMonths, "reweight")
        require(supportLevel != BenchmarkSupportLevel.VERIFIED_RULES ||
            provenance == FundOfFundsRuleProvenance.VERIFIED_INDEX_METHODOLOGY)
        require(this.officialSourceUrls.size <= MAX_OFFICIAL_SOURCE_URLS)
        require(this.officialSourceUrls.all(::isValidHttpsUrl))
        when (provenance) {
            FundOfFundsRuleProvenance.VERIFIED_INDEX_METHODOLOGY,
            FundOfFundsRuleProvenance.VERIFIED_PRODUCT_DISCLOSURE,
            -> {
                require(this.officialSourceUrls.isNotEmpty())
                require(assumptionId == null)
            }
            FundOfFundsRuleProvenance.MODEL_ASSUMPTION -> {
                require(!assumptionId.isNullOrBlank())
                require(ASSUMPTION_ID_PATTERN.matches(assumptionId))
            }
        }
        when (universe) {
            FundOfFundsUniverse.US_CLOSED_END_FUNDS -> require(
                FundOfFundsCategory.SINGLE_SECURITY_OPTION_INCOME !in this.eligibleCategories,
            )
            FundOfFundsUniverse.US_OPTION_INCOME_ETFS -> require(
                this.eligibleCategories.all { category ->
                    category in setOf(
                        FundOfFundsCategory.EQUITY_OPTION_INCOME,
                        FundOfFundsCategory.SINGLE_SECURITY_OPTION_INCOME,
                    )
                },
            )
        }
    }

    val componentBenchmarkRefs: Set<BenchmarkRef> = this.categoryReferences
        .mapTo(linkedSetOf(), FundOfFundsCategoryReference::benchmarkRef)

    fun benchmarkRefFor(category: FundOfFundsCategory): BenchmarkRef =
        categoryReferences.first { reference -> reference.category == category }.benchmarkRef

    fun weightCapAtRank(rank: Int): Double {
        require(rank in 1..targetFundCount)
        return rankedWeightCapTiers.firstOrNull { tier -> rank <= tier.lastRankInclusive }
            ?.maximumWeight ?: individualWeightCap
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FundOfFundsMethodologyProfile &&
            universe == other.universe &&
            selectionModel == other.selectionModel &&
            weightingModel == other.weightingModel &&
            targetFundCount == other.targetFundCount &&
            candidateUniverseSize == other.candidateUniverseSize &&
            eligibleCategories == other.eligibleCategories &&
            categoryReferences == other.categoryReferences &&
            minimumDistributionYield == other.minimumDistributionYield &&
            maximumAbsoluteDiscount == other.maximumAbsoluteDiscount &&
            minimumLiquidityScore == other.minimumLiquidityScore &&
            individualWeightCap == other.individualWeightCap &&
            categoryWeightCap == other.categoryWeightCap &&
            rankedWeightCapTiers == other.rankedWeightCapTiers &&
            selectionCalendar == other.selectionCalendar &&
            selectionMonths == other.selectionMonths &&
            reweightCalendar == other.reweightCalendar &&
            reweightMonths == other.reweightMonths &&
            supportLevel == other.supportLevel &&
            provenance == other.provenance &&
            confidence == other.confidence &&
            officialSourceUrls == other.officialSourceUrls &&
            assumptionId == other.assumptionId

    override fun hashCode(): Int {
        var result = universe.hashCode()
        result = 31 * result + selectionModel.hashCode()
        result = 31 * result + weightingModel.hashCode()
        result = 31 * result + targetFundCount
        result = 31 * result + candidateUniverseSize
        result = 31 * result + eligibleCategories.hashCode()
        result = 31 * result + categoryReferences.hashCode()
        result = 31 * result + minimumDistributionYield.hashCode()
        result = 31 * result + maximumAbsoluteDiscount.hashCode()
        result = 31 * result + minimumLiquidityScore.hashCode()
        result = 31 * result + individualWeightCap.hashCode()
        result = 31 * result + categoryWeightCap.hashCode()
        result = 31 * result + rankedWeightCapTiers.hashCode()
        result = 31 * result + selectionCalendar.hashCode()
        result = 31 * result + selectionMonths.hashCode()
        result = 31 * result + reweightCalendar.hashCode()
        result = 31 * result + reweightMonths.hashCode()
        result = 31 * result + supportLevel.hashCode()
        result = 31 * result + provenance.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        result = 31 * result + (assumptionId?.hashCode() ?: 0)
        return result
    }

    private fun validateCalendar(
        calendar: FundOfFundsRebalanceCalendar,
        months: Set<Int>,
        label: String,
    ) {
        require(months.all { month -> month in 1..12 })
        when (calendar) {
            FundOfFundsRebalanceCalendar.MONTHLY -> require(months == (1..12).toSet())
            FundOfFundsRebalanceCalendar.QUARTERLY -> require(months.size == 4)
            FundOfFundsRebalanceCalendar.SEMI_ANNUAL -> require(months.size == 2)
            FundOfFundsRebalanceCalendar.ANNUAL -> require(months.size == 1)
        }
        require(months.isNotEmpty()) { "$label calendar must have explicit months." }
    }

    private fun rankedCapCapacity(): Double {
        var priorRank = 0
        return rankedWeightCapTiers.sumOf { tier ->
            val count = tier.lastRankInclusive - priorRank
            priorRank = tier.lastRankInclusive
            count * tier.maximumWeight
        }
    }

    private fun isValidHttpsUrl(value: String): Boolean =
        value.length <= MAX_URL_LENGTH && value.startsWith("https://") &&
            value.length > "https://".length && value.none(Char::isISOControl)

    companion object {
        const val MAX_TARGET_FUNDS: Int = 256
        const val MAX_CANDIDATE_FUNDS: Int = 512
        const val MAX_OFFICIAL_SOURCE_URLS: Int = 16
        const val MAX_URL_LENGTH: Int = 2_048
        private const val MAX_DISCOUNT: Double = 0.95
        private const val MIN_WEIGHT: Double = 1e-6
        private const val WEIGHT_EPSILON: Double = 1e-9
        private val ASSUMPTION_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
