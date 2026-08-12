package com.amond.kmpbook.domain.simulation.fundstructure

import com.amond.kmpbook.domain.model.fundstructure.EtnAdvance
import com.amond.kmpbook.domain.model.fundstructure.EtnCouponKind
import com.amond.kmpbook.domain.model.fundstructure.EtnCreditEvent
import com.amond.kmpbook.domain.model.fundstructure.EtnIndicativeValueObservation
import com.amond.kmpbook.domain.model.fundstructure.EtnLedgerEntry
import com.amond.kmpbook.domain.model.fundstructure.EtnLedgerKind
import com.amond.kmpbook.domain.model.fundstructure.EtnLifecycle
import com.amond.kmpbook.domain.model.fundstructure.EtnProductTerms
import com.amond.kmpbook.domain.model.fundstructure.EtnSettlementValuationMethod
import com.amond.kmpbook.domain.model.fundstructure.EtnSettlementValuationRule
import com.amond.kmpbook.domain.model.fundstructure.EtnState
import com.amond.kmpbook.domain.model.fundstructure.MAX_EXACT_INTEGER_QUANTITY
import com.amond.kmpbook.domain.model.fundstructure.MAX_FUND_STRUCTURE_VALUE
import com.amond.kmpbook.domain.model.fundstructure.amountsAreClose
import kotlin.math.exp
import kotlin.math.pow

