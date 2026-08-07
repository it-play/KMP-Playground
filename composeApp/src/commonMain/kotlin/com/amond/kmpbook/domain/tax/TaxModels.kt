package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/** Inclusive effective-date range for a tax or fee rule. */
data class EffectiveDateRange(
    val validFrom: LocalDate,
    val validThrough: LocalDate? = null,
) {
    init {
        require(validThrough == null || validThrough >= validFrom) {
            "The effective-date range cannot end before it starts."
        }
    }

    operator fun contains(date: LocalDate): Boolean =
        date >= validFrom && (validThrough == null || date <= validThrough)
}

data class RuleSource(
    val title: String,
    val url: String? = null,
    val accessedOn: LocalDate = TaxPolicyPack2026.FROZEN_AS_OF,
) {
    init {
        require(title.isNotBlank()) { "A rule source needs a title." }
        require(url == null || url.startsWith("https://")) { "A source URL must use HTTPS." }
    }
}

/**
 * Money is stored in the currency's smallest unit: won for KRW and cents for USD.
 * This prevents UI formatting and floating-point noise from changing a tax result.
 */
data class MoneyAmount(
    val minorUnits: Long,
    val currency: Currency,
) : Comparable<MoneyAmount> {
    val amount: Double
        get() = minorUnits / 10.0.pow(currency.decimalPlaces)

    override fun compareTo(other: MoneyAmount): Int {
        require(currency == other.currency) { "Money with different currencies cannot be compared." }
        return minorUnits.compareTo(other.minorUnits)
    }

    operator fun plus(other: MoneyAmount): MoneyAmount {
        require(currency == other.currency) { "Money with different currencies cannot be added." }
        val result = minorUnits + other.minorUnits
        require(((minorUnits xor result) and (other.minorUnits xor result)) >= 0L) { "Money addition overflow." }
        return copy(minorUnits = result)
    }

    operator fun minus(other: MoneyAmount): MoneyAmount {
        require(currency == other.currency) { "Money with different currencies cannot be subtracted." }
        val result = minorUnits - other.minorUnits
        require(((minorUnits xor other.minorUnits) and (minorUnits xor result)) >= 0L) {
            "Money subtraction overflow."
        }
        return copy(minorUnits = result)
    }

    companion object {
        fun zero(currency: Currency): MoneyAmount = MoneyAmount(0L, currency)
    }
}

enum class RoundingDirection {
    DOWN,
    HALF_UP,
    UP,
}

/** [minorUnitIncrement] is one won/cent by default, but can express broker-specific 10-won rules. */
data class MoneyRoundingPolicy(
    val id: String,
    val direction: RoundingDirection,
    val minorUnitIncrement: Long = 1L,
) {
    init {
        require(id.isNotBlank()) { "A rounding policy needs an id." }
        require(minorUnitIncrement > 0L) { "The rounding increment must be positive." }
    }

    fun roundMinorUnits(unroundedMinorUnits: Double, currency: Currency): MoneyAmount {
        require(unroundedMinorUnits.isFinite()) { "The amount to round must be finite." }
        val scaled = unroundedMinorUnits / minorUnitIncrement
        val rounded = when (direction) {
            RoundingDirection.DOWN -> if (scaled >= 0.0) floor(scaled) else ceil(scaled)
            RoundingDirection.HALF_UP -> if (scaled >= 0.0) floor(scaled + 0.5) else ceil(scaled - 0.5)
            RoundingDirection.UP -> if (scaled >= 0.0) ceil(scaled) else floor(scaled)
        }
        require(rounded in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
            "The rounded amount does not fit in Long."
        }
        return MoneyAmount(rounded.toLong() * minorUnitIncrement, currency)
    }

    fun fromMajorUnits(unroundedAmount: Double, currency: Currency): MoneyAmount =
        roundMinorUnits(unroundedAmount * 10.0.pow(currency.decimalPlaces), currency)

    companion object {
        val TAX_WON_DOWN = MoneyRoundingPolicy("tax-won-down", RoundingDirection.DOWN)
        val MINOR_UNIT_HALF_UP = MoneyRoundingPolicy("minor-unit-half-up", RoundingDirection.HALF_UP)
        val REGULATORY_FEE_UP = MoneyRoundingPolicy("regulatory-fee-up", RoundingDirection.UP)
    }
}

/** Exact rational rate. 2,000 ppm is 0.20%; 220,000 ppm is 22%. */
data class TaxRate(
    val numerator: Long,
    val denominator: Long = PARTS_PER_MILLION,
) {
    init {
        require(numerator >= 0L) { "A tax rate cannot be negative." }
        require(denominator > 0L) { "A tax-rate denominator must be positive." }
    }

    val fraction: Double get() = numerator.toDouble() / denominator
    val percent: Double get() = fraction * 100.0

    fun apply(
        baseMinorUnits: Long,
        currency: Currency,
        rounding: MoneyRoundingPolicy,
    ): MoneyAmount {
        require(baseMinorUnits >= 0L) { "A tax base cannot be negative." }
        // Splitting the quotient avoids Long overflow for normal portfolio-sized bases.
        val whole = baseMinorUnits / denominator
        val remainder = baseMinorUnits % denominator
        val exact = whole.toDouble() * numerator + remainder.toDouble() * numerator / denominator
        return rounding.roundMinorUnits(exact, currency)
    }

    companion object {
        const val PARTS_PER_MILLION = 1_000_000L

        val ZERO = TaxRate(0L)
        val PERCENT_1_4 = TaxRate(14_000L)
        val PERCENT_2 = TaxRate(20_000L)
        val PERCENT_10 = TaxRate(100_000L)
        val PERCENT_14 = TaxRate(140_000L)
        val PERCENT_15 = TaxRate(150_000L)
        val PERCENT_20 = TaxRate(200_000L)
        val PERCENT_25 = TaxRate(250_000L)
        val PERCENT_30 = TaxRate(300_000L)
    }
}

