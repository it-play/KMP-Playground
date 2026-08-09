package com.amond.kmpbook.presentation.protection

import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertDesignation
import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertStatus
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingFinalDisposition
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleState
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.protection.core.InstrumentTradingHalt
import com.amond.kmpbook.domain.model.protection.core.ProgramOrderSide
import com.amond.kmpbook.domain.model.protection.core.TradingHaltReason
import com.amond.kmpbook.domain.model.protection.core.TradingHaltStatus
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerState
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarState
import com.amond.kmpbook.domain.model.protection.krx.KrxViKind
import com.amond.kmpbook.domain.model.protection.krx.KrxViPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxViState
import com.amond.kmpbook.domain.model.protection.us.UsLuldPhase
import com.amond.kmpbook.domain.model.protection.us.UsLuldState
import com.amond.kmpbook.domain.model.protection.us.UsMwcbPhase
import com.amond.kmpbook.domain.model.protection.us.UsMwcbState
import com.amond.kmpbook.domain.simulation.protection.TradingProtectionEngine
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class ProtectionUiProjection(
    val marketStrip: MarketProtectionStripUi?,
    /** 시장 전체 상태를 매 행에 반복하지 않고, 해당 종목 자체의 가장 강한 상태만 표시한다. */
    val symbolBadges: Map<String, ProtectionStatusBadgeUi>,
    val selectedSymbolDetail: ProtectionDetailUi?,
)

/**
 * 저장 가능한 도메인 스냅샷에서 Toss식 점진적 공개 UI를 만든다.
 *
 * - 상단에는 현재 작동 중인 시장 전체 보호장치 중 가장 강한 하나만 보인다.
 * - 종목 행에는 가장 강한 상태 하나와 `외 N`만 보인다.
 * - 상세 화면에서만 주문 영향·재개 조건·게임 규칙을 모두 펼친다.
 * - 투자경보 지정·해제는 [at]을 종목 거래소 현지 날짜로 바꾼 뒤 효력일을 판단한다.
 */
fun buildProtectionUiProjection(
    snapshot: TradingProtectionSnapshot,
    listingStates: Map<String, ListingLifecycleState> = emptyMap(),
    selectedStockId: String? = null,
    selectedMarket: Market? = null,
    at: Instant? = null,
): ProtectionUiProjection {
    val marketStatuses = snapshot.marketWideStatuses().sortedForDisplay()
    val marketDetail = marketStatuses.toDetailOrNull("시장 보호장치")
    val marketStrip = marketDetail?.let { detail ->
        MarketProtectionStripUi(
            title = detail.primary.title,
            summary = detail.primary.summary,
            detail = detail,
            stateDescription = detail.accessibilityDescription(),
        )
    }

    val lifecycleByStockId = listingStates.values.associateBy(ListingLifecycleState::stockId)
    val stockIds = buildSet {
        addAll(lifecycleByStockId.keys)
        addAll(snapshot.krxVolatilityInterruptions.keys)
        addAll(snapshot.instrumentTradingHalts.keys)
        addAll(snapshot.scheduledInstrumentTradingHalts.values.map(InstrumentTradingHalt::stockId))
        addAll(snapshot.investmentAlerts.keys)
        addAll(snapshot.usLuldStates.keys)
    }
    val statusesByStockId = stockIds.associateWith { stockId ->
        val listingState = lifecycleByStockId[stockId]
        val market = selectedMarket.takeIf { stockId == selectedStockId }
            ?: listingState?.market
            ?: stockId.marketFromStableId()
        val marketDate = if (at != null && market != null) {
            GameCalendar.marketLocalDateTime(market, at).date
        } else {
            null
        }
        snapshot.instrumentStatuses(stockId, listingState, at, marketDate).sortedForDisplay()
    }
    val badges = statusesByStockId.mapNotNull { (stockId, statuses) ->
        statuses.takeIf(List<ProtectionUiStatus>::isNotEmpty)?.let { stockId to it.toBadge() }
    }.toMap()

    val resolvedSelectedMarket = selectedMarket
        ?: selectedStockId?.let(lifecycleByStockId::get)?.market
        ?: selectedStockId?.marketFromStableId()
    val selectedSymbolStatuses = selectedStockId?.let(statusesByStockId::get).orEmpty()
    val selectedListingIsTerminal = selectedStockId?.let(lifecycleByStockId::get)?.isTerminal == true
    val applicableMarketStatuses = if (
        selectedStockId == null || resolvedSelectedMarket == null || selectedListingIsTerminal
    ) {
        emptyList()
    } else {
        marketStatuses.filter { resolvedSelectedMarket in it.markets }
    }
    val selectedDetail = (selectedSymbolStatuses + applicableMarketStatuses)
        .distinctBy(ProtectionUiStatus::id)
        .sortedForDisplay()
        .toDetailOrNull("종목 거래 상태")

    return ProtectionUiProjection(
        marketStrip = marketStrip,
        symbolBadges = badges,
        selectedSymbolDetail = selectedDetail,
    )
}