/** Deterministic contractual-value and issuer-credit engine for unsecured ETNs. */
class EtnEngine(
    private val terms: EtnProductTerms,
) {
    /**
     * Advances reference and credit state first, then coupon payment, note flow, and contract
     * settlement. Multiple legal/cash entries share one batch revision and are ordered by
     * `sequenceInBatch`; an interval without those events does not create a ledger revision.
     */
    fun advance(
        state: EtnState,
        input: EtnAdvanceInput,
    ): EtnAdvance {
        require(state.productId == terms.productId)
        require(state.lifecycle == EtnLifecycle.ACTIVE) { "A settled ETN cannot advance." }
        require(input.effectiveAt >= state.asOf) { "ETN time cannot move backwards." }
        require(input.elapsedYearFraction == 0.0 || input.effectiveAt > state.asOf) {
            "A routine ETN interval must advance time; only an event batch may share asOf."
        }
        require(input.effectiveDate >= terms.issueDate)
        if (input.contractualSettlementDeadlineReached) {
            require(input.effectiveDate >= terms.maturityDate) {
                "Contractual settlement cannot precede the ETN maturity date."
            }
        }

        val referenceLevelAfter = checkedPositiveValue(
            state.referenceLevel * exp(input.referenceLogReturn),
            "referenceLevel",
        )
        val contractualCouponAccrual = contractualCouponAccrual(input)
        val grossIndicativeValue = checkedNonNegativeValue(
            state.feeAdjustedIndicativeValuePerNote * exp(input.referenceLogReturn),
            "gross indicative value",
        )
        val feeFactor = (1.0 - terms.annualInvestorFeeRate / terms.investorFeeDayCountBasis)
            .pow(input.elapsedYearFraction * terms.investorFeeDayCountBasis)
        var indicativeValueAfter = checkedNonNegativeValue(
            grossIndicativeValue * feeFactor,
            "feeAdjustedIndicativeValuePerNote",
        )
        if (terms.couponRule.accrualReducesIndicativeValue) {
            indicativeValueAfter = checkedNonNegativeValue(
                maxOf(0.0, indicativeValueAfter - contractualCouponAccrual),
                "feeAdjustedIndicativeValuePerNote after coupon accrual",
            )
        }

        val couponLiabilityAccrual = contractualCouponAccrual.takeIf {
            state.notesOutstanding > 0L
        } ?: 0.0
        var accruedCoupon = checkedNonNegativeValue(
            state.accruedCouponPerNote + couponLiabilityAccrual,
            "accruedCouponPerNote",
        )
        require(input.couponPaymentPerNote <= accruedCoupon) {
            "A coupon payment cannot exceed the accrued coupon per note."
        }
        if (input.couponPaymentPerNote > 0.0) {
            require(state.notesOutstanding > 0L)
            accruedCoupon = checkedNonNegativeValue(
                accruedCoupon - input.couponPaymentPerNote,
                "accruedCouponPerNote after payment",
            )
        }
        if (input.notesIssued > 0L) {
            require(accruedCoupon == 0.0) {
                "New notes cannot inherit a coupon accrued before their issuance."
            }
        }

        var issuerCreditSpread = state.issuerCreditSpread + input.issuerCreditSpreadShock
        var issuerHazardRate = state.issuerHazardRate + input.issuerHazardRateShock
        require(issuerCreditSpread.isFinite() && issuerCreditSpread >= 0.0)
        require(issuerHazardRate.isFinite() && issuerHazardRate in 0.0..1.0)
        var issuerRecoveryRate = input.issuerRecoveryRateUpdate ?: state.issuerRecoveryRate

        require(state.notesOutstanding + input.notesIssued <= MAX_EXACT_INTEGER_QUANTITY)
        val afterIssuance = state.notesOutstanding + input.notesIssued
        require(input.notesCancelled <= afterIssuance)
        var notesOutstanding = afterIssuance - input.notesCancelled

        var observationWindow = state.indicativeValueObservationWindow
        if (input.recordIndicativeValueObservation) {
            observationWindow = recordIndicativeValueObservation(
                window = observationWindow,
                observationDate = input.effectiveDate,
                indicativeValuePerNote = indicativeValueAfter,
            )
        }
        val settlement = settlementIndicativeValue(
            input = input,
            availableNotes = notesOutstanding,
            currentIndicativeValue = indicativeValueAfter,
            observationWindow = observationWindow,
        )
        val settlementObservations = settlement.first
        val settlementIndicativeValue = settlement.second
        if (input.contractEvent != EtnCreditEvent.NONE &&
            settlementObservations.lastOrNull()?.observationDate == input.effectiveDate &&
            observationWindow.lastOrNull()?.observationDate != input.effectiveDate
        ) {
            observationWindow = recordIndicativeValueObservation(
                window = observationWindow,
                observationDate = input.effectiveDate,
                indicativeValuePerNote = indicativeValueAfter,
            )
        }

        validateContractEvent(
            input = input,
            availableNotes = notesOutstanding,
            stateRecoveryRate = issuerRecoveryRate,
            currentIndicativeValue = indicativeValueAfter,
            settlementObservations = settlementObservations,
        )
        if (input.contractEvent == EtnCreditEvent.CREDIT_DEFAULT) {
            issuerRecoveryRate = checkNotNull(input.creditEventRecoveryRate)
            issuerHazardRate = 1.0
        }

        val settlementPerNote = settlementPerNote(
            event = input.contractEvent,
            indicativeValuePerNote = settlementIndicativeValue,
            accruedCouponPerNote = accruedCoupon,
            recoveryRate = issuerRecoveryRate,
        )
        if (input.contractEvent != EtnCreditEvent.NONE) {
            notesOutstanding -= input.contractSettlementNotes
        }
        if (notesOutstanding == 0L) accruedCoupon = 0.0
        val settlesProduct =
            input.contractEvent != EtnCreditEvent.NONE &&
                input.contractEvent != EtnCreditEvent.HOLDER_REDEMPTION &&
                notesOutstanding == 0L
        val lifecycle = if (settlesProduct) {
            accruedCoupon = 0.0
            EtnLifecycle.SETTLED
        } else {
            EtnLifecycle.ACTIVE
        }

        val hasCouponPayment = input.couponPaymentPerNote > 0.0
        val hasNoteFlow = input.notesIssued > 0L || input.notesCancelled > 0L
        val hasSettlement = input.contractEvent != EtnCreditEvent.NONE
        val hasLedgerBatch = hasCouponPayment || hasNoteFlow || hasSettlement
        val revision = if (hasLedgerBatch) state.revision + 1L else state.revision
        require(revision >= state.revision) { "ETN revision overflow." }

        val nextState = EtnState(
            productId = state.productId,
            referenceLevel = referenceLevelAfter,
            feeAdjustedIndicativeValuePerNote = indicativeValueAfter,
            notesOutstanding = notesOutstanding,
            accruedCouponPerNote = accruedCoupon,
            issuerCreditSpread = issuerCreditSpread,
            issuerHazardRate = issuerHazardRate,
            issuerRecoveryRate = issuerRecoveryRate,
            indicativeValueObservationWindow = observationWindow,
            lifecycle = lifecycle,
            terminalCreditEvent = input.contractEvent.takeIf { settlesProduct },
            asOf = input.effectiveAt,
            revision = revision,
        )

        val ledger = buildLedger(
            oldState = state,
            nextState = nextState,
            input = input,
            settlementPerNote = settlementPerNote,
            settlementObservations = settlementObservations,
        )
        return EtnAdvance(
            state = nextState,
            previousRevision = state.revision,
            previousNotesOutstanding = state.notesOutstanding,
            referenceLogReturn = input.referenceLogReturn,
            elapsedYearFraction = input.elapsedYearFraction,
            ledgerEntries = ledger,
        )
    }

    private fun contractualCouponAccrual(input: EtnAdvanceInput): Double =
        when (terms.couponRule.kind) {
            EtnCouponKind.NONE -> {
                require(input.referenceCouponAccrualPerNote == 0.0)
                0.0
            }
            EtnCouponKind.FIXED_RATE -> {
                require(input.referenceCouponAccrualPerNote == 0.0)
                checkedNonNegativeValue(
                    terms.statedPrincipalPerNote *
                        terms.couponRule.annualFixedRate *
                        input.elapsedYearFraction,
                    "fixed coupon accrual",
                )
            }
            EtnCouponKind.REFERENCE_CASH_FLOW,
            EtnCouponKind.OPTION_PREMIUM_LINKED,
            -> checkedNonNegativeValue(
                input.referenceCouponAccrualPerNote * terms.couponRule.participationRate,
                "reference coupon accrual",
            )
        }

    private fun validateContractEvent(
        input: EtnAdvanceInput,
        availableNotes: Long,
        stateRecoveryRate: Double,
        currentIndicativeValue: Double,
        settlementObservations: List<EtnIndicativeValueObservation>,
    ) {
        if (input.contractEvent == EtnCreditEvent.NONE) return
        require(input.contractSettlementNotes <= availableNotes)
        when (input.contractEvent) {
            EtnCreditEvent.NONE -> Unit
            EtnCreditEvent.HOLDER_REDEMPTION -> {
                require(terms.callTerms.holderRedeemable)
                require(
                    input.contractSettlementNotes >=
                        checkNotNull(terms.callTerms.minimumHolderRedemptionNotes),
                )
                require(
                    input.contractSettlementNotes %
                        checkNotNull(terms.callTerms.holderRedemptionNoteIncrement) == 0L,
                )
            }
            EtnCreditEvent.ISSUER_CALL -> {
                require(terms.callTerms.issuerCallable)
                if (!terms.callTerms.issuerCallMayBePartial) {
                    require(input.contractSettlementNotes == availableNotes)
                }
            }
            EtnCreditEvent.CONTRACTUAL_MATURITY -> {
                require(input.contractualSettlementDeadlineReached)
                require(input.effectiveDate >= terms.maturityDate)
            }
            EtnCreditEvent.ISSUER_ACCELERATION -> {
                require(terms.accelerationTerms.issuerMayAccelerate)
                if (input.contractSettlementNotes < availableNotes) {
                    require(terms.accelerationTerms.partialAccelerationAllowed)
                    require(
                        input.contractSettlementNotes >=
                            checkNotNull(terms.accelerationTerms.minimumPartialAccelerationNotes),
                    )
                    require(
                        input.contractSettlementNotes %
                            checkNotNull(terms.accelerationTerms.partialAccelerationNoteIncrement) == 0L,
                    )
                }
            }
            EtnCreditEvent.CREDIT_DEFAULT -> {
                require(terms.accelerationTerms.creditDefaultCausesAcceleration)
                val recovery = checkNotNull(input.creditEventRecoveryRate)
                require(
                    input.issuerRecoveryRateUpdate == null ||
                        amountsAreClose(input.issuerRecoveryRateUpdate, recovery),
                )
                require(stateRecoveryRate in 0.0..1.0)
            }
        }
        if (input.contractEvent.requiresFullSettlement) {
            require(input.contractSettlementNotes == availableNotes) {
                "A terminal ETN event must settle every outstanding note."
            }
        }
        require(
            amountsAreClose(
                settlementObservations.last().indicativeValuePerNote,
                currentIndicativeValue,
            ),
        ) { "The final settlement observation must equal the current indicative value." }
    }

    private fun settlementIndicativeValue(
        input: EtnAdvanceInput,
        availableNotes: Long,
        currentIndicativeValue: Double,
        observationWindow: List<EtnIndicativeValueObservation>,
    ): Pair<List<EtnIndicativeValueObservation>, Double> {
        if (input.contractEvent == EtnCreditEvent.NONE) return emptyList<EtnIndicativeValueObservation>() to 0.0
        val rule = when (input.contractEvent) {
            EtnCreditEvent.NONE -> error("Handled above.")
            EtnCreditEvent.HOLDER_REDEMPTION ->
                checkNotNull(terms.callTerms.holderRedemptionValuationRule)
            EtnCreditEvent.ISSUER_CALL -> checkNotNull(terms.callTerms.issuerCallValuationRule)
            EtnCreditEvent.CONTRACTUAL_MATURITY -> terms.maturityValuationRule
            EtnCreditEvent.ISSUER_ACCELERATION -> {
                if (input.contractSettlementNotes == availableNotes) {
                    checkNotNull(terms.accelerationTerms.fullAccelerationValuationRule)
                } else {
                    checkNotNull(terms.accelerationTerms.partialAccelerationValuationRule)
                }
            }
            EtnCreditEvent.CREDIT_DEFAULT -> CREDIT_DEFAULT_VALUATION_RULE
        }
        val hasCurrentObservation = observationWindow.lastOrNull()?.let { observation ->
            observation.observationDate == input.effectiveDate &&
                amountsAreClose(observation.indicativeValuePerNote, currentIndicativeValue)
        } == true
        val canonicalWindow = if (hasCurrentObservation) {
            observationWindow
        } else {
            require(
                input.recordIndicativeValueObservation ||
                    input.contractEvent == EtnCreditEvent.CREDIT_DEFAULT,
            ) {
                "A contractual settlement must use the official current observation window."
            }
            recordIndicativeValueObservation(
                window = observationWindow,
                observationDate = input.effectiveDate,
                indicativeValuePerNote = currentIndicativeValue,
            )
        }
        require(canonicalWindow.size >= rule.observationCount) {
            "The canonical ETN observation window is too short for settlement."
        }
        val settlementObservations = canonicalWindow.takeLast(rule.observationCount)
        val value = when (rule.method) {
            EtnSettlementValuationMethod.LAST_INDICATIVE_VALUE ->
                settlementObservations.single().indicativeValuePerNote
            EtnSettlementValuationMethod.ARITHMETIC_AVERAGE ->
                settlementObservations.map(EtnIndicativeValueObservation::indicativeValuePerNote)
                    .average()
        }
        return settlementObservations to
            checkedNonNegativeValue(value, "contract settlement indicative value")
    }

    private fun recordIndicativeValueObservation(
        window: List<EtnIndicativeValueObservation>,
        observationDate: kotlinx.datetime.LocalDate,
        indicativeValuePerNote: Double,
    ): List<EtnIndicativeValueObservation> {
        val lastDate = window.lastOrNull()?.observationDate
        require(lastDate == null || observationDate >= lastDate) {
            "ETN indicative-value observation dates cannot move backwards."
        }
        val preceding = if (lastDate == observationDate) window.dropLast(1) else window
        return (preceding + EtnIndicativeValueObservation(observationDate, indicativeValuePerNote))
            .takeLast(EtnState.MAX_OBSERVATIONS)
    }

    private fun settlementPerNote(
        event: EtnCreditEvent,
        indicativeValuePerNote: Double,
        accruedCouponPerNote: Double,
        recoveryRate: Double,
    ): Double {
        val payout = when (event) {
            EtnCreditEvent.NONE -> 0.0
            EtnCreditEvent.HOLDER_REDEMPTION ->
                (terms.callTerms.holderRedemptionSettlementMultiplier -
                    terms.callTerms.holderRedemptionChargeRate) * indicativeValuePerNote +
                    accruedCouponPerNote.takeIf { terms.callTerms.includesAccruedCoupon }.orZero()
            EtnCreditEvent.ISSUER_CALL ->
                terms.callTerms.issuerCallSettlementMultiplier * indicativeValuePerNote +
                    accruedCouponPerNote.takeIf { terms.callTerms.includesAccruedCoupon }.orZero()
            EtnCreditEvent.CONTRACTUAL_MATURITY ->
                terms.maturitySettlementMultiplier * indicativeValuePerNote +
                    accruedCouponPerNote.takeIf { terms.maturityIncludesAccruedCoupon }.orZero()
            EtnCreditEvent.ISSUER_ACCELERATION ->
                terms.accelerationTerms.accelerationSettlementMultiplier * indicativeValuePerNote +
                    accruedCouponPerNote.takeIf {
                        terms.accelerationTerms.nonCreditAccelerationIncludesAccruedCoupon
                    }.orZero()
            EtnCreditEvent.CREDIT_DEFAULT -> {
                val unsecuredClaim =
                    terms.accelerationTerms.accelerationSettlementMultiplier * indicativeValuePerNote +
                        accruedCouponPerNote.takeIf {
                            terms.accelerationTerms.creditDefaultIncludesAccruedCouponBeforeRecovery
                        }.orZero()
                unsecuredClaim * recoveryRate
            }
        }
        return checkedNonNegativeValue(payout, "contract settlement per note")
    }

    private fun buildLedger(
        oldState: EtnState,
        nextState: EtnState,
        input: EtnAdvanceInput,
        settlementPerNote: Double,
        settlementObservations: List<EtnIndicativeValueObservation>,
    ): List<EtnLedgerEntry> {
        if (nextState.revision == oldState.revision) return emptyList()
        val entries = mutableListOf<EtnLedgerEntry>()
        var ledgerNotesOutstanding = oldState.notesOutstanding

        fun add(
            kind: EtnLedgerKind,
            notesIssued: Long,
            notesCancelled: Long,
            notesSettled: Long,
            cashPaid: Double,
            cashReceived: Double,
            event: EtnCreditEvent,
            settlementObservations: List<EtnIndicativeValueObservation>,
        ) {
            val sequence = entries.size
            val notesDelta = notesIssued - notesCancelled - notesSettled
            val notesAfter = ledgerNotesOutstanding + notesDelta
            entries += EtnLedgerEntry(
                id = "${terms.productId}:${nextState.revision}:$sequence",
                productId = terms.productId,
                kind = kind,
                effectiveAt = input.effectiveAt,
                revision = nextState.revision,
                sequenceInBatch = sequence,
                settlementCurrency = terms.settlementCurrency,
                referenceLevelBefore = oldState.referenceLevel,
                referenceLevelAfter = nextState.referenceLevel,
                indicativeValueBefore = oldState.feeAdjustedIndicativeValuePerNote,
                indicativeValueAfter = nextState.feeAdjustedIndicativeValuePerNote,
                notesOutstandingBefore = ledgerNotesOutstanding,
                notesOutstandingAfter = notesAfter,
                notesIssued = notesIssued,
                notesCancelled = notesCancelled,
                notesSettled = notesSettled,
                notesDelta = notesDelta,
                cashPaidToNoteholders = checkedNonNegativeValue(cashPaid, "ledger cash paid"),
                cashReceivedFromNoteholders = checkedNonNegativeValue(
                    cashReceived,
                    "ledger cash received",
                ),
                contractEvent = event,
                settlementIndicativeValueObservations = settlementObservations,
            )
            ledgerNotesOutstanding = notesAfter
        }

        if (input.couponPaymentPerNote > 0.0) {
            add(
                kind = EtnLedgerKind.COUPON_PAYMENT,
                notesIssued = 0L,
                notesCancelled = 0L,
                notesSettled = 0L,
                cashPaid = input.couponPaymentPerNote * oldState.notesOutstanding.toDouble(),
                cashReceived = 0.0,
                event = EtnCreditEvent.NONE,
                settlementObservations = emptyList(),
            )
        }
        if (input.notesIssued > 0L || input.notesCancelled > 0L) {
            add(
                kind = EtnLedgerKind.NOTE_FLOW,
                notesIssued = input.notesIssued,
                notesCancelled = input.notesCancelled,
                notesSettled = 0L,
                cashPaid = input.noteCancellationCashPerNote * input.notesCancelled.toDouble(),
                cashReceived = input.noteIssuanceCashPerNote * input.notesIssued.toDouble(),
                event = EtnCreditEvent.NONE,
                settlementObservations = emptyList(),
            )
        }
        if (input.contractEvent != EtnCreditEvent.NONE) {
            add(
                kind = EtnLedgerKind.CONTRACT_SETTLEMENT,
                notesIssued = 0L,
                notesCancelled = 0L,
                notesSettled = input.contractSettlementNotes,
                cashPaid = settlementPerNote * input.contractSettlementNotes.toDouble(),
                cashReceived = 0.0,
                event = input.contractEvent,
                settlementObservations = settlementObservations,
            )
        }
        require(ledgerNotesOutstanding == nextState.notesOutstanding)
        return entries
    }

    private fun checkedPositiveValue(value: Double, label: String): Double {
        require(value.isFinite() && value > 0.0 && value <= MAX_FUND_STRUCTURE_VALUE) {
            "$label is outside the supported positive range."
        }
        return value
    }

    private fun checkedNonNegativeValue(value: Double, label: String): Double {
        require(value.isFinite() && value >= 0.0 && value <= MAX_FUND_STRUCTURE_VALUE) {
            "$label is outside the supported non-negative range."
        }
        return value
    }

    private fun Double?.orZero(): Double = this ?: 0.0

    companion object {
        private val CREDIT_DEFAULT_VALUATION_RULE = EtnSettlementValuationRule(
            method = EtnSettlementValuationMethod.LAST_INDICATIVE_VALUE,
            observationCount = 1,
        )
    }
}
