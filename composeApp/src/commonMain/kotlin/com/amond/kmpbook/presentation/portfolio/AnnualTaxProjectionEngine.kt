package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.tax.core.CheckedMonetaryArithmetic
import com.amond.kmpbook.domain.tax.dividend.DistributionReturnOfCapitalPolicy
import com.amond.kmpbook.domain.tax.foreign.ForeignInstrumentTaxClass
import com.amond.kmpbook.domain.tax.liability.AnnualStockTaxCalculator
import com.amond.kmpbook.domain.tax.liability.AnnualStockTaxRequest
import com.amond.kmpbook.domain.tax.liability.AnnualTaxLedger
import com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus
import com.amond.kmpbook.domain.tax.liability.StockGainTaxTreatment
import com.amond.kmpbook.domain.tax.lot.RealizedStockGain
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.domain.time.KofrBusinessCalendar
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Runtime tax refresh and strict save validation share this canonical annual projection. */
object AnnualTaxProjectionEngine {
    fun calculate(
        year: Int,
        stocksById: Map<String, StockDefinition>,
        dividendEntries: List<DividendLedgerEntry>,
        gainEntries: List<RealizedGainRecord>,
    ): Pair<AnnualTaxLedger, List<TaxPaymentNotice>>? {
        if (year !in 2026..2040) return null
        val tradeGains = gainEntries.filter { it.settlementDate.year == year }.map { record ->
            RealizedStockGain(
                id = record.tradeId,
                stockId = record.stockId,
                realizedOn = record.settlementDate,
                gainKrw = record.taxGainKrw,
                treatment = record.taxTreatment,
                instrumentTaxClass = if (record.market.isUnitedStates) {
                    stocksById[record.stockId]?.let(::foreignInstrumentTaxClass)
                        ?: ForeignInstrumentTaxClass.OTHER_FOREIGN_EQUITY
                } else {
                    null
                },
            )
        }
        val yearDividends = dividendEntries.filter { GameCalendar.campaignDate(it.paidAt).year == year }
        val rocGains = yearDividends.mapNotNull { entry ->
            val gain = entry.excessReturnOfCapitalGainKrw
            if (gain <= 0L) return@mapNotNull null
            val stock = stocksById[entry.stockId] ?: return@mapNotNull null
            require(DistributionReturnOfCapitalPolicy.isEligible(stock)) {
                "ROC 초과이익은 미국 상장 ETF·폐쇄형 펀드 분배에서만 발생할 수 있습니다."
            }
            RealizedStockGain(
                id = "${entry.id}:excess-roc",
                stockId = entry.stockId,
                realizedOn = GameCalendar.campaignDate(entry.paidAt),
                gainKrw = gain,
                treatment = StockGainTaxTreatment.FOREIGN_STANDARD,
                instrumentTaxClass = foreignInstrumentTaxClass(stock),
            )
        }
        val gains = tradeGains + rocGains
        val dividendFinancialIncomeKrw = CheckedMonetaryArithmetic.sum(
            yearDividends.asSequence().map { entry ->
                CheckedMonetaryArithmetic.roundedToLong(
                    entry.financialIncomeAmountKrw,
                    "Dividend financial income",
                )
            },
            "Annual dividend financial income",
        )
        val domesticEtfFinancialIncomeKrw = CheckedMonetaryArithmetic.sum(
            gainEntries.asSequence()
                .filter {
                    it.settlementDate.year == year &&
                        it.taxTreatment == StockGainTaxTreatment.DOMESTIC_ETF_HOLDING_PERIOD_WITHHELD
                }
                .map(RealizedGainRecord::taxableFinancialIncomeKrw),
            "Annual domestic ETF financial income",
        )
        val financialIncomeGrossKrw = CheckedMonetaryArithmetic.add(
            dividendFinancialIncomeKrw,
            domesticEtfFinancialIncomeKrw,
            "Annual gross financial income",
        )
        val foreignTaxPaidKrw = CheckedMonetaryArithmetic.sum(
            yearDividends.asSequence()
                .filter { it.currency == Currency.USD }
                .map { entry ->
                    CheckedMonetaryArithmetic.roundedToLong(
                        entry.withholdingTaxKrw,
                        "Foreign dividend withholding tax",
                    )
                },
            "Annual foreign tax paid",
        )
        val withholdingCreditsKrw = CheckedMonetaryArithmetic.sum(
            gainEntries.asSequence()
                .filter {
                    it.settlementDate.year == year &&
                        it.taxTreatment == StockGainTaxTreatment.DOMESTIC_ETF_HOLDING_PERIOD_WITHHELD
                }
                .map { gain ->
                    CheckedMonetaryArithmetic.roundedToLong(
                        gain.saleTax * gain.exchangeRateToKrw,
                        "Domestic ETF withholding credit",
                    )
                },
            "Annual withholding credits",
        )
        val ledger = AnnualStockTaxCalculator().calculate(
            AnnualStockTaxRequest(
                taxYear = year,
                gains = gains,
                financialIncomeGrossKrw = financialIncomeGrossKrw,
                foreignTaxPaidKrw = foreignTaxPaidKrw,
                withholdingCreditsKrw = withholdingCreditsKrw,
            ),
        )
        val canonicalLedger = ledger.copy(
            liabilities = ledger.liabilities.map { liability ->
                val statutoryDueDate = liability.dueDate ?: LocalDate(year + 1, 5, 31)
                val canonicalDueDate = firstKoreanFinancialBusinessDateOnOrAfter(statutoryDueDate)
                liability.copy(
                    dueDate = canonicalDueDate,
                    warnings = liability.warnings
                        .filterNot { warning -> warning == UNADJUSTED_DUE_DATE_WARNING }
                        .let { warnings ->
                            if (canonicalDueDate == statutoryDueDate) warnings
                            else warnings +
                                "$statutoryDueDate 휴일 신고기한을 한국 금융영업일 $canonicalDueDate(으)로 이월했습니다."
                        },
                )
            },
        )
        // A positive taxable base can round to a zero-won liability. Such a liability remains in
        // the annual ledger, but it is not a payable event and must never acquire a PAID fact.
        val notices = canonicalLedger.liabilities.filter { liability ->
            liability.payableKrw > 0L
        }.map { liability ->
            val dueDate = checkNotNull(liability.dueDate)
            TaxPaymentNotice(
                id = liability.id,
                taxYear = year,
                dueDate = dueDate,
                currency = Currency.KRW,
                amountKrw = liability.payableKrw,
                status = liability.status,
                paidAt = null,
                accountingSequence = null,
                message = "${year}년 ${liability.label} ${liability.payableKrw}원은 " +
                    "${dueDate}까지 납부 예정입니다.",
            )
        }
        return canonicalLedger to notices
    }