private fun TradingProtectionSnapshot.marketWideStatuses(): List<ProtectionUiStatus> = buildList {
    krxCircuitBreakers.values.mapNotNullTo(this) { it.toUiStatus() }
    krxSidecars.values.mapNotNullTo(this) { it.toUiStatus() }
    usMarketWideCircuitBreaker?.toUiStatus()?.let(::add)
}

private fun TradingProtectionSnapshot.instrumentStatuses(
    stockId: String,
    listingState: ListingLifecycleState?,
    at: Instant?,
    marketDate: LocalDate?,
): List<ProtectionUiStatus> = buildList {
    val listingStatus = listingState?.toUiStatus()
    listingStatus?.let(::add)
    // 상장이 끝난 종목에는 종목별 보호장치를 거래 상태로 함께 노출하지 않는다.
    if (listingState?.isTerminal == true) return@buildList
    buildList {
        instrumentTradingHalts[stockId]?.let(::add)
        if (at != null) {
            scheduledInstrumentTradingHalts.values
                .filterTo(this) { it.stockId == stockId }
        }
    }.distinct()
        .filter { halt -> at == null || TradingProtectionEngine.isInstrumentHaltActive(halt, at) }
        .mapNotNullTo(this) { it.toUiStatus() }
    krxVolatilityInterruptions[stockId]?.toUiStatus()?.let(::add)
    investmentAlerts[stockId]?.toUiStatus(marketDate)?.let(::add)
    usLuldStates[stockId]?.toUiStatus()?.let(::add)
}

private fun KrxCircuitBreakerState.toUiStatus(): ProtectionUiStatus? {
    val marketName = market.displayName
    return when (phase) {
        KrxCircuitBreakerPhase.NORMAL -> null
        KrxCircuitBreakerPhase.HALTED -> ProtectionUiStatus(
            id = "market:krx-cb:${market.name}",
            badgeLabel = "$marketName 거래정지",
            title = "$marketName 거래가 잠시 멈췄어요",
            summary = "20분 뒤 단일가로 다시 시작해요.",
            orderImpact = "새 주문과 체결이 잠시 멈춰요. 대기 주문은 거래 재개 규칙에 따라 처리돼요.",
            resumeGuidance = "20분 거래정지 뒤 10분 단일가를 거쳐 연속 매매로 돌아가요.",
            ruleExplanation = "지수가 전일 종가보다 8%·15%·20% 하락한 상태가 1분 이어지면 단계별로 발동해요.",
            tone = ProtectionUiTone.CRITICAL,
            emphasis = ProtectionBadgeEmphasis.FILL,
            priority = 96,
            markets = setOf(market),
            endsAt = haltEndsAt,
        )

        KrxCircuitBreakerPhase.REOPENING_CALL_AUCTION -> ProtectionUiStatus(
            id = "market:krx-cb:${market.name}",
            badgeLabel = "$marketName 단일가",
            title = "$marketName 거래가 다시 열리고 있어요",
            summary = "10분 동안 주문을 모아 한 가격으로 체결해요.",
            orderImpact = "단일가 주문은 낼 수 있지만 연속 매매 체결은 아직 시작되지 않았어요.",
            resumeGuidance = "재개 단일가가 끝나면 연속 매매로 돌아가요.",
            ruleExplanation = "1·2단계 서킷브레이커의 20분 거래정지 뒤 10분 재개 단일가를 진행해요.",
            tone = ProtectionUiTone.INFO,
            emphasis = ProtectionBadgeEmphasis.WEAK,
            priority = 74,
            markets = setOf(market),
            endsAt = reopeningEndsAt,
        )

        KrxCircuitBreakerPhase.CLOSED_FOR_DAY -> ProtectionUiStatus(
            id = "market:krx-cb:${market.name}",
            badgeLabel = "$marketName 장 종료",
            title = "$marketName 거래가 오늘 종료됐어요",
            summary = "3단계 서킷브레이커가 발동했어요.",
            orderImpact = "오늘은 새 주문과 체결을 받지 않아요.",
            resumeGuidance = "다음 거래일의 정상 개장 절차를 기다려요.",
            ruleExplanation = "지수가 전일 종가보다 20% 이상 하락한 상태가 1분 이어지면 당일 장을 종료해요.",
            tone = ProtectionUiTone.CRITICAL,
            emphasis = ProtectionBadgeEmphasis.FILL,
            priority = 100,
            markets = setOf(market),
        )
    }
}

