package com.amond.kmpbook.presentation.portfolio

/** Exact aggregation order shared by runtime snapshots and canonical prefix replay. */
object CanonicalPortfolioAccountingTotals {
    fun cumulativeTaxKrw(
        saleTaxesKrw: Sequence<Double>,
        dividendWithholdingTaxesKrw: Sequence<Double>,
        paidAnnualTaxesKrw: Sequence<Double>,
    ): Double = combineCumulativeTaxCategorySums(
        saleTaxKrw = saleTaxesKrw.sum(),
        dividendWithholdingTaxKrw = dividendWithholdingTaxesKrw.sum(),
        paidAnnualTaxKrw = paidAnnualTaxesKrw.sum(),
    )

    fun combineCumulativeTaxCategorySums(
        saleTaxKrw: Double,
        dividendWithholdingTaxKrw: Double,
        paidAnnualTaxKrw: Double,
    ): Double = (saleTaxKrw + dividendWithholdingTaxKrw) + paidAnnualTaxKrw

    fun checkedTaxGainKrwSum(gainsKrw: Sequence<Long>): Long {
        var total = 0L
        gainsKrw.forEach { gain ->
            total = checkedTaxGainKrwAdd(total, gain)
        }
        return total
    }

    fun checkedTaxGainKrwAdd(currentTotalKrw: Long, gainKrw: Long): Long {
        val next = currentTotalKrw + gainKrw
        if (((currentTotalKrw xor next) and (gainKrw xor next)) < 0L) {
            throw ArithmeticException(
                "Realized tax-gain total exceeds the signed 64-bit accounting range.",
            )
        }
        return next
    }

    fun checkedTaxGainKrwSubtract(currentTotalKrw: Long, priorGainKrw: Long): Long {
        val next = currentTotalKrw - priorGainKrw
        if (((currentTotalKrw xor priorGainKrw) and (currentTotalKrw xor next)) < 0L) {
            throw ArithmeticException(
                "Realized tax-gain total exceeds the signed 64-bit accounting range.",
            )
        }
        return next
    }

    fun replaceCheckedTaxGainKrwTotal(
        currentTotalKrw: Long,
        priorGainKrw: Long,
        nextGainKrw: Long,
        updatedOrderedGainsKrw: Sequence<Long>,
    ): Long = try {
        checkedTaxGainKrwAdd(
            checkedTaxGainKrwSubtract(currentTotalKrw, priorGainKrw),
            nextGainKrw,
        )
    } catch (_: ArithmeticException) {
        checkedTaxGainKrwSum(updatedOrderedGainsKrw)
    }

    fun realizedProfitKrw(gainsKrw: Sequence<Long>): Double =
        checkedTaxGainKrwSum(gainsKrw).toDouble()
}
