package com.amond.kmpbook.domain.simulation.listing

import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleProfileId
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRuleBasis
import com.amond.kmpbook.domain.model.market.Market
import kotlinx.datetime.plus

/** 공식 규정 출처와 게임에서 실행할 보수적인 근사를 한곳에 둔다. */
object ListingLifecyclePolicyCatalog {
    private const val KRX_TRADING_GUIDE =
        "https://global.krx.co.kr/contents/GLB/01/0109/0109000000/guide_to_trading_in_the_korean_stock_market.pdf"
    private const val KRX_KOSDAQ_DELISTING =
        "https://global.krx.co.kr/contents/GLB/03/0303/0303060600/GLB0303060600.jsp"
    private const val KRX_ETN_DELISTING =
        "https://global.krx.co.kr/contents/GLB/03/0303/0303100300/GLB0303100300.jsp"
    private const val NASDAQ_MINIMUM_BID_FAQ =
        "https://listingcenter.nasdaq.com/Material_search.aspx?cid=14&criteria=2&materials=354&mcd=LQ"
    private const val NYSE_CONTINUED_LISTING = "https://www.nyse.com/regulation/continued-listing"
    private const val CBOE_SUSPENSIONS_AND_DELISTINGS =
        "https://www.cboe.com/us/equities/listings/listed_products/suspensions_delistings/"
    private const val SEC_FUND_LIQUIDATION_EXAMPLE =
        "https://www.sec.gov/Archives/edgar/data/1928561/000121390026050902/ea0288763-01_497.htm"

    private val krxMarkets = setOf(Market.KOSPI, Market.KOSDAQ)
    private val usMarkets = Market.entries.filterTo(linkedSetOf(), Market::isUnitedStates)
    private val companyTypes = setOf(InstrumentType.STOCK, InstrumentType.REIT, InstrumentType.ADR)

