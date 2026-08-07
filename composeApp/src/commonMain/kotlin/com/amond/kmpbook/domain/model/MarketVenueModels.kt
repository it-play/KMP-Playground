package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalTime

/**
 * A venue's dominant matching/liquidity model as represented by the simulator.
 *
 * Real U.S. securities can execute across several venues. These values describe the
 * primary listing venue used by the game and are not a promise about every execution.
 */
enum class MarketVenueModel(val displayName: String) {
    KRX_CENTRAL_LIMIT_ORDER_BOOK("KRX 중앙 지정가 주문장"),
    NASDAQ_ELECTRONIC_MARKET_MAKERS("전자식 마켓메이커 시장"),
    NYSE_HYBRID_DMM_AUCTION("DMM 기반 하이브리드 경매"),
    NYSE_ARCA_ELECTRONIC_LMM("전자식 주문장·ETF LMM"),
    CBOE_BZX_ELECTRONIC_MARKET_MAKERS("Cboe 전자식 주문장·마켓메이커"),
    NYSE_AMERICAN_HYBRID_AUCTION("소형주 중심 하이브리드 경매"),
}

/**
 * Stable game parameters for a primary listing venue.
 *
 * [spreadMultiplier] and [depthMultiplier] are explicit simulation assumptions, not
 * exchange-published constants. Security size, volatility, volume and stress remain the
 * primary inputs; these multipliers add a smaller venue-level tendency. Session times are
 * exchange-local (America/New_York for U.S. venues).
 */
data class MarketVenueProfile(
    val market: Market,
    val marketModel: MarketVenueModel,
    val spreadMultiplier: Double,
    val depthMultiplier: Double,
    val preMarketOpensAt: LocalTime?,
    val afterHoursClosesAt: LocalTime?,
    val auctionDescription: String,
    val liquidityDescription: String,
    val gameAssumption: String,
) {
    init {
        require(spreadMultiplier > 0.0 && spreadMultiplier.isFinite())
        require(depthMultiplier > 0.0 && depthMultiplier.isFinite())
        require(auctionDescription.isNotBlank())
        require(liquidityDescription.isNotBlank())
        require(gameAssumption.isNotBlank())
    }
}

/** Venue rules used by the calendar and synthetic order-book generator. */
object MarketVenueProfiles {
    fun forMarket(market: Market): MarketVenueProfile = when (market) {
        Market.KOSPI -> MarketVenueProfile(
            market = market,
            marketModel = MarketVenueModel.KRX_CENTRAL_LIMIT_ORDER_BOOK,
            spreadMultiplier = 1.0,
            depthMultiplier = 1.0,
            preMarketOpensAt = null,
            afterHoursClosesAt = null,
            auctionDescription = "KRX 시가·종가 단일가와 정규장 연속매매를 단순화해 반영합니다.",
            liquidityDescription = "종목 거래량과 시가총액이 호가 깊이를 결정하며 venue 보정은 중립입니다.",
            gameAssumption = "기준 프로필: 스프레드 1.00배, 깊이 1.00배",
        )

        Market.KOSDAQ -> MarketVenueProfile(
            market = market,
            marketModel = MarketVenueModel.KRX_CENTRAL_LIMIT_ORDER_BOOK,
            spreadMultiplier = 1.0,
            depthMultiplier = 1.0,
            preMarketOpensAt = null,
            afterHoursClosesAt = null,
            auctionDescription = "KRX 시가·종가 단일가와 정규장 연속매매를 단순화해 반영합니다.",
            liquidityDescription = "종목 거래량과 시가총액이 호가 깊이를 결정하며 venue 보정은 중립입니다.",
            gameAssumption = "기준 프로필: 스프레드 1.00배, 깊이 1.00배",
        )

        Market.NASDAQ -> MarketVenueProfile(
            market = market,
            marketModel = MarketVenueModel.NASDAQ_ELECTRONIC_MARKET_MAKERS,
            spreadMultiplier = 0.95,
            depthMultiplier = 1.05,
            preMarketOpensAt = LocalTime(4, 0),
            afterHoursClosesAt = LocalTime(20, 0),
            auctionDescription = "전자식 opening/closing cross와 복수 마켓메이커 경쟁을 반영합니다.",
            liquidityDescription = "대형 성장주 중심의 전자 유동성을 기준보다 소폭 깊게 모델링합니다.",
            gameAssumption = "NASDAQ 보정: 스프레드 0.95배, 깊이 1.05배",
        )

        Market.NYSE -> MarketVenueProfile(
            market = market,
            marketModel = MarketVenueModel.NYSE_HYBRID_DMM_AUCTION,
            spreadMultiplier = 0.90,
            depthMultiplier = 1.15,
            preMarketOpensAt = null,
            afterHoursClosesAt = null,
            auctionDescription = "DMM이 관여하는 시가·종가 경매와 전자 연속매매를 함께 반영합니다.",
            liquidityDescription = "대형 상장주와 DMM의 완충 역할을 기준보다 깊은 유동성으로 모델링합니다.",
            gameAssumption = "NYSE primary 보정: 스프레드 0.90배, 깊이 1.15배; 06:30 pre-opening queue는 체결 세션으로 표시하지 않음",
        )

        Market.NYSE_ARCA -> MarketVenueProfile(
            market = market,
            marketModel = MarketVenueModel.NYSE_ARCA_ELECTRONIC_LMM,
            spreadMultiplier = 0.80,
            depthMultiplier = 1.35,
            preMarketOpensAt = LocalTime(4, 0),
            afterHoursClosesAt = LocalTime(20, 0),
            auctionDescription = "완전 전자식 경매와 ETF 중심의 lead market maker 구조를 반영합니다.",
            liquidityDescription = "대표 ETF의 LMM 유동성 공급을 더 촘촘하고 깊은 호가로 모델링합니다.",
            gameAssumption = "NYSE Arca ETF 보정: 스프레드 0.80배, 깊이 1.35배",
        )

        Market.CBOE_BZX -> MarketVenueProfile(
            market = market,
            marketModel = MarketVenueModel.CBOE_BZX_ELECTRONIC_MARKET_MAKERS,
            spreadMultiplier = 0.86,
            depthMultiplier = 1.24,
            preMarketOpensAt = LocalTime(4, 0),
            afterHoursClosesAt = LocalTime(20, 0),
            auctionDescription = "Cboe BZX의 전자식 주문장과 ETF 시장조성자 경쟁을 반영합니다.",
            liquidityDescription = "ETF·ETP 중심 유동성을 Arca보다 약간 얇지만 기준보다 깊게 모델링합니다.",
            gameAssumption = "Cboe BZX 보정: 스프레드 0.86배, 깊이 1.24배",
        )

        Market.NYSE_AMERICAN -> MarketVenueProfile(
            market = market,
            marketModel = MarketVenueModel.NYSE_AMERICAN_HYBRID_AUCTION,
            spreadMultiplier = 1.40,
            depthMultiplier = 0.65,
            preMarketOpensAt = LocalTime(7, 0),
            afterHoursClosesAt = LocalTime(20, 0),
            auctionDescription = "전자 주문장과 지정 마켓메이커가 결합된 시가·종가 경매를 반영합니다.",
            liquidityDescription = "상대적으로 작은 발행사 비중을 넓은 스프레드와 얕은 깊이 경향으로 모델링합니다.",
            gameAssumption = "NYSE American 보정: 스프레드 1.40배, 깊이 0.65배",
        )
    }
}
