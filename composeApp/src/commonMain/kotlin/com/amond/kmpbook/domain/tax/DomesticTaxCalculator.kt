package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

fun domesticListedSaleTaxTreatment(
    isOnExchange: Boolean,
    assessment: MajorShareholderAssessment,
): DomesticListedSaleTaxTreatment = when {
    assessment.isMajorShareholder -> DomesticListedSaleTaxTreatment.TAXABLE_MAJOR_SHAREHOLDER
    isOnExchange -> DomesticListedSaleTaxTreatment.EXEMPT_SMALL_SHAREHOLDER_ON_EXCHANGE
    else -> DomesticListedSaleTaxTreatment.TAXABLE_OFF_EXCHANGE
}

internal fun progressiveNationalTax(
    taxableBaseKrw: Long,
    lowerBandUpperKrw: Long,
    lowerRate: TaxRate,
    upperRate: TaxRate,
    rounding: MoneyRoundingPolicy,
): Long {
    val lowerBase = minOf(taxableBaseKrw, lowerBandUpperKrw)
    val upperBase = (taxableBaseKrw - lowerBandUpperKrw).coerceAtLeast(0L)
    return lowerRate.apply(lowerBase, Currency.KRW, rounding).minorUnits +
        upperRate.apply(upperBase, Currency.KRW, rounding).minorUnits
}