private fun KrxSidecarState.toUiStatus(): ProtectionUiStatus? {
    if (phase != KrxSidecarPhase.PROGRAM_FLOW_SUSPENDED) return null
    val sideName = when (suspendedProgramSide) {
        ProgramOrderSide.BUY -> "매수"
        ProgramOrderSide.SELL -> "매도"
        null -> "해당 방향"
    }
    return ProtectionUiStatus(
        id = "market:krx-sidecar:${market.name}",
        badgeLabel = "${market.displayName} 사이드카",
        title = "${market.displayName} 사이드카가 발동했어요",
        summary = "프로그램 $sideName 주문만 5분간 제한돼요.",
        orderImpact = "일반 투자자의 주문과 체결은 그대로 진행돼요.",
        resumeGuidance = "5분이 지나거나 서킷브레이커 거래가 재개되면 프로그램 주문 제한이 풀려요.",
        ruleExplanation = if (market == Market.KOSPI) {
            "KOSPI200 선물이 같은 방향으로 5% 이상 움직인 상태가 1분 이어지면 하루 한 번 발동해요."
        } else {
            "KOSDAQ150 선물 6%와 현물지수 3% 조건이 같은 방향으로 1분 이어지면 하루 한 번 발동해요."
        },
        tone = ProtectionUiTone.CAUTION,
        emphasis = ProtectionBadgeEmphasis.WEAK,
        priority = 52,
        markets = setOf(market),
        endsAt = suspensionEndsAt,
    )
}

private fun UsMwcbState.toUiStatus(): ProtectionUiStatus? {
    val usMarkets = Market.entries.filter(Market::isUnitedStates).toSet()
    return when (phase) {
        UsMwcbPhase.NORMAL -> null
        UsMwcbPhase.HALTED -> ProtectionUiStatus(
            id = "market:us-mwcb",
            badgeLabel = "미국 시장 거래정지",
            title = "미국 시장 거래가 잠시 멈췄어요",
            summary = "15분 뒤 거래소별 재개 경매를 시작해요.",
            orderImpact = "미국 정규장의 새 체결이 시장 전체에서 잠시 멈춰요.",
            resumeGuidance = "15분 거래정지 뒤 주거래소별 재개 경매를 거쳐 순차적으로 열려요.",
            ruleExplanation = "S&P 500이 전일 종가보다 7%·13%·20% 하락하면 미국 시장 전체 보호장치가 단계별로 발동해요.",
            tone = ProtectionUiTone.CRITICAL,
            emphasis = ProtectionBadgeEmphasis.FILL,
            priority = 97,
            markets = usMarkets,
            endsAt = haltEndsAt,
        )

        UsMwcbPhase.REOPENING_AUCTIONS -> ProtectionUiStatus(
            id = "market:us-mwcb",
            badgeLabel = "미국 재개 경매",
            title = "미국 시장이 순차적으로 다시 열리고 있어요",
            summary = "거래소별 재개 경매가 진행 중이에요.",
            orderImpact = "주거래소가 재개된 종목부터 체결되고, 아직 경매 중인 종목은 주문만 모아요.",
            resumeGuidance = "각 거래소의 재개 경매가 끝난 종목부터 연속 매매로 돌아가요.",
            ruleExplanation = "미국 시장 전체 거래정지 뒤 NYSE·Nasdaq 등 주거래소가 각자의 경매 절차로 재개해요.",
            tone = ProtectionUiTone.INFO,
            emphasis = ProtectionBadgeEmphasis.WEAK,
            priority = 76,
            markets = usMarkets,
        )

        UsMwcbPhase.CLOSED_FOR_DAY -> ProtectionUiStatus(
            id = "market:us-mwcb",
            badgeLabel = "미국 시장 장 종료",
            title = "미국 시장 거래가 오늘 종료됐어요",
            summary = "3단계 시장 전체 서킷브레이커가 발동했어요.",
            orderImpact = "오늘 정규장에서는 새 체결이 일어나지 않아요.",
            resumeGuidance = "다음 거래일의 거래소별 개장 절차를 기다려요.",
            ruleExplanation = "S&P 500이 전일 종가보다 20% 하락하면 남은 정규장을 종료해요.",
            tone = ProtectionUiTone.CRITICAL,
            emphasis = ProtectionBadgeEmphasis.FILL,
            priority = 100,
            markets = usMarkets,
        )
    }
}

