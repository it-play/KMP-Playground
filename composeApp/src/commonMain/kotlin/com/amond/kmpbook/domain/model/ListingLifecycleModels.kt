package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

/** 거래소 상장 유지 심사부터 최종 처분까지 저장되는 종목 상태. */
enum class ListingLifecycleStatus(val displayName: String) {
    LISTED("정상 상장"),
    DEFICIENCY_NOTICE("상장 유지 요건 미달"),
    UNDER_REVIEW("상장 적격성 심사"),
    TRADING_SUSPENDED("거래정지"),
    DELISTING_SCHEDULED("상장폐지 예정"),
    LIQUIDATION_PENDING("청산금 지급 대기"),
    DELISTED("상장폐지"),
    TERMINATED("상품 종료"),
}

/**
 * 공개 규칙을 그대로 적용할 수 있는지, 데이터가 없어 게임 근사를 쓰는지를 구분한다.
 * 재무제표·유통주식수·감사의견 원문이 필요한 기준은 반드시 [GAME_APPROXIMATION]을 사용한다.
 */
enum class ListingRuleBasis(val displayName: String) {
    OFFICIAL_PUBLIC_RULE_SUMMARY("공식 공개 규칙 요약"),
    HYBRID_PUBLIC_RULE_AND_GAME_APPROXIMATION("공식 규칙·게임 근사 혼합"),
    GAME_APPROXIMATION("게임 근사"),
}

/** 종목·시장별 정책팩 ID. 저장 파일에는 정책 객체 대신 이 안정적인 ID를 기록한다. */
enum class ListingLifecycleProfileId {
    KRX_EQUITY_GAME_APPROXIMATION,
    KRX_ETF_GAME_APPROXIMATION,
    KRX_ETN_GAME_APPROXIMATION,
    NASDAQ_EQUITY_PUBLIC_RULE_WITH_GAME_APPROXIMATION,
    US_EQUITY_GAME_APPROXIMATION,
    US_FUND_GAME_APPROXIMATION,
    US_ETN_GAME_APPROXIMATION,
}

/** 상장 유지 조치의 직접 원인. */
enum class ListingLifecycleReason(val displayName: String) {
    KRX_LISTING_MAINTENANCE("KRX 상장 유지 요건"),
    KRX_ADMINISTRATIVE_ISSUE("KRX 관리종목 사유"),
    US_LISTING_MAINTENANCE("미국 거래소 상장 유지 요건"),
    US_MINIMUM_BID_PRICE("미국 최저 호가 요건"),
    US_MARKET_CAPITALIZATION("미국 시가총액 요건"),
    LOW_TRADING_LIQUIDITY("거래 유동성 부족"),
    AUDIT_OR_DISCLOSURE_FAILURE("감사·공시 의무 위반"),
    SERIOUS_COMPLIANCE_EVENT("중대한 규정 준수 사건"),
    BANKRUPTCY_OR_INSOLVENCY("파산·지급불능"),
    CORE_BUSINESS_SUSPENSION("주요 영업 정지"),
    ETF_VOLUNTARY_LIQUIDATION("ETF·펀드 자진 청산"),
    ETN_MATURITY_OR_EARLY_REDEMPTION("ETN 만기·조기상환"),
    ISSUER_ELIGIBILITY_FAILURE("발행사 자격·신용 문제"),
    UNDERLYING_INDEX_UNAVAILABLE("기초지수 산출 중단"),
    LIQUIDITY_PROVIDER_FAILURE("유동성공급자 부재"),
}

/** 일별 감시 입력에 붙는 구조화된 위험 신호. */
enum class ListingRiskTag {
    LISTING_MAINTENANCE_DEFICIENCY,
    ADMINISTRATIVE_ISSUE,
    LOW_BID_PRICE,
    LOW_MARKET_CAPITALIZATION,
    LOW_TRADING_LIQUIDITY,
    AUDIT_OPINION_FAILURE,
    DISCLOSURE_VIOLATION,
    SERIOUS_COMPLIANCE_EVENT,
    BANKRUPTCY_OR_INSOLVENCY,
    CORE_BUSINESS_SUSPENSION,
    QUALITATIVE_LISTING_REVIEW,
    ETF_LIQUIDATION_APPROVED,
    ETN_MATURITY_OR_EARLY_REDEMPTION,
    ISSUER_ELIGIBILITY_FAILURE,
    UNDERLYING_INDEX_UNAVAILABLE,
    LIQUIDITY_PROVIDER_FAILURE,
}

