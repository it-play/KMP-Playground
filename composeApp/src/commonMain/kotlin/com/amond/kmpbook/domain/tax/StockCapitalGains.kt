package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.floor

data class TaxLot(
    val lotId: String,
    val stockId: String,
    val acquiredOn: LocalDate,
    val remainingQuantity: Double,
    /** Purchase price plus directly attributable purchase costs, translated to KRW. */
    val remainingCostBasisKrw: Long,
) {
    init {
        require(lotId.isNotBlank() && stockId.isNotBlank()) { "A tax lot needs a lot and stock id." }
        require(remainingQuantity > 0.0) { "A tax-lot quantity must be positive." }
        require(remainingCostBasisKrw >= 0L) { "A tax-lot cost basis cannot be negative." }
    }
}

data class ConsumedTaxLot(
    val lotId: String,
    val acquiredOn: LocalDate,
    val quantity: Double,
    val allocatedCostBasisKrw: Long,
)

data class FifoSaleResult(
    val stockId: String,
    val soldOn: LocalDate,
    val soldQuantity: Double,
    val grossProceedsKrw: Long,
    val allocatedCostBasisKrw: Long,
    val directSellingCostsKrw: Long,
    val realizedGainKrw: Long,
    val consumedLots: List<ConsumedTaxLot>,
    val updatedBook: FifoCostBasisBook,
)

/**
 * Immutable FIFO lot book. The final fraction of a lot consumes its full remaining won basis so
 * repeated partial sales never create or destroy basis through rounding.
 */
data class FifoCostBasisBook(
    val lots: List<TaxLot> = emptyList(),
) {
    init {
        require(lots.map { it.lotId }.distinct().size == lots.size) { "Tax-lot ids must be unique." }
    }

    val method: CostBasisMethod get() = CostBasisMethod.FIFO

    fun addPurchase(
        lotId: String,
        stockId: String,
        acquiredOn: LocalDate,
        quantity: Double,
        purchasePriceKrw: Long,
        directPurchaseCostsKrw: Long = 0L,
    ): FifoCostBasisBook {
        require(quantity > 0.0) { "Purchase quantity must be positive." }
        require(purchasePriceKrw >= 0L && directPurchaseCostsKrw >= 0L) {
            "Purchase price and costs cannot be negative."
        }
        require(lots.none { it.lotId == lotId }) { "Tax-lot id $lotId already exists." }
        return copy(
            lots = lots + TaxLot(
                lotId = lotId,
                stockId = stockId,
                acquiredOn = acquiredOn,
                remainingQuantity = quantity,
                remainingCostBasisKrw = purchasePriceKrw + directPurchaseCostsKrw,
            ),
        )
    }

    fun sell(
        stockId: String,
        soldOn: LocalDate,
        quantity: Double,
        grossProceedsKrw: Long,
        directSellingCostsKrw: Long = 0L,
    ): FifoSaleResult {
        require(stockId.isNotBlank()) { "A stock id is required." }
        require(quantity > 0.0) { "Sale quantity must be positive." }
        require(grossProceedsKrw >= 0L && directSellingCostsKrw >= 0L) {
            "Sale proceeds and costs cannot be negative."
        }
        val available = lots.filter { it.stockId == stockId }.sumOf { it.remainingQuantity }
        require(available + QUANTITY_EPSILON >= quantity) {
            "Cannot sell $quantity shares of $stockId; FIFO book contains $available."
        }

        var remainingToSell = quantity
        var allocatedBasis = 0L
        val consumed = mutableListOf<ConsumedTaxLot>()
        val replacements = mutableMapOf<String, TaxLot?>()

        lots.withIndex()
            .filter { it.value.stockId == stockId }
            .sortedWith(compareBy<IndexedValue<TaxLot>> { it.value.acquiredOn }.thenBy { it.index })
            .forEach { indexed ->
                if (remainingToSell <= QUANTITY_EPSILON) return@forEach
                val lot = indexed.value
                require(lot.acquiredOn <= soldOn) { "A sale cannot consume a lot acquired later." }
                val usedQuantity = minOf(remainingToSell, lot.remainingQuantity)
                val consumesEntireLot = abs(usedQuantity - lot.remainingQuantity) <= QUANTITY_EPSILON
                val usedBasis = if (consumesEntireLot) {
                    lot.remainingCostBasisKrw
                } else {
                    floor(
                        lot.remainingCostBasisKrw.toDouble() * usedQuantity / lot.remainingQuantity,
                    ).toLong()
                }
                allocatedBasis += usedBasis
                consumed += ConsumedTaxLot(
                    lotId = lot.lotId,
                    acquiredOn = lot.acquiredOn,
                    quantity = usedQuantity,
                    allocatedCostBasisKrw = usedBasis,
                )
                replacements[lot.lotId] = if (consumesEntireLot) {
                    null
                } else {
                    lot.copy(
                        remainingQuantity = lot.remainingQuantity - usedQuantity,
                        remainingCostBasisKrw = lot.remainingCostBasisKrw - usedBasis,
                    )
                }
                remainingToSell -= usedQuantity
            }

        require(remainingToSell <= QUANTITY_EPSILON) { "FIFO allocation did not consume the full sale." }
        val updatedLots = lots.mapNotNull { lot -> replacements[lot.lotId] ?: if (lot.lotId in replacements) null else lot }
        val gain = grossProceedsKrw - allocatedBasis - directSellingCostsKrw
        return FifoSaleResult(
            stockId = stockId,
            soldOn = soldOn,
            soldQuantity = quantity,
            grossProceedsKrw = grossProceedsKrw,
            allocatedCostBasisKrw = allocatedBasis,
            directSellingCostsKrw = directSellingCostsKrw,
            realizedGainKrw = gain,
            consumedLots = consumed,
            updatedBook = copy(lots = updatedLots),
        )
    }

    private companion object {
        const val QUANTITY_EPSILON = 1e-8
    }
}