private fun InstrumentTradingHalt.toUiStatus(): ProtectionUiStatus? {
    if (status != TradingHaltStatus.ACTIVE) return null
    val reasonName = reason.koreanLabel()
    val orderCopy = when {
        policy.acceptsNewOrders && policy.allowsExecution -> "주문과 체결은 허용되지만 연속 매매 여부는 별도 보호장치를 따라요."
        policy.acceptsNewOrders -> "주문은 접수하지만 거래 재개 전까지 체결되지 않아요."
        else -> "새 주문과 체결을 받지 않아요."
    }
    val cancellationCopy = if (policy.allowsCancellation) {
        " 대기 주문은 취소할 수 있어요."
    } else {
        " 대기 주문도 지금은 취소할 수 없어요."
    }
    return ProtectionUiStatus(
        id = "stock:halt:$stockId",
        badgeLabel = "거래정지",
        title = "이 종목은 거래가 멈춰 있어요",
        summary = detail,
        orderImpact = orderCopy + cancellationCopy,
        resumeGuidance = if (scheduledReleaseAt != null) {
            "예정 시각에 해제 조건을 다시 확인해요."
        } else {
            "거래소의 해제 조건이 충족될 때까지 유지돼요."
        },
        ruleExplanation = "사유는 ${reasonName}이에요. 게임은 주문 접수·취소·체결 권한을 각각 적용해요.",
        tone = ProtectionUiTone.CRITICAL,
        emphasis = ProtectionBadgeEmphasis.FILL,
        priority = 94,
        stockId = stockId,
        endsAt = scheduledReleaseAt,
    )
}

private fun KrxViState.toUiStatus(): ProtectionUiStatus? {
    if (phase != KrxViPhase.CALL_AUCTION) return null
    val kindName = if (kind == KrxViKind.DYNAMIC) "동적 VI" else "정적 VI"
    val rate = triggerRate?.times(100.0)?.roundToInt()
    return ProtectionUiStatus(
        id = "stock:krx-vi:$stockId",
        badgeLabel = "VI 단일가",
        title = "이 종목은 VI 단일가 중이에요",
        summary = "2분 동안 주문을 모아 한 가격으로 체결해요.",
        orderImpact = "주문은 낼 수 있지만 연속 매매처럼 즉시 체결되지는 않아요.",
        resumeGuidance = "VI 단일가가 끝나면 연속 매매로 돌아가요.",
        ruleExplanation = if (rate == null) {
            "$kindName 기준가격에서 급하게 벗어날 체결을 멈추고 2분 단일가로 전환해요."
        } else {
            "$kindName 기준가격에서 약 $rate% 벗어날 체결을 멈추고 2분 단일가로 전환해요."
        },
        tone = ProtectionUiTone.INFO,
        emphasis = ProtectionBadgeEmphasis.WEAK,
        priority = 68,
        stockId = stockId,
        markets = setOf(market),
        endsAt = auctionEndsAt,
    )
}

private fun InvestmentAlertDesignation.toUiStatus(marketDate: LocalDate?): ProtectionUiStatus? {
    if (status == InvestmentAlertStatus.RELEASED) {
        val effectiveOn = releaseEffectiveOn ?: return null
        if (marketDate == null || marketDate >= effectiveOn) return null
        return activeAlertUiStatus(
            displayedLevel = level,
            summaryOverride = buildString {
                append("현재는 ${level.label()} 상태예요. ")
                append(effectiveOn.changeLeadFrom(marketDate))
                append(" 지정이 해제돼요.")
            },
            titleOverride = "${level.label()} 상태가 아직 유지돼요",
            resumeOverride = "${effectiveOn.changeLeadFrom(marketDate)} ${level.label()} 지정이 해제돼요.",
            ruleOverride = "해제 판단일은 ${releasedOn?.uiDate() ?: "공시일"}이고, " +
                "화면과 거래 규칙은 ${effectiveOn.uiDate()}부터 바뀌어요.",
        )
    }
    if (status != InvestmentAlertStatus.ACTIVE) return null

    if (marketDate != null && marketDate < designatedOn) {
        val priorLevel = priorLevelUntilEffective
        if (priorLevel == null) return upcomingAlertUiStatus(marketDate)
        val transitionCopy = if (priorLevel == level) {
            "${designatedOn.changeLeadFrom(marketDate)} 같은 단계가 새 지정 기준으로 이어져요."
        } else {
            "${designatedOn.changeLeadFrom(marketDate)} ${level.label()}으로 바뀌어요."
        }
        return activeAlertUiStatus(
            displayedLevel = priorLevel,
            titleOverride = "${priorLevel.label()} 상태가 아직 유지돼요",
            summaryOverride = "현재는 ${priorLevel.label()} 상태예요. $transitionCopy",
            resumeOverride = transitionCopy,
            ruleOverride = "새 지정은 먼저 공시됐지만 ${designatedOn.uiDate()}부터 효력이 생겨요. " +
                "그전까지는 ${priorLevel.label()} 규칙을 적용해요.",
        )
    }
    return activeAlertUiStatus(displayedLevel = level)
}