enum class ListingRiskSeverity(val level: Int) {
    NONE(0),
    LOW(1),
    MODERATE(2),
    HIGH(3),
    CRITICAL(4),
}

/** 정량 가격만으로 확인할 수 없는 개선 사실을 게임 이벤트가 명시한다. */
enum class ListingRecoveryCondition {
    BID_PRICE_RESTORED,
    MARKET_CAPITALIZATION_RESTORED,
    LIQUIDITY_RESTORED,
    FINANCIAL_DEFICIENCY_RESOLVED,
    AUDIT_OR_DISCLOSURE_CURED,
    REGULATORY_CLEARANCE,
    BUSINESS_RESUMED,
    ISSUER_ELIGIBILITY_RESTORED,
    UNDERLYING_INDEX_RESTORED,
    LIQUIDITY_PROVIDER_REPLACED,
}

/** 보유 잔고를 상장 종료 시 어떤 경로로 처리해야 하는지 알려준다. */
enum class ListingFinalDispositionType(val displayName: String) {
    /** 상장폐지 전 정리매매·정규 매매에서 보유자가 매도한 경우. */
    MARKET_SALE("시장 매도"),

    /** ETF 청산·ETN 만기처럼 기준가 또는 약정가로 현금을 지급하는 경우. */
    CASH_LIQUIDATION("현금 청산"),

    /** 파산 등으로 회수 가능액이 0인 게임상의 무가치 처분. */
    WORTHLESS_DISPOSITION("무가치 처분"),

    /** 미국 거래소 상장만 종료되고 장외시장으로 권리가 이전되는 경우. */
    OTC_TRANSFER("장외시장 이전"),
}

data class ListingFinalDisposition(
    val type: ListingFinalDispositionType,
    val effectiveOn: LocalDate,
    val settlementDueOn: LocalDate? = null,
    val cashPerUnit: Double? = null,
    /** 청산 효력일에 확정된 권리 수량·취득원가. 이후 분할·배당과 분리해 지급한다. */
    val entitledQuantity: Double? = null,
    val entitledCostBasis: Double? = null,
) {
    init {
        require(cashPerUnit == null || cashPerUnit >= 0.0 && cashPerUnit.isFinite()) {
            "청산 단가는 유한한 0 이상 값이어야 합니다."
        }
        require(type == ListingFinalDispositionType.CASH_LIQUIDATION || settlementDueOn == null) {
            "현금 청산 외 처분에는 지급 예정일을 둘 수 없습니다."
        }
        require(type == ListingFinalDispositionType.CASH_LIQUIDATION || cashPerUnit == null || cashPerUnit == 0.0) {
            "시장 매도·장외 이전에는 청산 단가를 둘 수 없습니다."
        }
        if (type == ListingFinalDispositionType.CASH_LIQUIDATION) {
            require(settlementDueOn != null && cashPerUnit != null) {
                "현금 청산에는 지급일과 확정 단가가 필요합니다."
            }
        }
        require(entitledQuantity == null || entitledQuantity >= 0.0 && entitledQuantity.isFinite())
        require(entitledCostBasis == null || entitledCostBasis >= 0.0 && entitledCostBasis.isFinite())
        require(type == ListingFinalDispositionType.CASH_LIQUIDATION ||
            entitledQuantity == null && entitledCostBasis == null)
        require(settlementDueOn == null || settlementDueOn >= effectiveOn) {
            "청산금 지급일은 처분 효력일보다 빠를 수 없습니다."
        }
    }
}