enum class StockGainTaxTreatment(val displayName: String) {
    DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE("국내 장내 소액주주 비과세"),
    DOMESTIC_MAJOR_GENERAL("국내 대주주 일반세율"),
    DOMESTIC_MAJOR_NON_SME_SHORT_TERM("국내 비중소기업 대주주 1년 미만"),
    FOREIGN_STANDARD("국외주식 일반"),
}

data class RealizedStockGain(
    val id: String,
    val stockId: String,
    /** The Korean tax transfer/settlement date, not necessarily the order date. */
    val realizedOn: LocalDate,
    val gainKrw: Long,
    val treatment: StockGainTaxTreatment,
    val instrumentTaxClass: ForeignInstrumentTaxClass? = null,
) {
    init {
        require(id.isNotBlank() && stockId.isNotBlank()) { "A realized gain needs an id and stock id." }
        require(
            treatment == StockGainTaxTreatment.FOREIGN_STANDARD || instrumentTaxClass == null,
        ) { "Only a foreign gain should carry a foreign instrument tax class." }
    }
}

data class AnnualStockTaxRequest(
    val taxYear: Int,
    val gains: List<RealizedStockGain>,
    val financialIncomeGrossKrw: Long = 0L,
    val highDividendIncomeKrw: Long = 0L,
    val foreignTaxPaidKrw: Long = 0L,
    val withholdingCreditsKrw: Long = 0L,
    val roundingPolicy: MoneyRoundingPolicy = MoneyRoundingPolicy.TAX_WON_DOWN,
) {
    init {
        require(taxYear in 2026..2040) { "The frozen scenario supports tax years 2026 through 2040." }
        require(gains.all { it.realizedOn.year == taxYear }) { "Every gain must belong to taxYear." }
        require(financialIncomeGrossKrw >= 0L && highDividendIncomeKrw >= 0L) {
            "Financial income cannot be negative."
        }
        require(foreignTaxPaidKrw >= 0L && withholdingCreditsKrw >= 0L) {
            "Taxes paid and credits cannot be negative."
        }
    }
}