private fun InvestmentAlertDesignation.activeAlertUiStatus(
    displayedLevel: InvestmentAlertLevel,
    titleOverride: String? = null,
    summaryOverride: String? = null,
    resumeOverride: String? = null,
    ruleOverride: String? = null,
): ProtectionUiStatus {
    val (label, title, summaryCopy, tone, emphasis, priority) = displayedLevel.uiValues()
    return ProtectionUiStatus(
        id = "stock:investment-alert:$stockId",
        badgeLabel = label,
        title = titleOverride ?: title,
        summary = summaryOverride ?: if (isRedesignation) "다시 지정됐어요. $summaryCopy" else summaryCopy,
        orderImpact = "현재 주문은 가능해요. 별도 거래정지가 발동하면 그 제한이 먼저 적용돼요.",
        resumeGuidance = resumeOverride ?: "${releaseReviewWindow.startsOn.uiDate()}부터 해제 요건을 확인해요.",
        ruleExplanation = ruleOverride
            ?: "$summary 지정 사유 코드는 ${reasonCodes.sorted().joinToString(", ")}예요.",
        tone = tone,
        emphasis = emphasis,
        priority = priority,
        stockId = stockId,
    )
}

private fun InvestmentAlertDesignation.upcomingAlertUiStatus(marketDate: LocalDate): ProtectionUiStatus {
    val values = level.uiValues()
    val lead = designatedOn.changeLeadFrom(marketDate)
    return ProtectionUiStatus(
        id = "stock:investment-alert:$stockId",
        badgeLabel = "${values.label} 예정",
        title = "$lead ${values.label} 종목으로 지정돼요",
        summary = "오늘은 지정 전이에요. 가격 변동 가능성을 미리 확인해 주세요.",
        orderImpact = "효력일 전이라 이 투자경보만으로 주문이 제한되지는 않아요.",
        resumeGuidance = "$lead ${values.label} 기준을 적용해요.",
        ruleExplanation = "지정 공시는 먼저 나왔지만 거래소 현지 날짜인 ${designatedOn.uiDate()}부터 효력이 생겨요.",
        tone = ProtectionUiTone.CAUTION,
        emphasis = ProtectionBadgeEmphasis.WEAK,
        priority = 30,
        stockId = stockId,
    )
}

private fun InvestmentAlertLevel.uiValues(): AlertUiValues = when (this) {
    InvestmentAlertLevel.CAUTION -> AlertUiValues(
        label = "투자주의",
        title = "투자주의 종목이에요",
        summary = "최근 거래 흐름을 한 번 더 확인해 주세요.",
        tone = ProtectionUiTone.CAUTION,
        emphasis = ProtectionBadgeEmphasis.WEAK,
        priority = 38,
    )

    InvestmentAlertLevel.WARNING -> AlertUiValues(
        label = "투자경고",
        title = "투자경고 종목이에요",
        summary = "추가 급등 시 하루 동안 거래가 멈출 수 있어요.",
        tone = ProtectionUiTone.CAUTION,
        emphasis = ProtectionBadgeEmphasis.WEAK,
        priority = 56,
    )

    InvestmentAlertLevel.DANGER -> AlertUiValues(
        label = "투자위험",
        title = "투자위험 종목이에요",
        summary = "급격한 가격 변동과 거래정지 가능성이 커요.",
        tone = ProtectionUiTone.CRITICAL,
        emphasis = ProtectionBadgeEmphasis.FILL,
        priority = 82,
    )
}

private fun InvestmentAlertLevel.label(): String = uiValues().label

