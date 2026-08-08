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