class AnnualStockTaxCalculator(
    private val policy: TaxPolicyPack = TaxPolicyPack2026.POLICY,
) {
    fun calculate(request: AnnualStockTaxRequest): AnnualTaxLedger {
        val closingDate = LocalDate(request.taxYear, 12, 31)
        policy.requireSimulationDate(closingDate)
        val taxableEntries = request.gains
            .filter { it.treatment != StockGainTaxTreatment.DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE }
            .sortedBy { it.realizedOn }

        val domesticGain = taxableEntries
            .filter { it.treatment != StockGainTaxTreatment.FOREIGN_STANDARD }
            .sumOf { it.gainKrw }
        val foreignGain = taxableEntries
            .filter { it.treatment == StockGainTaxTreatment.FOREIGN_STANDARD }
            .sumOf { it.gainKrw }
        val netGain = domesticGain + foreignGain
        val positiveNet = netGain.coerceAtLeast(0L)
        val deduction = minOf(positiveNet, policy.foreignStockCapitalGains.annualBasicDeductionKrw)
        val taxableBase = positiveNet - deduction
        val expiredLoss = (-netGain).coerceAtLeast(0L)

        val allocatedBases = allocateTaxableBaseByTreatment(
            entries = taxableEntries,
            finalTaxableBaseKrw = taxableBase,
        )
        val liabilities = buildList {
            allocatedBases[StockGainTaxTreatment.FOREIGN_STANDARD]
                ?.takeIf { it > 0L }
                ?.let { add(foreignStockLiability(request, it)) }
            allocatedBases[StockGainTaxTreatment.DOMESTIC_MAJOR_GENERAL]
                ?.takeIf { it > 0L }
                ?.let { add(domesticMajorLiability(request, it, shortTerm = false)) }
            allocatedBases[StockGainTaxTreatment.DOMESTIC_MAJOR_NON_SME_SHORT_TERM]
                ?.takeIf { it > 0L }
                ?.let { add(domesticMajorLiability(request, it, shortTerm = true)) }
        }

        val crossRateWarning = taxableEntries.map { it.treatment }.distinct().size > 1 &&
            taxableEntries.any { it.gainKrw < 0L }
        val warnings = buildList {
            if (expiredLoss > 0L) {
                add("주식 양도차손 ${expiredLoss}원은 현재 정책에서 다음 연도로 이월되지 않습니다.")
            }
            if (crossRateWarning) {
                add("서로 다른 세율의 주식 손익이 섞여 있어 신고서의 법정 통산 순서를 확인해야 합니다.")
            }
            if (request.financialIncomeGrossKrw > policy.financialIncomeComprehensiveThresholdKrw) {
                add("금융소득이 2,000만원을 초과했습니다. 다른 종합소득 정보가 없어 종합과세는 추정입니다.")
            }
            add("2026-08-07 세법 동결 시나리오이며 2027년 이후 실제 개정은 자동 반영되지 않습니다.")
        }

        return AnnualTaxLedger(
            taxYear = request.taxYear,
            policyId = policy.id,
            taxableDomesticGainKrw = domesticGain,
            foreignGainKrw = foreignGain,
            currentYearNetStockGainKrw = netGain,
            sharedStockBasicDeductionKrw = deduction,
            stockTaxableBaseKrw = taxableBase,
            expiredStockLossKrw = expiredLoss,
            financialIncomeGrossKrw = request.financialIncomeGrossKrw,
            highDividendIncomeKrw = request.highDividendIncomeKrw,
            foreignTaxPaidKrw = request.foreignTaxPaidKrw,
            withholdingCreditsKrw = request.withholdingCreditsKrw,
            liabilities = liabilities,
            warnings = warnings,
        )
    }

    private fun foreignStockLiability(request: AnnualStockTaxRequest, baseKrw: Long): TaxLiability {
        val rule = policy.foreignStockCapitalGains
        val national = rule.nationalRate.apply(baseKrw, Currency.KRW, request.roundingPolicy).minorUnits
        val local = rule.localRate.apply(baseKrw, Currency.KRW, request.roundingPolicy).minorUnits
        val items = listOf(
            annualCapitalGainLine(
                id = "foreign-stock-cgt-national-${request.taxYear}",
                label = "국외주식 양도소득세",
                amountKrw = national,
                jurisdiction = TaxJurisdiction.KOREA_NATIONAL,
                category = TaxCategory.CAPITAL_GAINS,
                source = rule.source,
                range = rule.effectiveRange,
            ),
            annualCapitalGainLine(
                id = "foreign-stock-cgt-local-${request.taxYear}",
                label = "국외주식 양도 지방소득세",
                amountKrw = local,
                jurisdiction = TaxJurisdiction.KOREA_LOCAL,
                category = TaxCategory.LOCAL_INCOME,
                source = rule.source,
                range = rule.effectiveRange,
            ),
        )
        return TaxLiability(
            id = "foreign-stock-cgt-${request.taxYear}",
            label = "${request.taxYear}년 국외주식 양도세",
            taxYear = request.taxYear,
            assessedTaxKrw = national + local,
            dueDate = LocalDate(request.taxYear + 1, 5, 31),
            status = TaxLiabilityStatus.DUE,
            items = items,
            warnings = listOf("법정 신고일이 휴일이면 다음 영업일 조정이 필요합니다."),
        )
    }

    private fun domesticMajorLiability(
        request: AnnualStockTaxRequest,
        baseKrw: Long,
        shortTerm: Boolean,
    ): TaxLiability {
        return DomesticMajorCapitalGainsCalculator(policy).calculate(
            DomesticMajorCapitalGainsRequest(
                taxYear = request.taxYear,
                taxableBaseKrw = baseKrw,
                isSmallOrMediumEnterprise = !shortTerm,
                heldLessThanOneYear = shortTerm,
                calculatedOn = LocalDate(request.taxYear, 12, 31),
                roundingPolicy = request.roundingPolicy,
            ),
        )
    }

    /**
     * Same-year losses and the one shared deduction are applied chronologically. This preserves the
     * statutory "earlier transfer first" basis-deduction rule. Mixed-rate loss cases remain flagged.
     */
    private fun allocateTaxableBaseByTreatment(
        entries: List<RealizedStockGain>,
        finalTaxableBaseKrw: Long,
    ): Map<StockGainTaxTreatment, Long> {
        if (finalTaxableBaseKrw == 0L) return emptyMap()
        val positiveByTreatment = entries
            .groupBy { it.treatment }
            .mapValues { (_, values) -> values.sumOf { it.gainKrw }.coerceAtLeast(0L) }
            .filterValues { it > 0L }
        if (positiveByTreatment.isEmpty()) return emptyMap()

        val earliest = entries
            .filter { it.gainKrw > 0L }
            .groupBy { it.treatment }
            .mapValues { (_, values) -> values.minOf { it.realizedOn } }
        var remaining = finalTaxableBaseKrw
        val result = linkedMapOf<StockGainTaxTreatment, Long>()
        positiveByTreatment.keys.sortedBy { earliest.getValue(it) }.forEach { treatment ->
            val assigned = minOf(positiveByTreatment.getValue(treatment), remaining)
            if (assigned > 0L) result[treatment] = assigned
            remaining -= assigned
        }
        require(remaining == 0L) { "Taxable-base allocation failed by $remaining won." }
        return result
    }

    private fun annualCapitalGainLine(
        id: String,
        label: String,
        amountKrw: Long,
        jurisdiction: TaxJurisdiction,
        category: TaxCategory,
        source: RuleSource,
        range: EffectiveDateRange,
    ) = TaxLineItem(
        id = id,
        label = label,
        amount = MoneyAmount(amountKrw, Currency.KRW),
        jurisdiction = jurisdiction,
        category = category,
        source = source,
        effectiveRange = range,
    )
}