/** 뉴스와 종목 상세 화면이 공통으로 소비하는 상장 상태 원장 이벤트. */
enum class ListingLifecycleEventKind {
    DEFICIENCY_DESIGNATED,
    DEFICIENCY_REDESIGNATED,
    REVIEW_STARTED,
    TRADING_SUSPENDED,
    DEFICIENCY_CURED,
    TRADING_RESUMED,
    DELISTING_SCHEDULED,
    LIQUIDATION_STARTED,
    DELISTED,
    TERMINATED,
}

enum class ListingNoticeLevel {
    INFO,
    CAUTION,
    WARNING,
    CRITICAL,
}

data class ListingLifecycleLedgerEvent(
    val id: String,
    val sequence: Long,
    val stockId: String,
    val tradingDate: LocalDate,
    val kind: ListingLifecycleEventKind,
    val fromStatus: ListingLifecycleStatus,
    val toStatus: ListingLifecycleStatus,
    val reason: ListingLifecycleReason?,
    val level: ListingNoticeLevel,
    val title: String,
    val summary: String,
    val deadline: LocalDate? = null,
    val disposition: ListingFinalDisposition? = null,
    val sourceUrls: List<String> = emptyList(),
) {
    init {
        require(id.isNotBlank() && stockId.isNotBlank())
        require(sequence > 0L)
        require(title.isNotBlank() && summary.isNotBlank())
        require(sourceUrls.distinct().size == sourceUrls.size)
        require(sourceUrls.all { it.startsWith("https://") })
    }
}

/**
 * 하루 한 번 공급하는 감시 스냅샷. 정량 자료가 없는 사유는 [riskTags]와
 * [recoveryConditions]로 명시해, 숨은 난수나 시스템 시계 없이 재생할 수 있게 한다.
 */
data class DailyListingSurveillanceInput(
    val stockId: String,
    val tradingDate: LocalDate,
    val close: Double? = null,
    val marketCapitalization: Double? = null,
    val tradedVolume: Long? = null,
    /** 0.01은 하루 유통주식의 1%가 거래됐다는 뜻이다. */
    val turnoverRate: Double? = null,
    val riskTags: Set<ListingRiskTag> = emptySet(),
    val riskSeverity: ListingRiskSeverity = ListingRiskSeverity.NONE,
    val recoveryConditions: Set<ListingRecoveryCondition> = emptySet(),
    /** 실제 공시 일정이 있는 캠페인 이벤트가 정책 기본 일정을 덮어쓸 때 사용한다. */
    val scheduledDelistingOn: LocalDate? = null,
    val scheduledSettlementOn: LocalDate? = null,
    val finalDispositionHint: ListingFinalDispositionType? = null,
    val otcTransferAvailable: Boolean = false,
    val liquidationCashPerUnit: Double? = null,
) {
    init {
        require(stockId.isNotBlank())
        require(close == null || close >= 0.0 && close.isFinite())
        require(marketCapitalization == null || marketCapitalization >= 0.0 && marketCapitalization.isFinite())
        require(tradedVolume == null || tradedVolume >= 0L)
        require(turnoverRate == null || turnoverRate >= 0.0 && turnoverRate.isFinite())
        require(scheduledDelistingOn == null || scheduledDelistingOn >= tradingDate)
        require(scheduledSettlementOn == null || scheduledSettlementOn >= tradingDate)
        require(liquidationCashPerUnit == null || liquidationCashPerUnit >= 0.0 && liquidationCashPerUnit.isFinite())
    }
}