    val all: Map<ListingLifecycleProfileId, ListingLifecyclePolicyProfile> = listOf(
        ListingLifecyclePolicyProfile(
            id = ListingLifecycleProfileId.KRX_EQUITY_GAME_APPROXIMATION,
            ruleBasis = ListingRuleBasis.GAME_APPROXIMATION,
            applicableMarkets = krxMarkets,
            applicableInstrumentTypes = companyTypes + InstrumentType.CLOSED_END_FUND,
            minimumTurnoverRate = 0.0001,
            liquidityDeficiencyTradingDays = 20,
            curePeriodCalendarDays = 30,
            reviewPeriodCalendarDays = 10,
            delistingNoticeCalendarDays = 5,
            liquidationSettlementCalendarDays = 5,
            officialSourceUrls = listOf(KRX_TRADING_GUIDE, KRX_KOSDAQ_DELISTING),
            gameApproximationExplanation =
                "재무제표, 감사의견 원문, 유통주식수와 분기 평균 거래량이 없어 위험 태그와 20일 회전율로 관리종목·심사를 근사합니다.",
        ),
        ListingLifecyclePolicyProfile(
            id = ListingLifecycleProfileId.KRX_ETF_GAME_APPROXIMATION,
            ruleBasis = ListingRuleBasis.GAME_APPROXIMATION,
            applicableMarkets = krxMarkets,
            applicableInstrumentTypes = setOf(InstrumentType.ETF),
            minimumTurnoverRate = 0.0001,
            liquidityDeficiencyTradingDays = 20,
            curePeriodCalendarDays = 30,
            reviewPeriodCalendarDays = 10,
            delistingNoticeCalendarDays = 5,
            liquidationSettlementCalendarDays = 5,
            officialSourceUrls = listOf(KRX_TRADING_GUIDE),
            gameApproximationExplanation =
                "신탁원본액, 순자산, 추적오차와 LP 호가 원자료가 없어 이벤트 태그와 거래 회전율로 ETF 유지 요건을 근사합니다.",
        ),
        ListingLifecyclePolicyProfile(
            id = ListingLifecycleProfileId.KRX_ETN_GAME_APPROXIMATION,
            ruleBasis = ListingRuleBasis.HYBRID_PUBLIC_RULE_AND_GAME_APPROXIMATION,
            applicableMarkets = krxMarkets,
            applicableInstrumentTypes = setOf(InstrumentType.ETN),
            minimumTurnoverRate = 0.0001,
            liquidityDeficiencyTradingDays = 20,
            curePeriodCalendarDays = 30,
            reviewPeriodCalendarDays = 10,
            delistingNoticeCalendarDays = 5,
            liquidationSettlementCalendarDays = 5,
            officialSourceUrls = listOf(KRX_TRADING_GUIDE, KRX_ETN_DELISTING),
            gameApproximationExplanation =
                "공식 ETN 사유 유형은 유지하되 발행사 자격, 지표가치와 LP 교체 여부는 캠페인 위험·회복 태그로 공급합니다.",
        ),
        ListingLifecyclePolicyProfile(
            id = ListingLifecycleProfileId.NASDAQ_EQUITY_PUBLIC_RULE_WITH_GAME_APPROXIMATION,
            ruleBasis = ListingRuleBasis.HYBRID_PUBLIC_RULE_AND_GAME_APPROXIMATION,
            applicableMarkets = setOf(Market.NASDAQ),
            applicableInstrumentTypes = companyTypes,
            minimumBidPrice = 1.0,
            bidDeficiencyTradingDays = 30,
            bidCureTradingDays = 10,
            minimumMarketCapitalization = 15_000_000.0,
            marketCapDeficiencyTradingDays = 30,
            curePeriodCalendarDays = 180,
            reviewPeriodCalendarDays = 10,
            delistingNoticeCalendarDays = 7,
            liquidationSettlementCalendarDays = 5,
            officialSourceUrls = listOf(NASDAQ_MINIMUM_BID_FAQ),
            gameApproximationExplanation =
                "최저 호가의 30일·180일·통상 10일 회복 규칙은 공개 기준을 따르며, 상장 티어를 알 수 없는 시가총액은 1,500만 달러 게임 기준으로 근사합니다.",
        ),
        ListingLifecyclePolicyProfile(
            id = ListingLifecycleProfileId.US_EQUITY_GAME_APPROXIMATION,
            ruleBasis = ListingRuleBasis.GAME_APPROXIMATION,
            applicableMarkets = usMarkets - Market.NASDAQ,
            applicableInstrumentTypes = companyTypes,
            minimumBidPrice = 1.0,
            bidDeficiencyTradingDays = 30,
            bidCureTradingDays = 10,
            minimumMarketCapitalization = 15_000_000.0,
            marketCapDeficiencyTradingDays = 30,
            curePeriodCalendarDays = 180,
            reviewPeriodCalendarDays = 10,
            delistingNoticeCalendarDays = 7,
            liquidationSettlementCalendarDays = 5,
            officialSourceUrls = listOf(NYSE_CONTINUED_LISTING, CBOE_SUSPENSIONS_AND_DELISTINGS),
            gameApproximationExplanation =
                "NYSE·NYSE American·Arca·Cboe의 종목별 상장 구획과 재무자료를 알 수 없어 30일 가격·시가총액 및 180일 개선기간으로 통합 근사합니다.",
        ),
        ListingLifecyclePolicyProfile(
            id = ListingLifecycleProfileId.US_FUND_GAME_APPROXIMATION,
            ruleBasis = ListingRuleBasis.GAME_APPROXIMATION,
            applicableMarkets = usMarkets,
            applicableInstrumentTypes = setOf(InstrumentType.ETF, InstrumentType.CLOSED_END_FUND),
            minimumTurnoverRate = 0.00005,
            liquidityDeficiencyTradingDays = 20,
            curePeriodCalendarDays = 30,
            reviewPeriodCalendarDays = 5,
            delistingNoticeCalendarDays = 5,
            liquidationSettlementCalendarDays = 7,
            officialSourceUrls = listOf(CBOE_SUSPENSIONS_AND_DELISTINGS, SEC_FUND_LIQUIDATION_EXAMPLE),
            gameApproximationExplanation =
                "거래소별 ETP 순자산·보유자 수·마켓메이커 요건 대신 위험 태그를 사용하고, 공시가 없을 때만 5일 거래기간과 7일 현금지급 기간을 적용합니다.",
        ),
        ListingLifecyclePolicyProfile(
            id = ListingLifecycleProfileId.US_ETN_GAME_APPROXIMATION,
            ruleBasis = ListingRuleBasis.GAME_APPROXIMATION,
            applicableMarkets = usMarkets,
            applicableInstrumentTypes = setOf(InstrumentType.ETN),
            minimumTurnoverRate = 0.00005,
            liquidityDeficiencyTradingDays = 20,
            curePeriodCalendarDays = 30,
            reviewPeriodCalendarDays = 5,
            delistingNoticeCalendarDays = 5,
            liquidationSettlementCalendarDays = 7,
            officialSourceUrls = listOf(CBOE_SUSPENSIONS_AND_DELISTINGS),
            gameApproximationExplanation =
                "투자설명서별 만기·조기상환·발행사 사건 조건을 일반화하며 실제 캠페인 공시일이 있으면 입력의 일정을 우선합니다.",
        ),
    ).associateBy(ListingLifecyclePolicyProfile::id)

    init {
        require(all.keys == ListingLifecycleProfileId.entries.toSet())
    }

    operator fun get(id: ListingLifecycleProfileId): ListingLifecyclePolicyProfile = requireNotNull(all[id])

    fun profileIdFor(market: Market, instrumentType: InstrumentType): ListingLifecycleProfileId = when {
        market.isKorean && instrumentType == InstrumentType.ETF ->
            ListingLifecycleProfileId.KRX_ETF_GAME_APPROXIMATION
        market.isKorean && instrumentType == InstrumentType.ETN ->
            ListingLifecycleProfileId.KRX_ETN_GAME_APPROXIMATION
        market.isKorean -> ListingLifecycleProfileId.KRX_EQUITY_GAME_APPROXIMATION
        instrumentType == InstrumentType.ETN -> ListingLifecycleProfileId.US_ETN_GAME_APPROXIMATION
        instrumentType in setOf(InstrumentType.ETF, InstrumentType.CLOSED_END_FUND) ->
            ListingLifecycleProfileId.US_FUND_GAME_APPROXIMATION
        market == Market.NASDAQ ->
            ListingLifecycleProfileId.NASDAQ_EQUITY_PUBLIC_RULE_WITH_GAME_APPROXIMATION
        else -> ListingLifecycleProfileId.US_EQUITY_GAME_APPROXIMATION
    }
}
