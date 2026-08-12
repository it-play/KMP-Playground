package com.amond.kmpbook.domain.model.fundstructure

import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlin.math.abs
import kotlin.time.Instant

/** Immutable double-entry reconciliation evidence for one CEF accounting stage. */
data class ClosedEndFundLedgerEntry(
    val id: String,
    val fundId: String,
    val kind: ClosedEndFundLedgerKind,
    val effectiveAt: Instant,
    val revision: Long,
    val sequenceInBatch: Int,
    val settlementCurrency: ReferenceCurrency,
    val capitalActionKind: ClosedEndFundCapitalActionKind,
    val financingActionKind: ClosedEndFundFinancingActionKind,
    val grossAssetsDelta: Double,
    val commonSharesDelta: Double,
    val debtLiabilityDelta: Double,
    val preferredShareLiabilityDelta: Double,
    val externalCashFlow: Double,
    val cashToCommonShareholders: Double,
    val netInvestmentIncomeDistribution: Double,
    val realizedGainDistribution: Double,
    val returnOfCapitalDistribution: Double,
    val navPerShareBefore: Double,
    val navPerShareAfter: Double,
) {
    init {
        requireFundStructureId(id, "id")
        requireFundStructureId(fundId, "fundId")
        require(revision > 0L)
        require(sequenceInBatch in 0..MAX_BATCH_ENTRIES)
        require(grossAssetsDelta.isFinite() && abs(grossAssetsDelta) <= MAX_FUND_STRUCTURE_VALUE)
        require(commonSharesDelta.isFinite() && abs(commonSharesDelta) <= MAX_FUND_STRUCTURE_VALUE)
        require(debtLiabilityDelta.isFinite() && abs(debtLiabilityDelta) <= MAX_FUND_STRUCTURE_VALUE)
        require(
            preferredShareLiabilityDelta.isFinite() &&
                abs(preferredShareLiabilityDelta) <= MAX_FUND_STRUCTURE_VALUE,
        )
        require(externalCashFlow.isFinite() && abs(externalCashFlow) <= MAX_FUND_STRUCTURE_VALUE)
        requireNonNegativeAmount(cashToCommonShareholders, "cashToCommonShareholders")
        requireNonNegativeAmount(
            netInvestmentIncomeDistribution,
            "netInvestmentIncomeDistribution",
        )
        requireNonNegativeAmount(realizedGainDistribution, "realizedGainDistribution")
        requireNonNegativeAmount(returnOfCapitalDistribution, "returnOfCapitalDistribution")
        requirePositiveAmount(navPerShareBefore, "navPerShareBefore")
        requirePositiveAmount(navPerShareAfter, "navPerShareAfter")
        val classifiedDistribution =
            netInvestmentIncomeDistribution + realizedGainDistribution + returnOfCapitalDistribution
        when (kind) {
            ClosedEndFundLedgerKind.DISTRIBUTION -> {
                require(capitalActionKind == ClosedEndFundCapitalActionKind.NONE)
                require(financingActionKind == ClosedEndFundFinancingActionKind.NONE)
                require(commonSharesDelta == 0.0)
                require(debtLiabilityDelta == 0.0 && preferredShareLiabilityDelta == 0.0)
                require(externalCashFlow == -cashToCommonShareholders)
                require(amountsAreClose(cashToCommonShareholders, classifiedDistribution))
                require(amountsAreClose(grossAssetsDelta, externalCashFlow))
                require(cashToCommonShareholders > 0.0)
            }
            ClosedEndFundLedgerKind.CAPITAL_ACTION -> {
                require(capitalActionKind != ClosedEndFundCapitalActionKind.NONE)
                require(financingActionKind == ClosedEndFundFinancingActionKind.NONE)
                require(commonSharesDelta != 0.0)
                require(debtLiabilityDelta == 0.0 && preferredShareLiabilityDelta == 0.0)
                require(classifiedDistribution == 0.0)
                require(amountsAreClose(grossAssetsDelta, externalCashFlow))
                when (capitalActionKind) {
                    ClosedEndFundCapitalActionKind.NONE -> error("Validated above.")
                    ClosedEndFundCapitalActionKind.RIGHTS_OFFERING,
                    ClosedEndFundCapitalActionKind.AT_THE_MARKET_OFFERING,
                    -> {
                        require(commonSharesDelta > 0.0 && externalCashFlow > 0.0)
                        require(cashToCommonShareholders == 0.0)
                    }
                    ClosedEndFundCapitalActionKind.TENDER_OFFER,
                    ClosedEndFundCapitalActionKind.SHARE_BUYBACK,
                    -> {
                        require(commonSharesDelta < 0.0 && externalCashFlow < 0.0)
                        require(cashToCommonShareholders > 0.0)
                    }
                }
            }
            ClosedEndFundLedgerKind.FINANCING -> {
                require(capitalActionKind == ClosedEndFundCapitalActionKind.NONE)
                require(financingActionKind != ClosedEndFundFinancingActionKind.NONE)
                require(commonSharesDelta == 0.0)
                require(debtLiabilityDelta != 0.0 || preferredShareLiabilityDelta != 0.0)
                require(classifiedDistribution == 0.0)
                require(amountsAreClose(grossAssetsDelta, externalCashFlow))
                require(cashToCommonShareholders == 0.0)
                when (financingActionKind) {
                    ClosedEndFundFinancingActionKind.NONE -> error("Validated above.")
                    ClosedEndFundFinancingActionKind.DRAW_DEBT -> {
                        require(debtLiabilityDelta > 0.0 && preferredShareLiabilityDelta == 0.0)
                        require(externalCashFlow > 0.0)
                    }
                    ClosedEndFundFinancingActionKind.REPAY_DEBT -> {
                        require(debtLiabilityDelta < 0.0 && preferredShareLiabilityDelta == 0.0)
                        require(externalCashFlow < 0.0)
                    }
                    ClosedEndFundFinancingActionKind.ISSUE_PREFERRED_SHARES -> {
                        require(debtLiabilityDelta == 0.0 && preferredShareLiabilityDelta > 0.0)
                        require(externalCashFlow > 0.0)
                    }
                    ClosedEndFundFinancingActionKind.REDEEM_PREFERRED_SHARES -> {
                        require(debtLiabilityDelta == 0.0 && preferredShareLiabilityDelta < 0.0)
                        require(externalCashFlow < 0.0)
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_BATCH_ENTRIES: Int = 100
    }
}