/** 직렬화 가능한 상장 생명주기 스냅샷. */
data class ListingLifecycleState(
    val stockId: String,
    val market: Market,
    val instrumentType: InstrumentType,
    val profileId: ListingLifecycleProfileId,
    val status: ListingLifecycleStatus = ListingLifecycleStatus.LISTED,
    val activeReason: ListingLifecycleReason? = null,
    val designatedOn: LocalDate? = null,
    val cureDeadline: LocalDate? = null,
    val reviewDeadline: LocalDate? = null,
    val scheduledDelistingOn: LocalDate? = null,
    val settlementDueOn: LocalDate? = null,
    /** 공시 후 매매 가능한 정리 기간인지, 기존 정지가 계속되는지를 구분한다. */
    val tradingAllowedUntilDelisting: Boolean = true,
    val consecutiveLowBidTradingDays: Int = 0,
    val consecutiveLowMarketCapTradingDays: Int = 0,
    val consecutiveLowLiquidityTradingDays: Int = 0,
    val consecutiveCureTradingDays: Int = 0,
    /** 해제 뒤 같은 사유가 다시 발생해도 이전 이력을 잃지 않는다. */
    val designationCount: Int = 0,
    val finalDisposition: ListingFinalDisposition? = null,
    val lastEvaluatedTradingDate: LocalDate? = null,
    val ledgerSequence: Long = 0L,
) {
    init {
        require(stockId.isNotBlank())
        require(
            consecutiveLowBidTradingDays >= 0 &&
                consecutiveLowMarketCapTradingDays >= 0 &&
                consecutiveLowLiquidityTradingDays >= 0 &&
                consecutiveCureTradingDays >= 0 &&
                designationCount >= 0 &&
                ledgerSequence >= 0L,
        )
        require(
            status in setOf(ListingLifecycleStatus.LISTED, ListingLifecycleStatus.DELISTED, ListingLifecycleStatus.TERMINATED) ||
                activeReason != null,
        ) { "진행 중인 상장 조치에는 사유가 필요합니다." }
        require(status != ListingLifecycleStatus.DELISTING_SCHEDULED || scheduledDelistingOn != null)
        require(status != ListingLifecycleStatus.LIQUIDATION_PENDING || settlementDueOn != null)
        require(
            status !in setOf(ListingLifecycleStatus.DELISTED, ListingLifecycleStatus.TERMINATED) ||
                finalDisposition != null,
        ) { "최종 상장 상태에는 잔고 처분 방식이 필요합니다." }
    }

    /** 상장 상태만 본 체결 가능성. 장 운영시간·VI·서킷브레이커는 별도로 AND 결합한다. */
    val isTradable: Boolean
        get() = when (status) {
            ListingLifecycleStatus.LISTED,
            ListingLifecycleStatus.DEFICIENCY_NOTICE,
            -> true

            ListingLifecycleStatus.DELISTING_SCHEDULED -> tradingAllowedUntilDelisting

            /** 미국 개선·심사 기간은 거래를 유지할 수 있지만 KRX 적격성 심사는 정지로 모델링한다. */
            ListingLifecycleStatus.UNDER_REVIEW -> market.isUnitedStates
            ListingLifecycleStatus.TRADING_SUSPENDED,
            ListingLifecycleStatus.LIQUIDATION_PENDING,
            ListingLifecycleStatus.DELISTED,
            ListingLifecycleStatus.TERMINATED,
            -> false
        }

    val isOrderAllowed: Boolean get() = isTradable

    /**
     * 거래정지와 지수 편출은 별도 조치다. 별도 지수 리밸런싱 일자가 없는 게임 데이터에서는
     * 상장 종료 효력일까지 마지막 체결가로 구성종목에 남기고, 청산·최종 종료 시점에만 제외한다.
     */
    val isIndexEligible: Boolean
        get() = status !in setOf(
            ListingLifecycleStatus.LIQUIDATION_PENDING,
            ListingLifecycleStatus.DELISTED,
            ListingLifecycleStatus.TERMINATED,
        )

    val isSettlementPending: Boolean get() = status == ListingLifecycleStatus.LIQUIDATION_PENDING
    val isTerminal: Boolean
        get() = status == ListingLifecycleStatus.DELISTED || status == ListingLifecycleStatus.TERMINATED
}

data class ListingLifecycleEvaluation(
    val state: ListingLifecycleState,
    val ledgerEvents: List<ListingLifecycleLedgerEvent>,
)

data class ListingLifecycleReplayResult(
    val state: ListingLifecycleState,
    val ledgerEvents: List<ListingLifecycleLedgerEvent>,
)