enum class TaxJurisdiction(val displayName: String) {
    KOREA_NATIONAL("대한민국 국세"),
    KOREA_LOCAL("대한민국 지방소득세"),
    UNITED_STATES_FEDERAL("미국 연방"),
}

enum class TaxCategory(val displayName: String) {
    SECURITIES_TRANSACTION("증권거래세"),
    SPECIAL_RURAL("농어촌특별세"),
    CAPITAL_GAINS("양도소득세"),
    LOCAL_INCOME("지방소득세"),
    DIVIDEND_WITHHOLDING("배당 원천징수"),
    HIGH_DIVIDEND_SEPARATE("고배당 분리과세"),
}

data class TaxLineItem(
    val id: String,
    val label: String,
    val amount: MoneyAmount,
    val jurisdiction: TaxJurisdiction,
    val category: TaxCategory,
    val source: RuleSource,
    val effectiveRange: EffectiveDateRange,
) {
    init {
        require(id.isNotBlank() && label.isNotBlank()) { "A tax line needs an id and label." }
        require(amount.minorUnits >= 0L) { "A tax line cannot be negative." }
    }
}

data class TaxBreakdown(
    val policyId: String,
    val calculatedOn: LocalDate,
    val taxableBase: MoneyAmount,
    val items: List<TaxLineItem>,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(items.all { it.amount.currency == taxableBase.currency }) {
            "Tax lines and their taxable base must use the same currency."
        }
    }

    val totalTax: MoneyAmount
        get() = items.fold(MoneyAmount.zero(taxableBase.currency)) { total, item -> total + item.amount }
}

enum class TaxLiabilityStatus(val displayName: String) {
    ESTIMATED("추정"),
    WITHHELD("원천징수됨"),
    DUE("납부 예정"),
    PAID("납부 완료"),
    REFUNDABLE("환급 예정"),
}

data class TaxLiability(
    val id: String,
    val label: String,
    val taxYear: Int,
    val assessedTaxKrw: Long,
    val withholdingCreditsKrw: Long = 0L,
    val dueDate: LocalDate? = null,
    val status: TaxLiabilityStatus = TaxLiabilityStatus.ESTIMATED,
    val items: List<TaxLineItem> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    init {
        require(id.isNotBlank() && label.isNotBlank()) { "A liability needs an id and label." }
        require(taxYear >= 1900) { "The tax year is invalid." }
        require(assessedTaxKrw >= 0L && withholdingCreditsKrw >= 0L) {
            "Assessed tax and credits cannot be negative."
        }
    }

    val payableKrw: Long get() = (assessedTaxKrw - withholdingCreditsKrw).coerceAtLeast(0L)
    val refundableKrw: Long get() = (withholdingCreditsKrw - assessedTaxKrw).coerceAtLeast(0L)
}

/** Tax classification must not be inferred only from NASDAQ/NYSE listing venue. */
enum class ForeignInstrumentTaxClass(val displayName: String) {
    US_COMMON_STOCK("미국 일반법인 보통주"),
    US_ETF_RIC("미국 등록 투자회사 ETF"),
    US_CLOSED_END_FUND_RIC("미국 등록 폐쇄형 펀드"),
    US_ETN_DEBT_SECURITY("미국 상장 ETN 채무증권"),
    US_REIT_USRPI("미국 REIT·부동산지분"),
    US_PUBLICLY_TRADED_PARTNERSHIP("미국 PTP"),
    ADR("미국 예탁증서"),
    OTHER_FOREIGN_EQUITY("기타 국외주식"),
}

enum class CostBasisMethod(val displayName: String) {
    FIFO("선입선출법"),
    MOVING_AVERAGE("이동평균법"),
}

data class AnnualTaxLedger(
    val taxYear: Int,
    val policyId: String,
    val taxableDomesticGainKrw: Long,
    val foreignGainKrw: Long,
    val currentYearNetStockGainKrw: Long,
    val sharedStockBasicDeductionKrw: Long,
    val stockTaxableBaseKrw: Long,
    val expiredStockLossKrw: Long,
    val financialIncomeGrossKrw: Long,
    val highDividendIncomeKrw: Long,
    val foreignTaxPaidKrw: Long,
    val withholdingCreditsKrw: Long,
    val liabilities: List<TaxLiability>,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(taxYear >= 1900) { "The tax year is invalid." }
        require(sharedStockBasicDeductionKrw >= 0L && stockTaxableBaseKrw >= 0L) {
            "A deduction and taxable base cannot be negative."
        }
        require(expiredStockLossKrw >= 0L) { "An expired loss cannot be negative." }
        require(financialIncomeGrossKrw >= 0L && highDividendIncomeKrw >= 0L) {
            "Financial income cannot be negative."
        }
        require(foreignTaxPaidKrw >= 0L && withholdingCreditsKrw >= 0L) {
            "Tax paid and credits cannot be negative."
        }
    }

    val totalPayableKrw: Long get() = liabilities.sumOf { it.payableKrw }
    val totalRefundableKrw: Long get() = liabilities.sumOf { it.refundableKrw }
}