private fun UsLuldState.toUiStatus(): ProtectionUiStatus? = when (phase) {
    UsLuldPhase.NORMAL -> null
    UsLuldPhase.LIMIT_STATE -> ProtectionUiStatus(
        id = "stock:us-luld:$stockId",
        badgeLabel = "가격제한 상태",
        title = "가격제한선에 닿았어요",
        summary = "15초 안에 호가가 풀리지 않으면 거래가 잠시 멈춰요.",
        orderImpact = "가격 밴드 밖 체결은 막고, 밴드 안의 주문만 처리해요.",
        resumeGuidance = "제한 호가가 모두 체결되거나 취소되면 정상 거래로 돌아가요.",
        ruleExplanation = "미국 LULD 가격 밴드는 기준가격과 종목 티어·시간대에 따라 계산해요.",
        tone = ProtectionUiTone.CAUTION,
        emphasis = ProtectionBadgeEmphasis.WEAK,
        priority = 58,
        stockId = stockId,
        markets = setOf(primaryMarket),
        endsAt = limitStateDeadline,
    )

    UsLuldPhase.TRADING_PAUSE -> ProtectionUiStatus(
        id = "stock:us-luld:$stockId",
        badgeLabel = "LULD 거래정지",
        title = "가격 급변으로 거래가 잠시 멈췄어요",
        summary = "5분 거래정지 뒤 주거래소 경매로 재개해요.",
        orderImpact = "새 체결은 멈추고, 거래소 규칙에 맞는 주문만 재개 경매로 넘겨요.",
        resumeGuidance = "거래정지 뒤 주거래소 재개 경매를 거쳐 다시 거래해요.",
        ruleExplanation = "가격제한 상태가 15초 안에 풀리지 않아 LULD 거래정지가 발동했어요.",
        tone = ProtectionUiTone.CRITICAL,
        emphasis = ProtectionBadgeEmphasis.FILL,
        priority = 90,
        stockId = stockId,
        markets = setOf(primaryMarket),
        endsAt = pauseEndsAt,
    )

    UsLuldPhase.REOPENING_AUCTION -> ProtectionUiStatus(
        id = "stock:us-luld:$stockId",
        badgeLabel = "재개 경매",
        title = "이 종목의 재개 경매가 진행 중이에요",
        summary = "주문을 모아 한 가격으로 거래를 다시 시작해요.",
        orderImpact = "경매 주문은 낼 수 있지만 연속 매매 체결은 아직 시작되지 않았어요.",
        resumeGuidance = "주거래소가 재개 가격을 정하면 연속 매매로 돌아가요.",
        ruleExplanation = "LULD 거래정지 뒤 주거래소가 가격 발견을 위한 재개 경매를 진행해요.",
        tone = ProtectionUiTone.INFO,
        emphasis = ProtectionBadgeEmphasis.WEAK,
        priority = 70,
        stockId = stockId,
        markets = setOf(primaryMarket),
    )

    UsLuldPhase.CLOSING_AUCTION_ONLY -> ProtectionUiStatus(
        id = "stock:us-luld:$stockId",
        badgeLabel = "종가 경매만",
        title = "오늘은 종가 경매로만 마무리해요",
        summary = "연속 매매로 돌아가지 않고 종가 결정 절차를 진행해요.",
        orderImpact = "종가 경매에 들어갈 수 있는 주문만 받아요.",
        resumeGuidance = "종가 경매가 끝나면 오늘 거래가 종료돼요.",
        ruleExplanation = "장 마감과 가까운 LULD 거래정지는 연속 매매 대신 종가 경매로 이어질 수 있어요.",
        tone = ProtectionUiTone.CAUTION,
        emphasis = ProtectionBadgeEmphasis.WEAK,
        priority = 72,
        stockId = stockId,
        markets = setOf(primaryMarket),
    )

    UsLuldPhase.CLOSED_FOR_DAY -> ProtectionUiStatus(
        id = "stock:us-luld:$stockId",
        badgeLabel = "오늘 거래 종료",
        title = "이 종목의 오늘 거래가 종료됐어요",
        summary = "LULD 절차가 장 마감까지 이어졌어요.",
        orderImpact = "오늘 정규장에서는 새 주문과 체결을 받지 않아요.",
        resumeGuidance = "다음 거래일의 주거래소 개장 절차를 기다려요.",
        ruleExplanation = "재개 경매가 장 마감 전에 끝나지 않아 당일 상태를 종료했어요.",
        tone = ProtectionUiTone.CRITICAL,
        emphasis = ProtectionBadgeEmphasis.FILL,
        priority = 95,
        stockId = stockId,
        markets = setOf(primaryMarket),
    )
}

