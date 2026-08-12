package com.amond.kmpbook.domain.simulation.fundstructure

import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundAdvance
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundCapitalAction
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundCapitalActionKind
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundDistribution
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundFinancingAction
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundFinancingActionKind
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundLedgerEntry
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundLedgerKind
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundMarketModelParameters
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundState
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundTerms
import com.amond.kmpbook.domain.model.fundstructure.MAX_FUND_STRUCTURE_VALUE
import com.amond.kmpbook.domain.model.fundstructure.MAX_RATE
import com.amond.kmpbook.domain.model.fundstructure.amountsAreClose
import kotlin.math.exp

/** Deterministic balance-sheet engine for an exchange-listed, legally closed-end fund. */
class ClosedEndFundEngine(
    private val terms: ClosedEndFundTerms,
    private val marketModelParameters: ClosedEndFundMarketModelParameters,
) {
    init {
        require(marketModelParameters.fundId == terms.fundId)
    }

    /**
     * Maximum common-share cash distribution that leaves every contractual leverage coverage
     * floor intact. The returned amount is per current listed share and can be zero when the fund
     * is already at a floor. This is a declaration limit, not a relaxation of [advance]'s checks.
     */
    fun maximumPermittedCommonDistributionPerShare(state: ClosedEndFundState): Double {
        validateOpeningState(state)
        val maximumCashByDebtCoverage = if (state.debtLiability > 0.0) {
            state.grossAssets -
                checkNotNull(terms.minimumDebtAssetCoverageRatio) * state.debtLiability
        } else {
            state.grossAssets
        }
        val maximumCashByPreferredCoverage = if (state.preferredShareLiability > 0.0) {
            state.grossAssets - state.debtLiability -
                checkNotNull(terms.minimumPreferredAssetCoverageRatio) *
                state.preferredShareLiability
        } else {
            state.grossAssets
        }
        val commonEquity = state.grossAssets - state.debtLiability -
            state.preferredShareLiability
        val strictPositiveCommonEquityLimit = if (commonEquity > 0.0) {
            // Preserve a scale-aware positive reserve because advance requires strict positivity.
            (commonEquity - accountingTolerance(commonEquity)).coerceAtLeast(0.0)
        } else {
            0.0
        }
        val maximumCash = minOf(
            state.grossAssets,
            maximumCashByDebtCoverage,
            maximumCashByPreferredCoverage,
            strictPositiveCommonEquityLimit,
        ).coerceAtLeast(0.0)
        return checkedNonNegative(
            maximumCash / state.commonSharesOutstanding,
            "maximum permitted common distribution per share",
        )
    }

    /**
     * Applies a listed common-share split while preserving the CEF's aggregate legal balance
     * sheet. Existing CEF ledger rows remain expressed in their historical share denomination;
     * the corporate-action ledger supplies the boundary for replay.
     */
    fun applyProductUnitAdjustment(
        state: ClosedEndFundState,
        quantityMultiplier: Double,
        corporateActionAccountingSequence: Long,
        at: kotlin.time.Instant,
    ): ClosedEndFundState {
        validateOpeningState(state)
        require(quantityMultiplier.isFinite() && quantityMultiplier > 0.0)
        require(corporateActionAccountingSequence > 0L)
        require(
            state.lastCorporateActionAccountingSequence == null ||
                corporateActionAccountingSequence > state.lastCorporateActionAccountingSequence,
        )
        require(at >= state.asOf)
        val adjusted = state.copy(
            commonSharesOutstanding = checkedPositive(
                state.commonSharesOutstanding * quantityMultiplier,
                "re-denominated common shares",
            ),
            navPerCommonShare = checkedPositive(
                state.navPerCommonShare / quantityMultiplier,
                "re-denominated NAV per common share",
            ),
            cumulativeUnitAdjustmentFactor =
                state.cumulativeUnitAdjustmentFactor * quantityMultiplier,
            lastCorporateActionAccountingSequence = corporateActionAccountingSequence,
            asOf = at,
        )
        validateOpeningState(adjusted)
        return adjusted
    }

    /**
     * Ordinary secondary-market buying and selling never changes common shares. Legal/cash events
     * in one call share a single batch revision and use `sequenceInBatch` to preserve settlement
     * order. Asset returns and expense accrual alone create no ledger row or revision.
     */
    fun advance(
        state: ClosedEndFundState,
        input: ClosedEndFundAdvanceInput,
    ): ClosedEndFundAdvance {
        validateOpeningState(state)
        require(input.effectiveAt >= state.asOf) { "CEF time cannot move backwards." }
        require(input.elapsedYearFraction == 0.0 || input.effectiveAt > state.asOf) {
            "A routine CEF interval must advance time; only an event batch may share asOf."
        }

        val borrowingCost = checkedNonNegative(
            state.debtLiability * input.annualBorrowingRate * input.elapsedYearFraction,
            "borrowing cost",
        )
        val preferredDistributionCost = checkedNonNegative(
            state.preferredShareLiability *
                input.annualPreferredDistributionRate *
                input.elapsedYearFraction,
            "preferred distribution cost",
        )
        val totalFundCosts = checkedNonNegative(
            borrowingCost + preferredDistributionCost + input.operatingExpenses,
            "total fund costs",
        )

        var grossAssets = checkedPositive(
            state.grossAssets * exp(input.assetTotalLogReturn) - totalFundCosts,
            "gross assets after asset return and expenses",
        )
        var commonShares = state.commonSharesOutstanding
        var debtLiability = state.debtLiability
        var preferredLiability = state.preferredShareLiability
        var unii = checkedSigned(
            state.undistributedNetInvestmentIncome +
                input.grossInvestmentIncome -
                totalFundCosts,
            "UNII",
        )
        var distributionReserve = checkedNonNegative(
            state.distributionReserve + input.realizedGainReserveChange,
            "distribution reserve",
        )
        requirePositiveCommonEquity(grossAssets, debtLiability, preferredLiability)

        val hasDistribution = input.distribution.totalPerShare > 0.0
        val hasCapitalAction = input.capitalAction.kind != ClosedEndFundCapitalActionKind.NONE
        val hasFinancingAction = input.financingAction.kind != ClosedEndFundFinancingActionKind.NONE
        val hasLedgerBatch = hasDistribution || hasCapitalAction || hasFinancingAction
        val revision = if (hasLedgerBatch) state.revision + 1L else state.revision
        require(revision >= state.revision) { "CEF revision overflow." }
        val ledger = mutableListOf<ClosedEndFundLedgerEntry>()

        if (hasDistribution) {
            val navBefore = navPerShare(
                grossAssets,
                commonShares,
                debtLiability,
                preferredLiability,
            )
            val distributionAmounts = distributionAmounts(input.distribution, commonShares)
            val availableIncome = unii.coerceAtLeast(0.0)
            require(
                availableIncome + accountingTolerance(availableIncome) >=
                    distributionAmounts.income,
            ) {
                "An income-classified distribution (${distributionAmounts.income}) exceeds " +
                    "available UNII ($unii)."
            }
            require(
                distributionReserve + accountingTolerance(distributionReserve) >=
                    distributionAmounts.realizedGain,
            ) { "A gain-classified distribution exceeds the distribution reserve." }
            grossAssets = checkedPositive(
                grossAssets - distributionAmounts.total,
                "gross assets after common distribution",
            )
            unii = normalizedSignedSubtraction(unii, distributionAmounts.income)
            distributionReserve = normalizedNonNegativeSubtraction(
                distributionReserve,
                distributionAmounts.realizedGain,
            )
            requirePositiveCommonEquity(grossAssets, debtLiability, preferredLiability)
            requireMinimumAssetCoverage(grossAssets, debtLiability, preferredLiability)
            val navAfter = navPerShare(
                grossAssets,
                commonShares,
                debtLiability,
                preferredLiability,
            )
            ledger += ledgerEntry(
                state = state,
                input = input,
                revision = revision,
                sequence = ledger.size,
                kind = ClosedEndFundLedgerKind.DISTRIBUTION,
                capitalActionKind = ClosedEndFundCapitalActionKind.NONE,
                financingActionKind = ClosedEndFundFinancingActionKind.NONE,
                grossAssetsDelta = -distributionAmounts.total,
                commonSharesDelta = 0.0,
                debtLiabilityDelta = 0.0,
                preferredLiabilityDelta = 0.0,
                externalCashFlow = -distributionAmounts.total,
                cashToCommonShareholders = distributionAmounts.total,
                incomeDistribution = distributionAmounts.income,
                realizedGainDistribution = distributionAmounts.realizedGain,
                returnOfCapitalDistribution = distributionAmounts.returnOfCapital,
                navBefore = navBefore,
                navAfter = navAfter,
            )
        }

        if (hasCapitalAction) {
            val navBefore = navPerShare(
                grossAssets,
                commonShares,
                debtLiability,
                preferredLiability,
            )
            val capitalResult = applyCapitalAction(
                action = input.capitalAction,
                grossAssets = grossAssets,
                commonShares = commonShares,
            )
            grossAssets = capitalResult.grossAssets
            commonShares = capitalResult.commonShares
            requirePositiveCommonEquity(grossAssets, debtLiability, preferredLiability)
            if (
                input.capitalAction.kind == ClosedEndFundCapitalActionKind.TENDER_OFFER ||
                input.capitalAction.kind == ClosedEndFundCapitalActionKind.SHARE_BUYBACK
            ) {
                requireMinimumAssetCoverage(grossAssets, debtLiability, preferredLiability)
            }
            val navAfter = navPerShare(
                grossAssets,
                commonShares,
                debtLiability,
                preferredLiability,
            )
            ledger += ledgerEntry(
                state = state,
                input = input,
                revision = revision,
                sequence = ledger.size,
                kind = ClosedEndFundLedgerKind.CAPITAL_ACTION,
                capitalActionKind = input.capitalAction.kind,
                financingActionKind = ClosedEndFundFinancingActionKind.NONE,
                grossAssetsDelta = capitalResult.grossAssetsDelta,
                commonSharesDelta = capitalResult.commonSharesDelta,
                debtLiabilityDelta = 0.0,
                preferredLiabilityDelta = 0.0,
                externalCashFlow = capitalResult.grossAssetsDelta,
                cashToCommonShareholders = capitalResult.cashToCommonShareholders,
                incomeDistribution = 0.0,
                realizedGainDistribution = 0.0,
                returnOfCapitalDistribution = 0.0,
                navBefore = navBefore,
                navAfter = navAfter,
            )
        }

        if (hasFinancingAction) {
            val navBefore = navPerShare(
                grossAssets,
                commonShares,
                debtLiability,
                preferredLiability,
            )
            val financingResult = applyFinancingAction(
                action = input.financingAction,
                grossAssets = grossAssets,
                debtLiability = debtLiability,
                preferredLiability = preferredLiability,
            )
            grossAssets = financingResult.grossAssets
            debtLiability = financingResult.debtLiability
            preferredLiability = financingResult.preferredLiability
            requirePositiveCommonEquity(grossAssets, debtLiability, preferredLiability)
            when (input.financingAction.kind) {
                ClosedEndFundFinancingActionKind.DRAW_DEBT,
                ClosedEndFundFinancingActionKind.ISSUE_PREFERRED_SHARES,
                -> requireMinimumAssetCoverage(grossAssets, debtLiability, preferredLiability)
                ClosedEndFundFinancingActionKind.REPAY_DEBT,
                ClosedEndFundFinancingActionKind.REDEEM_PREFERRED_SHARES,
                -> requireAssetCoverageDidNotWorsen(
                    grossAssetsBefore = grossAssets - financingResult.grossAssetsDelta,
                    debtBefore = debtLiability - financingResult.debtLiabilityDelta,
                    preferredBefore =
                        preferredLiability - financingResult.preferredLiabilityDelta,
                    grossAssetsAfter = grossAssets,
                    debtAfter = debtLiability,
                    preferredAfter = preferredLiability,
                )
                ClosedEndFundFinancingActionKind.NONE -> error("NONE is not executable here.")
            }
            val navAfter = navPerShare(
                grossAssets,
                commonShares,
                debtLiability,
                preferredLiability,
            )
            ledger += ledgerEntry(
                state = state,
                input = input,
                revision = revision,
                sequence = ledger.size,
                kind = ClosedEndFundLedgerKind.FINANCING,
                capitalActionKind = ClosedEndFundCapitalActionKind.NONE,
                financingActionKind = input.financingAction.kind,
                grossAssetsDelta = financingResult.grossAssetsDelta,
                commonSharesDelta = 0.0,
                debtLiabilityDelta = financingResult.debtLiabilityDelta,
                preferredLiabilityDelta = financingResult.preferredLiabilityDelta,
                externalCashFlow = financingResult.grossAssetsDelta,
                cashToCommonShareholders = 0.0,
                incomeDistribution = 0.0,
                realizedGainDistribution = 0.0,
                returnOfCapitalDistribution = 0.0,
                navBefore = navBefore,
                navAfter = navAfter,
            )
        }

        val marketDiscountRate = state.marketDiscountRate +
            marketModelParameters.annualDiscountMeanReversionRate *
            (marketModelParameters.targetMarketDiscountRate - state.marketDiscountRate) *
            input.elapsedYearFraction +
            input.marketDiscountShock
        require(marketDiscountRate.isFinite() && marketDiscountRate in -0.99..MAX_RATE) {
            "The resulting CEF discount/premium is outside the supported range."
        }
        val nextState = ClosedEndFundState(
            fundId = state.fundId,
            grossAssets = grossAssets,
            commonSharesOutstanding = commonShares,
            debtLiability = debtLiability,
            preferredShareLiability = preferredLiability,
            navPerCommonShare = navPerShare(
                grossAssets,
                commonShares,
                debtLiability,
                preferredLiability,
            ),
            undistributedNetInvestmentIncome = unii,
            distributionReserve = distributionReserve,
            marketDiscountRate = marketDiscountRate,
            cumulativeUnitAdjustmentFactor = state.cumulativeUnitAdjustmentFactor,
            lastCorporateActionAccountingSequence = state.lastCorporateActionAccountingSequence,
            asOf = input.effectiveAt,
            revision = revision,
        )
        return ClosedEndFundAdvance(
            state = nextState,
            previousRevision = state.revision,
            previousCommonSharesOutstanding = state.commonSharesOutstanding,
            previousDebtLiability = state.debtLiability,
            previousPreferredShareLiability = state.preferredShareLiability,
            assetTotalLogReturn = input.assetTotalLogReturn,
            elapsedYearFraction = input.elapsedYearFraction,
            ledgerEntries = ledger,
        )
    }

    private fun validateOpeningState(state: ClosedEndFundState) {
        require(state.fundId == terms.fundId)
        require(terms.allowsDebtLeverage || state.debtLiability == 0.0)
        require(terms.allowsPreferredLeverage || state.preferredShareLiability == 0.0)
    }

    private fun requireMinimumAssetCoverage(
        grossAssets: Double,
        debtLiability: Double,
        preferredLiability: Double,
    ) {
        if (debtLiability > 0.0) {
            val debtCoverage = debtAssetCoverage(grossAssets, debtLiability)
            require(
                debtCoverage + accountingTolerance(debtCoverage) >=
                    checkNotNull(terms.minimumDebtAssetCoverageRatio),
            ) { "CEF debt asset coverage is below its required minimum." }
        }
        if (preferredLiability > 0.0) {
            val preferredCoverage = preferredAssetCoverage(
                grossAssets,
                debtLiability,
                preferredLiability,
            )
            require(
                preferredCoverage + accountingTolerance(preferredCoverage) >=
                    checkNotNull(terms.minimumPreferredAssetCoverageRatio),
            ) { "CEF preferred-share asset coverage is below its required minimum." }
        }
    }

    private fun requireAssetCoverageDidNotWorsen(
        grossAssetsBefore: Double,
        debtBefore: Double,
        preferredBefore: Double,
        grossAssetsAfter: Double,
        debtAfter: Double,
        preferredAfter: Double,
    ) {
        if (debtBefore > 0.0 && debtAfter > 0.0) {
            val before = debtAssetCoverage(grossAssetsBefore, debtBefore)
            val after = debtAssetCoverage(grossAssetsAfter, debtAfter)
            val minimum = checkNotNull(terms.minimumDebtAssetCoverageRatio)
            require(
                after + accountingTolerance(after) >= minimum ||
                    after + accountingTolerance(after) >= before,
            )
        }
        if (preferredBefore > 0.0 && preferredAfter > 0.0) {
            val before = preferredAssetCoverage(grossAssetsBefore, debtBefore, preferredBefore)
            val after = preferredAssetCoverage(grossAssetsAfter, debtAfter, preferredAfter)
            val minimum = checkNotNull(terms.minimumPreferredAssetCoverageRatio)
            require(
                after + accountingTolerance(after) >= minimum ||
                    after + accountingTolerance(after) >= before,
            )
        }
    }

    private fun debtAssetCoverage(grossAssets: Double, debtLiability: Double): Double =
        grossAssets / debtLiability

    private fun preferredAssetCoverage(
        grossAssets: Double,
        debtLiability: Double,
        preferredLiability: Double,
    ): Double = (grossAssets - debtLiability) / preferredLiability

    private fun distributionAmounts(
        distribution: ClosedEndFundDistribution,
        shares: Double,
    ): ClosedEndFundDistributionAmounts {
        val income = checkedNonNegative(
            distribution.netInvestmentIncomePerShare * shares,
            "income distribution",
        )
        val realizedGain = checkedNonNegative(
            distribution.realizedGainPerShare * shares,
            "realized-gain distribution",
        )
        val returnOfCapital = checkedNonNegative(
            distribution.returnOfCapitalPerShare * shares,
            "return-of-capital distribution",
        )
        return ClosedEndFundDistributionAmounts(
            income = income,
            realizedGain = realizedGain,
            returnOfCapital = returnOfCapital,
        )
    }

    private fun applyCapitalAction(
        action: ClosedEndFundCapitalAction,
        grossAssets: Double,
        commonShares: Double,
    ): ClosedEndFundCapitalResult {
        val shareholderCash = checkedNonNegative(
            action.commonShares * action.cashPricePerShare,
            "capital-action shareholder cash",
        )
        return when (action.kind) {
            ClosedEndFundCapitalActionKind.NONE -> error("NONE is not an executable action.")
            ClosedEndFundCapitalActionKind.TENDER_OFFER,
            ClosedEndFundCapitalActionKind.SHARE_BUYBACK,
            -> {
                if (action.kind == ClosedEndFundCapitalActionKind.TENDER_OFFER) {
                    require(terms.allowsTenderOffers)
                } else {
                    require(terms.allowsShareRepurchases)
                }
                require(action.commonShares < commonShares) {
                    "A CEF tender or buyback cannot retire every common share."
                }
                val cashOut = checkedNonNegative(
                    shareholderCash + action.transactionCosts,
                    "capital-action cash outflow",
                )
                ClosedEndFundCapitalResult(
                    grossAssets = checkedPositive(
                        grossAssets - cashOut,
                        "gross assets after tender or buyback",
                    ),
                    commonShares = checkedPositive(
                        commonShares - action.commonShares,
                        "common shares after tender or buyback",
                    ),
                    grossAssetsDelta = -cashOut,
                    commonSharesDelta = -action.commonShares,
                    cashToCommonShareholders = shareholderCash,
                )
            }
            ClosedEndFundCapitalActionKind.RIGHTS_OFFERING,
            ClosedEndFundCapitalActionKind.AT_THE_MARKET_OFFERING,
            -> {
                if (action.kind == ClosedEndFundCapitalActionKind.RIGHTS_OFFERING) {
                    require(terms.allowsRightsOfferings)
                } else {
                    require(terms.allowsAtTheMarketOfferings)
                }
                require(shareholderCash > action.transactionCosts) {
                    "A common-share offering must provide positive net proceeds."
                }
                val netProceeds = shareholderCash - action.transactionCosts
                ClosedEndFundCapitalResult(
                    grossAssets = checkedPositive(
                        grossAssets + netProceeds,
                        "gross assets after rights offering",
                    ),
                    commonShares = checkedPositive(
                        commonShares + action.commonShares,
                        "common shares after rights offering",
                    ),
                    grossAssetsDelta = netProceeds,
                    commonSharesDelta = action.commonShares,
                    cashToCommonShareholders = 0.0,
                )
            }
        }
    }

    private fun applyFinancingAction(
        action: ClosedEndFundFinancingAction,
        grossAssets: Double,
        debtLiability: Double,
        preferredLiability: Double,
    ): ClosedEndFundFinancingResult = when (action.kind) {
        ClosedEndFundFinancingActionKind.NONE -> error("NONE is not an executable action.")
        ClosedEndFundFinancingActionKind.DRAW_DEBT -> {
            require(terms.allowsDebtLeverage)
            require(action.principalAmount > action.transactionCosts)
            val netProceeds = action.principalAmount - action.transactionCosts
            ClosedEndFundFinancingResult(
                grossAssets = checkedPositive(grossAssets + netProceeds, "gross assets after debt draw"),
                debtLiability = checkedNonNegative(
                    debtLiability + action.principalAmount,
                    "debt liability after draw",
                ),
                preferredLiability = preferredLiability,
                grossAssetsDelta = netProceeds,
                debtLiabilityDelta = action.principalAmount,
                preferredLiabilityDelta = 0.0,
            )
        }
        ClosedEndFundFinancingActionKind.REPAY_DEBT -> {
            require(action.principalAmount <= debtLiability)
            val cashOut = checkedNonNegative(
                action.principalAmount + action.transactionCosts,
                "debt repayment cash",
            )
            ClosedEndFundFinancingResult(
                grossAssets = checkedPositive(grossAssets - cashOut, "gross assets after debt repayment"),
                debtLiability = normalizedNonNegativeSubtraction(
                    debtLiability,
                    action.principalAmount,
                ),
                preferredLiability = preferredLiability,
                grossAssetsDelta = -cashOut,
                debtLiabilityDelta = -action.principalAmount,
                preferredLiabilityDelta = 0.0,
            )
        }
        ClosedEndFundFinancingActionKind.ISSUE_PREFERRED_SHARES -> {
            require(terms.allowsPreferredLeverage)
            require(action.principalAmount > action.transactionCosts)
            val netProceeds = action.principalAmount - action.transactionCosts
            ClosedEndFundFinancingResult(
                grossAssets = checkedPositive(
                    grossAssets + netProceeds,
                    "gross assets after preferred issuance",
                ),
                debtLiability = debtLiability,
                preferredLiability = checkedNonNegative(
                    preferredLiability + action.principalAmount,
                    "preferred liability after issuance",
                ),
                grossAssetsDelta = netProceeds,
                debtLiabilityDelta = 0.0,
                preferredLiabilityDelta = action.principalAmount,
            )
        }
        ClosedEndFundFinancingActionKind.REDEEM_PREFERRED_SHARES -> {
            require(action.principalAmount <= preferredLiability)
            val cashOut = checkedNonNegative(
                action.principalAmount + action.transactionCosts,
                "preferred redemption cash",
            )
            ClosedEndFundFinancingResult(
                grossAssets = checkedPositive(
                    grossAssets - cashOut,
                    "gross assets after preferred redemption",
                ),
                debtLiability = debtLiability,
                preferredLiability = normalizedNonNegativeSubtraction(
                    preferredLiability,
                    action.principalAmount,
                ),
                grossAssetsDelta = -cashOut,
                debtLiabilityDelta = 0.0,
                preferredLiabilityDelta = -action.principalAmount,
            )
        }
    }

    private fun ledgerEntry(
        state: ClosedEndFundState,
        input: ClosedEndFundAdvanceInput,
        revision: Long,
        sequence: Int,
        kind: ClosedEndFundLedgerKind,
        capitalActionKind: ClosedEndFundCapitalActionKind,
        financingActionKind: ClosedEndFundFinancingActionKind,
        grossAssetsDelta: Double,
        commonSharesDelta: Double,
        debtLiabilityDelta: Double,
        preferredLiabilityDelta: Double,
        externalCashFlow: Double,
        cashToCommonShareholders: Double,
        incomeDistribution: Double,
        realizedGainDistribution: Double,
        returnOfCapitalDistribution: Double,
        navBefore: Double,
        navAfter: Double,
    ): ClosedEndFundLedgerEntry = ClosedEndFundLedgerEntry(
        id = "${terms.fundId}:$revision:$sequence",
        fundId = state.fundId,
        kind = kind,
        effectiveAt = input.effectiveAt,
        revision = revision,
        sequenceInBatch = sequence,
        settlementCurrency = terms.settlementCurrency,
        capitalActionKind = capitalActionKind,
        financingActionKind = financingActionKind,
        grossAssetsDelta = grossAssetsDelta,
        commonSharesDelta = commonSharesDelta,
        debtLiabilityDelta = debtLiabilityDelta,
        preferredShareLiabilityDelta = preferredLiabilityDelta,
        externalCashFlow = externalCashFlow,
        cashToCommonShareholders = cashToCommonShareholders,
        netInvestmentIncomeDistribution = incomeDistribution,
        realizedGainDistribution = realizedGainDistribution,
        returnOfCapitalDistribution = returnOfCapitalDistribution,
        navPerShareBefore = navBefore,
        navPerShareAfter = navAfter,
    )

    private fun navPerShare(
        grossAssets: Double,
        commonShares: Double,
        debtLiability: Double,
        preferredLiability: Double,
    ): Double = checkedPositive(
        (grossAssets - debtLiability - preferredLiability) / commonShares,
        "NAV per common share",
    )

    private fun requirePositiveCommonEquity(
        grossAssets: Double,
        debtLiability: Double,
        preferredLiability: Double,
    ) {
        require(grossAssets > debtLiability + preferredLiability) {
            "CEF common equity must remain positive."
        }
    }

    private fun normalizedNonNegativeSubtraction(left: Double, right: Double): Double {
        val result = left - right
        return if (result < 0.0 && amountsAreClose(result, 0.0)) 0.0 else checkedNonNegative(
            result,
            "non-negative accounting balance",
        )
    }

    private fun normalizedSignedSubtraction(left: Double, right: Double): Double {
        val result = left - right
        return if (amountsAreClose(result, 0.0)) 0.0 else checkedSigned(
            result,
            "signed accounting balance",
        )
    }

    private fun accountingTolerance(value: Double): Double =
        maxOf(1.0, kotlin.math.abs(value)) * 1.0e-9

    private fun checkedPositive(value: Double, label: String): Double {
        require(value.isFinite() && value > 0.0 && value <= MAX_FUND_STRUCTURE_VALUE) {
            "$label is outside the supported positive range."
        }
        return value
    }

    private fun checkedNonNegative(value: Double, label: String): Double {
        require(value.isFinite() && value >= 0.0 && value <= MAX_FUND_STRUCTURE_VALUE) {
            "$label is outside the supported non-negative range."
        }
        return value
    }

    private fun checkedSigned(value: Double, label: String): Double {
        require(value.isFinite() && value in -MAX_FUND_STRUCTURE_VALUE..MAX_FUND_STRUCTURE_VALUE) {
            "$label is outside the supported range."
        }
        return value
    }

}
