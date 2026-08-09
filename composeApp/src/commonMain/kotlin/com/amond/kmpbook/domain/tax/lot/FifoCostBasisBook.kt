package com.amond.kmpbook.domain.tax.lot

import kotlin.math.abs
import kotlin.math.floor
import kotlinx.datetime.LocalDate

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

    /**
     * 분할·병합은 처분이 아니므로 취득일과 총 원화원가를 보존하고 수량만 조정한다.
     * 단주는 캠페인에서 소수 권리로 유지하며, 전량 처분 시 단위 예외를 허용한다.
     */
    fun applyQuantityMultiplier(stockId: String, multiplier: Double): FifoCostBasisBook {
        require(stockId.isNotBlank())
        require(multiplier > 0.0 && multiplier.isFinite())
        return copy(
            lots = lots.map { lot ->
                if (lot.stockId == stockId) {
                    lot.copy(remainingQuantity = lot.remainingQuantity * multiplier)
                } else {
                    lot
                }
            },
        )
    }

    /**
     * RIC/CEF 분배의 원금환급(ROC)은 현금은 받지만 즉시 배당소득이 아니므로, 분배 기준일에
     * 보유한 각 lot에 주당 동일 금액을 배분해 원화 세무원가를 낮춘다. 특정 FIFO lot부터
     * 차감하지 않으며, 각 lot에 배분된 금액이 그 lot의 원가를 넘는 부분만 양도이득으로 돌린다.
     */
    fun applyReturnOfCapital(stockId: String, amountKrw: Long): Pair<FifoCostBasisBook, Long> {
        require(stockId.isNotBlank())
        require(amountKrw >= 0L)
        if (amountKrw == 0L) return this to 0L
        val matching = lots.filter { it.stockId == stockId }
        if (matching.isEmpty()) return this to amountKrw
        val totalQuantity = matching.sumOf(TaxLot::remainingQuantity)
        require(totalQuantity > 0.0)

        var allocatedRoc = 0L
        var excessGain = 0L
        var matchingIndex = 0
        val adjustedLots = lots.map { lot ->
            if (lot.stockId != stockId) return@map lot
            val isLast = matchingIndex == matching.lastIndex
            val lotRoc = if (isLast) {
                amountKrw - allocatedRoc
            } else {
                floor(amountKrw.toDouble() * lot.remainingQuantity / totalQuantity).toLong()
            }
            matchingIndex += 1
            allocatedRoc += lotRoc
            val lotReduction = minOf(lot.remainingCostBasisKrw, lotRoc)
            excessGain += lotRoc - lotReduction
            lot.copy(remainingCostBasisKrw = lot.remainingCostBasisKrw - lotReduction)
        }
        check(allocatedRoc == amountKrw) { "ROC allocation did not consume the requested amount." }
        return copy(lots = adjustedLots) to excessGain
    }

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