private fun ListingLifecycleState.toUiStatus(): ProtectionUiStatus? {
    val reason = activeReason?.displayName ?: "상품 운영 정책"
    val common = ListingUiValues(
        badgeLabel = "상장 상태",
        title = "상장 상태를 확인하고 있어요",
        summary = "$reason 관련 상태를 확인 중이에요.",
        orderImpact = "현재 주문 가능 여부는 거래소 상태와 함께 판단해요.",
        resumeGuidance = "다음 심사 결과를 기다려요.",
        ruleExplanation = "상장 유지 심사는 가격뿐 아니라 공시·재무·유동성 조건을 함께 반영해요.",
        tone = ProtectionUiTone.CAUTION,
        emphasis = ProtectionBadgeEmphasis.WEAK,
        priority = 50,
    )
    val values = when (status) {
        ListingLifecycleStatus.LISTED -> return null
        ListingLifecycleStatus.DEFICIENCY_NOTICE -> common.copy(
            badgeLabel = "상장 주의",
            title = "상장 유지 요건을 확인하고 있어요",
            summary = "${reason}에 대한 개선 기간이에요.",
            orderImpact = "현재 주문과 체결은 가능해요.",
            resumeGuidance = cureDeadline?.let { "${it.uiDate()}까지 개선 여부를 확인해요." }
                ?: "개선 기간이 끝날 때 요건을 다시 확인해요.",
            priority = 50,
        )

        ListingLifecycleStatus.UNDER_REVIEW -> common.copy(
            badgeLabel = "상장 심사",
            title = "상장 적격성을 심사하고 있어요",
            summary = "$reason 때문에 거래소 심사가 진행 중이에요.",
            orderImpact = if (isOrderAllowed) {
                "현재 주문은 가능하지만 심사 결과에 따라 거래가 멈출 수 있어요."
            } else {
                "심사가 끝날 때까지 새 주문과 체결을 받지 않아요."
            },
            resumeGuidance = reviewDeadline?.let { "${it.uiDate()}까지 심사 결과를 확인해요." }
                ?: "거래소 심사 결과를 기다려요.",
            tone = if (isOrderAllowed) ProtectionUiTone.CAUTION else ProtectionUiTone.CRITICAL,
            emphasis = if (isOrderAllowed) ProtectionBadgeEmphasis.WEAK else ProtectionBadgeEmphasis.FILL,
            priority = if (isOrderAllowed) 64 else 86,
        )

        ListingLifecycleStatus.TRADING_SUSPENDED -> common.copy(
            badgeLabel = "상장 심사 정지",
            title = "상장 심사로 거래가 멈춰 있어요",
            summary = "$reason 관련 확인이 끝날 때까지 기다려 주세요.",
            orderImpact = "새 주문과 체결을 받지 않아요.",
            resumeGuidance = reviewDeadline?.let { "${it.uiDate()}까지 심사 결과를 확인해요." }
                ?: "거래소의 재개 또는 상장폐지 결정을 기다려요.",
            tone = ProtectionUiTone.CRITICAL,
            emphasis = ProtectionBadgeEmphasis.FILL,
            priority = 91,
        )

        ListingLifecycleStatus.DELISTING_SCHEDULED -> common.copy(
            badgeLabel = "상장폐지 예정",
            title = "상장폐지가 예정돼 있어요",
            summary = "${reason}에 따른 상장 종료 절차가 진행 중이에요.",
            orderImpact = if (isOrderAllowed) {
                "정리매매 기간에는 주문할 수 있지만 가격 급변과 미체결 위험이 커요."
            } else {
                "새 주문과 체결을 받지 않아요."
            },
            resumeGuidance = scheduledDelistingOn?.let { "${it.uiDate()}에 상장이 종료될 예정이에요." }
                ?: "거래소가 공지한 상장 종료 절차를 따라요.",
            tone = ProtectionUiTone.CRITICAL,
            emphasis = ProtectionBadgeEmphasis.FILL,
            priority = 97,
        )

        ListingLifecycleStatus.LIQUIDATION_PENDING -> common.copy(
            badgeLabel = "청산금 지급 대기",
            title = "상품 청산금 지급을 기다리고 있어요",
            summary = "거래는 끝났고 보유 수량의 현금 정산이 진행 중이에요.",
            orderImpact = "이 상품에는 더 이상 주문할 수 없어요.",
            resumeGuidance = settlementDueOn?.let { "${it.uiDate()}에 청산금이 반영될 예정이에요." }
                ?: "정산 처리가 끝나면 현금 잔고에 반영돼요.",
            tone = ProtectionUiTone.CRITICAL,
            emphasis = ProtectionBadgeEmphasis.FILL,
            priority = 98,
        )

        ListingLifecycleStatus.DELISTED -> common.copy(
            badgeLabel = "상장폐지",
            title = "이 종목은 상장폐지됐어요",
            summary = finalDisposition.dispositionCopy(),
            orderImpact = "거래소에서는 더 이상 주문하거나 체결할 수 없어요.",
            resumeGuidance = "이 상태는 거래소에서 다시 거래되는 재개 상태가 아니에요.",
            tone = ProtectionUiTone.CRITICAL,
            emphasis = ProtectionBadgeEmphasis.FILL,
            priority = 100,
        )

        ListingLifecycleStatus.TERMINATED -> common.copy(
            badgeLabel = "상품 종료",
            title = "이 상품의 운용이 종료됐어요",
            summary = finalDisposition.dispositionCopy(),
            orderImpact = "이 상품에는 더 이상 주문할 수 없어요.",
            resumeGuidance = "만기·청산 결과가 잔고에 최종 반영돼요.",
            tone = ProtectionUiTone.CRITICAL,
            emphasis = ProtectionBadgeEmphasis.FILL,
            priority = 100,
        )
    }
    return ProtectionUiStatus(
        id = "stock:listing:$stockId",
        badgeLabel = values.badgeLabel,
        title = values.title,
        summary = values.summary,
        orderImpact = values.orderImpact,
        resumeGuidance = values.resumeGuidance,
        ruleExplanation = values.ruleExplanation,
        tone = values.tone,
        emphasis = values.emphasis,
        priority = values.priority,
        stockId = stockId,
        markets = setOf(market),
    )
}