    /**
     * A saved PAID status is accepted only as a payment fact attached to the unchanged canonical
     * liability. DUE/REFUNDABLE projections themselves never inherit persisted status.
     */
    fun mergeCanonicalProjectionWithPaidFacts(
        projection: Pair<AnnualTaxLedger, List<TaxPaymentNotice>>,
        paidFacts: List<TaxPaymentNotice>,
        currentTime: Instant,
    ): Pair<AnnualTaxLedger, List<TaxPaymentNotice>> {
        val (canonicalLedger, canonicalNotices) = projection
        require(paidFacts.all { it.status == TaxLiabilityStatus.PAID })
        require(paidFacts.map(TaxPaymentNotice::id).distinct().size == paidFacts.size)
        val canonicalById = canonicalNotices.associateBy(TaxPaymentNotice::id)
        require(paidFacts.all { fact ->
            canonicalById[fact.id]?.let { canonical ->
                isValidPaidFact(fact, canonical, currentTime)
            } == true
        }) { "납부 사실이 canonical 세액·기한 또는 게임 시각과 다릅니다." }

        val paidById = paidFacts.associateBy(TaxPaymentNotice::id)
        val mergedNotices = canonicalNotices.map { canonical ->
            paidById[canonical.id]?.let { paid ->
                canonical.copy(
                    status = TaxLiabilityStatus.PAID,
                    paidAt = paid.paidAt,
                    accountingSequence = paid.accountingSequence,
                    message = paidMessage(canonical, checkNotNull(paid.paidAt)),
                )
            } ?: canonical
        }
        val paidIds = paidById.keys
        val mergedLedger = canonicalLedger.copy(
            liabilities = canonicalLedger.liabilities.map { liability ->
                if (liability.id in paidIds) liability.copy(status = TaxLiabilityStatus.PAID)
                else liability
            },
        )
        return mergedLedger to mergedNotices
    }

    fun paidMessage(canonical: TaxPaymentNotice, paidAt: Instant): String =
        "${canonical.taxYear}년 귀속 세금 ${canonical.amountKrw}원을 " +
            "${GameCalendar.campaignDate(paidAt)}에 납부했습니다."

    private fun isValidPaidFact(
        fact: TaxPaymentNotice,
        canonical: TaxPaymentNotice,
        currentTime: Instant,
    ): Boolean {
        val paidAt = fact.paidAt ?: return false
        val accountingSequence = fact.accountingSequence ?: return false
        val paidOn = GameCalendar.campaignDate(paidAt)
        return canonical.status == TaxLiabilityStatus.DUE && canonical.amountKrw > 0L &&
            fact.id == canonical.id && fact.taxYear == canonical.taxYear &&
            fact.dueDate == canonical.dueDate && fact.currency == canonical.currency &&
            fact.amountKrw == canonical.amountKrw && accountingSequence > 0L &&
            paidAt in GameCalendar.startInstant..currentTime &&
            paidAt == GameCalendar.startInstant + GameCalendar.turnAt(paidAt).hours &&
            paidOn >= canonical.dueDate
    }

    private fun foreignInstrumentTaxClass(stock: StockDefinition): ForeignInstrumentTaxClass =
        when (stock.instrumentType) {
            InstrumentType.STOCK -> ForeignInstrumentTaxClass.US_COMMON_STOCK
            InstrumentType.ETF -> ForeignInstrumentTaxClass.US_ETF_RIC
            InstrumentType.CLOSED_END_FUND -> ForeignInstrumentTaxClass.US_CLOSED_END_FUND_RIC
            InstrumentType.ETN -> ForeignInstrumentTaxClass.US_ETN_DEBT_SECURITY
            InstrumentType.REIT -> ForeignInstrumentTaxClass.US_REIT_USRPI
            InstrumentType.ADR -> ForeignInstrumentTaxClass.ADR
        }

    private fun firstKoreanFinancialBusinessDateOnOrAfter(date: LocalDate): LocalDate {
        var candidate = date
        while (!KofrBusinessCalendar.isBusinessDate(candidate)) {
            candidate = candidate.plus(1, DateTimeUnit.DAY)
        }
        return candidate
    }

    private const val UNADJUSTED_DUE_DATE_WARNING: String =
        "법정 신고일이 휴일이면 다음 영업일 조정이 필요합니다."
}
