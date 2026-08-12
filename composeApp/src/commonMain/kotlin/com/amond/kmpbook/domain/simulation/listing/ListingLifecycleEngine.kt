package com.amond.kmpbook.domain.simulation.listing

import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.listing.lifecycle.DailyListingSurveillanceInput
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingFinalDisposition
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleEvaluation
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleEventKind
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleProfileId
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleReason
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleReplayResult
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleState
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingNoticeLevel
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRecoveryCondition
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRiskSeverity
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRiskTag
import com.amond.kmpbook.domain.model.listing.lifecycle.blocksOrderlyProductTermination
import com.amond.kmpbook.domain.model.listing.lifecycle.preemptsOrderlyProductTermination
import com.amond.kmpbook.domain.model.market.Market
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 숨은 시간·난수 없이 하루 입력 하나를 상태 하나로 접는 상장 생명주기 엔진.
 * 동일 날짜를 재적용하면 아무 일도 하지 않으므로 저장 직후 재시도에도 중복 뉴스가 생기지 않는다.
 */
class ListingLifecycleEngine(
    private val profiles: Map<ListingLifecycleProfileId, ListingLifecyclePolicyProfile> =
        ListingLifecyclePolicyCatalog.all,
) {
    init {
        require(profiles.keys.containsAll(ListingLifecycleProfileId.entries))
    }

    fun initialState(stock: StockDefinition): ListingLifecycleState = initialState(
        stockId = stock.id,
        market = stock.market,
        instrumentType = stock.instrumentType,
    )

    fun initialState(
        stockId: String,
        market: Market,
        instrumentType: InstrumentType,
    ): ListingLifecycleState = ListingLifecycleState(
        stockId = stockId,
        market = market,
        instrumentType = instrumentType,
        profileId = ListingLifecyclePolicyCatalog.profileIdFor(market, instrumentType),
    )

    fun evaluate(
        current: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
    ): ListingLifecycleEvaluation {
        require(input.stockId == current.stockId) { "감시 입력과 상장 상태의 종목 ID가 다릅니다." }
        current.lastEvaluatedTradingDate?.let { lastDate ->
            require(input.tradingDate >= lastDate) { "상장 감시 입력은 날짜순이어야 합니다." }
            if (input.tradingDate == lastDate) return ListingLifecycleEvaluation(current, emptyList())
        }
        val profile = requireNotNull(profiles[current.profileId])
        require(profile.supports(current)) { "상장 생명주기 프로필이 종목 시장·유형과 맞지 않습니다." }

        val observed = updateObservationCounters(current, input, profile)
            .copy(lastEvaluatedTradingDate = input.tradingDate)
        if (current.isTerminal) return ListingLifecycleEvaluation(observed, emptyList())

        return when (current.status) {
            ListingLifecycleStatus.LISTED -> evaluateListed(current, observed, input, profile)
            ListingLifecycleStatus.DEFICIENCY_NOTICE -> evaluateDeficiency(current, observed, input, profile)
            ListingLifecycleStatus.UNDER_REVIEW -> evaluateReview(current, observed, input, profile)
            ListingLifecycleStatus.TRADING_SUSPENDED -> evaluateSuspension(current, observed, input, profile)
            ListingLifecycleStatus.DELISTING_SCHEDULED -> evaluateScheduled(current, observed, input, profile)
            ListingLifecycleStatus.LIQUIDATION_PENDING -> evaluateLiquidation(current, observed, input, profile)
            ListingLifecycleStatus.DELISTED,
            ListingLifecycleStatus.TERMINATED,
            -> ListingLifecycleEvaluation(observed, emptyList())
        }
    }

    fun replay(
        initial: ListingLifecycleState,
        inputs: List<DailyListingSurveillanceInput>,
    ): ListingLifecycleReplayResult {
        var state = initial
        val events = mutableListOf<ListingLifecycleLedgerEvent>()
        inputs.forEach { input ->
            val result = evaluate(state, input)
            state = result.state
            events += result.ledgerEvents
        }
        return ListingLifecycleReplayResult(state, events.toList())
    }

    private fun evaluateListed(
        previous: ListingLifecycleState,
        observed: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
    ): ListingLifecycleEvaluation {
        val reason = detectReason(observed, input, profile) ?: return noTransition(observed)
        beginPreemptingProcedure(previous, observed, input, profile, reason)?.let { return it }
        return when {
            reason.isOrderlyProductTermination() -> scheduleDelisting(
                previous = previous,
                observed = observed,
                input = input,
                profile = profile,
                reason = reason,
                tradingAllowed = input.scheduledDelistingOn?.let { it > input.tradingDate } ?: true,
            )
            else -> designateDeficiency(previous, observed, input, profile, reason)
        }
    }

    private fun evaluateDeficiency(
        previous: ListingLifecycleState,
        observed: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
    ): ListingLifecycleEvaluation {
        val incoming = detectReason(observed, input, profile)
        incoming?.let { reason ->
            beginPreemptingProcedure(previous, observed, input, profile, reason)?.let { return it }
        }
        if (incoming?.isOrderlyProductTermination() == true) {
            return scheduleDelisting(
                previous,
                observed,
                input,
                profile,
                incoming,
                tradingAllowed = input.scheduledDelistingOn?.let { it > input.tradingDate } ?: true,
            )
        }
        if (isRecovered(observed, input, profile)) {
            return release(previous, observed, input, profile, ListingLifecycleEventKind.DEFICIENCY_CURED)
        }
        if (observed.cureDeadline?.let { input.tradingDate >= it } == true) {
            return if (observed.market.isKorean) {
                startReview(previous, observed, input, profile, requireNotNull(observed.activeReason))
            } else {
                scheduleDelisting(
                    previous,
                    observed,
                    input,
                    profile,
                    requireNotNull(observed.activeReason),
                    tradingAllowed = true,
                )
            }
        }
        return noTransition(observed)
    }

    private fun evaluateReview(
        previous: ListingLifecycleState,
        observed: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
    ): ListingLifecycleEvaluation {
        val incoming = detectReason(observed, input, profile)
        incoming?.let { reason ->
            beginPreemptingProcedure(previous, observed, input, profile, reason)?.let { return it }
        }
        if (isRecovered(observed, input, profile)) {
            return release(previous, observed, input, profile, ListingLifecycleEventKind.TRADING_RESUMED)
        }
        if (incoming?.isOrderlyProductTermination() == true &&
            observed.activeReason?.blocksOrderlyProductTermination() != true
        ) {
            return scheduleDelisting(
                previous,
                observed,
                input,
                profile,
                incoming,
                tradingAllowed = input.scheduledDelistingOn?.let { it > input.tradingDate } ?: true,
            )
        }
        if (observed.reviewDeadline?.let { input.tradingDate >= it } == true) {
            return suspend(
                previous,
                observed,
                input,
                profile,
                requireNotNull(observed.activeReason),
            )
        }
        return noTransition(observed)
    }

    private fun evaluateSuspension(
        previous: ListingLifecycleState,
        observed: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
    ): ListingLifecycleEvaluation {
        val incoming = detectReason(observed, input, profile)
        incoming?.let { reason ->
            beginPreemptingProcedure(previous, observed, input, profile, reason)?.let { return it }
        }
        if (isRecovered(observed, input, profile)) {
            return release(previous, observed, input, profile, ListingLifecycleEventKind.TRADING_RESUMED)
        }
        if (incoming?.isOrderlyProductTermination() == true &&
            observed.activeReason?.blocksOrderlyProductTermination() != true
        ) {
            return scheduleDelisting(
                previous,
                observed,
                input,
                profile,
                incoming,
                tradingAllowed = false,
            )
        }
        if (observed.reviewDeadline?.let { input.tradingDate >= it } == true) {
            return scheduleDelisting(
                previous,
                observed,
                input,
                profile,
                requireNotNull(observed.activeReason),
                tradingAllowed = false,
            )
        }
        return noTransition(observed)
    }

    private fun evaluateScheduled(
        previous: ListingLifecycleState,
        observed: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
    ): ListingLifecycleEvaluation {
        val incoming = detectReason(observed, input, profile)
        incoming?.let { reason ->
            beginPreemptingProcedure(previous, observed, input, profile, reason)?.let { return it }
        }
        val incomingDate = input.scheduledDelistingOn
        val storedDate = requireNotNull(observed.scheduledDelistingOn)
        val incomingRawEffectiveOn = input.controllingTerminationRawEffectiveOn
        val storedRawEffectiveOn = observed.controllingTerminationRawEffectiveOn
        val incomingPreempts = incoming?.isOrderlyProductTermination() == true &&
            incomingRawEffectiveOn != null && (
            storedRawEffectiveOn == null && requireNotNull(incomingDate) < storedDate ||
                storedRawEffectiveOn != null && (
                    incomingRawEffectiveOn < storedRawEffectiveOn ||
                        incomingRawEffectiveOn == storedRawEffectiveOn &&
                        requireNotNull(input.controllingTerminationNoticePriority) <
                        requireNotNull(observed.controllingTerminationNoticePriority)
                    )
            )
        if (incomingPreempts) {
            return scheduleDelisting(
                previous = previous,
                observed = observed,
                input = input,
                profile = profile,
                reason = incoming,
                tradingAllowed = observed.tradingAllowedUntilDelisting &&
                    requireNotNull(incomingDate) > input.tradingDate,
            )
        }
        if (observed.activeReason?.isOrderlyProductTermination() == true &&
            input.controllingTerminationOccurrenceId != observed.controllingTerminationOccurrenceId
        ) {
            // A later/losing notice must never supply the winner's valuation or settlement terms.
            return noTransition(observed)
        }
        if (observed.activeReason?.canBeCuredAfterSchedule() == true && isRecovered(observed, input, profile)) {
            return release(previous, observed, input, profile, ListingLifecycleEventKind.TRADING_RESUMED)
        }
        if (input.tradingDate < storedDate) return noTransition(observed)

        val dispositionType = chooseDisposition(observed, input)
        if (dispositionType == ListingFinalDispositionType.CASH_LIQUIDATION) {
            // 지급 단가가 확정되지 않은 상태를 임의의 마지막 호가로 청산하지 않는다.
            val cashPerUnit = input.liquidationCashPerUnit ?: return noTransition(observed)
            val settlementDueOn = input.scheduledSettlementOn
                ?: input.tradingDate.plus(profile.liquidationSettlementCalendarDays, DateTimeUnit.DAY)
            val disposition = ListingFinalDisposition(
                type = dispositionType,
                effectiveOn = input.tradingDate,
                settlementDueOn = settlementDueOn,
                cashPerUnit = cashPerUnit,
            )
            val next = observed.copy(
                status = ListingLifecycleStatus.LIQUIDATION_PENDING,
                settlementDueOn = settlementDueOn,
                tradingAllowedUntilDelisting = false,
                finalDisposition = disposition,
            )
            return transition(
                previous,
                next,
                input,
                profile,
                ListingLifecycleEventKind.LIQUIDATION_STARTED,
                ListingNoticeLevel.WARNING,
                disposition,
            )
        }

        val disposition = ListingFinalDisposition(
            type = dispositionType,
            effectiveOn = input.tradingDate,
            cashPerUnit = if (dispositionType == ListingFinalDispositionType.WORTHLESS_DISPOSITION) 0.0 else null,
        )
        val terminalStatus = if (observed.instrumentType == InstrumentType.ETN) {
            ListingLifecycleStatus.TERMINATED
        } else {
            ListingLifecycleStatus.DELISTED
        }
        val next = observed.copy(
            status = terminalStatus,
            tradingAllowedUntilDelisting = false,
            finalDisposition = disposition,
        )
        return transition(
            previous,
            next,
            input,
            profile,
            if (terminalStatus == ListingLifecycleStatus.TERMINATED) {
                ListingLifecycleEventKind.TERMINATED
            } else {
                ListingLifecycleEventKind.DELISTED
            },
            ListingNoticeLevel.CRITICAL,
            disposition,
        )
    }

    private fun evaluateLiquidation(
        previous: ListingLifecycleState,
        observed: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
    ): ListingLifecycleEvaluation {
        if (observed.settlementDueOn?.let { input.tradingDate >= it } != true) return noTransition(observed)
        val next = observed.copy(
            status = ListingLifecycleStatus.TERMINATED,
            tradingAllowedUntilDelisting = false,
        )
        return transition(
            previous,
            next,
            input,
            profile,
            ListingLifecycleEventKind.TERMINATED,
            ListingNoticeLevel.INFO,
            observed.finalDisposition,
        )
    }

    private fun designateDeficiency(
        previous: ListingLifecycleState,
        observed: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
        reason: ListingLifecycleReason,
    ): ListingLifecycleEvaluation {
        val count = observed.designationCount + 1
        val deadline = input.tradingDate.plus(profile.curePeriodCalendarDays, DateTimeUnit.DAY)
        val next = observed.copy(
            status = ListingLifecycleStatus.DEFICIENCY_NOTICE,
            activeReason = reason,
            designatedOn = input.tradingDate,
            cureDeadline = deadline,
            reviewDeadline = null,
            scheduledDelistingOn = null,
            settlementDueOn = null,
            consecutiveCureTradingDays = 0,
            designationCount = count,
            finalDisposition = null,
            tradingAllowedUntilDelisting = true,
            controllingTerminationOccurrenceId = null,
            controllingTerminationNoticePriority = null,
            controllingTerminationRawEffectiveOn = null,
        )
        return transition(
            previous,
            next,
            input,
            profile,
            if (count == 1) {
                ListingLifecycleEventKind.DEFICIENCY_DESIGNATED
            } else {
                ListingLifecycleEventKind.DEFICIENCY_REDESIGNATED
            },
            ListingNoticeLevel.CAUTION,
        )
    }

    private fun startReview(
        previous: ListingLifecycleState,
        observed: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
        reason: ListingLifecycleReason,
    ): ListingLifecycleEvaluation {
        val deadline = input.tradingDate.plus(profile.reviewPeriodCalendarDays, DateTimeUnit.DAY)
        val next = observed.copy(
            status = ListingLifecycleStatus.UNDER_REVIEW,
            activeReason = reason,
            designatedOn = observed.designatedOn ?: input.tradingDate,
            cureDeadline = null,
            reviewDeadline = deadline,
            scheduledDelistingOn = null,
            settlementDueOn = null,
            consecutiveCureTradingDays = 0,
            tradingAllowedUntilDelisting = observed.market.isUnitedStates,
            controllingTerminationOccurrenceId = null,
            controllingTerminationNoticePriority = null,
            controllingTerminationRawEffectiveOn = null,
        )
        return transition(
            previous,
            next,
            input,
            profile,
            ListingLifecycleEventKind.REVIEW_STARTED,
            ListingNoticeLevel.WARNING,
        )
    }

    private fun suspend(
        previous: ListingLifecycleState,
        observed: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
        reason: ListingLifecycleReason,
    ): ListingLifecycleEvaluation {
        val deadline = input.tradingDate.plus(profile.reviewPeriodCalendarDays, DateTimeUnit.DAY)
        val next = observed.copy(
            status = ListingLifecycleStatus.TRADING_SUSPENDED,
            activeReason = reason,
            designatedOn = observed.designatedOn ?: input.tradingDate,
            cureDeadline = null,
            reviewDeadline = deadline,
            scheduledDelistingOn = null,
            settlementDueOn = null,
            consecutiveCureTradingDays = 0,
            tradingAllowedUntilDelisting = false,
            controllingTerminationOccurrenceId = null,
            controllingTerminationNoticePriority = null,
            controllingTerminationRawEffectiveOn = null,
        )
        return transition(
            previous,
            next,
            input,
            profile,
            ListingLifecycleEventKind.TRADING_SUSPENDED,
            ListingNoticeLevel.CRITICAL,
        )
    }

    private fun scheduleDelisting(
        previous: ListingLifecycleState,
        observed: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
        reason: ListingLifecycleReason,
        tradingAllowed: Boolean,
    ): ListingLifecycleEvaluation {
        val date = input.scheduledDelistingOn
            ?: input.tradingDate.plus(profile.delistingNoticeCalendarDays, DateTimeUnit.DAY)
        val controllingTerminationOccurrenceId = if (reason.isOrderlyProductTermination()) {
            requireNotNull(input.controllingTerminationOccurrenceId) {
                "상품 종료 일정에는 이를 공급한 정확한 공시 ID가 필요합니다."
            }
        } else {
            null
        }
        val controllingTerminationNoticePriority = if (reason.isOrderlyProductTermination()) {
            requireNotNull(input.controllingTerminationNoticePriority)
        } else {
            null
        }
        val controllingTerminationRawEffectiveOn = if (reason.isOrderlyProductTermination()) {
            requireNotNull(input.controllingTerminationRawEffectiveOn)
        } else {
            null
        }
        val next = observed.copy(
            status = ListingLifecycleStatus.DELISTING_SCHEDULED,
            activeReason = reason,
            designatedOn = observed.designatedOn ?: input.tradingDate,
            cureDeadline = null,
            reviewDeadline = null,
            scheduledDelistingOn = date,
            settlementDueOn = null,
            consecutiveCureTradingDays = 0,
            tradingAllowedUntilDelisting = tradingAllowed,
            controllingTerminationOccurrenceId = controllingTerminationOccurrenceId,
            controllingTerminationNoticePriority = controllingTerminationNoticePriority,
            controllingTerminationRawEffectiveOn = controllingTerminationRawEffectiveOn,
        )
        val scheduled = transition(
            previous,
            next,
            input,
            profile,
            ListingLifecycleEventKind.DELISTING_SCHEDULED,
            ListingNoticeLevel.CRITICAL,
        )
        if (date <= input.tradingDate && reason.isOrderlyProductTermination()) {
            val liquidation = evaluateScheduled(
                previous = scheduled.state,
                observed = scheduled.state,
                input = input,
                profile = profile,
            )
            if (liquidation.state.status == ListingLifecycleStatus.LIQUIDATION_PENDING) {
                return ListingLifecycleEvaluation(
                    state = liquidation.state,
                    ledgerEvents = scheduled.ledgerEvents + liquidation.ledgerEvents,
                )
            }
        }
        return scheduled
    }

    private fun release(
        previous: ListingLifecycleState,
        observed: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
        kind: ListingLifecycleEventKind,
    ): ListingLifecycleEvaluation {
        val next = observed.copy(
            status = ListingLifecycleStatus.LISTED,
            activeReason = null,
            designatedOn = null,
            cureDeadline = null,
            reviewDeadline = null,
            scheduledDelistingOn = null,
            settlementDueOn = null,
            consecutiveLowBidTradingDays = 0,
            consecutiveLowMarketCapTradingDays = 0,
            consecutiveLowLiquidityTradingDays = 0,
            consecutiveCureTradingDays = 0,
            finalDisposition = null,
            tradingAllowedUntilDelisting = true,
            controllingTerminationOccurrenceId = null,
            controllingTerminationNoticePriority = null,
            controllingTerminationRawEffectiveOn = null,
        )
        return transition(previous, next, input, profile, kind, ListingNoticeLevel.INFO)
    }

    private fun transition(
        previous: ListingLifecycleState,
        candidate: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
        kind: ListingLifecycleEventKind,
        level: ListingNoticeLevel,
        disposition: ListingFinalDisposition? = null,
    ): ListingLifecycleEvaluation {
        val sequence = previous.ledgerSequence + 1L
        val next = candidate.copy(
            lastEvaluatedTradingDate = input.tradingDate,
            ledgerSequence = sequence,
        )
        val deadline = when (next.status) {
            ListingLifecycleStatus.DEFICIENCY_NOTICE -> next.cureDeadline
            ListingLifecycleStatus.UNDER_REVIEW,
            ListingLifecycleStatus.TRADING_SUSPENDED,
            -> next.reviewDeadline
            ListingLifecycleStatus.DELISTING_SCHEDULED -> next.scheduledDelistingOn
            ListingLifecycleStatus.LIQUIDATION_PENDING -> next.settlementDueOn
            else -> null
        }
        val reason = next.activeReason ?: previous.activeReason
        val event = ListingLifecycleLedgerEvent(
            id = "${next.stockId}:${input.tradingDate}:$sequence:${kind.name}",
            sequence = sequence,
            stockId = next.stockId,
            tradingDate = input.tradingDate,
            kind = kind,
            fromStatus = previous.status,
            toStatus = next.status,
            reason = reason,
            level = level,
            title = titleFor(kind),
            summary = summaryFor(next, reason, deadline, disposition),
            deadline = deadline,
            disposition = disposition,
            controllingTerminationOccurrenceId = next.controllingTerminationOccurrenceId,
            controllingTerminationNoticePriority = next.controllingTerminationNoticePriority,
            controllingTerminationRawEffectiveOn = next.controllingTerminationRawEffectiveOn,
            sourceUrls = profile.officialSourceUrls,
        )
        return ListingLifecycleEvaluation(next, listOf(event))
    }

    private fun noTransition(state: ListingLifecycleState): ListingLifecycleEvaluation =
        ListingLifecycleEvaluation(state, emptyList())

    private fun updateObservationCounters(
        state: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
    ): ListingLifecycleState {
        val isLowBid = ListingRiskTag.LOW_BID_PRICE in input.riskTags ||
            (profile.minimumBidPrice != null && input.close != null && input.close < profile.minimumBidPrice)
        val isLowMarketCap = ListingRiskTag.LOW_MARKET_CAPITALIZATION in input.riskTags ||
            (
                profile.minimumMarketCapitalization != null && input.marketCapitalization != null &&
                    input.marketCapitalization < profile.minimumMarketCapitalization
                )
        val isLowLiquidity = ListingRiskTag.LOW_TRADING_LIQUIDITY in input.riskTags ||
            (
                profile.minimumTurnoverRate != null &&
                    (
                        input.tradedVolume == 0L ||
                            input.turnoverRate != null && input.turnoverRate < profile.minimumTurnoverRate
                        )
                )
        val cureHealthy = state.activeReason?.let { reason -> isHealthyObservation(reason, input, profile) } ?: false
        return state.copy(
            consecutiveLowBidTradingDays = if (isLowBid) state.consecutiveLowBidTradingDays + 1 else 0,
            consecutiveLowMarketCapTradingDays =
                if (isLowMarketCap) state.consecutiveLowMarketCapTradingDays + 1 else 0,
            consecutiveLowLiquidityTradingDays =
                if (isLowLiquidity) state.consecutiveLowLiquidityTradingDays + 1 else 0,
            consecutiveCureTradingDays = if (cureHealthy) state.consecutiveCureTradingDays + 1 else 0,
        )
    }

    private fun detectReason(
        state: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
    ): ListingLifecycleReason? {
        val tags = input.riskTags
        val nonOrderlyReasons = buildList {
            if (ListingRiskTag.BANKRUPTCY_OR_INSOLVENCY in tags) {
                add(ListingLifecycleReason.BANKRUPTCY_OR_INSOLVENCY)
            }
            if (ListingRiskTag.ISSUER_ELIGIBILITY_FAILURE in tags) {
                add(ListingLifecycleReason.ISSUER_ELIGIBILITY_FAILURE)
            }
            if (ListingRiskTag.UNDERLYING_INDEX_UNAVAILABLE in tags) {
                add(ListingLifecycleReason.UNDERLYING_INDEX_UNAVAILABLE)
            }
            if (ListingRiskTag.LIQUIDITY_PROVIDER_FAILURE in tags) {
                add(ListingLifecycleReason.LIQUIDITY_PROVIDER_FAILURE)
            }
            if (ListingRiskTag.CORE_BUSINESS_SUSPENSION in tags) {
                add(ListingLifecycleReason.CORE_BUSINESS_SUSPENSION)
            }
            if (ListingRiskTag.SERIOUS_COMPLIANCE_EVENT in tags) {
                add(ListingLifecycleReason.SERIOUS_COMPLIANCE_EVENT)
            }
            if (ListingRiskTag.AUDIT_OPINION_FAILURE in tags || ListingRiskTag.DISCLOSURE_VIOLATION in tags) {
                add(ListingLifecycleReason.AUDIT_OR_DISCLOSURE_FAILURE)
            }
            if (ListingRiskTag.ADMINISTRATIVE_ISSUE in tags) {
                add(ListingLifecycleReason.KRX_ADMINISTRATIVE_ISSUE)
            }
            if (ListingRiskTag.LISTING_MAINTENANCE_DEFICIENCY in tags ||
                ListingRiskTag.QUALITATIVE_LISTING_REVIEW in tags
            ) {
                add(
                    if (state.market.isKorean) {
                        ListingLifecycleReason.KRX_LISTING_MAINTENANCE
                    } else {
                        ListingLifecycleReason.US_LISTING_MAINTENANCE
                    },
                )
            }
        }
        nonOrderlyReasons.firstOrNull { reason ->
            reason.preemptsOrderlyProductTermination(input.severityFor(reason))
        }?.let { return it }

        return when {
            ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION in tags &&
                state.instrumentType == InstrumentType.ETN ->
                ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION
            ListingRiskTag.ETF_LIQUIDATION_APPROVED in tags &&
                state.instrumentType in setOf(InstrumentType.ETF, InstrumentType.CLOSED_END_FUND) ->
                ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION
            nonOrderlyReasons.isNotEmpty() -> nonOrderlyReasons.first()
            profile.minimumBidPrice != null &&
                state.consecutiveLowBidTradingDays >= profile.bidDeficiencyTradingDays ->
                ListingLifecycleReason.US_MINIMUM_BID_PRICE
            profile.minimumMarketCapitalization != null &&
                state.consecutiveLowMarketCapTradingDays >= profile.marketCapDeficiencyTradingDays ->
                ListingLifecycleReason.US_MARKET_CAPITALIZATION
            profile.minimumTurnoverRate != null &&
                state.consecutiveLowLiquidityTradingDays >= profile.liquidityDeficiencyTradingDays ->
                ListingLifecycleReason.LOW_TRADING_LIQUIDITY
            else -> null
        }
    }

    private fun beginPreemptingProcedure(
        previous: ListingLifecycleState,
        observed: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
        reason: ListingLifecycleReason,
    ): ListingLifecycleEvaluation? {
        val severity = input.severityFor(reason)
        if (!reason.preemptsOrderlyProductTermination(severity)) return null
        val suspendImmediately = reason.requiresImmediateSuspension(severity)
        val targetStatus = if (suspendImmediately) {
            ListingLifecycleStatus.TRADING_SUSPENDED
        } else {
            ListingLifecycleStatus.UNDER_REVIEW
        }
        if (
            observed.activeReason == reason &&
            observed.status.hasReachedOrPassedPreemptingStatus(targetStatus)
        ) {
            // An enduring risk observation must not restart its own procedure. In particular,
            // DELISTING_SCHEDULED is later than review/suspension; moving it backwards would reset
            // reviewDeadline on every review cycle and keep a bankrupt listing suspended forever.
            return null
        }
        return if (suspendImmediately) {
            suspend(previous, observed, input, profile, reason)
        } else {
            startReview(previous, observed, input, profile, reason)
        }
    }

    private fun ListingLifecycleStatus.hasReachedOrPassedPreemptingStatus(
        targetStatus: ListingLifecycleStatus,
    ): Boolean = when (targetStatus) {
        ListingLifecycleStatus.UNDER_REVIEW -> this in setOf(
            ListingLifecycleStatus.UNDER_REVIEW,
            ListingLifecycleStatus.TRADING_SUSPENDED,
            ListingLifecycleStatus.DELISTING_SCHEDULED,
            ListingLifecycleStatus.LIQUIDATION_PENDING,
            ListingLifecycleStatus.DELISTED,
            ListingLifecycleStatus.TERMINATED,
        )
        ListingLifecycleStatus.TRADING_SUSPENDED -> this in setOf(
            ListingLifecycleStatus.TRADING_SUSPENDED,
            ListingLifecycleStatus.DELISTING_SCHEDULED,
            ListingLifecycleStatus.LIQUIDATION_PENDING,
            ListingLifecycleStatus.DELISTED,
            ListingLifecycleStatus.TERMINATED,
        )
        ListingLifecycleStatus.LISTED,
        ListingLifecycleStatus.DEFICIENCY_NOTICE,
        ListingLifecycleStatus.DELISTING_SCHEDULED,
        ListingLifecycleStatus.LIQUIDATION_PENDING,
        ListingLifecycleStatus.DELISTED,
        ListingLifecycleStatus.TERMINATED,
        -> error("선점 절차의 목표 상태는 심사 또는 거래정지여야 합니다.")
    }

    private fun DailyListingSurveillanceInput.severityFor(
        reason: ListingLifecycleReason,
    ): ListingRiskSeverity {
        val tags = when (reason) {
            ListingLifecycleReason.BANKRUPTCY_OR_INSOLVENCY ->
                setOf(ListingRiskTag.BANKRUPTCY_OR_INSOLVENCY)
            ListingLifecycleReason.ISSUER_ELIGIBILITY_FAILURE ->
                setOf(ListingRiskTag.ISSUER_ELIGIBILITY_FAILURE)
            ListingLifecycleReason.UNDERLYING_INDEX_UNAVAILABLE ->
                setOf(ListingRiskTag.UNDERLYING_INDEX_UNAVAILABLE)
            ListingLifecycleReason.LIQUIDITY_PROVIDER_FAILURE ->
                setOf(ListingRiskTag.LIQUIDITY_PROVIDER_FAILURE)
            ListingLifecycleReason.CORE_BUSINESS_SUSPENSION ->
                setOf(ListingRiskTag.CORE_BUSINESS_SUSPENSION)
            ListingLifecycleReason.SERIOUS_COMPLIANCE_EVENT ->
                setOf(ListingRiskTag.SERIOUS_COMPLIANCE_EVENT)
            ListingLifecycleReason.AUDIT_OR_DISCLOSURE_FAILURE -> setOf(
                ListingRiskTag.AUDIT_OPINION_FAILURE,
                ListingRiskTag.DISCLOSURE_VIOLATION,
            )
            ListingLifecycleReason.KRX_ADMINISTRATIVE_ISSUE -> setOf(ListingRiskTag.ADMINISTRATIVE_ISSUE)
            ListingLifecycleReason.KRX_LISTING_MAINTENANCE,
            ListingLifecycleReason.US_LISTING_MAINTENANCE,
            -> setOf(ListingRiskTag.LISTING_MAINTENANCE_DEFICIENCY, ListingRiskTag.QUALITATIVE_LISTING_REVIEW)
            ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION -> setOf(ListingRiskTag.ETF_LIQUIDATION_APPROVED)
            ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION ->
                setOf(ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION)
            ListingLifecycleReason.US_MINIMUM_BID_PRICE,
            ListingLifecycleReason.US_MARKET_CAPITALIZATION,
            ListingLifecycleReason.LOW_TRADING_LIQUIDITY,
            -> emptySet()
        }
        return tags.maxOfOrNull { tag ->
            riskSeverityByTag[tag]?.level ?: ListingRiskSeverity.NONE.level
        }?.let { level -> ListingRiskSeverity.entries.first { it.level == level } }
            ?: ListingRiskSeverity.NONE
    }

    private fun isRecovered(
        state: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
    ): Boolean {
        val reason = state.activeReason ?: return false
        if (ListingRecoveryCondition.REGULATORY_CLEARANCE in input.recoveryConditions && reason.canBeExplicitlyCured()) {
            return true
        }
        return when (reason) {
            ListingLifecycleReason.US_MINIMUM_BID_PRICE ->
                state.consecutiveCureTradingDays >= profile.bidCureTradingDays ||
                    ListingRecoveryCondition.BID_PRICE_RESTORED in input.recoveryConditions
            ListingLifecycleReason.US_MARKET_CAPITALIZATION ->
                state.consecutiveCureTradingDays >= profile.bidCureTradingDays ||
                    ListingRecoveryCondition.MARKET_CAPITALIZATION_RESTORED in input.recoveryConditions
            ListingLifecycleReason.LOW_TRADING_LIQUIDITY ->
                state.consecutiveCureTradingDays >= profile.bidCureTradingDays ||
                    ListingRecoveryCondition.LIQUIDITY_RESTORED in input.recoveryConditions
            ListingLifecycleReason.KRX_LISTING_MAINTENANCE,
            ListingLifecycleReason.KRX_ADMINISTRATIVE_ISSUE,
            ListingLifecycleReason.US_LISTING_MAINTENANCE,
            -> ListingRecoveryCondition.FINANCIAL_DEFICIENCY_RESOLVED in input.recoveryConditions
            ListingLifecycleReason.AUDIT_OR_DISCLOSURE_FAILURE,
            ListingLifecycleReason.SERIOUS_COMPLIANCE_EVENT,
            -> ListingRecoveryCondition.AUDIT_OR_DISCLOSURE_CURED in input.recoveryConditions
            ListingLifecycleReason.CORE_BUSINESS_SUSPENSION ->
                ListingRecoveryCondition.BUSINESS_RESUMED in input.recoveryConditions
            ListingLifecycleReason.ISSUER_ELIGIBILITY_FAILURE ->
                ListingRecoveryCondition.ISSUER_ELIGIBILITY_RESTORED in input.recoveryConditions
            ListingLifecycleReason.UNDERLYING_INDEX_UNAVAILABLE ->
                ListingRecoveryCondition.UNDERLYING_INDEX_RESTORED in input.recoveryConditions
            ListingLifecycleReason.LIQUIDITY_PROVIDER_FAILURE ->
                ListingRecoveryCondition.LIQUIDITY_PROVIDER_REPLACED in input.recoveryConditions
            ListingLifecycleReason.BANKRUPTCY_OR_INSOLVENCY,
            ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION,
            ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION,
            -> false
        }
    }

    private fun isHealthyObservation(
        reason: ListingLifecycleReason,
        input: DailyListingSurveillanceInput,
        profile: ListingLifecyclePolicyProfile,
    ): Boolean = when (reason) {
        ListingLifecycleReason.US_MINIMUM_BID_PRICE ->
            profile.minimumBidPrice != null && input.close != null && input.close >= profile.minimumBidPrice &&
                ListingRiskTag.LOW_BID_PRICE !in input.riskTags
        ListingLifecycleReason.US_MARKET_CAPITALIZATION ->
            profile.minimumMarketCapitalization != null && input.marketCapitalization != null &&
                input.marketCapitalization >= profile.minimumMarketCapitalization &&
                ListingRiskTag.LOW_MARKET_CAPITALIZATION !in input.riskTags
        ListingLifecycleReason.LOW_TRADING_LIQUIDITY ->
            profile.minimumTurnoverRate != null && input.turnoverRate != null &&
                input.turnoverRate >= profile.minimumTurnoverRate &&
                ListingRiskTag.LOW_TRADING_LIQUIDITY !in input.riskTags
        else -> false
    }

    private fun chooseDisposition(
        state: ListingLifecycleState,
        input: DailyListingSurveillanceInput,
    ): ListingFinalDispositionType {
        input.finalDispositionHint?.let { return it }
        val reason = requireNotNull(state.activeReason)
        if (reason.isOrderlyProductTermination()) return ListingFinalDispositionType.CASH_LIQUIDATION
        if (state.instrumentType == InstrumentType.ETN && input.liquidationCashPerUnit != null) {
            return ListingFinalDispositionType.CASH_LIQUIDATION
        }
        if (state.market.isUnitedStates && input.otcTransferAvailable) {
            return ListingFinalDispositionType.OTC_TRANSFER
        }
        return ListingFinalDispositionType.WORTHLESS_DISPOSITION
    }

    private fun ListingLifecycleReason.isOrderlyProductTermination(): Boolean =
        this == ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION ||
            this == ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION

    private fun ListingLifecycleReason.requiresImmediateSuspension(severity: ListingRiskSeverity): Boolean = when (this) {
        ListingLifecycleReason.BANKRUPTCY_OR_INSOLVENCY -> true
        ListingLifecycleReason.ISSUER_ELIGIBILITY_FAILURE,
        ListingLifecycleReason.UNDERLYING_INDEX_UNAVAILABLE,
        -> severity.level >= ListingRiskSeverity.HIGH.level
        ListingLifecycleReason.LIQUIDITY_PROVIDER_FAILURE,
        ListingLifecycleReason.SERIOUS_COMPLIANCE_EVENT,
        -> severity == ListingRiskSeverity.CRITICAL
        else -> false
    }

    private fun ListingLifecycleReason.canBeExplicitlyCured(): Boolean =
        this !in setOf(
            ListingLifecycleReason.BANKRUPTCY_OR_INSOLVENCY,
            ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION,
            ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION,
        )

    private fun ListingLifecycleReason.canBeCuredAfterSchedule(): Boolean = canBeExplicitlyCured()

    private fun titleFor(kind: ListingLifecycleEventKind): String = when (kind) {
        ListingLifecycleEventKind.DEFICIENCY_DESIGNATED -> "상장 유지 요건 안내"
        ListingLifecycleEventKind.DEFICIENCY_REDESIGNATED -> "상장 유지 요건 재지정"
        ListingLifecycleEventKind.REVIEW_STARTED -> "상장 적격성 심사 시작"
        ListingLifecycleEventKind.TRADING_SUSPENDED -> "거래정지"
        ListingLifecycleEventKind.DEFICIENCY_CURED -> "상장 유지 요건 회복"
        ListingLifecycleEventKind.TRADING_RESUMED -> "상장 조치 해제"
        ListingLifecycleEventKind.DELISTING_SCHEDULED -> "상장폐지 예정"
        ListingLifecycleEventKind.LIQUIDATION_STARTED -> "청산금 지급 절차"
        ListingLifecycleEventKind.DELISTED -> "상장폐지 완료"
        ListingLifecycleEventKind.TERMINATED -> "상품 종료"
    }

    private fun summaryFor(
        state: ListingLifecycleState,
        reason: ListingLifecycleReason?,
        deadline: LocalDate?,
        disposition: ListingFinalDisposition?,
    ): String = buildString {
        append(state.status.displayName)
        reason?.let { append(" · ${it.displayName}") }
        deadline?.let { append(" · $it 예정") }
        disposition?.let { append(" · ${it.type.displayName}") }
    }
}