private fun TradingHaltReason.koreanLabel(): String = when (this) {
    TradingHaltReason.MATERIAL_DISCLOSURE -> "중요 공시 확인"
    TradingHaltReason.DISCLOSURE_INQUIRY -> "조회공시 확인"
    TradingHaltReason.LISTING_MAINTENANCE_REVIEW -> "상장 유지 심사"
    TradingHaltReason.DELISTING_PROCESS -> "상장폐지 절차"
    TradingHaltReason.INVESTOR_PROTECTION -> "투자자 보호"
    TradingHaltReason.SETTLEMENT_FAILURE -> "결제 불이행"
    TradingHaltReason.CORPORATE_ACTION -> "기업행위 처리"
    TradingHaltReason.TECHNICAL_DISRUPTION -> "거래 시스템 장애"
    TradingHaltReason.REGULATORY_ACTION -> "규제기관 조치"
    TradingHaltReason.OTHER -> "기타 거래소 조치"
}

private fun com.amond.kmpbook.domain.model.listing.lifecycle.ListingFinalDisposition?.dispositionCopy(): String = when (this?.type) {
    ListingFinalDispositionType.CASH_LIQUIDATION -> "보유 수량은 현금 청산 절차로 정리돼요."
    ListingFinalDispositionType.WORTHLESS_DISPOSITION -> "회수 금액 없이 무가치 처분으로 정리됐어요."
    ListingFinalDispositionType.OTC_TRANSFER -> "거래소 상장은 끝났고 권리는 장외시장으로 이전됐어요."
    ListingFinalDispositionType.MARKET_SALE -> "시장 매도로 보유 수량이 정리됐어요."
    null -> "상장 종료 처리가 완료됐어요."
}

private fun List<ProtectionUiStatus>.sortedForDisplay(): List<ProtectionUiStatus> =
    sortedWith(compareByDescending<ProtectionUiStatus> { it.priority }.thenBy { it.id })

private fun List<ProtectionUiStatus>.toDetailOrNull(contextLabel: String): ProtectionDetailUi? =
    firstOrNull()?.let { primary ->
        ProtectionDetailUi(
            contextLabel = contextLabel,
            primary = primary,
            additional = drop(1),
        )
    }

internal fun List<ProtectionUiStatus>.toBadge(): ProtectionStatusBadgeUi {
    require(isNotEmpty())
    val sorted = sortedForDisplay()
    val primary = sorted.first()
    val additionalCount = sorted.size - 1
    return ProtectionStatusBadgeUi(
        text = buildString {
            append(primary.badgeLabel)
            if (additionalCount > 0) append(" 외 $additionalCount")
        },
        tone = primary.tone,
        emphasis = primary.emphasis,
        additionalCount = additionalCount,
        stateDescription = sorted.joinToString(". ") { "${it.badgeLabel}: ${it.summary}" },
    )
}

private fun ProtectionDetailUi.accessibilityDescription(): String = buildString {
    append(primary.title)
    append(". ")
    append(primary.summary)
    if (additionalCount > 0) append(". 함께 적용 중인 상태 ${additionalCount}개")
}

private fun String.marketFromStableId(): Market? {
    val marketName = substringBefore(':', missingDelimiterValue = "")
    return Market.entries.firstOrNull { it.name == marketName }
}

private fun kotlinx.datetime.LocalDate.uiDate(): String = toString().replace('-', '.')

private fun LocalDate.changeLeadFrom(currentMarketDate: LocalDate): String =
    if (currentMarketDate.plus(1, DateTimeUnit.DAY) == this) "내일부터" else "${uiDate()}부터"
